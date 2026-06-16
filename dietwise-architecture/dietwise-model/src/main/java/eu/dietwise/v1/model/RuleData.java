package eu.dietwise.v1.model;

import eu.dietwise.common.types.Nullable;
import eu.dietwise.v1.types.Recommendation;
import eu.dietwise.v1.types.RoleOrTechnique;
import eu.dietwise.v1.types.TriggerIngredient;
import org.immutables.value.Value;

@Value.Immutable
public interface RuleData {
	Recommendation getRecommendation();

	TriggerIngredient getTriggerIngredient();

	@Nullable
	RoleOrTechnique getRoleOrTechnique();

	@Nullable
	String getRationale();

	@Nullable
	String getCuisineContext();

	/** Whether this Rule is applied by recipe assessment. A deactivated Rule is retained but ignored at assessment time. */
	@Value.Default
	default boolean isActive() {
		return true;
	}
}
