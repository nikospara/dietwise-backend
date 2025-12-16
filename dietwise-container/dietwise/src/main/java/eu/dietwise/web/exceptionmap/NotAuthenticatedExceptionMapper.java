package eu.dietwise.web.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.dietwise.common.types.authorization.NotAuthenticatedException;


/**
 * Map the {@link NotAuthenticatedException} of TealHelix to HTTP 401 (UNAUTHORIZED).
 */
@Provider
public class NotAuthenticatedExceptionMapper implements ExceptionMapper<NotAuthenticatedException> {
	@Override
	public Response toResponse(NotAuthenticatedException exception) {
		return Response.status(Response.Status.UNAUTHORIZED).build();
	}
}
