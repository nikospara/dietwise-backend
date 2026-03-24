package eu.dietwise.services.v1.types;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

public class RecipeAssessmentMessageTest {
	@Test
	void recipeAssessmentErrorMessageCopiesErrors() {
		List<String> errors = new ArrayList<>(List.of("error"));

		var sut = new RecipeAssessmentMessage.RecipeAssessmentErrorMessage(errors);
		errors.add("mutated");

		assertThat(sut.errors()).containsExactly("error");
	}

	@Test
	void recipeExtractionRecipeAssessmentMessageCopiesRecipes() {
		List<RecipeAndDetectionType> recipes = new ArrayList<>();

		var sut = new RecipeAssessmentMessage.RecipeExtractionRecipeAssessmentMessage(recipes, "page");
		recipes.add(null);

		assertThat(sut.recipes()).isEmpty();
	}

	@Test
	void suggestionsRecipeAssessmentMessageCopiesSuggestions() {
		List<eu.dietwise.v1.model.Suggestion> suggestions = new ArrayList<>();

		var sut = new RecipeAssessmentMessage.SuggestionsRecipeAssessmentMessage(suggestions);
		suggestions.add(null);

		assertThat(sut.suggestions()).isEmpty();
	}
}
