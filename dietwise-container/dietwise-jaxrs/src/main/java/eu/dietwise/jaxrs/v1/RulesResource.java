package eu.dietwise.jaxrs.v1;

import java.util.List;
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
import eu.dietwise.services.v1.BackofficeRulesService;
import eu.dietwise.v1.types.impl.GenericRuleId;
import io.smallrye.mutiny.Uni;

@Path("rules")
public class RulesResource {
	@Inject
	BackofficeRulesService backofficeRulesService;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<RuleResponse>> listRules(@Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.listRules(user).map(RuleResponse::fromAll);
	}

	@PUT
	@Path("{id}/rationale")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<StagedVersionResponse> stageRationale(@PathParam("id") String id, StageRationaleRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.stageRationale(user, new GenericRuleId(id), request.rationale(), request.baseVersion())
				.map(StagedVersionResponse::new);
	}
}
