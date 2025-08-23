package eu.dietwise.v1.model;

import java.util.List;

import eu.dietwise.v1.types.RecipeAssessmentResultStatus;

public interface RecipeAssessmentResult {
	RecipeAssessmentResultStatus getStatus();
	List<String> getErrors();
	Integer getRating();
	List<Suggestion> getSuggestions();
}
