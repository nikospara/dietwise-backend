package eu.dietwise.dao.suggestions;

import java.util.List;

import eu.dietwise.common.dao.reactive.ReactivePersistenceContext;
import eu.dietwise.services.types.suggestions.HasRoleOrTechniqueId;
import eu.dietwise.services.types.suggestions.HasTriggerIngredientId;
import eu.dietwise.v1.model.Ingredient;
import eu.dietwise.v1.model.Suggestion;
import eu.dietwise.v1.types.HasRuleId;
import io.smallrye.mutiny.Uni;

public interface SuggestionDao {
	Uni<List<Suggestion>> findByRoleAndTriggerIngredient(
			ReactivePersistenceContext em, HasRoleOrTechniqueId roleId, HasTriggerIngredientId triggerIngredientId, Ingredient ingredient);

	Uni<List<Suggestion>> findByRule(ReactivePersistenceContext em, HasRuleId ruleId, Ingredient ingredient);
}
