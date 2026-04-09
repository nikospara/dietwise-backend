package eu.dietwise.services.v1.extraction.impl;

import static eu.dietwise.common.utils.StringUtils.limit;
import static eu.dietwise.services.v1.filtering.MarkdownBlockSegmenter.segment;
import static eu.dietwise.services.v1.types.RecipeDetectionType.JSONLD;
import static eu.dietwise.services.v1.types.RecipeDetectionType.LLM_FROM_TEXT;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.utils.UniComprehensions;
import eu.dietwise.services.model.RecipeExtractedFromInput;
import eu.dietwise.services.renderer.RenderRequest;
import eu.dietwise.services.renderer.RenderResponse;
import eu.dietwise.services.renderer.RendererClient;
import eu.dietwise.services.v1.extraction.MarkdownRecipeExtractionService;
import eu.dietwise.services.v1.extraction.NoIngredientsInRecipeException;
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

	public RecipeExtractionServiceImpl(
			RecipeFilterAiFacade filterAiFacade,
			MarkdownRecipeExtractionService extractionService,
			@ConfigProperty(name = "dietwise.renderer.allow-cached-test-pages", defaultValue = "false") boolean allowCachedTestPages,
			@RestClient RendererClient rendererClient
	) {
		this.filterAiFacade = filterAiFacade;
		this.extractionService = extractionService;
		this.allowCachedTestPages = allowCachedTestPages;
		this.rendererClient = rendererClient;
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
		return Multi.createFrom().iterable(blocks).onItem().transformToUniAndConcatenate(this::filterRecipeBlock)
				.filter(block -> !block.isBlank())
				.collect().asList()
				.map(filteredBlocks -> String.join("\n\n", filteredBlocks));
	}

	private Uni<String> filterRecipeBlock(String block) {
		return filterAiFacade.filterRecipeBlock(block)
				.map(result -> result == null ? "" : result.trim().toUpperCase()) // sanitize
				.map(result -> "KEEP".equals(result) ? block : "");
	}

	private Uni<Recipe> extractRecipeFromMarkdown(RecipeAssessmentParam param) {
		return extractionService.extractRecipeFromMarkdown(param.getPageContent())
				.map(this::toRecipeWithOnlyIngredientNames);
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
					: sanitizeRecipes(correlationId, param, renderResponse.jsonLdRecipes());
			return Uni.createFrom().item(new RenderResponse(renderResponse.output(), sanitizedJsonLdRecipes, renderResponse.finalUrl(), renderResponse.screenshot()));
		};
	}

	private List<RecipeExtractedFromInput> sanitizeRecipes(UUID correlationId, RecipeExtractionAndAssessmentParam param, List<RecipeExtractedFromInput> recipes) {
		return recipes.stream()
				.map(this::sanitizeRecipe)
				.filter(keepRecipeWithValidIngredients(correlationId, param))
				.toList();
	}

	private RecipeExtractedFromInput sanitizeRecipe(RecipeExtractedFromInput recipe) {
		List<String> filteredIngredients = recipe.recipeIngredients().stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).toList();
		return filteredIngredients.size() == recipe.recipeIngredients().size()
				? recipe
				: new RecipeExtractedFromInput(recipe.name(), recipe.recipeYield(), filteredIngredients, recipe.recipeInstructions(), recipe.text());
	}

	private Predicate<? super RecipeExtractedFromInput> keepRecipeWithValidIngredients(UUID correlationId, RecipeExtractionAndAssessmentParam param) {
		return recipe -> {
			if (recipe.recipeIngredients().isEmpty()) {
				LOG.warn("Recipe contains no ingredients <{}> {} in {}", correlationId, recipe.name(), param.getUrl());
				return false;
			} else {
				return true;
			}
		};
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
