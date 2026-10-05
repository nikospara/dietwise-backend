package eu.dietwise.services.v1.types;

import java.util.UUID;

import eu.dietwise.v1.types.RecommendationWeight;

/**
 * One column of the substitution-value grid: a Recommendation an Alternative Ingredient can be linked to, meaning the
 * Alternative Ingredient carries that Recommendation's component for scoring. Identified by its id, labelled by its
 * component for scoring (the immutable scoring key shown in the header) and carrying its weight, since carrying an
 * ENCOURAGED component raises the score of a recipe and carrying a LIMITED one lowers it.
 */
public record RecommendationColumn(UUID id, String componentForScoring, RecommendationWeight weight) {
}
