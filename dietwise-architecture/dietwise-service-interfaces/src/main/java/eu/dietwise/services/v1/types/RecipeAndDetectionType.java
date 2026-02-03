package eu.dietwise.services.v1.types;

import eu.dietwise.v1.model.Recipe;

public record RecipeAndDetectionType(Recipe recipe, RecipeDetectionType detectionType) {
}
