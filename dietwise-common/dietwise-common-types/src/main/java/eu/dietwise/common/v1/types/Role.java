package eu.dietwise.common.v1.types;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum Role {
	// WARNING: If we ever want to use this enum in @RolesAllowed, we have to revisit how roles are assigned.
	// For now there is no such intention, so leaving the implementation as is.
	CITIZEN,
	INFLUENCER;

	public static final Set<String> ALL_VALUES_AS_STRINGS = Collections.unmodifiableSet(Stream.of(values()).map(Enum::name).collect(Collectors.toSet()));
}
