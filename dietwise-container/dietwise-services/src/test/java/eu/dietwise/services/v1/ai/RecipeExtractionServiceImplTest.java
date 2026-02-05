package eu.dietwise.services.v1.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.dietwise.v1.json.ObjectMapperModelUtils;
import eu.dietwise.v1.model.Recipe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecipeExtractionServiceImplTest {

	@Mock
	private RecipeExtractionAiService aiService;

	@Test
	void repairsMissingClosingBraceAndParsesRecipe() {
		var objectMapper = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		RecipeExtractionServiceImpl service = new RecipeExtractionServiceImpl(aiService, objectMapper);

		String markdown = "ignored";
		String malformedJson = """
				```json
				{
				  "name": "Simple Pasta",
				  "recipeIngredients": ["200g pasta", "salt"],
				  "recipeInstructions": ["Boil water", "Cook pasta"]
				""";

		when(aiService.extractRecipeFromMarkdown(markdown)).thenReturn(malformedJson);

		Recipe recipe = service.extractRecipeFromMarkdown(markdown).await().indefinitely();

		assertThat(recipe.getName()).hasValue("Simple Pasta");
		assertThat(recipe.getRecipeIngredients()).containsExactly("200g pasta", "salt");
		assertThat(recipe.getRecipeInstructions()).containsExactly("Boil water", "Cook pasta");
		verify(aiService).extractRecipeFromMarkdown(markdown);
	}

	@Test
	void repairsMissingClosingBracketAndParsesRecipe() {
		var objectMapper = ObjectMapperModelUtils.applyDefaultObjectMapperConfiguration(new ObjectMapper());
		RecipeExtractionServiceImpl service = new RecipeExtractionServiceImpl(aiService, objectMapper);

		String markdown = "ignored";
		String malformedJson = """
				{
				  "name": "Tomato Soup",
				  "recipeIngredients": ["tomatoes", "salt"
				  ],
				  "recipeInstructions": ["Simmer", "Blend"]
				}
				""";

		when(aiService.extractRecipeFromMarkdown(markdown)).thenReturn(malformedJson);

		Recipe recipe = service.extractRecipeFromMarkdown(markdown).await().indefinitely();

		assertThat(recipe.getName()).hasValue("Tomato Soup");
		assertThat(recipe.getRecipeIngredients()).containsExactly("tomatoes", "salt");
		assertThat(recipe.getRecipeInstructions()).containsExactly("Simmer", "Blend");
		verify(aiService).extractRecipeFromMarkdown(markdown);
	}
}
