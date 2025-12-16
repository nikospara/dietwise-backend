package eu.dietwise.jaxrs.v1;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.PersonalInfoService;
import eu.dietwise.v1.model.PersonalInfo;
import io.smallrye.mutiny.Uni;

@Path("personal-info")
public class PersonalInfoResource {
	@Inject
	PersonalInfoService personalInfoService;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<PersonalInfo> getPersonalInfo(@Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return personalInfoService.findByUser(user);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<PersonalInfo> storePersonalInfo(@Context ContainerRequestContext crc, PersonalInfo personalInfo) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return personalInfoService.storeForUser(user, personalInfo);
	}
}
