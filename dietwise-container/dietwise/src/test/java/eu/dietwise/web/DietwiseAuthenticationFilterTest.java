package eu.dietwise.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import eu.dietwise.common.v1.types.Role;
import org.junit.jupiter.api.Test;

class DietwiseAuthenticationFilterTest {

	@Test
	void recipewatchClientMapsToCitizen() {
		assertEquals(EnumSet.of(Role.CITIZEN), DietwiseAuthenticationFilter.rolesFor(Optional.of("recipewatch"), Set.of()));
	}

	@Test
	void rcaClientMapsToInfluencer() {
		assertEquals(EnumSet.of(Role.INFLUENCER), DietwiseAuthenticationFilter.rolesFor(Optional.of("rca"), Set.of()));
	}

	@Test
	void backofficeRealmRoleMapsToAdmin() {
		assertEquals(
				EnumSet.of(Role.ADMIN),
				DietwiseAuthenticationFilter.rolesFor(Optional.of("backoffice"), Set.of("backoffice", "offline_access")));
	}

	@Test
	void backofficeClientWithoutRealmRoleGetsNoRole() {
		// Authenticating against the backoffice client is not enough; the realm role must be granted.
		assertEquals(EnumSet.noneOf(Role.class), DietwiseAuthenticationFilter.rolesFor(Optional.of("backoffice"), Set.of()));
	}

	@Test
	void realmRoleGrantsAdminRegardlessOfClient() {
		assertEquals(
				EnumSet.of(Role.CITIZEN, Role.ADMIN),
				DietwiseAuthenticationFilter.rolesFor(Optional.of("recipewatch"), Set.of("backoffice")));
	}

	@Test
	void noClientAndNoRealmRoleYieldsNoRoles() {
		assertEquals(EnumSet.noneOf(Role.class), DietwiseAuthenticationFilter.rolesFor(Optional.empty(), Set.of()));
	}
}
