package eu.dietwise.v1.model;

import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.RoleOrTechnique;
import eu.dietwise.v1.types.TriggerIngredient;
import org.immutables.value.Value;

@Value.Immutable
public interface RuleData {
	Recommendation getRecommendation();

	TriggerIngredient getTriggerIngredient();

	RoleOrTechnique getRoleOrTechnique();

	String getCuisineContext();
}
