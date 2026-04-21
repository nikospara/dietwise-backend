package eu.dietwise.services.v1.extraction.impl;

import static eu.dietwise.common.utils.StringUtils.limit;
import static eu.dietwise.services.v1.filtering.MarkdownBlockSegmenter.segment;
import static eu.dietwise.services.v1.types.RecipeDetectionType.JSONLD;
import static eu.dietwise.services.v1.types.RecipeDetectionType.LLM_FROM_TEXT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import jakarta.enterprise.context.ApplicationScoped;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.common.utils.UniComprehensions;
import eu.dietwise.services.model.RecipeExtractedFromInput;
import eu.dietwise.services.renderer.RenderRequest;
import eu.dietwise.services.renderer.RenderResponse;
import eu.dietwise.services.renderer.RendererClient;
import eu.dietwise.services.v1.extraction.MarkdownRecipeExtractionService;
import eu.dietwise.services.v1.extraction.NoIngredientsInRecipeException;
import eu.dietwise.services.v1.extraction.RecipeJsonCoercionStrategy;
import eu.dietwise.services.v1.extraction.RecipeExtractionService;
import eu.dietwise.services.v1.filtering.MarkdownBlockCoalescer;
import eu.dietwise.services.v1.filtering.MarkdownBlockSegmenter;
import eu.dietwise.services.v1.filtering.RecipeFilterAiFacade;
import eu.dietwise.services.v1.types.RecipeAndDetectionType;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableIngredient;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.ImmutableRecipeAssessmentParam;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RecipeExtractionServiceImpl implements RecipeExtractionService {
	private static final Logger LOG = LoggerFactory.getLogger(RecipeExtractionServiceImpl.class);

	private final RecipeFilterAiFacade filterAiFacade;
	private final MarkdownRecipeExtractionService extractionService;
	private final RendererClient rendererClient;
	private final boolean allowCachedTestPages;
	private final ObjectMapper objectMapper;
	private final RecipeJsonCoercionStrategy recipeJsonCoercionStrategy;

	public RecipeExtractionServiceImpl(
			RecipeFilterAiFacade filterAiFacade,
			MarkdownRecipeExtractionService extractionService,
			@ConfigProperty(name = "dietwise.renderer.allow-cached-test-pages", defaultValue = "false") boolean allowCachedTestPages,
			@RestClient RendererClient rendererClient,
			ObjectMapper objectMapper,
			RecipeJsonCoercionStrategy recipeJsonCoercionStrategy
	) {
		this.filterAiFacade = filterAiFacade;
		this.extractionService = extractionService;
		this.allowCachedTestPages = allowCachedTestPages;
		this.rendererClient = rendererClient;
		this.objectMapper = objectMapper;
		this.recipeJsonCoercionStrategy = recipeJsonCoercionStrategy;
	}

	@Override
	public Uni<RecipeExtractionRecipeAssessmentMessage> extractRecipeFromJsonLdOrMarkdown(UUID correlationId, RecipeAssessmentParam param) {
		String jsonLdContent = param.getJsonLdContent();
		if (jsonLdContent == null || jsonLdContent.isBlank()) {
			return useAiToExtractRecipeFromMarkdown(correlationId, param.getUrl(), param.getLang(), param.getPageContent());
		}
		LOG.info("Will try JSON-LD content first <{}> for {}", correlationId, param.getUrl());
		return extractRecipesFromJsonLd(correlationId, param)
				.onFailure().recoverWithUni(error -> {
					LOG.warn("JSON-LD content was not usable, will fall back to page content <{}> for {}", correlationId, param.getUrl(), error);
					return useAiToExtractRecipeFromMarkdown(correlationId, param.getUrl(), param.getLang(), param.getPageContent());
				})
				.flatMap(message -> {
					if (message.recipes() == null || message.recipes().isEmpty()) {
						LOG.info("JSON-LD content produced no recipes, will fall back to page content <{}> for {}", correlationId, param.getUrl());
						return useAiToExtractRecipeFromMarkdown(correlationId, param.getUrl(), param.getLang(), param.getPageContent());
					} else {
						return Uni.createFrom().item(message);
					}
				});
	}

	@Override
	public Uni<RecipeExtractionRecipeAssessmentMessage> useAiToExtractRecipeFromMarkdown(UUID correlationId, String url, RecipeLanguage lang, String markdown) {
		return keepOnlyRelevantPageContent(markdown, lang)
				.map(filteredContent -> ImmutableRecipeAssessmentParam.builder().url(url).lang(lang).pageContent(filteredContent).build())
				.flatMap(this::extractRecipeFromMarkdown)
				.flatMap(recipe -> failIfRecipeHasNoIngredients(correlationId, url, recipe))
				.map(llmResponse -> convertLlmResponseToRecipes(llmResponse, markdown));
	}

	@Override
	public Uni<RecipeExtractionRecipeAssessmentMessage> extractRecipeFromUrl(UUID correlationId, RecipeExtractionAndAssessmentParam param) {
		RecipeSourceUrlValidator.validateHttpPublicUrl(param.getUrl(), allowCachedTestPages);
		var renderRequest = new RenderRequest(param.getUrl(), true, true, 30000, param.getViewport().orElse(null), false, true);
		return UniComprehensions.forc(
				rendererClient.render(renderRequest),
				sanitizeRenderResponse(correlationId, param),
				extractRecipesEitherFromJsonLdOrFromAi(correlationId, param)
		);
	}

	private Uni<String> keepOnlyRelevantPageContent(String pageTextAsMarkdown, RecipeLanguage lang) {
		List<MarkdownBlockSegmenter.Block> segmentedContent = segment(pageTextAsMarkdown);
		List<String> blocks = MarkdownBlockCoalescer.coalesce(segmentedContent, 5000);
		return Multi.createFrom().iterable(blocks).onItem().transformToUniAndConcatenate(block -> filterRecipeBlock(lang, block))
				.filter(block -> !block.isBlank())
				.collect().asList()
				.map(filteredBlocks -> String.join("\n\n", filteredBlocks));
	}

	private Uni<String> filterRecipeBlock(RecipeLanguage lang, String block) {
		return filterAiFacade.filterRecipeBlock(lang, block)
				.map(result -> result == null ? "" : result.trim().toUpperCase()) // sanitize
				.map(result -> "KEEP".equals(result) ? block : "");
	}

	private Uni<Recipe> extractRecipeFromMarkdown(RecipeAssessmentParam param) {
		return extractionService.extractRecipeFromMarkdown(param.getLang(), param.getPageContent())
				.map(this::toRecipeWithOnlyIngredientNames);
	}

	private Uni<RecipeExtractionRecipeAssessmentMessage> extractRecipesFromJsonLd(UUID correlationId, RecipeAssessmentParam param) {
		return Uni.createFrom().item(() -> {
			List<RecipeExtractedFromInput> jsonLdRecipes;
			try {
				jsonLdRecipes = sanitizeRecipes(correlationId, param.getUrl(), parseJsonLdRecipes(param.getJsonLdContent()));
			} catch (JsonProcessingException e) {
				throw new IllegalArgumentException("Invalid JSON-LD", e);
			}
			if (!jsonLdRecipes.isEmpty()) {
				LOG.info("Found JSON-LD format recipes <{}> in {} ({} in total)", correlationId, param.getUrl(), jsonLdRecipes.size());
				LOG.debug("JSON-LD recipes: {}", jsonLdRecipes);
			}
			var recipes = jsonLdRecipes.stream()
					.map(this::toRecipeWithOnlyIngredientNames)
					.map(r -> new RecipeAndDetectionType(r, JSONLD))
					.toList();
			return new RecipeExtractionRecipeAssessmentMessage(recipes, param.getPageContent());
		});
	}

	private Recipe toRecipeWithOnlyIngredientNames(RecipeExtractedFromInput inputRecipe) {
		return ImmutableRecipe.builder()
				.name(inputRecipe.name())
				.recipeYield(inputRecipe.recipeYield())
				.recipeIngredients(inputRecipe.recipeIngredients().stream().map(this::fromName).toList())
				.recipeInstructions(inputRecipe.recipeInstructions())
				.text(inputRecipe.text())
				.build();
	}

	private Ingredient fromName(String name) {
		return ImmutableIngredient.builder().id(new GenericIngredientId(UUID.randomUUID().toString())).nameInRecipe(name).build();
	}

	private Uni<Recipe> failIfRecipeHasNoIngredients(UUID correlationId, String url, Recipe recipe) {
		List<Ingredient> filteredIngredients = recipe.getRecipeIngredients().stream().filter(Ingredient::hasName).toList();
		if (filteredIngredients.isEmpty()) {
			LOG.warn("AI extracted recipe without ingredients <{}> in {}", correlationId, url);
			return Uni.createFrom().failure(new NoIngredientsInRecipeException());
		}
		var recipeToReturn = filteredIngredients.size() == recipe.getRecipeIngredients().size()
				? recipe
				: ImmutableRecipe.copyOf(recipe).withRecipeIngredients(filteredIngredients);
		return Uni.createFrom().item(recipeToReturn);
	}

	private RecipeExtractionRecipeAssessmentMessage convertLlmResponseToRecipes(Recipe recipe, String pageText) {
		return new RecipeExtractionRecipeAssessmentMessage(List.of(new RecipeAndDetectionType(recipe, LLM_FROM_TEXT)), pageText);
	}

	private Function<? super RestResponse<RenderResponse>, Uni<? extends RenderResponse>> sanitizeRenderResponse(
			UUID correlationId, RecipeExtractionAndAssessmentParam param) {
		return restRenderResponse -> {
			RenderResponse renderResponse = restRenderResponse.getEntity();
			List<RecipeExtractedFromInput> sanitizedJsonLdRecipes = renderResponse.jsonLdRecipes() == null
					? Collections.emptyList()
					: sanitizeRecipes(correlationId, param.getUrl(), renderResponse.jsonLdRecipes());
			return Uni.createFrom().item(new RenderResponse(renderResponse.output(), sanitizedJsonLdRecipes, renderResponse.finalUrl(), renderResponse.screenshot()));
		};
	}

	private List<RecipeExtractedFromInput> sanitizeRecipes(UUID correlationId, String url, List<RecipeExtractedFromInput> recipes) {
		return recipes.stream()
				.map(this::sanitizeRecipe)
				.filter(keepRecipeWithValidIngredients(correlationId, url))
				.toList();
	}

	private RecipeExtractedFromInput sanitizeRecipe(RecipeExtractedFromInput recipe) {
		List<String> filteredIngredients = recipe.recipeIngredients().stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).toList();
		return filteredIngredients.size() == recipe.recipeIngredients().size()
				? recipe
				: new RecipeExtractedFromInput(recipe.name(), recipe.recipeYield(), filteredIngredients, recipe.recipeInstructions(), recipe.text());
	}

	private Predicate<? super RecipeExtractedFromInput> keepRecipeWithValidIngredients(UUID correlationId, String url) {
		return recipe -> {
			if (recipe.recipeIngredients().isEmpty()) {
				LOG.warn("Recipe contains no ingredients <{}> {} in {}", correlationId, recipe.name(), url);
				return false;
			} else {
				return true;
			}
		};
	}

	private List<RecipeExtractedFromInput> parseJsonLdRecipes(String jsonLdContent) throws JsonProcessingException {
		JsonNode root = objectMapper.readTree(jsonLdContent);
		List<JsonNode> recipeNodes = new ArrayList<>();
		collectRecipeNodes(root, recipeNodes);
		return recipeNodes.stream().map(this::toRecipeExtractedFromInput).toList();
	}

	private void collectRecipeNodes(JsonNode node, List<JsonNode> recipeNodes) {
		if (node == null || node.isNull()) {
			return;
		}
		if (isRecipeNode(node)) {
			recipeNodes.add(node);
		}
		if (node.isArray()) {
			node.forEach(item -> collectRecipeNodes(item, recipeNodes));
		} else if (node.isObject()) {
			node.properties().forEach(entry -> collectRecipeNodes(entry.getValue(), recipeNodes));
		}
	}

	private boolean isRecipeNode(JsonNode node) {
		if (!node.isObject()) {
			return false;
		}
		JsonNode typeNode = node.get("@type");
		if (typeNode == null || typeNode.isNull()) {
			return false;
		}
		if (typeNode.isTextual()) {
			return isRecipeType(typeNode.textValue());
		}
		if (typeNode.isArray()) {
			for (JsonNode item : typeNode) {
				if (item.isTextual() && isRecipeType(item.textValue())) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isRecipeType(String type) {
		return type != null && ("Recipe".equals(type) || type.endsWith("/Recipe") || type.endsWith(":Recipe"));
	}

	private RecipeExtractedFromInput toRecipeExtractedFromInput(JsonNode recipeNode) {
		return new RecipeExtractedFromInput(
				optionalText(recipeNode.get("name")),
				optionalText(recipeNode.get("recipeYield")),
				ingredients(recipeNode),
				instructions(recipeNode.get("recipeInstructions")),
				optionalText(recipeNode.get("description"))
		);
	}

	private Optional<String> optionalText(JsonNode node) {
		if (node == null || node.isNull()) {
			return Optional.empty();
		}
		String text = recipeJsonCoercionStrategy.coerce(node, objectMapper).trim();
		return text.isEmpty() ? Optional.empty() : Optional.of(text);
	}

	private List<String> ingredients(JsonNode recipeNode) {
		JsonNode ingredientsNode = recipeNode.get("recipeIngredient");
		if (ingredientsNode == null || ingredientsNode.isNull()) {
			ingredientsNode = recipeNode.get("ingredients");
		}
		return stringsFromNode(ingredientsNode);
	}

	private List<String> instructions(JsonNode instructionsNode) {
		return stringsFromNode(instructionsNode);
	}

	private List<String> stringsFromNode(JsonNode node) {
		if (node == null || node.isNull()) {
			return List.of();
		}
		if (node.isArray()) {
			List<String> values = new ArrayList<>();
			node.forEach(item -> {
				String value = recipeJsonCoercionStrategy.coerce(item, objectMapper).trim();
				if (!value.isEmpty()) {
					values.add(value);
				}
			});
			return values;
		}
		String value = recipeJsonCoercionStrategy.coerce(node, objectMapper).trim();
		return value.isEmpty() ? List.of() : List.of(value);
	}

	private Function<? super RenderResponse, Uni<? extends RecipeExtractionRecipeAssessmentMessage>> extractRecipesEitherFromJsonLdOrFromAi(
			UUID correlationId, RecipeExtractionAndAssessmentParam param) {
		return renderResponse -> {
			if (renderResponse.jsonLdRecipes() != null && !renderResponse.jsonLdRecipes().isEmpty()) {
				LOG.info("Found JSON-LD format recipes <{}> in {} ({} in total)", correlationId, param.getUrl(), renderResponse.jsonLdRecipes().size());
				LOG.debug("JSON-LD recipes: {}", renderResponse.jsonLdRecipes());
				var recipes = renderResponse.jsonLdRecipes().stream()
						.map(this::toRecipeWithOnlyIngredientNames)
						.map(r -> new RecipeAndDetectionType(r, JSONLD))
						.toList();
				return Uni.createFrom().item(new RecipeExtractionRecipeAssessmentMessage(recipes, renderResponse.output()));
			} else {
				LOG.info("No JSON-LD content, will use AI <{}> for {}", correlationId, param.getUrl());
				String markdown = renderResponse.output();
				LOG.debug("Page content: (length {}): {}", markdown.length(), limit(markdown, 1000));
				return useAiToExtractRecipeFromMarkdown(correlationId, param.getUrl(), param.getLang(), markdown);
			}
		};
	}
}
