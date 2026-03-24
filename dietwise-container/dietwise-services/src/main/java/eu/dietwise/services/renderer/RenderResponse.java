package eu.dietwise.services.renderer;

import java.util.List;

import eu.dietwise.services.model.RecipeExtractedFromInput;

public record RenderResponse(
		String output,
		List<RecipeExtractedFromInput> jsonLdRecipes,
		String finalUrl,
		Screenshot screenshot
) {
	public RenderResponse {
		jsonLdRecipes = jsonLdRecipes == null ? null : List.copyOf(jsonLdRecipes);
	}
}
