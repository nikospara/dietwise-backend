package eu.dietwise.v1.types;

import java.util.UUID;

import eu.dietwise.common.types.RepresentableAsString;

public interface SuggestionTemplateId extends HasSuggestionTemplateId, RepresentableAsString {
	@Override
	default SuggestionTemplateId getId() {
		return this;
	}

	UUID asUuid();
}
