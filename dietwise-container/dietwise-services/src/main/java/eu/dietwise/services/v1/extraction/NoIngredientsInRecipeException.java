package eu.dietwise.services.v1.extraction;

/**
 * Used to signal that the detected recipe contains no ingredients, leading to an error response.
 */
public class NoIngredientsInRecipeException extends RuntimeException {
}
