package eu.dietwise.services.v1.suggestions.impl;

import eu.dietwise.v1.model.Ingredient;

class TriggerIngredientFromAiNotInDbException extends NonFatalIngredientProcessingException {
	private final Ingredient ingredient;
	private final String triggerIngredientFromAi;

	public TriggerIngredientFromAiNotInDbException(Ingredient ingredient, String triggerIngredientFromAi) {
		super("The AI returned a trigger ingredient name that cannot be found in the DB for " + ingredient.getNameInRecipe() + ": " + triggerIngredientFromAi);
		this.ingredient = ingredient;
		this.triggerIngredientFromAi = triggerIngredientFromAi;
	}

	public Ingredient getIngredient() {
		return ingredient;
	}

	public String getTriggerIngredientFromAi() {
		return triggerIngredientFromAi;
	}
}
