package eu.dietwise.services.v1.impl;

import static eu.dietwise.services.v1.types.RecipeDetectionType.JSONLD;
import static eu.dietwise.services.v1.types.RecipeDetectionType.LLM_FROM_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import eu.dietwise.common.v1.model.ImmutableUser;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.impl.UserIdImpl;
import eu.dietwise.services.v1.StatisticsService;
import eu.dietwise.services.v1.extraction.NoRecipesDetectedException;
import eu.dietwise.services.v1.extraction.RecipeExtractionService;
import eu.dietwise.services.v1.types.RecipeAndDetectionType;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.MoreThanOneRecipesAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeAssessmentErrorMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableIngredient;
import eu.dietwise.v1.model.ImmutableRecipe;
import eu.dietwise.v1.model.ImmutableRecipeAssessmentParam;
import eu.dietwise.v1.model.ImmutableRecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Recipe;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.types.impl.GenericIngredientId;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeAssessmentServiceImplTest {
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

	@Mock
	private RecipeExtractionService recipeExtractionService;

	@Mock
	private StatisticsService statisticsService;

	@Test
	void assessMarkdownRecipeEmitsExtractionThenSuggestionsOnHappyPath() {
		var sut = new RecipeAssessmentServiceImpl(recipeExtractionService, statisticsService);
		var extractionMessage = new RecipeExtractionRecipeAssessmentMessage(
				List.of(new RecipeAndDetectionType(RECIPE_SIMPLE_PASTA, LLM_FROM_TEXT)),
				MARKDOWN
		);
		when(recipeExtractionService.useAiToExtractRecipeFromMarkdown(any(UUID.class), any(), any(), any()))
				.thenReturn(Uni.createFrom().item(extractionMessage));
		when(statisticsService.assessedRecipe(USER)).thenReturn(Uni.createFrom().item(USER));

		List<RecipeAssessmentMessage> messages = sut.assessMarkdownRecipe(USER, MARKDOWN_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(messages).hasSize(2);
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

		assertThat(messages.getLast()).isInstanceOf(SuggestionsRecipeAssessmentMessage.class);
		var suggestions = (SuggestionsRecipeAssessmentMessage) messages.getLast();
		assertThat(suggestions.rating()).isNotNull().isBetween(0.0, 4.5);
		assertThat(suggestions.suggestions()).hasSize(2);
		assertThat(suggestions.suggestions().getFirst().getText()).contains("placeholder response");

		verify(recipeExtractionService).useAiToExtractRecipeFromMarkdown(any(UUID.class), any(), any(), any());
		verify(statisticsService).assessedRecipe(USER);
	}

	@Test
	void extractAndAssessRecipeFromUrlEmitsExtractionThenSuggestionsOnHappyPath() {
		var sut = new RecipeAssessmentServiceImpl(recipeExtractionService, statisticsService);
		var extractionMessage = new RecipeExtractionRecipeAssessmentMessage(
				List.of(new RecipeAndDetectionType(RECIPE_SIMPLE_PASTA, JSONLD)),
				RENDERED_MARKDOWN
		);
		when(recipeExtractionService.extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class)))
				.thenReturn(Uni.createFrom().item(extractionMessage));
		when(statisticsService.assessedRecipe(USER)).thenReturn(Uni.createFrom().item(USER));

		List<RecipeAssessmentMessage> messages = sut.extractAndAssessRecipeFromUrl(USER, URL_EXTRACTION_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(messages).hasSize(2);
		assertThat(messages.getFirst()).isInstanceOf(RecipeExtractionRecipeAssessmentMessage.class);
		var extraction = (RecipeExtractionRecipeAssessmentMessage) messages.getFirst();
		assertThat(extraction.pageText()).isEqualTo(RENDERED_MARKDOWN);
		assertThat(extraction.recipes()).hasSize(1);
		assertThat(extraction.recipes().getFirst().detectionType()).isEqualTo(JSONLD);
		assertThat(extraction.recipes().getFirst().recipe().getName()).contains("Simple Pasta");

		assertThat(messages.getLast()).isInstanceOf(SuggestionsRecipeAssessmentMessage.class);
		var suggestions = (SuggestionsRecipeAssessmentMessage) messages.getLast();
		assertThat(suggestions.rating()).isNotNull().isBetween(0.0, 4.5);
		assertThat(suggestions.suggestions()).hasSize(2);

		verify(recipeExtractionService).extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class));
		verify(statisticsService).assessedRecipe(USER);
	}

	@Test
	void extractAndAssessRecipeFromUrlEmitsMoreThanOneRecipesMessageWhenMultipleRecipesExtracted() {
		var sut = new RecipeAssessmentServiceImpl(recipeExtractionService, statisticsService);
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
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(messages).hasSize(2);
		assertThat(messages.getFirst()).isInstanceOf(RecipeExtractionRecipeAssessmentMessage.class);
		assertThat(((RecipeExtractionRecipeAssessmentMessage) messages.getFirst()).recipes()).hasSize(2);
		assertThat(messages.getLast()).isInstanceOf(MoreThanOneRecipesAssessmentMessage.class);
		assertThat(((MoreThanOneRecipesAssessmentMessage) messages.getLast()).numberOfRecipes()).isEqualTo(2);

		verify(recipeExtractionService).extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class));
		verify(statisticsService).assessedRecipe(USER);
	}

	@Test
	void extractAndAssessRecipeFromUrlEmitsErrorWhenNoRecipesDetected() {
		var sut = new RecipeAssessmentServiceImpl(recipeExtractionService, statisticsService);
		when(recipeExtractionService.extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class)))
				.thenReturn(Uni.createFrom().failure(new NoRecipesDetectedException()));
		when(statisticsService.assessedRecipe(USER)).thenReturn(Uni.createFrom().item(USER));

		List<RecipeAssessmentMessage> messages = sut.extractAndAssessRecipeFromUrl(USER, URL_EXTRACTION_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst()).isInstanceOf(RecipeAssessmentErrorMessage.class);
		assertThat(((RecipeAssessmentErrorMessage) messages.getFirst()).errors())
				.containsExactly("No recipes detected on the page");

		verify(recipeExtractionService).extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class));
		verify(statisticsService).assessedRecipe(USER);
	}

	@Test
	void extractAndAssessRecipeFromUrlEmitsErrorWhenExtractionFails() {
		var sut = new RecipeAssessmentServiceImpl(recipeExtractionService, statisticsService);
		when(recipeExtractionService.extractRecipeFromUrl(any(UUID.class), any(RecipeExtractionAndAssessmentParam.class)))
				.thenReturn(Uni.createFrom().failure(new RuntimeException("Extraction failed")));
		when(statisticsService.assessedRecipe(USER)).thenReturn(Uni.createFrom().item(USER));

		List<RecipeAssessmentMessage> messages = sut.extractAndAssessRecipeFromUrl(USER, URL_EXTRACTION_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(5L));

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
}
