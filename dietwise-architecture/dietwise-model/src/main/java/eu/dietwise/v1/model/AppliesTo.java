package eu.dietwise.v1.model;

public sealed interface AppliesTo {
	record AppliesToIngredient(String ingredient) implements AppliesTo {
	}

	record AppliesToRecipe(String recipe) implements AppliesTo {
	}
}
