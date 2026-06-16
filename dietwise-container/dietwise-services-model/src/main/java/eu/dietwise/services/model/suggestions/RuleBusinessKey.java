package eu.dietwise.services.model.suggestions;

import java.util.UUID;

/**
 * The business key that uniquely identifies a Rule: the triplet of recommendation, trigger ingredient and role or
 * technique. A {@code null} role or technique is treated as a value (two Rules with the same recommendation and
 * trigger ingredient and no role collide).
 */
public record RuleBusinessKey(UUID recommendationId, UUID triggerIngredientId, UUID roleOrTechniqueId) {
}
