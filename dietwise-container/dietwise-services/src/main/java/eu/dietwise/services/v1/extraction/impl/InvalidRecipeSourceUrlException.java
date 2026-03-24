package eu.dietwise.services.v1.extraction.impl;

public class InvalidRecipeSourceUrlException extends IllegalArgumentException {
	public InvalidRecipeSourceUrlException(String message) {
		super(message);
	}
}
