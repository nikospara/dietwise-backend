package eu.dietwise.jaxrs.v1;

/**
 * Request to stage a brand-new Rule in the Working Copy, choosing its business key from existing reference data.
 *
 * @param recommendationId    The chosen Recommendation id
 * @param triggerIngredientId The chosen Trigger Ingredient id
 * @param roleOrTechniqueId   The chosen Role or Technique id, or {@code null} for none
 */
public record CreateRuleRequest(String recommendationId, String triggerIngredientId, String roleOrTechniqueId) {
}
