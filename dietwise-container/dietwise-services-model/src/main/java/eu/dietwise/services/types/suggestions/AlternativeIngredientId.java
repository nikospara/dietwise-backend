package eu.dietwise.services.types.suggestions;

import java.util.UUID;

import eu.dietwise.common.types.RepresentableAsString;

public interface AlternativeIngredientId extends HasAlternativeIngredientId, RepresentableAsString {
	@Override
	default AlternativeIngredientId getId() {
		return this;
	}

	UUID asUuid();
}
