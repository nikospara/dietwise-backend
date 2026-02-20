package eu.dietwise.services.model;

import java.util.List;
import java.util.Optional;

public record RecipeExtractedFromInput(
		Optional<String> name,
		Optional<String> recipeYield,
		List<String> recipeIngredients,
		List<String> recipeInstructions,
		Optional<String> text
) {
}
