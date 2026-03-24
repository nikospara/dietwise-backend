package eu.dietwise.web;

import java.security.Principal;
import jakarta.ws.rs.core.SecurityContext;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.common.v1.types.Role;

public class DietwiseSecurityContextImpl implements SecurityContext {
	private final SecurityContext delegate;
	private final User user;

	public DietwiseSecurityContextImpl(SecurityContext delegate, User user) {
		this.delegate = delegate;
		this.user = user;
	}

	public SecurityContext getDelegate() {
		return delegate;
	}

	@Override
	public Principal getUserPrincipal() {
		return user;
	}

	@Override
	public boolean isUserInRole(String s) {
		return Role.ALL_VALUES_AS_STRINGS.contains(s) && user.getRoles().contains(Role.valueOf(s));
	}

	@Override
	public boolean isSecure() {
		return delegate.isSecure();
	}

	@Override
	public String getAuthenticationScheme() {
		return delegate.getAuthenticationScheme();
	}
}
