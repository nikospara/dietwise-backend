package eu.dietwise.jaxrs.v1.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.dietwise.common.dao.EntityNotFoundException;

/**
 * Map the {@link EntityNotFoundException} of TealHelix to HTTP 404 (NOT_FOUND).
 */
@Provider
public class EntityNotFoundExceptionMapper implements ExceptionMapper<EntityNotFoundException> {
	@Override
	public Response toResponse(EntityNotFoundException e) {
		return Response.status(Response.Status.NOT_FOUND).build();
	}
}
