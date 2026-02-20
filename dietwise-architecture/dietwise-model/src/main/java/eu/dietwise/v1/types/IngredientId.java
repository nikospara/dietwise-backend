package eu.dietwise.v1.types;

import eu.dietwise.common.types.RepresentableAsString;

public interface IngredientId extends HasIngredientId, RepresentableAsString {
	@Override
	default IngredientId getId() {
		return this;
	}
}
