package eu.dietwise.services.types.suggestions;

import java.util.UUID;

import eu.dietwise.common.types.RepresentableAsString;

public interface TriggerIngredientId extends HasTriggerIngredientId, RepresentableAsString {
	@Override
	default TriggerIngredientId getId() {
		return this;
	}

	UUID asUuid();
}
