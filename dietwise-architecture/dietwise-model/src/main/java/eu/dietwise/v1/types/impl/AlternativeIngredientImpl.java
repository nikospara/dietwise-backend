package eu.dietwise.v1.types.impl;

import java.util.Objects;

import eu.dietwise.v1.types.AlternativeIngredient;

public class AlternativeIngredientImpl implements AlternativeIngredient {
	private final String representation;

	public AlternativeIngredientImpl(String representation) {
		this.representation = Objects.requireNonNull(representation);
	}

	@Override
	public String asString() {
		return representation;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof AlternativeIngredient that)) return false;
		return Objects.equals(representation, that.asString());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}

	@Override
	public String toString() {
		return "AlternativeIngredientImpl(" + representation + ")";
	}
}
