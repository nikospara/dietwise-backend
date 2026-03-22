package eu.dietwise.services.v1.suggestions.impl;

import eu.dietwise.services.model.suggestions.TriggerIngredient;

class NoRulesForTriggerIngredientException extends NonFatalIngredientProcessingException {
	private final TriggerIngredient triggerIngredient;

	public NoRulesForTriggerIngredientException(TriggerIngredient triggerIngredient) {
		super("No rules for trigger ingredient: " + triggerIngredient.getName());
		this.triggerIngredient = triggerIngredient;
	}

	public TriggerIngredient getTriggerIngredient() {
		return triggerIngredient;
	}
}
