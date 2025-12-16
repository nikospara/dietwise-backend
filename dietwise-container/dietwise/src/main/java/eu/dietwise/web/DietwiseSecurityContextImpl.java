package eu.dietwise.web;

import java.security.Principal;
import jakarta.ws.rs.core.SecurityContext;

public class DietwiseSecurityContextImpl implements SecurityContext {
	private final SecurityContext delegate;
	private final Principal principal;

	public DietwiseSecurityContextImpl(SecurityContext delegate, Principal principal) {
		this.delegate = delegate;
		this.principal = principal;
	}

	public SecurityContext getDelegate() {
		return delegate;
	}

	@Override
	public Principal getUserPrincipal() {
		return principal;
	}

	@Override
	public boolean isUserInRole(String s) {
		return delegate.isUserInRole(s);
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
