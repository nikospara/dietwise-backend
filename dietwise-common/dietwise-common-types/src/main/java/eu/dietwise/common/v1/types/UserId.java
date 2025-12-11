package eu.dietwise.common.v1.types;

import eu.dietwise.common.types.RepresentableAsString;

/**
 * Abstract user id.
 */
public interface UserId extends HasUserId, RepresentableAsString {
	@Override
	default UserId getId() {
		return this;
	}
}
