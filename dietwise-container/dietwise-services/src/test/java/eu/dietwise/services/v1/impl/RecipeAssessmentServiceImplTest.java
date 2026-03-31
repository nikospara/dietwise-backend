package eu.dietwise.services.v1.impl;

import static eu.dietwise.services.v1.types.RecipeDetectionType.JSONLD;
import static eu.dietwise.services.v1.types.RecipeDetectionType.LLM_FROM_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import eu.dietwise.common.types.authorization.NotAuthenticatedException;
import eu.dietwise.common.types.authorization.NotAuthorizedException;
import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.services.authz.Authorization;
import eu.dietwise.services.authz.AuthorizationImpl;
import eu.dietwise.services.v1.StatisticsService;
import eu.dietwise.services.v1.extraction.NoIngredientsInRecipeException;
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
import eu.dietwise.v1.model.AppliesTo.AppliesToIngredient;
import eu.dietwise.v1.model.ImmutableIngredient;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.ImmutableRecipeAssessmentParam;
import eu.dietwise.v1.model.ImmutableRecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.ImmutableScoringData;
import eu.dietwise.v1.model.ImmutableSuggestion;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.ScoringData;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.HasSuggestionTemplateIds;
import eu.dietwise.v1.types.impl.AlternativeIngredientImpl;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import eu.dietwise.v1.types.impl.RecommendationImpl;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeAssessmentServiceImplTest {
	private static final long ASYNC_WAIT_SECONDS = 5;

	private static final UUID USER_UUID = UUID.fromString("11111111-2222-3333-4444-555555555555");
	private static final String USER_EMAIL = "user@example.test";
	private static final String APPLICATION_ID = "recipewatch";
	private static final User USER = ImmutableUser.builder()
			.id(new UserIdImpl(USER_UUID.toString()))
			.name(USER_EMAIL)
			.email(null)
			.isService(false)
			.isSystem(false)
			.isUnauthenticated(false)
			.roles(EnumSet.of(Role.CITIZEN))
			.applicationId(APPLICATION_ID)
			.build();
	private static final User USER_UNAUTHENTICATED = ImmutableUser.copyOf(USER).withIsUnauthenticated(true);
	private static final User USER_NO_APPID = ImmutableUser.copyOf(USER).withApplicationId(Optional.empty());
	private static final Recipe RECIPE_SIMPLE_PASTA = recipe(
			"Simple Pasta",
			List.of("200g pasta", "salt"),
			List.of("Boil water", "Cook pasta")
	);
	private static final Recipe RECIPE_SIMPLE_SALAD = recipe(
			"Simple Salad",
			List.of("lettuce", "olive oil"),
			List.of("Mix", "Serve")
	);
	private static final RecipeExtractionAndAssessmentParam URL_EXTRACTION_PARAM = ImmutableRecipeExtractionAndAssessmentParam.builder()
			.url("https://example.test/recipe")
			.langCode("en")
			.build();
	private static final String MARKDOWN = "Simple pasta recipe with ingredients and instructions.";
	private static final RecipeAssessmentParam MARKDOWN_PARAM = ImmutableRecipeAssessmentParam.builder()
			.url("https://example.test/recipe")
			.langCode("en")
			.pageContent(MARKDOWN)
			.build();
	private static final String RENDERED_MARKDOWN = "rendered markdown";
	private static final String SUGGESTION_ID = "00000000-2222-3333-4444-555555555555";
	private static final String ALTERNATIVE_INGREDIENT = "Onion";
	private static final String RULE_ID = "22222222-2222-3333-4444-555555555555";
	private static final String RECOMMENDATION = "Recommendation";
	private static final String SUGGESTION_TEXT = "Suggestion text";

	private final Authorization authorization = new AuthorizationImpl();

	@Mock
	private RecipeExtractionService recipeExtractionService;

	@Mock
	private StatisticsService statisticsService;

	@Mock
	private RecipeSuggestionsService recipeSuggestionsService;

	@Mock
	private RecipeScoringService recipeScoringService;

	private RecipeAssessmentServiceImpl sut;

	@BeforeEach
	void beforeEach() {
		sut = new RecipeAssessmentServiceImpl(authorization, recipeExtractionService, statisticsService, recipeSuggestionsService, recipeScoringService);
	}

	@Test
	void testUnauthorizedUser() {
		assertThatThrownBy(() ->
				sut.assessMarkdownRecipe(USER_UNAUTHENTICATED, MARKDOWN_PARAM)
						.collect().asList()
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS))
		).isInstanceOf(NotAuthenticatedException.class);
		assertThatThrownBy(() ->
				sut.extractAndAssessRecipeFromUrl(USER_UNAUTHENTICATED, URL_EXTRACTION_PARAM)
						.collect().asList()
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS))
		).isInstanceOf(NotAuthenticatedException.class);
	}

	@Test
	void testNoAppId() {
		assertThatThrownBy(() ->
				sut.assessMarkdownRecipe(USER_NO_APPID, MARKDOWN_PARAM)
						.collect().asList()
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS))
		).isInstanceOf(NotAuthorizedException.class);
		assertThatThrownBy(() ->
				sut.extractAndAssessRecipeFromUrl(USER_NO_APPID, URL_EXTRACTION_PARAM)
						.collect().asList()
						.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS))
		).isInstanceOf(NotAuthorizedException.class);
	}

	@Test
	void assessMarkdownRecipeEmitsExtractionThenSuggestionsThenScoreOnHappyPath() {
		var extractionMessage = new RecipeExtractionRecipeAssessmentMessage(
				List.of(new RecipeAndDetectionType(RECIPE_SIMPLE_PASTA, LLM_FROM_TEXT)),
				MARKDOWN
		);
		when(recipeExtractionService.useAiToExtractRecipeFromMarkdown(any(UUID.class), any(), any(), any()))
				.thenReturn(Uni.createFrom().item(extractionMessage));
		when(statisticsService.assessedRecipe(USER)).thenReturn(Uni.createFrom().item(USER));
		when(recipeSuggestionsService.makeSuggestions(any(), eq(USER), any(Recipe.class)))
				.thenAnswer(iom -> Uni.createFrom().item(makeSuggestions(iom.getArgument(2))));
		when(recipeSuggestionsService.increaseTimesSuggested(any(), any(), any(), any())).thenReturn(Uni.createFrom().voidItem());
		when(recipeSuggestionsService.enrichWithStatistics(any(), any(), any(), any())).thenAnswer(iom -> Uni.createFrom().item((SuggestionsRecipeAssessmentMessage) iom.getArgument(3)));
		when(recipeScoringService.makeScoringMessage(any())).thenAnswer(iom -> Uni.createFrom().item(new ScoringRecipeAssessmentMessage(dummyScoringData())));

		List<RecipeAssessmentMessage> messages = sut.assessMarkdownRecipe(USER, MARKDOWN_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(messages).hasSize(3);
		assertThat(messages.getFirst()).isInstanceOf(RecipeExtractionRecipeAssessmentMessage.class);
		var extraction = (RecipeExtractionRecipeAssessmentMessage) messages.getFirst();
		assertThat(extraction.pageText()).isEqualTo(MARKDOWN);
		assertThat(extraction.recipes()).hasSize(1);
		assertThat(extraction.recipes().getFirst().detectionType()).isEqualTo(LLM_FROM_TEXT);
		assertThat(extraction.recipes().getFirst().recipe().getName()).contains("Simple Pasta");
		assertThat(extraction.recipes().getFirst().recipe().getRecipeIngredients())
				.extracting(Ingredient::getNameInRecipe)
				.containsExactly("200g pasta", "salt");
		assertThat(extraction.recipes().getFirst().recipe().getRecipeInstructions())
				.containsExactly("Boil water", "Cook pasta");

		assertThat(messages.get(1)).isInstanceOf(SuggestionsRecipeAssessmentMessage.class);
		var suggestions = (SuggestionsRecipeAssessmentMessage) messages.get(1);
		assertThat(suggestions.suggestions()).hasSize(1);
		assertThat(suggestions.suggestions().getFirst().getText()).contains(SUGGESTION_TEXT);

		assertThat(messages.get(2)).isInstanceOf(ScoringRecipeAssessmentMessage.class);

		verify(recipeExtractionService).useAiToExtractRecipeFromMarkdown(any(UUID.class), any(), any(), any());
		verify(statisticsService).assessedRecipe(USER);
		verify(recipeSuggestionsService).makeSuggestions(any(), eq(USER), any(Recipe.class));
		verify(recipeScoringService).makeScoringMessage(any());
		var applicationIdCaptor = ArgumentCaptor.forClass(String.class);
		var hasUserIdCaptor = ArgumentCaptor.forClass(HasUserId.class);
		var hasSuggestionTemplateIdsCaptor = ArgumentCaptor.forClass(HasSuggestionTemplateIds.class);
		verify(recipeSuggestionsService).increaseTimesSuggested(any(), applicationIdCaptor.capture(), hasUserIdCaptor.capture(), hasSuggestionTemplateIdsCaptor.capture());
		assertThat(applicationIdCaptor.getValue()).isEqualTo(APPLICATION_ID);
		assertThat(hasUserIdCaptor.getValue().getId().asString()).isEqualTo(USER_UUID.toString());
		assertThat(hasSuggestionTemplateIdsCaptor.getValue().getSuggestionTemplateIds()).containsExactlyInAnyOrder(new GenericSuggestionTemplateId(SUGGESTION_ID));
	}

	@Test
	void extractAndAssessRecipeFromUrlEmitsExtractionThenSuggestionsThenScoreOnHappyPath() {
		var extractionMessage = new RecipeExtractionRecipeAssessmentMessage(
				List.of(new RecipeAndDetectionType(RECIPE_SIMPLE_PASTA, JSONLD)),
				RENDERED_MARKDOWN
		);
		when(recipeExtractionService.extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class)))
				.thenReturn(Uni.createFrom().item(extractionMessage));
		when(statisticsService.assessedRecipe(USER)).thenReturn(Uni.createFrom().item(USER));
		when(recipeSuggestionsService.makeSuggestions(any(), eq(USER), any(Recipe.class)))
				.thenAnswer(iom -> Uni.createFrom().item(makeSuggestions(iom.getArgument(2))));
		when(recipeSuggestionsService.increaseTimesSuggested(any(), any(), any(), any())).thenReturn(Uni.createFrom().voidItem());
		when(recipeSuggestionsService.enrichWithStatistics(any(), any(), any(), any())).thenAnswer(iom -> Uni.createFrom().item((SuggestionsRecipeAssessmentMessage) iom.getArgument(3)));
		when(recipeScoringService.makeScoringMessage(any())).thenAnswer(iom -> Uni.createFrom().item(new ScoringRecipeAssessmentMessage(dummyScoringData())));

		List<RecipeAssessmentMessage> messages = sut.extractAndAssessRecipeFromUrl(USER, URL_EXTRACTION_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(messages).hasSize(3);
		assertThat(messages.getFirst()).isInstanceOf(RecipeExtractionRecipeAssessmentMessage.class);
		var extraction = (RecipeExtractionRecipeAssessmentMessage) messages.getFirst();
		assertThat(extraction.pageText()).isEqualTo(RENDERED_MARKDOWN);
		assertThat(extraction.recipes()).hasSize(1);
		assertThat(extraction.recipes().getFirst().detectionType()).isEqualTo(JSONLD);
		assertThat(extraction.recipes().getFirst().recipe().getName()).contains("Simple Pasta");

		assertThat(messages.get(1)).isInstanceOf(SuggestionsRecipeAssessmentMessage.class);
		var suggestions = (SuggestionsRecipeAssessmentMessage) messages.get(1);
		assertThat(suggestions.suggestions()).hasSize(1);
		assertThat(suggestions.suggestions().getFirst().getText()).contains(SUGGESTION_TEXT);

		assertThat(messages.get(2)).isInstanceOf(ScoringRecipeAssessmentMessage.class);

		verify(recipeExtractionService).extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class));
		verify(statisticsService).assessedRecipe(USER);
		verify(recipeSuggestionsService).makeSuggestions(any(), eq(USER), any(Recipe.class));
		verify(recipeScoringService).makeScoringMessage(any());
		var applicationIdCaptor = ArgumentCaptor.forClass(String.class);
		var hasUserIdCaptor = ArgumentCaptor.forClass(HasUserId.class);
		var hasSuggestionTemplateIdsCaptor = ArgumentCaptor.forClass(HasSuggestionTemplateIds.class);
		verify(recipeSuggestionsService).increaseTimesSuggested(any(), applicationIdCaptor.capture(), hasUserIdCaptor.capture(), hasSuggestionTemplateIdsCaptor.capture());
		assertThat(applicationIdCaptor.getValue()).isEqualTo(APPLICATION_ID);
		assertThat(hasUserIdCaptor.getValue().getId().asString()).isEqualTo(USER_UUID.toString());
		assertThat(hasSuggestionTemplateIdsCaptor.getValue().getSuggestionTemplateIds()).containsExactlyInAnyOrder(new GenericSuggestionTemplateId(SUGGESTION_ID));
	}

	@Test
	void extractAndAssessRecipeFromUrlEmitsMoreThanOneRecipesMessageWhenMultipleRecipesExtracted() {
		var extractionMessage = new RecipeExtractionRecipeAssessmentMessage(
				List.of(
						new RecipeAndDetectionType(RECIPE_SIMPLE_PASTA, JSONLD),
						new RecipeAndDetectionType(RECIPE_SIMPLE_SALAD, JSONLD)
				),
				RENDERED_MARKDOWN
		);
		when(recipeExtractionService.extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class)))
				.thenReturn(Uni.createFrom().item(extractionMessage));
		when(statisticsService.assessedRecipe(USER)).thenReturn(Uni.createFrom().item(USER));

		List<RecipeAssessmentMessage> messages = sut.extractAndAssessRecipeFromUrl(USER, URL_EXTRACTION_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(messages).hasSize(2);
		assertThat(messages.getFirst()).isInstanceOf(RecipeExtractionRecipeAssessmentMessage.class);
		assertThat(((RecipeExtractionRecipeAssessmentMessage) messages.getFirst()).recipes()).hasSize(2);
		assertThat(messages.getLast()).isInstanceOf(MoreThanOneRecipesAssessmentMessage.class);
		assertThat(((MoreThanOneRecipesAssessmentMessage) messages.getLast()).numberOfRecipes()).isEqualTo(2);

		verify(recipeExtractionService).extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class));
		verify(statisticsService).assessedRecipe(USER);
	}

	@Test
	void extractAndAssessRecipeFromUrlEmitsErrorWhenNoIngredientsInRecipe() {
		when(recipeExtractionService.extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class)))
				.thenReturn(Uni.createFrom().failure(new NoIngredientsInRecipeException()));
		when(statisticsService.assessedRecipe(USER)).thenReturn(Uni.createFrom().item(USER));

		List<RecipeAssessmentMessage> messages = sut.extractAndAssessRecipeFromUrl(USER, URL_EXTRACTION_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst()).isInstanceOf(RecipeAssessmentErrorMessage.class);
		assertThat(((RecipeAssessmentErrorMessage) messages.getFirst()).errors())
				.containsExactly("No ingredients could be detected");

		verify(recipeExtractionService).extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class));
		verify(statisticsService).assessedRecipe(USER);
	}

	@Test
	void extractAndAssessRecipeFromUrlEmitsErrorWhenNoRecipesDetected() {
		var extractionMessage = new RecipeExtractionRecipeAssessmentMessage(
				Collections.emptyList(),
				RENDERED_MARKDOWN
		);
		when(recipeExtractionService.extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class)))
				.thenReturn(Uni.createFrom().item(extractionMessage));

		List<RecipeAssessmentMessage> messages = sut.extractAndAssessRecipeFromUrl(USER, URL_EXTRACTION_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst()).isInstanceOf(RecipeAssessmentErrorMessage.class);
		assertThat(((RecipeAssessmentErrorMessage) messages.getFirst()).errors())
				.containsExactly("No recipes detected on the page");

		verify(recipeExtractionService).extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class));
		verify(statisticsService).assessedRecipe(USER);
	}

	@Test
	void extractAndAssessRecipeFromUrlEmitsErrorWhenExtractionFails() {
		when(recipeExtractionService.extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class)))
				.thenReturn(Uni.createFrom().failure(new RuntimeException("Extraction failed")));
		when(statisticsService.assessedRecipe(USER)).thenReturn(Uni.createFrom().item(USER));

		List<RecipeAssessmentMessage> messages = sut.extractAndAssessRecipeFromUrl(USER, URL_EXTRACTION_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(ASYNC_WAIT_SECONDS));

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst()).isInstanceOf(RecipeAssessmentErrorMessage.class);
		assertThat(((RecipeAssessmentErrorMessage) messages.getFirst()).errors())
				.containsExactly("The server failed to assess the recipe");

		verify(recipeExtractionService).extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class));
		verify(statisticsService).assessedRecipe(USER);
	}

	private static Recipe recipe(String name, List<String> ingredients, List<String> instructions) {
		var recipeIngredients = IntStream.range(0, ingredients.size())
				.mapToObj(i -> ImmutableIngredient.builder()
						.id(new GenericIngredientId("ingredient-" + i))
						.nameInRecipe(ingredients.get(i))
						.build())
				.toList();
		return ImmutableRecipe.builder()
				.name(Optional.of(name))
				.recipeIngredients(recipeIngredients)
				.recipeInstructions(instructions)
				.build();
	}

	private static MakeSuggestionsResult makeSuggestions(Recipe recipe) {
		Suggestion suggestion = ImmutableSuggestion.builder()
				.id(new GenericSuggestionTemplateId(SUGGESTION_ID))
				.alternative(new AlternativeIngredientImpl(ALTERNATIVE_INGREDIENT))
				.target(new AppliesToIngredient(recipe.getRecipeIngredients().getFirst().getId()))
				.ruleId(new GenericRuleId(RULE_ID))
				.recommendation(new RecommendationImpl(RECOMMENDATION))
				.text(SUGGESTION_TEXT)
				.build();
		var message = new SuggestionsRecipeAssessmentMessage(List.of(suggestion));
		return new MakeSuggestionsResult(message, Map.of());
	}

	private ScoringData dummyScoringData() {
		return ImmutableScoringData.builder()
				.totalNumberOfRecomendations(15)
				.recommendationWeights(Collections.emptyMap())
				.recommendationsPerIngredient(Collections.emptyMap())
				.build();
	}
}
