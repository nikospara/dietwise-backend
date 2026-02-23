package eu.dietwise.v1.model;

import eu.dietwise.v1.types.IngredientId;

public sealed interface AppliesTo {
	record AppliesToIngredient(IngredientId ingredient) implements AppliesTo {
	}

	record AppliesToRecipe(String recipeName) implements AppliesTo {
	}
}
