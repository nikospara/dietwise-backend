package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.StringUtils.limit;
import static eu.dietwise.services.v1.filtering.MarkdownBlockSegmenter.segment;
import static eu.dietwise.services.v1.types.RecipeDetectionType.JSONLD;
import static eu.dietwise.services.v1.types.RecipeDetectionType.LLM_FROM_TEXT;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.utils.UniComprehensions;
import eu.dietwise.services.model.RecipeExtractedFromInput;
import eu.dietwise.services.renderer.RenderRequest;
import eu.dietwise.services.renderer.RenderResponse;
import eu.dietwise.services.renderer.RendererClient;
import eu.dietwise.services.v1.RecipeAssessmentService;
import eu.dietwise.services.v1.extraction.NoRecipesDetectedException;
import eu.dietwise.services.v1.extraction.MarkdownRecipeExtractionService;
import eu.dietwise.services.v1.filtering.MarkdownBlockCoalescer;
import eu.dietwise.services.v1.filtering.MarkdownBlockSegmenter;
import eu.dietwise.services.v1.filtering.RecipeFilterAiService;
import eu.dietwise.services.v1.types.RecipeAndDetectionType;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.MoreThanOneRecipesAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeAssessmentErrorMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.ImmutableIngredient;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.ImmutableRecipeAssessmentParam;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.jboss.resteasy.reactive.RestResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RecipeAssessmentServiceImpl implements RecipeAssessmentService {
	private static final Logger LOG = LoggerFactory.getLogger(RecipeAssessmentServiceImpl.class);

	private final RecipeFilterAiService filterAiService;
	private final MarkdownRecipeExtractionService extractionService;
	private final RendererClient rendererClient;

	public RecipeAssessmentServiceImpl(RecipeFilterAiService filterAiService, MarkdownRecipeExtractionService extractionService, @RestClient RendererClient rendererClient) {
		this.filterAiService = filterAiService;
		this.extractionService = extractionService;
		this.rendererClient = rendererClient;
	}

	@Override
	public Multi<RecipeAssessmentMessage> assessMarkdownRecipe(RecipeAssessmentParam param) {
		var correlationId = UUID.randomUUID();
		LOG.info("assessMarkdownRecipe <{}> {} {}", correlationId, param.getUrl(), param.getLangCode());
		return Multi.createFrom().emitter(emitter -> {
			UniComprehensions.forc(
					useAiToExtractRecipeFromMarkdown(correlationId, param.getUrl(), param.getLangCode(), param.getPageContent()),
					emitRecipeExtractionMessageOrNoRecipesError(correlationId, param.getUrl(), emitter),
					assessSingleRecipe(correlationId, param.getUrl(), emitter)
			).subscribe().with(x -> emitter.complete(), handleError(emitter));
		});
	}

	@Override
	public Multi<RecipeAssessmentMessage> extractAndAssessRecipeFromUrl(RecipeExtractionAndAssessmentParam param) {
		var correlationId = UUID.randomUUID();
		LOG.info("extractAndAssessRecipeFromUrl <{}> {} {}", correlationId, param.getUrl(), param.getLangCode());
		return Multi.createFrom().emitter(emitter -> {
			var renderRequest = new RenderRequest(param.getUrl(), true, true, 30000, param.getViewport().orElse(null), false, true);
			UniComprehensions.forc(
					rendererClient.render(renderRequest),
					sanitizeRenderResponse(correlationId, param),
					extractRecipesEitherFromJsonLdOrFromAi(correlationId, param),
					emitRecipeExtractionMessageOrNoRecipesError(correlationId, param.getUrl(), emitter),
					assessSingleRecipe(correlationId, param.getUrl(), emitter)
			).subscribe().with(x -> emitter.complete(), handleError(emitter));
		});
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
				return useAiToExtractRecipeFromMarkdown(correlationId, param.getUrl(), param.getLangCode(), markdown);
			}
		};
	}

	private Uni<RecipeExtractionRecipeAssessmentMessage> useAiToExtractRecipeFromMarkdown(UUID correlationId, String url, String langCode, String markdown) {
		return keepOnlyRelevantPageContent(markdown, langCode)
				.map(filteredContent -> ImmutableRecipeAssessmentParam.builder().url(url).langCode(langCode).pageContent(filteredContent).build())
				.flatMap(this::extractRecipeFromMarkdown)
				.flatMap(recipe -> failIfRecipeHasNoIngredients(correlationId, url, recipe))
				.map(llmResponse -> convertLlmResponseToRecipes(llmResponse, markdown));
	}

	private Uni<Recipe> failIfRecipeHasNoIngredients(UUID correlationId, String url, Recipe recipe) {
		List<Ingredient> filteredIngredients = recipe.getRecipeIngredients().stream().filter(Ingredient::hasName).toList();
		if (filteredIngredients.isEmpty()) {
			LOG.warn("AI extracted recipe without ingredients <{}> in {}", correlationId, url);
			return Uni.createFrom().failure(new NoRecipesDetectedException());
		}
		var recipeToReturn = filteredIngredients.size() == recipe.getRecipeIngredients().size()
				? recipe
				: ImmutableRecipe.copyOf(recipe).withRecipeIngredients(filteredIngredients);
		return Uni.createFrom().item(recipeToReturn);
	}

	private Function<? super RecipeExtractionRecipeAssessmentMessage, Uni<? extends RecipeExtractionRecipeAssessmentMessage>> emitRecipeExtractionMessageOrNoRecipesError(
			UUID correlationId, String url, MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return recipeExtractionRecipeAssessmentMessage -> {
			if (recipeExtractionRecipeAssessmentMessage.recipes() == null || recipeExtractionRecipeAssessmentMessage.recipes().isEmpty()) {
				LOG.warn("No recipes detected <{}> in {}", correlationId, url);
				throw new NoRecipesDetectedException();
			} else {
				emitter.emit(recipeExtractionRecipeAssessmentMessage);
				return Uni.createFrom().item(recipeExtractionRecipeAssessmentMessage);
			}
		};
	}

	private Function<? super RecipeExtractionRecipeAssessmentMessage, Uni<? extends SuggestionsRecipeAssessmentMessage>> assessSingleRecipe(
			UUID correlationId, String url, MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return recipeExtractionRecipeAssessmentMessage -> {
			int numberOfRecipes = recipeExtractionRecipeAssessmentMessage.recipes().size();
			if (numberOfRecipes != 1) {
				LOG.warn("More than one recipe detected, will return error <{}> URL: {} ({} in total)", correlationId, url, numberOfRecipes);
				// this signals handleError to emit MoreThanOneRecipesAssessmentMessage
				return Uni.createFrom().failure(new MoreThanOneRecipesDetectedException(numberOfRecipes));
			} else {
				// TODO Call the actual assessment service here - probably use a separate helper service
				Recipe recipe = recipeExtractionRecipeAssessmentMessage.recipes().getFirst().recipe();
				return Uni.createFrom().item(makeDummySuggestionsRecipeAssessmentMessage(recipe))
						.onItem().delayIt().by(Duration.ofSeconds(2L)) // DUMMY for initial testing/demos
						.invoke(emitter::emit);
			}
		};
	}

	@Override
	public Multi<RecipeAssessmentMessage> extractAndAssessRecipeFromUrlDummy(RecipeExtractionAndAssessmentParam param) {
		var recipe = makeDummyRecipe();
		var recipeMsg = new RecipeExtractionRecipeAssessmentMessage(List.of(new RecipeAndDetectionType(recipe, JSONLD)), "dummy page text");
		double rating = new Random().nextInt(10) / 2.0;
		List<Suggestion> suggestions = List.of(
				dummyFromTextOnly(recipe, "Dummy suggestion one"),
				dummyFromTextOnly(recipe, "Dummy suggestion two")
		);
		var suggestionsMsg = new SuggestionsRecipeAssessmentMessage(rating, suggestions);
		return Multi.createFrom().<RecipeAssessmentMessage>items(recipeMsg, suggestionsMsg)
				.onItem().call(m -> Uni.createFrom().nullItem().onItem().delayIt().by(Duration.ofSeconds(3L)));
	}

	private Recipe makeDummyRecipe() {
		return ImmutableRecipe.builder()
				.text("""
						This is a dummy recipe, just for testing purposes
						Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum.
						Ed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?
						""")
				.addRecipeIngredients(
						ImmutableIngredient.builder().nameInRecipe("800 g ground beef").build(),
						ImmutableIngredient.builder().nameInRecipe("5 slices sandwich bread, 120 g").build(),
						ImmutableIngredient.builder().nameInRecipe("¼ bunch parsley").build(),
						ImmutableIngredient.builder().nameInRecipe("5-6 mint leaves").build(),
						ImmutableIngredient.builder().nameInRecipe("1 tablespoon(s) ketchup").build(),
						ImmutableIngredient.builder().nameInRecipe("1 egg, medium").build(),
						ImmutableIngredient.builder().nameInRecipe("1 onion").build(),
						ImmutableIngredient.builder().nameInRecipe("150g cheddar, grated").build(),
						ImmutableIngredient.builder().nameInRecipe("100g cream cheese").build()
				)
				.build();
	}

	private Uni<String> keepOnlyRelevantPageContent(String pageTextAsMarkdown, String langCode) {
		List<MarkdownBlockSegmenter.Block> segmentedContent = segment(pageTextAsMarkdown);
		List<String> blocks = MarkdownBlockCoalescer.coalesce(segmentedContent, 5000);
		return Multi.createFrom().iterable(blocks).onItem().transformToUniAndConcatenate(this::filterRecipeBlock)
				.filter(block -> !block.isBlank())
				.collect().asList()
				.map(filteredBlocks -> String.join("\n\n", filteredBlocks));
	}

	private Uni<String> filterRecipeBlock(String block) {
		return Uni.createFrom().item(() -> filterAiService.filterRecipeBlock(block))
				.runSubscriptionOn(Infrastructure.getDefaultExecutor())
				.map(result -> result == null ? "" : result.trim().toUpperCase()) // sanitize
				.map(result -> "KEEP".equals(result) ? block : "");
	}

	private RecipeExtractionRecipeAssessmentMessage convertLlmResponseToRecipes(Recipe recipe, String pageText) {
		return new RecipeExtractionRecipeAssessmentMessage(List.of(new RecipeAndDetectionType(recipe, LLM_FROM_TEXT)), pageText);
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

	private SuggestionsRecipeAssessmentMessage makeDummySuggestionsRecipeAssessmentMessage(Recipe recipe) {
		double rating = new Random().nextInt(10) / 2.0;
		List<Suggestion> suggestions = List.of(
				dummyFromTextOnly(recipe, "Coming from the server - this is just a dummy, placeholder response"),
//				dummyFromTextOnly(recipe, "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."),
//				dummyFromTextOnly(recipe, "Ed ut perspiciatis unde omnis iste natus error sit voluptatem accusantium doloremque laudantium, totam rem aperiam, eaque ipsa quae ab illo inventore veritatis et quasi architecto beatae vitae dicta sunt explicabo. Nemo enim ipsam voluptatem quia voluptas sit aspernatur aut odit aut fugit, sed quia consequuntur magni dolores eos qui ratione voluptatem sequi nesciunt. Neque porro quisquam est, qui dolorem ipsum quia dolor sit amet, consectetur, adipisci velit, sed quia non numquam eius modi tempora incidunt ut labore et dolore magnam aliquam quaerat voluptatem. Ut enim ad minima veniam, quis nostrum exercitationem ullam corporis suscipit laboriosam, nisi ut aliquid ex ea commodi consequatur? Quis autem vel eum iure reprehenderit qui in ea voluptate velit esse quam nihil molestiae consequatur, vel illum qui dolorem eum fugiat quo voluptas nulla pariatur?"),
				dummyFromTextOnly(recipe, "At vero eos et accusamus et iusto odio dignissimos ducimus qui blanditiis praesentium voluptatum deleniti atque corrupti quos dolores et quas molestias excepturi sint occaecati cupiditate non provident, similique sunt in culpa qui officia deserunt mollitia animi, id est laborum et dolorum fuga. Et harum quidem rerum facilis est et expedita distinctio. Nam libero tempore, cum soluta nobis est eligendi optio cumque nihil impedit quo minus id quod maxime placeat facere possimus, omnis voluptas assumenda est, omnis dolor repellendus. Temporibus autem quibusdam et aut officiis debitis aut rerum necessitatibus saepe eveniet ut et voluptates repudiandae sint et molestiae non recusandae. Itaque earum rerum hic tenetur a sapiente delectus, ut aut reiciendis voluptatibus maiores alias consequatur aut perferendis doloribus asperiores repellat.")
		);
//		throw new RuntimeException("Testing exception");
		return new SuggestionsRecipeAssessmentMessage(rating, suggestions);
	}

	private Suggestion dummyFromTextOnly(Recipe recipe, String text) {
		var rand = new Random();
		var ingredient = recipe.getRecipeIngredients().get(rand.nextInt(recipe.getRecipeIngredients().size()));
		return ImmutableSuggestion.builder()
				.id(new GenericSuggestionTemplateId(UUID.randomUUID().toString()))
				.alternative(new AlternativeIngredientImpl("alternative"))
				.target(new AppliesTo.AppliesToIngredient(ingredient.getId()))
				.ruleId(new GenericRuleId("7"))
				.recommendation(new RecommendationImpl("Increase milk"))
				.text(text)
				.build();
	}

	private Consumer<Throwable> handleError(MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return error -> {
			if (error instanceof NoRecipesDetectedException) {
				emitter.emit(new RecipeAssessmentErrorMessage(List.of("No recipes detected on the page")));
			} else if (error instanceof MoreThanOneRecipesDetectedException mto) {
				emitter.emit(new MoreThanOneRecipesAssessmentMessage(mto.getNumberOfRecipes()));
			} else {
				LOG.error("The server failed to assess the recipe", error);
				emitter.emit(new RecipeAssessmentErrorMessage(List.of("The server failed to assess the recipe")));
			}
			emitter.complete();
		};
	}

	/**
	 * @deprecated The rendering service returns appropriate messages and this left unused; keeping around because we may reconsider
	 */
	@Deprecated(forRemoval = true)
	private Function<ClientWebApplicationException, Multi<RecipeAssessmentMessage>> handleHtmlExtractionError(RecipeExtractionAndAssessmentParam param) {
		return e -> {
			LOG.error("Could not read the page at {}", param.getUrl(), e);
			return Multi.createFrom().item(new RecipeAssessmentErrorMessage(List.of("Could not read the page")));
		};
	}
}
