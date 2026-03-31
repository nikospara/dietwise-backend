package eu.dietwise.services.v1.extraction.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import eu.dietwise.services.model.RecipeExtractedFromInput;
import eu.dietwise.services.v1.extraction.RecipeExtractionAiService;
import eu.dietwise.v1.json.ObjectMapperModelUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarkdownRecipeExtractionServiceImplTest {
	private static final String MARKDOWN_DUMMY = "ignored Markdown dummy";

	@Mock
	private RecipeExtractionAiService aiService;

	private MarkdownRecipeExtractionServiceImpl sut;

	@BeforeEach
	void beforeEach() {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		om.registerModule(new Jdk8Module()); // at runtime Quarkus provides this
		var normalizer = new RecipeJsonNormalizerImpl(om, new CompactJsonCoercionStrategy());
		sut = new MarkdownRecipeExtractionServiceImpl(aiService, om, normalizer);
	}

	@Test
	void repairsMissingClosingBraceAndParsesRecipe() {
		String malformedJson = """
				```json
				starts with irrelevant text and the parser skips it
				{
				  "name": "Simple Pasta",
				  "recipeIngredients": ["200g pasta", "salt"],
				  "recipeInstructions": ["Boil water", "Cook pasta"]
				""";

		when(aiService.extractRecipeFromMarkdown(MARKDOWN_DUMMY)).thenReturn(malformedJson);

		RecipeExtractedFromInput recipe = sut.extractRecipeFromMarkdown(MARKDOWN_DUMMY).await().indefinitely();

		assertThat(recipe.name()).hasValue("Simple Pasta");
		assertThat(recipe.recipeIngredients()).containsExactly("200g pasta", "salt");
		assertThat(recipe.recipeInstructions()).containsExactly("Boil water", "Cook pasta");
		verify(aiService).extractRecipeFromMarkdown(MARKDOWN_DUMMY);
	}

	@Test
	void repairsMissingClosingBraceAndParsesRecipeWhenLastFenceExists() {
		String malformedJson = """
				```json
				{
				  "name": "Simple Pasta",
				  "recipeIngredients": ["200g pasta", "salt"],
				  "recipeInstructions": ["Boil water", "Cook pasta"]
				```
				""";

		when(aiService.extractRecipeFromMarkdown(MARKDOWN_DUMMY)).thenReturn(malformedJson);

		RecipeExtractedFromInput recipe = sut.extractRecipeFromMarkdown(MARKDOWN_DUMMY).await().indefinitely();

		assertThat(recipe.name()).hasValue("Simple Pasta");
		assertThat(recipe.recipeIngredients()).containsExactly("200g pasta", "salt");
		assertThat(recipe.recipeInstructions()).containsExactly("Boil water", "Cook pasta");
		verify(aiService).extractRecipeFromMarkdown(MARKDOWN_DUMMY);
	}

	@Test
	void repairsExtraClosingBraceOrBracket() {
		String malformedJsonWithExtraClosingBrace = """
				```json
				starts with irrelevant text and the parser skips it
				{
				  "name": "Simple Pasta",
				  "recipeIngredients": ["200g pasta", "salt"],
				  "recipeInstructions": ["Boil water", "Cook pasta"]
				}}
				""";

		when(aiService.extractRecipeFromMarkdown(MARKDOWN_DUMMY)).thenReturn(malformedJsonWithExtraClosingBrace);

		RecipeExtractedFromInput recipe = sut.extractRecipeFromMarkdown(MARKDOWN_DUMMY).await().indefinitely();

		assertThat(recipe.name()).hasValue("Simple Pasta");
		assertThat(recipe.recipeIngredients()).containsExactly("200g pasta", "salt");
		assertThat(recipe.recipeInstructions()).containsExactly("Boil water", "Cook pasta");
		verify(aiService).extractRecipeFromMarkdown(MARKDOWN_DUMMY);
	}

	@Test
	void repairsMissingClosingBracketAndParsesRecipe() {
		String malformedJson = """
				{
				  "name": "Tomato Soup",
				  "recipeIngredients": ["tomatoes", "salt"
				  ],
				  "recipeInstructions": ["Simmer", "Blend"]
				}
				""";

		when(aiService.extractRecipeFromMarkdown(MARKDOWN_DUMMY)).thenReturn(malformedJson);

		RecipeExtractedFromInput recipe = sut.extractRecipeFromMarkdown(MARKDOWN_DUMMY).await().indefinitely();

		assertThat(recipe.name()).hasValue("Tomato Soup");
		assertThat(recipe.recipeIngredients()).containsExactly("tomatoes", "salt");
		assertThat(recipe.recipeInstructions()).containsExactly("Simmer", "Blend");
		verify(aiService).extractRecipeFromMarkdown(MARKDOWN_DUMMY);
	}

	@Test
	void edgeCaseWithLastBraceBeforeLastBracket() {
		String malformedJson = """
				{
					"name": "Creamy Chocolate Cheesecake",
					"recipeYield": "12-16 servings",
					"recipeIngredients": [
						{
							"ingredientName": "Crust",
							"quantity": "16 Oreos",
							"unit": "count"
						},
						{
							"ingredientName": "Filling",
							"quantity": "bittersweet chocolate",
							"unit": "oz"
						}
					],
					"recipeInstructions": [
						"step one",
						"step two"
					]
				""";

		when(aiService.extractRecipeFromMarkdown(MARKDOWN_DUMMY)).thenReturn(malformedJson);

		RecipeExtractedFromInput recipe = sut.extractRecipeFromMarkdown(MARKDOWN_DUMMY).await().indefinitely();

		assertThat(recipe.name()).hasValue("Creamy Chocolate Cheesecake");
		assertThat(recipe.recipeIngredients()).containsExactly(
				"{\"ingredientName\":\"Crust\",\"quantity\":\"16 Oreos\",\"unit\":\"count\"}",
				"{\"ingredientName\":\"Filling\",\"quantity\":\"bittersweet chocolate\",\"unit\":\"oz\"}"
		);
		assertThat(recipe.recipeInstructions()).containsExactly("step one", "step two");
		verify(aiService).extractRecipeFromMarkdown(MARKDOWN_DUMMY);
	}

	@Test
	void ignoresTrailingTextAfterCompleteJsonObject() {
		String malformedJson = """
				{
				  "name": "Simple Salad",
				  "recipeIngredients": ["lettuce", "olive oil"],
				  "recipeInstructions": ["Mix", "Serve"]
				}
				Some trailing commentary that should be ignored.
				""";

		when(aiService.extractRecipeFromMarkdown(MARKDOWN_DUMMY)).thenReturn(malformedJson);

		RecipeExtractedFromInput recipe = sut.extractRecipeFromMarkdown(MARKDOWN_DUMMY).await().indefinitely();

		assertThat(recipe.name()).hasValue("Simple Salad");
		assertThat(recipe.recipeIngredients()).containsExactly("lettuce", "olive oil");
		assertThat(recipe.recipeInstructions()).containsExactly("Mix", "Serve");
		verify(aiService).extractRecipeFromMarkdown(MARKDOWN_DUMMY);
	}
}
