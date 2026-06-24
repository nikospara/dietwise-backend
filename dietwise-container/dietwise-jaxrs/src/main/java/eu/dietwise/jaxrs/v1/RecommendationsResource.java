package eu.dietwise.jaxrs.v1;

import java.util.List;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.BackofficeRecommendationsService;
import io.smallrye.mutiny.Uni;

@Path("recommendations")
public class RecommendationsResource {
	@Inject
	BackofficeRecommendationsService backofficeRecommendationsService;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<RecommendationResponse>> listRecommendations(@Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRecommendationsService.listRecommendations(user).map(RecommendationResponse::fromAll);
	}
}
