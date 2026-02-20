package eu.dietwise.v1.types.impl;

import java.util.Objects;

import eu.dietwise.v1.types.IngredientId;

public class GenericIngredientId implements IngredientId {
	private final String representation;

	public GenericIngredientId(String representation) {
		this.representation = Objects.requireNonNull(representation);
	}

	@Override
	public String asString() {
		return representation;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof IngredientId that)) return false;
		return Objects.equals(representation, that.asString());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}

	@Override
	public String toString() {
		return "GenericIngredientId(" + representation + ")";
	}
}
