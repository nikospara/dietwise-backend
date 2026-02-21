package eu.dietwise.v1.model;

import java.util.Optional;

import eu.dietwise.v1.types.HasIngredientId;
import eu.dietwise.v1.types.RoleOrTechnique;
import eu.dietwise.v1.types.TriggerIngredient;
import org.immutables.value.Value;

@Value.Immutable
public interface Ingredient extends HasIngredientId {
	String getNameInRecipe();

	Optional<TriggerIngredient> getTriggerIngredient();

	Optional<RoleOrTechnique> getRoleOrTechnique();

	static boolean hasName(Ingredient ingredient) {
		return ingredient != null && ingredient.getNameInRecipe() != null && !ingredient.getNameInRecipe().isBlank();
	}
}
