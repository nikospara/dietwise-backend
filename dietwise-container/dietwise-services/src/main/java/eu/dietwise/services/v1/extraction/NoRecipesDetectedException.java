package eu.dietwise.services.v1.extraction;

/**
 * Used from the {@link MarkdownRecipeExtractionService} to signal that no recipes could be detected.
 * This leads to an error response.
 */
public class NoRecipesDetectedException extends RuntimeException {
}
