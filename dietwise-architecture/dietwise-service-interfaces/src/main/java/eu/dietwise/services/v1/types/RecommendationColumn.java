package eu.dietwise.services.v1.types;

import java.util.UUID;

/**
 * One column of the substitution-value grid: an ENCOURAGED Recommendation an Alternative Ingredient can be linked to.
 * Identified by its id and labelled by its component for scoring (the immutable scoring key shown in the header). Only
 * ENCOURAGED Recommendations appear as columns, because an Alternative Ingredient contributes value by providing an
 * encouraged component.
 */
public record RecommendationColumn(UUID id, String componentForScoring) {
}
