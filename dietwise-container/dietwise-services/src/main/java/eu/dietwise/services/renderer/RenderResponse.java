package eu.dietwise.services.renderer;

import java.util.List;

import eu.dietwise.v1.model.Recipe;

public record RenderResponse(String output, List<Recipe> jsonLdRecipes, String finalUrl, Screenshot screenshot) {
}
