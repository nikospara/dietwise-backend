package eu.dietwise.v1.model;

import jakarta.validation.constraints.NotNull;

import eu.dietwise.v1.types.SuggestionTemplateId;
import org.immutables.value.Value;

@Value.Immutable
public interface StatisticsParam {
	@NotNull
	SuggestionTemplateId getSuggestionId();
}
