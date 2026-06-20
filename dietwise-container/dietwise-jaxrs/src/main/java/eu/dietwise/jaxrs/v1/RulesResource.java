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
import eu.dietwise.common.types.SuggestionTemplateField;
import eu.dietwise.common.types.VersionedText;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.BackofficeReferenceDataService;
import eu.dietwise.services.v1.BackofficeRulesService;
import eu.dietwise.services.v1.BackofficeSuggestionTemplatesService;
import eu.dietwise.services.v1.types.AddedTemplate;
import eu.dietwise.services.v1.types.AlternativeIngredientForEdit;
import eu.dietwise.services.v1.types.NewRuleOptions;
import eu.dietwise.v1.types.RecipeLanguage;
import eu.dietwise.v1.types.impl.GenericRuleId;
import eu.dietwise.v1.types.impl.GenericSuggestionTemplateId;
import io.smallrye.mutiny.Uni;

@Path("rules")
public class RulesResource {
	@Inject
	BackofficeRulesService backofficeRulesService;

	@Inject
	BackofficeSuggestionTemplatesService backofficeSuggestionTemplatesService;

	@Inject
	BackofficeReferenceDataService backofficeReferenceDataService;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<RuleResponse>> listRules(@Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.listRules(user).map(RuleResponse::fromAll);
	}

	@GET
	@Path("{id}/suggestion-templates")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<SuggestionTemplateResponse>> listSuggestionTemplates(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeSuggestionTemplatesService.listSuggestionTemplates(user, new GenericRuleId(id)).map(SuggestionTemplateResponse::fromAll);
	}

	@POST
	@Path("{id}/suggestion-templates")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<AddedTemplate> addSuggestionTemplate(@PathParam("id") String id, AddTemplateRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeSuggestionTemplatesService.addSuggestionTemplate(user, new GenericRuleId(id), UUID.fromString(request.alternativeIngredientId()));
	}

	@DELETE
	@Path("suggestion-templates/{templateId}")
	public Uni<Void> discardSuggestionTemplate(@PathParam("templateId") String templateId, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeSuggestionTemplatesService.discardSuggestionTemplate(user, new GenericSuggestionTemplateId(templateId), baseVersion);
	}

	@PUT
	@Path("suggestion-templates/{templateId}/{field}")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<StagedVersionResponse> stageSuggestionTemplateField(@PathParam("templateId") String templateId, @PathParam("field") String field, StageTemplateFieldRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeSuggestionTemplatesService.stageSuggestionTemplateField(user, new GenericSuggestionTemplateId(templateId), SuggestionTemplateField.valueOf(field), request.value(), request.baseVersion())
				.map(StagedVersionResponse::new);
	}

	@DELETE
	@Path("suggestion-templates/{templateId}/{field}")
	public Uni<Void> revertSuggestionTemplateField(@PathParam("templateId") String templateId, @PathParam("field") String field, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeSuggestionTemplatesService.revertSuggestionTemplateField(user, new GenericSuggestionTemplateId(templateId), SuggestionTemplateField.valueOf(field), baseVersion);
	}

	@PUT
	@Path("suggestion-templates/{templateId}/active")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> setSuggestionTemplateActive(@PathParam("templateId") String templateId, SetActiveRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeSuggestionTemplatesService.setSuggestionTemplateActive(user, new GenericSuggestionTemplateId(templateId), request.active(), request.baseVersion());
	}

	@GET
	@Path("suggestion-templates/{templateId}/{field}/translations")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Map<RecipeLanguage, VersionedText>> templateFieldTranslationsForEdit(@PathParam("templateId") String templateId, @PathParam("field") String field, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeSuggestionTemplatesService.templateFieldTranslationsForEdit(user, new GenericSuggestionTemplateId(templateId), SuggestionTemplateField.valueOf(field));
	}

	@PUT
	@Path("suggestion-templates/{templateId}/{field}/translations/{lang}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> stageTemplateFieldTranslation(@PathParam("templateId") String templateId, @PathParam("field") String field, @PathParam("lang") String lang, StageTemplateFieldRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeSuggestionTemplatesService.stageTemplateFieldTranslation(user, new GenericSuggestionTemplateId(templateId), SuggestionTemplateField.valueOf(field), RecipeLanguage.valueOf(lang), request.value(), request.baseVersion());
	}

	@DELETE
	@Path("suggestion-templates/{templateId}/{field}/translations/{lang}")
	public Uni<Void> revertTemplateFieldTranslation(@PathParam("templateId") String templateId, @PathParam("field") String field, @PathParam("lang") String lang, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeSuggestionTemplatesService.revertTemplateFieldTranslation(user, new GenericSuggestionTemplateId(templateId), SuggestionTemplateField.valueOf(field), RecipeLanguage.valueOf(lang), baseVersion);
	}

	@GET
	@Path("new-rule-options")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<NewRuleOptions> newRuleOptions(@Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRulesService.newRuleOptions(user);
	}

	@GET
	@Path("alternative-ingredients")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<List<ReferenceOption>> alternativeIngredientOptions(@Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeSuggestionTemplatesService.alternativeIngredientOptions(user);
	}

	@POST
	@Path("alternative-ingredients")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<ReferenceOption> createAlternativeIngredient(CreateReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.createAlternativeIngredient(user, request.name());
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
		return backofficeReferenceDataService.createTriggerIngredient(user, request.name());
	}

