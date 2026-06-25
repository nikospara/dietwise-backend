package eu.dietwise.jaxrs.v1;

import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.BackofficeAlternativeIngredientsService;
import io.smallrye.mutiny.Uni;

@Path("alternative-ingredients")
public class AlternativeIngredientsResource {
	@Inject
	BackofficeAlternativeIngredientsService backofficeAlternativeIngredientsService;

	@GET
	@Path("recommendations")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<AlternativeIngredientGridResponse> recommendationGrid(@Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeAlternativeIngredientsService.recommendationGrid(user).map(AlternativeIngredientGridResponse::from);
	}

	@PUT
	@Path("{id}/recommendations/{recommendationId}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> toggleRecommendation(@PathParam("id") String id, @PathParam("recommendationId") String recommendationId, ToggleRecommendationRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeAlternativeIngredientsService.toggleRecommendation(user, UUID.fromString(id), UUID.fromString(recommendationId), request.present());
	}

	@DELETE
	@Path("{id}")
	public Uni<Void> discardAlternativeIngredient(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeAlternativeIngredientsService.discardAlternativeIngredient(user, UUID.fromString(id));
	}
}
