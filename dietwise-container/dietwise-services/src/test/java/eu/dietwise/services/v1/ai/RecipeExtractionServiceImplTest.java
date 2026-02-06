package eu.dietwise.services.v1.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.v1.json.ObjectMapperModelUtils;
import eu.dietwise.v1.model.Recipe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeExtractionServiceImplTest {
	private static final String MARKDOWN_DUMMY = "ignored Markdown dummy";

	@Mock
	private RecipeExtractionAiService aiService;

	private RecipeExtractionServiceImpl sut;

	@BeforeEach
	void beforeEach() {
		var om = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		var normalizer = new RecipeJsonNormalizerImpl(om, new CompactJsonCoercionStrategy());
		sut = new RecipeExtractionServiceImpl(aiService, om, normalizer);
	}

	@Test
	void repairsMissingClosingBraceAndParsesRecipe() {
		String malformedJson = """
				```json
				{
				  "name": "Simple Pasta",
				  "recipeIngredients": ["200g pasta", "salt"],
				  "recipeInstructions": ["Boil water", "Cook pasta"]
				""";

		when(aiService.extractRecipeFromMarkdown(MARKDOWN_DUMMY)).thenReturn(malformedJson);

		Recipe recipe = sut.extractRecipeFromMarkdown(MARKDOWN_DUMMY).await().indefinitely();

		assertThat(recipe.getName()).hasValue("Simple Pasta");
		assertThat(recipe.getRecipeIngredients()).containsExactly("200g pasta", "salt");
		assertThat(recipe.getRecipeInstructions()).containsExactly("Boil water", "Cook pasta");
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

		Recipe recipe = sut.extractRecipeFromMarkdown(MARKDOWN_DUMMY).await().indefinitely();

		assertThat(recipe.getName()).hasValue("Tomato Soup");
		assertThat(recipe.getRecipeIngredients()).containsExactly("tomatoes", "salt");
		assertThat(recipe.getRecipeInstructions()).containsExactly("Simmer", "Blend");
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

		Recipe recipe = sut.extractRecipeFromMarkdown(MARKDOWN_DUMMY).await().indefinitely();

		assertThat(recipe.getName()).hasValue("Creamy Chocolate Cheesecake");
		assertThat(recipe.getRecipeIngredients()).containsExactly(
				"{\"ingredientName\":\"Crust\",\"quantity\":\"16 Oreos\",\"unit\":\"count\"}",
				"{\"ingredientName\":\"Filling\",\"quantity\":\"bittersweet chocolate\",\"unit\":\"oz\"}"
		);
		assertThat(recipe.getRecipeInstructions()).containsExactly("step one", "step two");
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

		Recipe recipe = sut.extractRecipeFromMarkdown(MARKDOWN_DUMMY).await().indefinitely();

		assertThat(recipe.getName()).hasValue("Simple Salad");
		assertThat(recipe.getRecipeIngredients()).containsExactly("lettuce", "olive oil");
		assertThat(recipe.getRecipeInstructions()).containsExactly("Mix", "Serve");
		verify(aiService).extractRecipeFromMarkdown(MARKDOWN_DUMMY);
	}
}
