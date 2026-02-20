package eu.dietwise.v1.types.impl;

import java.util.Objects;

import eu.dietwise.v1.types.Recommendation;

public class RecommendationImpl implements Recommendation {
	private final String representation;

	public RecommendationImpl(String representation) {
		this.representation = Objects.requireNonNull(representation);
	}

	@Override
	public String asString() {
		return representation;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof Recommendation that)) return false;
		return Objects.equals(representation, that.asString());
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}

	@Override
	public String toString() {
		return "RecommendationImpl(" + representation + ")";
	}
}
