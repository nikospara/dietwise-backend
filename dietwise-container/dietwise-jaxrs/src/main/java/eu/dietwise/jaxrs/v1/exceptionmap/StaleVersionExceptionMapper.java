package eu.dietwise.jaxrs.v1.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.dietwise.common.dao.StaleVersionException;

/**
 * Map a {@link StaleVersionException} to HTTP 409 (CONFLICT): the edit was based on a stale version, so the client
 * should reload the current state and redo it.
 */
@Provider
public class StaleVersionExceptionMapper implements ExceptionMapper<StaleVersionException> {
	@Override
	public Response toResponse(StaleVersionException exception) {
		return Response.status(Response.Status.CONFLICT).build();
	}
}
