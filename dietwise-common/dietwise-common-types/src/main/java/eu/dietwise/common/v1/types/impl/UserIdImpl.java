package eu.dietwise.common.v1.types.impl;

import java.util.Objects;

import eu.dietwise.common.v1.types.UserId;

public class UserIdImpl implements UserId {
	private final String representation;

	public UserIdImpl(String representation) {
		this.representation = Objects.requireNonNull(representation);
	}

	@Override
	public String asString() {
		return representation;
	}

	@Override
	public boolean equals(Object o) {
		return this == o || (o instanceof UserId other && Objects.equals(representation, other.asString()));
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(representation);
	}

	@Override
	public String toString() {
		return "UserIdImpl(" + representation + ")";
	}
}
