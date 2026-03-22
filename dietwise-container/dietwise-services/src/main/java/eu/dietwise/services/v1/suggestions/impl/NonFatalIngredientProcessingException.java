package eu.dietwise.services.v1.suggestions.impl;

abstract class NonFatalIngredientProcessingException extends RuntimeException {
	public NonFatalIngredientProcessingException(String message) {
		super(message);
	}
}
