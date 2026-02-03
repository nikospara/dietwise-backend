package eu.dietwise.services.v1.impl;

/**
 * Used internally from the {@link RecipeAssessmentServiceImpl} to signal that no recipes could be detected.
 * This leads to an error response.
 */
class NoRecipesDetectedException extends RuntimeException {
}
