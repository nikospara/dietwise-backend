package eu.dietwise.jaxrs.v1.security;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Marks a resource method or resource class as safe to call when the authenticated account is already tombstoned.
 */
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface AllowDeletedAccount {
}
