package eu.dietwise.services.v1.impl;

/**
 * Used internally from the {@link RecipeAssessmentServiceImpl} to signal that more than one recipe was detected,
 * so the user must choose manually which one to assess. This leads to a {@code MoreThanOneRecipesAssessmentMessage}
 * message.
 */
class MoreThanOneRecipesDetectedException extends RuntimeException {
	private final int numberOfRecipes;

	public MoreThanOneRecipesDetectedException(int numberOfRecipes) {
		this.numberOfRecipes = numberOfRecipes;
	}

	public int getNumberOfRecipes() {
		return numberOfRecipes;
	}
}
