package eu.dietwise.jaxrs.v1;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.StatisticsService;
import eu.dietwise.v1.model.ImmutableStatisticsResponse;
import eu.dietwise.v1.model.StatisticsParam;
import eu.dietwise.v1.model.StatisticsResponse;
import io.smallrye.mutiny.Uni;

@Path("statistics")
public class StatisticsResource {
	@Inject
	StatisticsService statisticsService;

	@POST
	@Path("increaseTimesAccepted")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<StatisticsResponse> increaseTimesAccepted(@Context ContainerRequestContext crc, @Valid StatisticsParam param) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return statisticsService.increaseTimesAccepted(user, param.getSuggestionId())
				.map(result -> ImmutableStatisticsResponse.builder().updatedValue(result).build());
	}

	@POST
	@Path("decreaseTimesAccepted")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<StatisticsResponse> decreaseTimesAccepted(@Context ContainerRequestContext crc, @Valid StatisticsParam param) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return statisticsService.decreaseTimesAccepted(user, param.getSuggestionId())
				.map(result -> ImmutableStatisticsResponse.builder().updatedValue(result).build());
	}

	@POST
	@Path("increaseTimesRejected")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<StatisticsResponse> increaseTimesRejected(@Context ContainerRequestContext crc, @Valid StatisticsParam param) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return statisticsService.increaseTimesRejected(user, param.getSuggestionId())
				.map(result -> ImmutableStatisticsResponse.builder().updatedValue(result).build());
	}

	@POST
	@Path("decreaseTimesRejected")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<StatisticsResponse> decreaseTimesRejected(@Context ContainerRequestContext crc, @Valid StatisticsParam param) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return statisticsService.decreaseTimesRejected(user, param.getSuggestionId())
				.map(result -> ImmutableStatisticsResponse.builder().updatedValue(result).build());
	}
}
