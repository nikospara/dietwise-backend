package eu.dietwise.jaxrs.v1;

/**
 * Request to stage an Alternative-Ingredient-to-Recommendation link to an absolute target presence in the Working Copy.
 *
 * @param present The target effective presence of the link ({@code true} to link, {@code false} to unlink)
 */
public record ToggleRecommendationRequest(boolean present) {
}
