package eu.dietwise.jaxrs.v1;

import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.BackofficeSeasonalityCostService;
import eu.dietwise.v1.types.Cost;
import eu.dietwise.v1.types.Country;
import io.smallrye.mutiny.Uni;

@Path("seasonality-cost")
public class SeasonalityCostResource {
	@Inject
	BackofficeSeasonalityCostService backofficeSeasonalityCostService;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<SeasonalityCostGridResponse> grid(@Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeSeasonalityCostService.grid(user).map(SeasonalityCostGridResponse::from);
	}

	@PUT
	@Path("{id}/seasonality/{country}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> stageSeasonality(@PathParam("id") String id, @PathParam("country") String country, StageSeasonalityRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeSeasonalityCostService.stageSeasonality(user, UUID.fromString(id), Country.fromCode2(country), request.monthFrom(), request.monthTo(), request.baseVersion());
	}

	@PUT
	@Path("{id}/cost/{country}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> stageCost(@PathParam("id") String id, @PathParam("country") String country, StageCostRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeSeasonalityCostService.stageCost(user, UUID.fromString(id), Country.fromCode2(country), parseCost(request.cost()), request.baseVersion());
	}

	private static Cost parseCost(String cost) {
		return cost == null || cost.isBlank() ? null : Cost.valueOf(cost);
	}
}
