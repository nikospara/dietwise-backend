package eu.dietwise.v1.model;

import java.util.List;

import eu.dietwise.v1.types.RecipeAssessmentResultStatus;
import org.immutables.value.Value;

@Value.Immutable
public interface RecipeAssessmentResult {
	RecipeAssessmentResultStatus getStatus();

	List<String> getErrors();

	Double getRating();

	List<Suggestion> getSuggestions();
}
