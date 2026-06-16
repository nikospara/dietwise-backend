package eu.dietwise.jaxrs.v1;

import java.util.List;
import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.BackofficeRulesService;
import eu.dietwise.services.v1.types.NewRuleOptions;
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

	@GET
	@Path("new-rule-options")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<NewRuleOptions> newRuleOptions(@Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.newRuleOptions(user);
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<CreatedRuleResponse> createRule(CreateRuleRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		UUID recommendationId = UUID.fromString(request.recommendationId());
		UUID triggerIngredientId = UUID.fromString(request.triggerIngredientId());
		UUID roleOrTechniqueId = request.roleOrTechniqueId() == null ? null : UUID.fromString(request.roleOrTechniqueId());
		return backofficeRulesService.createRule(user, recommendationId, triggerIngredientId, roleOrTechniqueId)
				.map(id -> new CreatedRuleResponse(id.asString()));
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

	@DELETE
	@Path("{id}/rationale")
	public Uni<Void> revertRationale(@PathParam("id") String id, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.revertRationale(user, new GenericRuleId(id), baseVersion);
	}

	@DELETE
	@Path("{id}")
	public Uni<Void> discardNewRule(@PathParam("id") String id, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.discardNewRule(user, new GenericRuleId(id), baseVersion);
	}

	@PUT
	@Path("{id}/active")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> setActive(@PathParam("id") String id, SetActiveRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.setActive(user, new GenericRuleId(id), request.active(), request.baseVersion());
	}
}
