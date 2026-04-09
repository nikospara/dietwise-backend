package eu.dietwise.v1.model;

import eu.dietwise.v1.types.RecipeLanguage;
import org.immutables.value.Value;

@Value.Immutable
public interface RecipeAssessmentParam {
	String getUrl();

	String getPageContent();

	RecipeLanguage getLang();
}
