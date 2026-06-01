package eu.dietwise.services.v1.impl;

import static eu.dietwise.common.utils.MultiComprehensions.emitInSequence;
import static eu.dietwise.common.utils.UniComprehensions.forcm;
import static eu.dietwise.services.v1.types.RecipeDetectionType.JSONLD;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import jakarta.enterprise.context.ApplicationScoped;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.v1.RecipeAssessmentService;
import eu.dietwise.services.v1.StatisticsService;
import eu.dietwise.services.v1.extraction.NoIngredientsInRecipeException;
import eu.dietwise.services.v1.extraction.RecipeExtractionService;
import eu.dietwise.services.v1.extraction.impl.InvalidRecipeSourceUrlException;
import eu.dietwise.services.v1.scoring.RecipeScoringService;
import eu.dietwise.services.v1.suggestions.MakeSuggestionsResult;
import eu.dietwise.services.v1.suggestions.RecipeSuggestionsService;
import eu.dietwise.services.v1.types.RecipeAndDetectionType;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.MoreThanOneRecipesAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeAssessmentErrorMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.AppliesTo;
import eu.dietwise.v1.model.ImmutableIngredient;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.Country;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RecipeAssessmentServiceImpl implements RecipeAssessmentService {
	private static final Logger LOG = LoggerFactory.getLogger(RecipeAssessmentServiceImpl.class);

	private final Authorization authorization;
	private final RecipeExtractionService recipeExtractionService;
	private final StatisticsService statisticsService;
	private final RecipeSuggestionsService recipeSuggestionsService;
	private final RecipeScoringService recipeScoringService;

	public RecipeAssessmentServiceImpl(
			Authorization authorization,
			RecipeExtractionService recipeExtractionService,
			StatisticsService statisticsService,
			RecipeSuggestionsService recipeSuggestionsService,
			RecipeScoringService recipeScoringService
	) {
		this.authorization = authorization;
		this.recipeExtractionService = recipeExtractionService;
		this.statisticsService = statisticsService;
		this.recipeSuggestionsService = recipeSuggestionsService;
		this.recipeScoringService = recipeScoringService;
	}

	@Override
	public Multi<RecipeAssessmentMessage> assessMarkdownRecipe(User user, RecipeAssessmentParam param) {
		var correlationId = UUID.randomUUID();
		LOG.info("assessMarkdownRecipe <{}> {} {}", correlationId, param.getUrl(), param.getLang());
		authorization.requireLogin(user);
		String applicationId = authorization.requireApplicationId(user);
		return emitInSequence(
				statisticsService.assessedRecipe(user),
				(_, _) -> recipeExtractionService.extractRecipeFromJsonLdOrMarkdown(correlationId, param),
				(emitter, message) -> emitExtractionAndClassify(emitter, user, correlationId, param.getUrl(), message),
				(emitter, outcome) -> assessExtractionOutcome(emitter, correlationId, applicationId, user, param.getUrl(), param.getLang(), param.getCountryOverride(), outcome),
				(emitter, suggestionsResult) -> emitScoringMessage(emitter, suggestionsResult, param.getLang()),
				this::handleError
		);
	}

	@Override
	public Multi<RecipeAssessmentMessage> extractAndAssessRecipeFromUrl(User user, RecipeExtractionAndAssessmentParam param) {
		var correlationId = UUID.randomUUID();
		LOG.info("extractAndAssessRecipeFromUrl <{}> {} {}", correlationId, param.getUrl(), param.getLang());
		authorization.requireLogin(user);
		String applicationId = authorization.requireApplicationId(user);
		return emitInSequence(
				statisticsService.assessedRecipe(user),
				(_, _) -> recipeExtractionService.extractRecipeFromUrl(correlationId, param),
				(emitter, message) -> emitExtractionAndClassify(emitter, user, correlationId, param.getUrl(), message),
				(emitter, outcome) -> assessExtractionOutcome(emitter, correlationId, applicationId, user, param.getUrl(), param.getLang(), null, outcome),
				(emitter, suggestionsResult) -> emitScoringMessage(emitter, suggestionsResult, param.getLang()),
				this::handleError
		);
	}

	private Uni<RecipeExtractionOutcome> emitExtractionAndClassify(
			MultiEmitter<? super RecipeAssessmentMessage> emitter,
			User user,
			UUID correlationId,
			String url,
			RecipeExtractionRecipeAssessmentMessage extraction
	) {
		RecipeExtractionOutcome outcome = RecipeExtractionOutcome.classify(extraction);
		if (outcome instanceof RecipeExtractionOutcome.NoRecipes) {
			LOG.warn("No recipes detected <{}> in {}", correlationId, url);
			return Uni.createFrom().item(outcome);
		}
		emitter.emit(extraction);
		return recordAssessedRecipe(user, url, extraction).replaceWith(outcome);
	}

	private Uni<User> recordAssessedRecipe(User user, String url, RecipeExtractionRecipeAssessmentMessage extraction) {
		int numberOfRecipes = extraction.recipes().size();
		String recipeName = numberOfRecipes == 1
				? extraction.recipes().getFirst().recipe().getName().orElse(null)
				: "(multiple recipes)";
		return statisticsService.assessedRecipe(user, url, recipeName);
	}

	private Uni<MakeSuggestionsResult> assessExtractionOutcome(
			MultiEmitter<? super RecipeAssessmentMessage> emitter,
			UUID correlationId,
			String applicationId,
			HasUserId hasUserId,
			String url,
			RecipeLanguage lang,
			Country countryOverride,
			RecipeExtractionOutcome outcome
	) {
		return switch (outcome) {
			case RecipeExtractionOutcome.NoRecipes _ -> {
				emitter.emit(new RecipeAssessmentErrorMessage(List.of("No recipes detected on the page")));
				yield Uni.createFrom().nullItem();
			}
			case RecipeExtractionOutcome.MultipleRecipes(int count) -> {
				LOG.warn("More than one recipe detected, will return error <{}> URL: {} ({} in total)", correlationId, url, count);
				emitter.emit(new MoreThanOneRecipesAssessmentMessage(count));
				yield Uni.createFrom().nullItem();
			}
			case RecipeExtractionOutcome.SingleRecipe(Recipe recipe) ->
					assessSingleRecipe(emitter, correlationId, applicationId, hasUserId, lang, countryOverride, recipe);
		};
	}

	private Uni<MakeSuggestionsResult> assessSingleRecipe(
			MultiEmitter<? super RecipeAssessmentMessage> emitter,
			UUID correlationId,
			String applicationId,
			HasUserId hasUserId,
			RecipeLanguage lang,
			Country countryOverride,
			Recipe recipe
	) {
		return forcm(
				recipeSuggestionsService.makeSuggestions(correlationId, hasUserId, lang, recipe, countryOverride),
				result -> recipeSuggestionsService.increaseTimesSuggested(correlationId, applicationId, hasUserId, result.message()),
				(result, _) -> recipeSuggestionsService.enrichWithStatistics(correlationId, applicationId, hasUserId, result.message()),
				(result, _, message) -> new MakeSuggestionsResult(message, result.recommendations())
		).invoke(result -> emitter.emit(result.message()));
	}

	private Uni<?> emitScoringMessage(
			MultiEmitter<? super RecipeAssessmentMessage> emitter,
			MakeSuggestionsResult suggestionsResult,
			RecipeLanguage lang
	) {
		// After the introduction of the RecipeExtractionOutcome, no exception is thrown in the previous stages. Now
		// that there are no exceptions, the chain runs to completion on every path. Therefore, this explicit guard
		// because assessExtractionOutcome yields a nullItem for the terminal NoRecipes/MultipleRecipes cases, and that
		// null flows down to the scoring stage (Mutiny passes null items through flatMap).
		if (suggestionsResult == null) {
			return Uni.createFrom().nullItem();
		}
		return recipeScoringService.makeScoringMessage(suggestionsResult.recommendations(), lang).invoke(emitter::emit);
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
				.onItem().call(_ -> Uni.createFrom().nullItem().onItem().delayIt().by(Duration.ofSeconds(3L)));
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
		var ingredient = recipe.getRecipeIngredients().get(ThreadLocalRandom.current().nextInt(recipe.getRecipeIngredients().size()));
		return ImmutableSuggestion.builder()
				.id(new GenericSuggestionTemplateId(UUID.randomUUID().toString()))
				.alternative(new AlternativeIngredientImpl("alternative"))
				.target(new AppliesTo.AppliesToIngredient(ingredient.getId()))
				.ruleId(new GenericRuleId("7"))
				.recommendation(new RecommendationImpl("Increase milk"))
				.text(text)
				.build();
	}

	private <T extends Throwable> void handleError(MultiEmitter<? super RecipeAssessmentMessage> emitter, T error) {
		switch (error) {
			case NoIngredientsInRecipeException _ ->
					emitter.emit(new RecipeAssessmentErrorMessage(List.of("No ingredients could be detected")));
			case InvalidRecipeSourceUrlException _ ->
					emitter.emit(new RecipeAssessmentErrorMessage(List.of("The supplied URL is not allowed")));
			case CircuitBreakerOpenException _ ->
					emitter.emit(new RecipeAssessmentErrorMessage(List.of("The AI service is temporarily unavailable, please try again in a few seconds")));
			case null, default -> {
				// TODO Create dedicated exceptions for extraction failures, suggestion failures
				LOG.error("The server failed to assess the recipe", error);
				emitter.emit(new RecipeAssessmentErrorMessage(List.of("The server failed to assess the recipe")));
			}
		}
		emitter.complete();
	}
}
