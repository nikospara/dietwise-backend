package eu.dietwise.services.v1.suggestions.impl;

class NoMatchingRuleException extends NonFatalIngredientProcessingException {
	private final String ruleIdFromAi;

	public NoMatchingRuleException(String ruleIdFromAi) {
		super("No rule matching the id emitted from the AI: " + ruleIdFromAi);
		this.ruleIdFromAi = ruleIdFromAi;
	}

	public String getRuleIdFromAi() {
		return ruleIdFromAi;
	}
}
