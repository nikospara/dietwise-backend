package eu.dietwise.jaxrs.v1;

import java.util.List;
import java.util.Map;
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

import eu.dietwise.common.types.ReferenceDetails;
import eu.dietwise.common.types.ReferenceOption;
import eu.dietwise.common.types.VersionedText;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.BackofficeRulesService;
import eu.dietwise.services.v1.types.NewRuleOptions;
import eu.dietwise.v1.types.RecipeLanguage;
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

	@POST
	@Path("trigger-ingredients")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<ReferenceOption> createTriggerIngredient(CreateReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.createTriggerIngredient(user, request.name());
	}

	@POST
	@Path("roles-or-techniques")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<ReferenceOption> createRoleOrTechnique(CreateReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.createRoleOrTechnique(user, request.name());
	}

	@GET
	@Path("trigger-ingredients/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<ReferenceDetails> triggerIngredientForEdit(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.triggerIngredientForEdit(user, UUID.fromString(id));
	}

	@GET
	@Path("roles-or-techniques/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<ReferenceDetails> roleOrTechniqueForEdit(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.roleOrTechniqueForEdit(user, UUID.fromString(id));
	}

	@PUT
	@Path("trigger-ingredients/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> editTriggerIngredient(@PathParam("id") String id, EditReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.editTriggerIngredient(user, UUID.fromString(id), request.name(), request.explanationForLlm(), request.baseVersion());
	}

	@PUT
	@Path("roles-or-techniques/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> editRoleOrTechnique(@PathParam("id") String id, EditReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.editRoleOrTechnique(user, UUID.fromString(id), request.name(), request.explanationForLlm(), request.baseVersion());
	}

	@DELETE
	@Path("trigger-ingredients/{id}")
	public Uni<Void> revertTriggerIngredient(@PathParam("id") String id, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.revertTriggerIngredient(user, UUID.fromString(id), baseVersion);
	}

	@DELETE
	@Path("roles-or-techniques/{id}")
	public Uni<Void> revertRoleOrTechnique(@PathParam("id") String id, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.revertRoleOrTechnique(user, UUID.fromString(id), baseVersion);
	}

	@GET
	@Path("trigger-ingredients/{id}/translations")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Map<RecipeLanguage, ReferenceDetails>> triggerIngredientTranslationsForEdit(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.triggerIngredientTranslationsForEdit(user, UUID.fromString(id));
	}

	@PUT
	@Path("trigger-ingredients/{id}/translations/{lang}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> stageTriggerIngredientTranslation(@PathParam("id") String id, @PathParam("lang") String lang, EditReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.stageTriggerIngredientTranslation(user, UUID.fromString(id), RecipeLanguage.valueOf(lang), request.name(), request.explanationForLlm(), request.baseVersion());
	}

	@DELETE
	@Path("trigger-ingredients/{id}/translations/{lang}")
	public Uni<Void> revertTriggerIngredientTranslation(@PathParam("id") String id, @PathParam("lang") String lang, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.revertTriggerIngredientTranslation(user, UUID.fromString(id), RecipeLanguage.valueOf(lang), baseVersion);
	}

	@GET
	@Path("roles-or-techniques/{id}/translations")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Map<RecipeLanguage, ReferenceDetails>> roleOrTechniqueTranslationsForEdit(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.roleOrTechniqueTranslationsForEdit(user, UUID.fromString(id));
	}

	@PUT
	@Path("roles-or-techniques/{id}/translations/{lang}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> stageRoleOrTechniqueTranslation(@PathParam("id") String id, @PathParam("lang") String lang, EditReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.stageRoleOrTechniqueTranslation(user, UUID.fromString(id), RecipeLanguage.valueOf(lang), request.name(), request.explanationForLlm(), request.baseVersion());
	}

	@DELETE
	@Path("roles-or-techniques/{id}/translations/{lang}")
	public Uni<Void> revertRoleOrTechniqueTranslation(@PathParam("id") String id, @PathParam("lang") String lang, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.revertRoleOrTechniqueTranslation(user, UUID.fromString(id), RecipeLanguage.valueOf(lang), baseVersion);
	}

	@GET
	@Path("{id}/rationale-translations")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Map<RecipeLanguage, VersionedText>> rationaleTranslationsForEdit(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.rationaleTranslationsForEdit(user, new GenericRuleId(id));
	}

	@PUT
	@Path("{id}/rationale-translations/{lang}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> stageRationaleTranslation(@PathParam("id") String id, @PathParam("lang") String lang, StageRationaleRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.stageRationaleTranslation(user, new GenericRuleId(id), RecipeLanguage.valueOf(lang), request.rationale(), request.baseVersion());
	}

	@DELETE
	@Path("{id}/rationale-translations/{lang}")
	public Uni<Void> revertRationaleTranslation(@PathParam("id") String id, @PathParam("lang") String lang, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.revertRationaleTranslation(user, new GenericRuleId(id), RecipeLanguage.valueOf(lang), baseVersion);
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
