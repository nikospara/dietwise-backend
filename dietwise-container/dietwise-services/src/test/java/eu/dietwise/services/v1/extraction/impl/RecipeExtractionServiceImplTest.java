package eu.dietwise.services.v1.extraction.impl;

import static eu.dietwise.services.v1.types.RecipeDetectionType.JSONLD;
import static eu.dietwise.services.v1.types.RecipeDetectionType.LLM_FROM_TEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import eu.dietwise.services.model.RecipeExtractedFromInput;
import eu.dietwise.services.renderer.RenderResponse;
import eu.dietwise.services.renderer.RendererClient;
import eu.dietwise.services.v1.extraction.MarkdownRecipeExtractionService;
import eu.dietwise.services.v1.extraction.NoIngredientsInRecipeException;
import eu.dietwise.services.v1.filtering.RecipeFilterAiFacade;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage;
import eu.dietwise.v1.model.ImmutableRecipeAssessmentParam;
import eu.dietwise.v1.model.ImmutableRecipeExtractionAndAssessmentParam;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.jboss.resteasy.reactive.RestResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RecipeExtractionServiceImplTest {
	private static final UUID CORRELATION_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
	private static final String URL = "https://example.test/recipe";
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
	private static final RecipeExtractedFromInput RECIPE_WITHOUT_INGREDIENTS = new RecipeExtractedFromInput(
			Optional.of("Ingredient-less recipe"),
			Optional.empty(),
			List.of(),
			List.of("Step one"),
			Optional.empty()
	);
	private static final RecipeExtractionAndAssessmentParam URL_EXTRACTION_PARAM = ImmutableRecipeExtractionAndAssessmentParam.builder()
			.url(URL)
			.langCode("en")
			.build();
	private static final String MARKDOWN = "Simple pasta recipe with ingredients and instructions.";
	private static final RecipeAssessmentParam MARKDOWN_PARAM = ImmutableRecipeAssessmentParam.builder()
			.url(URL)
			.langCode("en")
			.pageContent(MARKDOWN)
			.build();
	private static final String RENDERED_MARKDOWN = "rendered markdown";

	@Mock
	private RecipeFilterAiFacade filterAiFacade;

	@Mock
	private MarkdownRecipeExtractionService extractionService;

	@Mock
	private RendererClient rendererClient;

	@Test
	void useAiToExtractRecipeFromMarkdownEmitsExtractionMessage() {
		var sut = new RecipeExtractionServiceImpl(filterAiFacade, extractionService, false, rendererClient);

		when(filterAiFacade.filterRecipeBlock(MARKDOWN)).thenReturn(Uni.createFrom().item("KEEP"));
		when(extractionService.extractRecipeFromMarkdown(MARKDOWN)).thenReturn(Uni.createFrom().item(RECIPE_SIMPLE_PASTA));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.useAiToExtractRecipeFromMarkdown(CORRELATION_ID, URL, "en", MARKDOWN).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.pageText()).isEqualTo(MARKDOWN);
		assertThat(extractionMessage.recipes()).hasSize(1);
		assertThat(extractionMessage.recipes().getFirst().detectionType()).isEqualTo(LLM_FROM_TEXT);
		assertThat(extractionMessage.recipes().getFirst().recipe().getName()).contains("Simple Pasta");
		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeIngredients())
				.extracting(Ingredient::getNameInRecipe)
				.containsExactly("200g pasta", "salt");
		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeInstructions())
				.containsExactly("Boil water", "Cook pasta");

		verify(filterAiFacade).filterRecipeBlock(MARKDOWN);
		verify(extractionService).extractRecipeFromMarkdown(MARKDOWN);
	}

	@Test
	void extractRecipeFromUrlEmitsExtractionMessage() {
		var sut = new RecipeExtractionServiceImpl(filterAiFacade, extractionService, false, rendererClient);
		var restResponse = makeRenderResponseForRecipes(List.of(RECIPE_SIMPLE_PASTA));
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.pageText()).isEqualTo(RENDERED_MARKDOWN);
		assertThat(extractionMessage.recipes()).hasSize(1);
		assertThat(extractionMessage.recipes().getFirst().detectionType()).isEqualTo(JSONLD);
		assertThat(extractionMessage.recipes().getFirst().recipe().getName()).contains("Simple Pasta");
		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeIngredients())
				.extracting(Ingredient::getNameInRecipe)
				.containsExactly("200g pasta", "salt");
		assertThat(extractionMessage.recipes().getFirst().recipe().getRecipeInstructions())
				.containsExactly("Boil water", "Cook pasta");

		verify(rendererClient).render(any());
		verifyNoInteractions(filterAiFacade);
		verifyNoInteractions(extractionService);
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"http://localhost/recipe",
			"http://127.0.0.1/recipe",
			"http://10.0.0.1/recipe",
			"http://[::1]/recipe",
			"file:///tmp/recipe.txt",
			"https://user:pass@example.test/recipe"
	})
	void extractRecipeFromUrlRejectsUnsafeUrls(String url) {
		var sut = new RecipeExtractionServiceImpl(filterAiFacade, extractionService, false, rendererClient);
		RecipeExtractionAndAssessmentParam param = ImmutableRecipeExtractionAndAssessmentParam.builder()
				.url(url)
				.langCode("en")
				.build();

		assertThatThrownBy(() -> sut.extractRecipeFromUrl(CORRELATION_ID, param).await().atMost(Duration.ofSeconds(5L)))
				.isInstanceOf(InvalidRecipeSourceUrlException.class);

		verifyNoInteractions(rendererClient);
		verifyNoInteractions(filterAiFacade);
		verifyNoInteractions(extractionService);
	}

	@Test
	void extractRecipeFromUrlAllowsExactCachedTestPageWhenEnabled() {
		var sut = new RecipeExtractionServiceImpl(filterAiFacade, extractionService, true, rendererClient);
		RecipeExtractionAndAssessmentParam param = ImmutableRecipeExtractionAndAssessmentParam.builder()
				.url("001.html")
				.langCode("en")
				.build();
		var restResponse = makeRenderResponseForRecipes(List.of(RECIPE_SIMPLE_PASTA));
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromUrl(CORRELATION_ID, param).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.recipes()).hasSize(1);
		verify(rendererClient).render(any());
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"1.html",
			"0001.html",
			"001.HTML",
			"001.html?x=1",
			"/001.html",
			"foo/001.html"
	})
	void extractRecipeFromUrlRejectsNearMissCachedTestPagesEvenWhenEnabled(String url) {
		var sut = new RecipeExtractionServiceImpl(filterAiFacade, extractionService, true, rendererClient);
		RecipeExtractionAndAssessmentParam param = ImmutableRecipeExtractionAndAssessmentParam.builder()
				.url(url)
				.langCode("en")
				.build();

		assertThatThrownBy(() -> sut.extractRecipeFromUrl(CORRELATION_ID, param).await().atMost(Duration.ofSeconds(5L)))
				.isInstanceOf(InvalidRecipeSourceUrlException.class);

		verifyNoInteractions(rendererClient);
		verifyNoInteractions(filterAiFacade);
		verifyNoInteractions(extractionService);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void extractRecipeFromUrlFallsBackToAiWhenNoJsonLdRecipes(List<RecipeExtractedFromInput> recipes) {
		var sut = new RecipeExtractionServiceImpl(filterAiFacade, extractionService, false, rendererClient);
		var restResponse = makeRenderResponseForRecipes(recipes);
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));
		when(filterAiFacade.filterRecipeBlock(RENDERED_MARKDOWN)).thenReturn(Uni.createFrom().item("KEEP"));
		when(extractionService.extractRecipeFromMarkdown(RENDERED_MARKDOWN)).thenReturn(Uni.createFrom().item(RECIPE_SIMPLE_PASTA));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.pageText()).isEqualTo(RENDERED_MARKDOWN);
		assertThat(extractionMessage.recipes()).hasSize(1);
		assertThat(extractionMessage.recipes().getFirst().detectionType()).isEqualTo(LLM_FROM_TEXT);
		assertThat(extractionMessage.recipes().getFirst().recipe().getName()).contains("Simple Pasta");

		verify(rendererClient).render(any());
		verify(filterAiFacade).filterRecipeBlock(RENDERED_MARKDOWN);
		verify(extractionService).extractRecipeFromMarkdown(RENDERED_MARKDOWN);
	}

	@Test
	void extractRecipeFromUrlEmitsMoreThanOneRecipesMessageWhenMultipleRecipesExtracted() {
		var sut = new RecipeExtractionServiceImpl(filterAiFacade, extractionService, false, rendererClient);
		var restResponse = makeRenderResponseForRecipes(List.of(RECIPE_SIMPLE_PASTA, RECIPE_SIMPLE_SALAD));
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.recipes()).hasSize(2);

		verify(rendererClient).render(any());
	}

	@Test
	void extractRecipeFromUrlKeepsOnlyValidJsonLdRecipesWhenInputIsMixed() {
		var sut = new RecipeExtractionServiceImpl(filterAiFacade, extractionService, false, rendererClient);
		var restResponse = makeRenderResponseForRecipes(List.of(RECIPE_SIMPLE_PASTA, RECIPE_WITHOUT_INGREDIENTS));
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));

		RecipeExtractionRecipeAssessmentMessage extractionMessage =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM).await().atMost(Duration.ofSeconds(5L));

		assertThat(extractionMessage.recipes()).hasSize(1);
		assertThat(extractionMessage.recipes().getFirst().detectionType()).isEqualTo(JSONLD);
		assertThat(extractionMessage.recipes().getFirst().recipe().getName()).contains("Simple Pasta");

		verify(rendererClient).render(any());
		verifyNoInteractions(filterAiFacade);
		verifyNoInteractions(extractionService);
	}

	@Test
	void extractRecipeFromUrlEmitsErrorWhenAiReturnsRecipeWithoutIngredients() {
		var sut = new RecipeExtractionServiceImpl(filterAiFacade, extractionService, false, rendererClient);
		var restResponse = makeRenderResponseForRecipes(Collections.emptyList());
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().item(restResponse));
		when(filterAiFacade.filterRecipeBlock(RENDERED_MARKDOWN)).thenReturn(Uni.createFrom().item("KEEP"));
		when(extractionService.extractRecipeFromMarkdown(RENDERED_MARKDOWN)).thenReturn(Uni.createFrom().item(RECIPE_WITHOUT_INGREDIENTS));

		UniAssertSubscriber<RecipeExtractionRecipeAssessmentMessage> subscriber =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM)
						.subscribe().withSubscriber(UniAssertSubscriber.create());

		subscriber.awaitFailure().assertFailedWith(NoIngredientsInRecipeException.class);

		verify(rendererClient).render(any());
		verify(filterAiFacade).filterRecipeBlock(RENDERED_MARKDOWN);
		verify(extractionService).extractRecipeFromMarkdown(RENDERED_MARKDOWN);
	}

	@Test
	void extractRecipeFromUrlEmitsErrorWhenRendererFails() {
		var sut = new RecipeExtractionServiceImpl(filterAiFacade, extractionService, false, rendererClient);
		when(rendererClient.render(any())).thenReturn(Uni.createFrom().failure(new RuntimeException("Renderer failed")));

		UniAssertSubscriber<RecipeExtractionRecipeAssessmentMessage> subscriber =
				sut.extractRecipeFromUrl(CORRELATION_ID, URL_EXTRACTION_PARAM)
						.subscribe().withSubscriber(UniAssertSubscriber.create());

		subscriber.awaitFailure().assertFailedWith(RuntimeException.class, "Renderer failed");

		verify(rendererClient).render(any());
		verifyNoInteractions(filterAiFacade);
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
