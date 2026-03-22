package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.UniComprehensions.forc;
import static eu.dietwise.services.v1.types.RecipeDetectionType.JSONLD;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.services.v1.RecipeAssessmentService;
import eu.dietwise.services.v1.StatisticsService;
import eu.dietwise.services.v1.extraction.NoRecipesDetectedException;
import eu.dietwise.services.v1.extraction.RecipeExtractionService;
import eu.dietwise.services.v1.scoring.RecipeScoringService;
import eu.dietwise.services.v1.suggestions.MakeSuggestionsResult;
import eu.dietwise.services.v1.suggestions.RecipeSuggestionsService;
import eu.dietwise.services.v1.types.RecipeAndDetectionType;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.MoreThanOneRecipesAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeAssessmentErrorMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.ScoringRecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.ImmutableIngredient;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.jboss.resteasy.reactive.ClientWebApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RecipeAssessmentServiceImpl implements RecipeAssessmentService {
	private static final Logger LOG = LoggerFactory.getLogger(RecipeAssessmentServiceImpl.class);

	private final RecipeExtractionService recipeExtractionService;
	private final StatisticsService statisticsService;
	private final RecipeSuggestionsService recipeSuggestionsService;
	private final RecipeScoringService recipeScoringService;

	public RecipeAssessmentServiceImpl(
			RecipeExtractionService recipeExtractionService,
			StatisticsService statisticsService,
			RecipeSuggestionsService recipeSuggestionsService,
			RecipeScoringService recipeScoringService
	) {
		this.recipeExtractionService = recipeExtractionService;
		this.statisticsService = statisticsService;
		this.recipeSuggestionsService = recipeSuggestionsService;
		this.recipeScoringService = recipeScoringService;
	}

	@Override
	public Multi<RecipeAssessmentMessage> assessMarkdownRecipe(User user, RecipeAssessmentParam param) {
		var correlationId = UUID.randomUUID();
		LOG.info("assessMarkdownRecipe <{}> {} {}", correlationId, param.getUrl(), param.getLangCode());
		String applicationId = user.getApplicationId().orElseThrow(IllegalStateException::new);
		return Multi.createFrom().emitter(emitter ->
				Uni.combine().all()
						.unis(
								statisticsService.assessedRecipe(user),
								assessMarkdownRecipeInternal(correlationId, applicationId, user, param, emitter)
						).with((x, message) -> message)
						.subscribe().with(x -> emitter.complete(), handleError(emitter))
		);
	}

	private Uni<? extends RecipeAssessmentMessage> assessMarkdownRecipeInternal(
			UUID correlationId, String applicationId, HasUserId hasUserId, RecipeAssessmentParam param, MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return forc(
				recipeExtractionService.useAiToExtractRecipeFromMarkdown(correlationId, param.getUrl(), param.getLangCode(), param.getPageContent()),
				emitRecipeExtractionMessageOrNoRecipesError(correlationId, param.getUrl(), emitter),
				assessSingleRecipe(correlationId, applicationId, hasUserId, param.getUrl(), emitter),
				calculateScoreData(emitter)
		);
	}

	@Override
	public Multi<RecipeAssessmentMessage> extractAndAssessRecipeFromUrl(User user, RecipeExtractionAndAssessmentParam param) {
		var correlationId = UUID.randomUUID();
		LOG.info("extractAndAssessRecipeFromUrl <{}> {} {}", correlationId, param.getUrl(), param.getLangCode());
		String applicationId = user.getApplicationId().orElseThrow(IllegalStateException::new);
		return Multi.createFrom().emitter(emitter ->
				Uni.combine().all()
						.unis(
								statisticsService.assessedRecipe(user),
								extractAndAssessRecipeFromUrlInternal(correlationId, applicationId, user, param, emitter)
						).with((x, message) -> message)
						.subscribe().with(x -> emitter.complete(), handleError(emitter))
		);
	}

	private Uni<? extends RecipeAssessmentMessage> extractAndAssessRecipeFromUrlInternal(
			UUID correlationId, String applicationId, HasUserId hasUserId, RecipeExtractionAndAssessmentParam param, MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return forc(
				recipeExtractionService.extractRecipeFromUrl(correlationId, param),
				emitRecipeExtractionMessageOrNoRecipesError(correlationId, param.getUrl(), emitter),
				assessSingleRecipe(correlationId, applicationId, hasUserId, param.getUrl(), emitter),
				calculateScoreData(emitter)
		);
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

	private Function<? super RecipeExtractionRecipeAssessmentMessage, Uni<? extends MakeSuggestionsResult>> assessSingleRecipe(
			UUID correlationId, String applicationId, HasUserId hasUserId, String url, MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return recipeExtractionRecipeAssessmentMessage -> {
			int numberOfRecipes = recipeExtractionRecipeAssessmentMessage.recipes().size();
			if (numberOfRecipes != 1) {
				LOG.warn("More than one recipe detected, will return error <{}> URL: {} ({} in total)", correlationId, url, numberOfRecipes);
				// this signals handleError to emit MoreThanOneRecipesAssessmentMessage
				return Uni.createFrom().failure(new MoreThanOneRecipesDetectedException(numberOfRecipes));
			} else {
				Recipe recipe = recipeExtractionRecipeAssessmentMessage.recipes().getFirst().recipe();
				return recipeSuggestionsService.makeSuggestions(hasUserId, recipe)
						.call(result -> recipeSuggestionsService.increaseTimesSuggested(correlationId, applicationId, hasUserId, result.message()))
						.flatMap(result -> recipeSuggestionsService.enrichWithStatistics(correlationId, applicationId, hasUserId, result.message())
								.map(message -> new MakeSuggestionsResult(message, result.recommendations()))
						)
						.invoke(result -> emitter.emit(result.message()));
			}
		};
	}

	private BiFunction<? super RecipeExtractionRecipeAssessmentMessage, ? super MakeSuggestionsResult, Uni<? extends ScoringRecipeAssessmentMessage>> calculateScoreData(
			MultiEmitter<? super RecipeAssessmentMessage> emitter) {
		return (message, suggestionsResult) ->
				recipeScoringService.makeScoringMessage(suggestionsResult.recommendations())
						.invoke(emitter::emit);
	}

	@Override
	public Multi<RecipeAssessmentMessage> extractAndAssessRecipeFromUrlDummy(User user, RecipeExtractionAndAssessmentParam param) {
		var recipe = makeDummyRecipe();
		var recipeMsg = new RecipeExtractionRecipeAssessmentMessage(List.of(new RecipeAndDetectionType(recipe, JSONLD)), "dummy page text");
		List<Suggestion> suggestions = List.of(
				dummyFromTextOnly(recipe, "Dummy suggestion one"),
				dummyFromTextOnly(recipe, "Dummy suggestion two")
		);
		var suggestionsMsg = new SuggestionsRecipeAssessmentMessage(suggestions);
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
				// TODO Create dedicated exceptions for extraction failures, suggestion failures
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
