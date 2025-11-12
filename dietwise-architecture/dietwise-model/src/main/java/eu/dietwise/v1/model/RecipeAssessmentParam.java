package eu.dietwise.v1.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.immutables.value.Value;

@Value.Immutable
public interface RecipeAssessmentParam {
	String getUrl();

	String getPageContent();

	String getLangCode();
}
