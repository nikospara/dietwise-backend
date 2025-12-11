package eu.dietwise.common.types;

import eu.dietwise.common.types.impl.EmailAddressImpl;

/**
 * Representation of an email in the application.
 */
public interface EmailAddress extends RepresentableAsString {
	static EmailAddress of(String email) {
		return email == null ? null : new EmailAddressImpl(email);
	}
}
