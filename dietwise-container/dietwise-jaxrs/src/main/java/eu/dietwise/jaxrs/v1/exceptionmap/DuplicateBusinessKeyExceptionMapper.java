package eu.dietwise.jaxrs.v1.exceptionmap;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import eu.dietwise.common.dao.DuplicateBusinessKeyException;

/**
 * Map a {@link DuplicateBusinessKeyException} to HTTP 409 (CONFLICT): the proposed Rule would duplicate an existing
 * business key, so the client should choose a different recommendation, trigger ingredient or role.
 */
@Provider
public class DuplicateBusinessKeyExceptionMapper implements ExceptionMapper<DuplicateBusinessKeyException> {
	@Override
	public Response toResponse(DuplicateBusinessKeyException exception) {
		return Response.status(Response.Status.CONFLICT).build();
	}
}
