package eu.dietwise.common.v1.model;

import java.security.Principal;
import java.util.EnumSet;
import java.util.Optional;

import eu.dietwise.common.types.EmailAddress;
import eu.dietwise.common.types.Nullable;
import eu.dietwise.common.v1.types.HasUserId;
import eu.dietwise.common.v1.types.Role;
import eu.dietwise.common.v1.types.UserId;
import org.immutables.value.Value;

/**
 * A user of this application.
 */
@Value.Immutable
public interface User extends Principal, HasUserId {
	String SYSTEM_USER_ID = "00000000-0000-0000-0000-000000000000";
	String SYSTEM_USER_NAME = "system";

	@Override
	@Nullable
	UserId getId();

	@Override
	@Nullable
	String getName();

	@Nullable
	EmailAddress getEmail();

	/**
	 * The identifier of this user in the identity management system.
	 *
	 * @return The IDM id string; empty for the unauthenticated user
	 */
	Optional<String> getIdmId();

	/**
	 * Check if this user is the system user.
	 *
	 * @return {@code true} if this user is the system user
	 */
	boolean isSystem();

	/**
	 * Check if this user represents a service account.
	 *
	 * @return {@code true} if this user represents a service account
	 */
	boolean isService();

	/**
	 * Check if this user is the unauthenticated (anonymous) user.
	 *
	 * @return {@code true} if this user is the unauthenticated (anonymous) user
	 */
	boolean isUnauthenticated();

	/**
	 * The roles assigned to this user.
	 *
	 * @return The set of roles assigned to this user, never {@code null}, will be empty for the anonymous user
	 */
	EnumSet<Role> getRoles();

	/**
	 * The application id through which the user connected. Matches the client id in the IDM, communicated through the
	 * {@code azp} field of the JWT. See <a href="https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference">here</a>
	 * for more information.
	 *
	 * @return The application id string; could be empty e.g., for the unauthenticated user
	 */
	Optional<String> getApplicationId();
}
