package eu.dietwise.v1.types.impl;

import java.util.Objects;
import java.util.UUID;

import eu.dietwise.v1.types.SuggestionTemplateId;

public class GenericSuggestionTemplateId implements SuggestionTemplateId {
	private final String representation;

	public GenericSuggestionTemplateId(String representation) {
		this.representation = Objects.requireNonNull(representation);
	}

	@Override
	public String asString() {
		return representation;
	}

	@Override
	public UUID asUuid() {
		return UUID.fromString(representation);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof SuggestionTemplateId that)) return false;
		return Objects.equals(asString(), that.asString());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}

	@Override
	public String toString() {
		return "GenericSuggestionTemplateId(" + representation + ")";
	}
}
