package eu.dietwise.v1.model;

import org.immutables.value.Value;

@Value.Immutable
public interface RecipeAssessmentParam {
	String getUrl();

	String getPageContent();

	String getLangCode();
}