	@POST
	@Path("roles-or-techniques")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<ReferenceOption> createRoleOrTechnique(CreateReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.createRoleOrTechnique(user, request.name());
	}

	@GET
	@Path("trigger-ingredients/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<ReferenceDetails> triggerIngredientForEdit(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.triggerIngredientForEdit(user, UUID.fromString(id));
	}

	@GET
	@Path("roles-or-techniques/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<ReferenceDetails> roleOrTechniqueForEdit(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.roleOrTechniqueForEdit(user, UUID.fromString(id));
	}

	@PUT
	@Path("trigger-ingredients/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> editTriggerIngredient(@PathParam("id") String id, EditReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.editTriggerIngredient(user, UUID.fromString(id), request.name(), request.explanationForLlm(), request.baseVersion());
	}

	@PUT
	@Path("roles-or-techniques/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> editRoleOrTechnique(@PathParam("id") String id, EditReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.editRoleOrTechnique(user, UUID.fromString(id), request.name(), request.explanationForLlm(), request.baseVersion());
	}

	@DELETE
	@Path("trigger-ingredients/{id}")
	public Uni<Void> revertTriggerIngredient(@PathParam("id") String id, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.revertTriggerIngredient(user, UUID.fromString(id), baseVersion);
	}

	@DELETE
	@Path("roles-or-techniques/{id}")
	public Uni<Void> revertRoleOrTechnique(@PathParam("id") String id, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.revertRoleOrTechnique(user, UUID.fromString(id), baseVersion);
	}

	@GET
	@Path("trigger-ingredients/{id}/translations")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Map<RecipeLanguage, ReferenceDetails>> triggerIngredientTranslationsForEdit(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.triggerIngredientTranslationsForEdit(user, UUID.fromString(id));
	}

	@PUT
	@Path("trigger-ingredients/{id}/translations/{lang}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> stageTriggerIngredientTranslation(@PathParam("id") String id, @PathParam("lang") String lang, EditReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.stageTriggerIngredientTranslation(user, UUID.fromString(id), RecipeLanguage.valueOf(lang), request.name(), request.explanationForLlm(), request.baseVersion());
	}

	@DELETE
	@Path("trigger-ingredients/{id}/translations/{lang}")
	public Uni<Void> revertTriggerIngredientTranslation(@PathParam("id") String id, @PathParam("lang") String lang, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.revertTriggerIngredientTranslation(user, UUID.fromString(id), RecipeLanguage.valueOf(lang), baseVersion);
	}

	@GET
	@Path("roles-or-techniques/{id}/translations")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Map<RecipeLanguage, ReferenceDetails>> roleOrTechniqueTranslationsForEdit(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.roleOrTechniqueTranslationsForEdit(user, UUID.fromString(id));
	}

	@PUT
	@Path("roles-or-techniques/{id}/translations/{lang}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> stageRoleOrTechniqueTranslation(@PathParam("id") String id, @PathParam("lang") String lang, EditReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.stageRoleOrTechniqueTranslation(user, UUID.fromString(id), RecipeLanguage.valueOf(lang), request.name(), request.explanationForLlm(), request.baseVersion());
	}

	@DELETE
	@Path("roles-or-techniques/{id}/translations/{lang}")
	public Uni<Void> revertRoleOrTechniqueTranslation(@PathParam("id") String id, @PathParam("lang") String lang, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.revertRoleOrTechniqueTranslation(user, UUID.fromString(id), RecipeLanguage.valueOf(lang), baseVersion);
	}

	@GET
	@Path("alternative-ingredients/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<AlternativeIngredientForEdit> alternativeIngredientForEdit(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.alternativeIngredientForEdit(user, UUID.fromString(id));
	}

	@PUT
	@Path("alternative-ingredients/{id}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> editAlternativeIngredient(@PathParam("id") String id, EditReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.editAlternativeIngredient(user, UUID.fromString(id), request.name(), request.explanationForLlm(), request.baseVersion());
	}

	@DELETE
	@Path("alternative-ingredients/{id}")
	public Uni<Void> revertAlternativeIngredient(@PathParam("id") String id, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.revertAlternativeIngredient(user, UUID.fromString(id), baseVersion);
	}

	@GET
	@Path("alternative-ingredients/{id}/translations")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Map<RecipeLanguage, ReferenceDetails>> alternativeIngredientTranslationsForEdit(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.alternativeIngredientTranslationsForEdit(user, UUID.fromString(id));
	}

	@PUT
	@Path("alternative-ingredients/{id}/translations/{lang}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> stageAlternativeIngredientTranslation(@PathParam("id") String id, @PathParam("lang") String lang, EditReferenceRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.stageAlternativeIngredientTranslation(user, UUID.fromString(id), RecipeLanguage.valueOf(lang), request.name(), request.explanationForLlm(), request.baseVersion());
	}

	@DELETE
	@Path("alternative-ingredients/{id}/translations/{lang}")
	public Uni<Void> revertAlternativeIngredientTranslation(@PathParam("id") String id, @PathParam("lang") String lang, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeReferenceDataService.revertAlternativeIngredientTranslation(user, UUID.fromString(id), RecipeLanguage.valueOf(lang), baseVersion);
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
