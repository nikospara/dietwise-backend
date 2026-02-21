package eu.dietwise.services.v1.impl;

import static eu.dietwise.services.v1.types.RecipeDetectionType.JSONLD;
import static eu.dietwise.services.v1.types.RecipeDetectionType.LLM_FROM_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import eu.dietwise.services.model.RecipeExtractedFromInput;
import eu.dietwise.services.renderer.RenderResponse;
import eu.dietwise.services.renderer.RendererClient;
import eu.dietwise.services.v1.extraction.NoRecipesDetectedException;
import eu.dietwise.services.v1.extraction.RecipeExtractionService;
import eu.dietwise.services.v1.filtering.RecipeFilterAiService;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.MoreThanOneRecipesAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeAssessmentErrorMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableRecipeAssessmentParam;
import eu.dietwise.v1.model.ImmutableRecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import io.smallrye.mutiny.Uni;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeAssessmentServiceImplTest {
	private static final RecipeExtractedFromInput RECIPE_SIMPLE_PASTA = new RecipeExtractedFromInput(
			Optional.of("Simple Pasta"),
			Optional.empty(),
			List.of("200g pasta", "salt"),
			List.of("Boil water", "Cook pasta"),
			Optional.of("Recipe text")
	);
	private static final RecipeExtractedFromInput RECIPE_SIMPLE_SALAD = new RecipeExtractedFromInput(
			Optional.of("Simple Salad"),
			Optional.empty(),
			List.of("lettuce", "olive oil"),
			List.of("Mix", "Serve"),
			Optional.empty()
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
	private RecipeFilterAiService filterAiService;

	@Mock
	private RecipeExtractionService extractionService;

	@Mock
	private RendererClient rendererClient;

	@Test
	void assessMarkdownRecipeEmitsExtractionThenSuggestionsOnHappyPath() {
		var sut = new RecipeAssessmentServiceImpl(filterAiService, extractionService, rendererClient);

		when(filterAiService.filterRecipeBlock(MARKDOWN)).thenReturn("KEEP");
		when(extractionService.extractRecipeFromMarkdown(MARKDOWN)).thenReturn(Uni.createFrom().item(RECIPE_SIMPLE_PASTA));

		List<RecipeAssessmentMessage> messages = sut.assessMarkdownRecipe(MARKDOWN_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(messages).hasSize(2);
		assertThat(messages.getFirst()).isInstanceOf(RecipeExtractionRecipeAssessmentMessage.class);
		var extractionMessage = (RecipeExtractionRecipeAssessmentMessage) messages.getFirst();
		assertThat(extractionMessage.pageText()).isEqualTo(MARKDOWN);
		assertThat(extractionMessage.recipes()).hasSize(1);
		assertThat(extractionMessage.recipes().getFirst().detectionType()).isEqualTo(LLM_FROM_TEXT);
		assertThat(extractionMessage.recipes().getFirst().recipe().getName()).contains("Simple Pasta");
		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeIngredients())
				.extracting(Ingredient::getNameInRecipe)
				.containsExactly("200g pasta", "salt");
		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeInstructions())
				.containsExactly("Boil water", "Cook pasta");

		assertThat(messages.getLast()).isInstanceOf(SuggestionsRecipeAssessmentMessage.class);
		var suggestionsMessage = (SuggestionsRecipeAssessmentMessage) messages.getLast();
		assertThat(suggestionsMessage.rating()).isNotNull().isBetween(0.0, 4.5); // TODO This applies to the dummy suggestions
		assertThat(suggestionsMessage.suggestions()).hasSize(2);
		assertThat(suggestionsMessage.suggestions().getFirst().getText())
				.contains("placeholder response");

		verify(filterAiService).filterRecipeBlock(MARKDOWN);
		verify(extractionService).extractRecipeFromMarkdown(MARKDOWN);
	}

	@Test
	void extractAndAssessRecipeFromUrlEmitsExtractionThenSuggestionsOnHappyPath() {
		var sut = new RecipeAssessmentServiceImpl(filterAiService, extractionService, rendererClient);
		var restResponse = makeRenderResponseForRecipes(List.of(RECIPE_SIMPLE_PASTA));
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));

		List<RecipeAssessmentMessage> messages = sut.extractAndAssessRecipeFromUrl(URL_EXTRACTION_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(messages).hasSize(2);
		assertThat(messages.getFirst()).isInstanceOf(RecipeExtractionRecipeAssessmentMessage.class);
		var extractionMessage = (RecipeExtractionRecipeAssessmentMessage) messages.getFirst();
		assertThat(extractionMessage.pageText()).isEqualTo(RENDERED_MARKDOWN);
		assertThat(extractionMessage.recipes()).hasSize(1);
		assertThat(extractionMessage.recipes().getFirst().detectionType()).isEqualTo(JSONLD);
		assertThat(extractionMessage.recipes().getFirst().recipe().getName()).contains("Simple Pasta");
		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeIngredients())
				.extracting(Ingredient::getNameInRecipe)
				.containsExactly("200g pasta", "salt");
		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeInstructions())
				.containsExactly("Boil water", "Cook pasta");

		assertThat(messages.getLast()).isInstanceOf(SuggestionsRecipeAssessmentMessage.class);
		var suggestionsMessage = (SuggestionsRecipeAssessmentMessage) messages.getLast();
		assertThat(suggestionsMessage.rating()).isNotNull().isBetween(0.0, 4.5); // TODO This applies to the dummy suggestions
		assertThat(suggestionsMessage.suggestions()).hasSize(2);
		assertThat(suggestionsMessage.suggestions().getFirst().getText())
				.contains("placeholder response");

		verify(rendererClient).render(any());
		verifyNoInteractions(filterAiService);
		verifyNoInteractions(extractionService);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void extractAndAssessRecipeFromUrlFallsBackToAiWhenNoJsonLdRecipes(List<RecipeExtractedFromInput> recipes) {
		var sut = new RecipeAssessmentServiceImpl(filterAiService, extractionService, rendererClient);
		var restResponse = makeRenderResponseForRecipes(recipes);
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));
		when(filterAiService.filterRecipeBlock(RENDERED_MARKDOWN)).thenReturn("KEEP");
		when(extractionService.extractRecipeFromMarkdown(RENDERED_MARKDOWN)).thenReturn(Uni.createFrom().item(RECIPE_SIMPLE_PASTA));

		List<RecipeAssessmentMessage> messages = sut.extractAndAssessRecipeFromUrl(URL_EXTRACTION_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(messages).hasSize(2);
		assertThat(messages.getFirst()).isInstanceOf(RecipeExtractionRecipeAssessmentMessage.class);
		var extractionMessage = (RecipeExtractionRecipeAssessmentMessage) messages.getFirst();
		assertThat(extractionMessage.pageText()).isEqualTo(RENDERED_MARKDOWN);
		assertThat(extractionMessage.recipes()).hasSize(1);
		assertThat(extractionMessage.recipes().getFirst().detectionType()).isEqualTo(LLM_FROM_TEXT);
		assertThat(extractionMessage.recipes().getFirst().recipe().getName()).contains("Simple Pasta");

		assertThat(messages.getLast()).isInstanceOf(SuggestionsRecipeAssessmentMessage.class);
		var suggestionsMessage = (SuggestionsRecipeAssessmentMessage) messages.getLast();
		assertThat(suggestionsMessage.rating()).isNotNull().isBetween(0.0, 4.5); // TODO This applies to the dummy suggestions
		assertThat(suggestionsMessage.suggestions()).hasSize(2);

		verify(rendererClient).render(any());
		verify(filterAiService).filterRecipeBlock(RENDERED_MARKDOWN);
		verify(extractionService).extractRecipeFromMarkdown(RENDERED_MARKDOWN);
	}

	@Test
	void extractAndAssessRecipeFromUrlEmitsMoreThanOneRecipesMessageWhenMultipleRecipesExtracted() {
		var sut = new RecipeAssessmentServiceImpl(filterAiService, extractionService, rendererClient);
		var restResponse = makeRenderResponseForRecipes(List.of(RECIPE_SIMPLE_PASTA, RECIPE_SIMPLE_SALAD));
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));

		List<RecipeAssessmentMessage> messages = sut.extractAndAssessRecipeFromUrl(URL_EXTRACTION_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(messages).hasSize(2);
		assertThat(messages.getFirst()).isInstanceOf(RecipeExtractionRecipeAssessmentMessage.class);
		var extractionMessage = (RecipeExtractionRecipeAssessmentMessage) messages.getFirst();
		assertThat(extractionMessage.recipes()).hasSize(2);

		assertThat(messages.getLast()).isInstanceOf(MoreThanOneRecipesAssessmentMessage.class);
		var moreThanOneMessage = (MoreThanOneRecipesAssessmentMessage) messages.getLast();
		assertThat(moreThanOneMessage.numberOfRecipes()).isEqualTo(2);

		verify(rendererClient).render(any());
	}

	@Test
	void extractAndAssessRecipeFromUrlEmitsErrorWhenNoRecipesDetected() {
		var sut = new RecipeAssessmentServiceImpl(filterAiService, extractionService, rendererClient);
		var restResponse = makeRenderResponseForRecipes(Collections.emptyList());
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));
		when(filterAiService.filterRecipeBlock(any())).thenReturn("KEEP");
		when(extractionService.extractRecipeFromMarkdown(RENDERED_MARKDOWN)).thenReturn(Uni.createFrom().failure(new NoRecipesDetectedException()));

		List<RecipeAssessmentMessage> messages = sut.extractAndAssessRecipeFromUrl(URL_EXTRACTION_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst()).isInstanceOf(RecipeAssessmentErrorMessage.class);
		var errorMessage = (RecipeAssessmentErrorMessage) messages.getFirst();
		assertThat(errorMessage.errors()).containsExactly("No recipes detected on the page");

		verify(rendererClient).render(any());
		verify(filterAiService).filterRecipeBlock(any());
		verify(extractionService).extractRecipeFromMarkdown(RENDERED_MARKDOWN);
	}

	@Test
	void extractAndAssessRecipeFromUrlEmitsErrorWhenRendererFails() {
		var sut = new RecipeAssessmentServiceImpl(filterAiService, extractionService, rendererClient);
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("Renderer failed")));

		List<RecipeAssessmentMessage> messages = sut.extractAndAssessRecipeFromUrl(URL_EXTRACTION_PARAM)
				.collect().asList()
				.await().atMost(Duration.ofSeconds(5L));

		assertThat(messages).hasSize(1);
		assertThat(messages.getFirst()).isInstanceOf(RecipeAssessmentErrorMessage.class);
		var errorMessage = (RecipeAssessmentErrorMessage) messages.getFirst();
		assertThat(errorMessage.errors()).containsExactly("The server failed to assess the recipe");

		verify(rendererClient).render(any());
		verifyNoInteractions(filterAiService);
		verifyNoInteractions(extractionService);
	}

	private RestResponse<RenderResponse> makeRenderResponseForRecipes(List<RecipeExtractedFromInput> recipes) {
		var renderResponse = new RenderResponse(
				RENDERED_MARKDOWN,
				recipes,
				"https://example.test/recipe",
				null
		);
		@SuppressWarnings("unchecked")
		RestResponse<RenderResponse> restResponse = (RestResponse<RenderResponse>) mock(RestResponse.class);
		when(restResponse.getEntity()).thenReturn(renderResponse);
		return restResponse;
	}
}
