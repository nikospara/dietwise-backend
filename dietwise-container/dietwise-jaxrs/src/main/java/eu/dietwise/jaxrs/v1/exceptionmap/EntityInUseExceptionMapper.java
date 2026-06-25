package eu.dietwise.jaxrs.v1.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.dietwise.common.dao.EntityInUseException;

/**
 * Map an {@link EntityInUseException} to HTTP 409 (CONFLICT): the entity is still referenced or is published master
 * data, so it cannot be discarded; the client should clear the references first.
 */
@Provider
public class EntityInUseExceptionMapper implements ExceptionMapper<EntityInUseException> {
	@Override
	public Response toResponse(EntityInUseException exception) {
		return Response.status(Response.Status.CONFLICT).build();
	}
}
