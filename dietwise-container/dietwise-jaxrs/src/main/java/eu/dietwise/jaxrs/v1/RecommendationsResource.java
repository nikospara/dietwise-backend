package eu.dietwise.jaxrs.v1;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.dietwise.common.types.RecommendationTranslationDetails;
import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.BackofficeRecommendationsService;
import eu.dietwise.v1.types.RecipeLanguage;
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

	@PUT
	@Path("{id}/explanation")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<StagedVersionResponse> stageExplanation(@PathParam("id") String id, StageExplanationRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRecommendationsService.stageExplanation(user, UUID.fromString(id), request.explanationForLlm(), request.baseVersion())
				.map(StagedVersionResponse::new);
	}

	@DELETE
	@Path("{id}/explanation")
	public Uni<Void> revertExplanation(@PathParam("id") String id, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRecommendationsService.revertExplanation(user, UUID.fromString(id), baseVersion);
	}

	@GET
	@Path("{id}/translations")
	@Produces(MediaType.APPLICATION_JSON)
	public Uni<Map<RecipeLanguage, RecommendationTranslationDetails>> translationsForEdit(@PathParam("id") String id, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRecommendationsService.translationsForEdit(user, UUID.fromString(id));
	}

	@PUT
	@Path("{id}/translations/{lang}")
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Void> stageTranslation(@PathParam("id") String id, @PathParam("lang") String lang, EditRecommendationTranslationRequest request, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRecommendationsService.stageTranslation(user, UUID.fromString(id), RecipeLanguage.valueOf(lang), request.name(), request.componentForScoring(), request.explanationForLlm(), request.baseVersion());
	}

	@DELETE
	@Path("{id}/translations/{lang}")
	public Uni<Void> revertTranslation(@PathParam("id") String id, @PathParam("lang") String lang, @QueryParam("baseVersion") long baseVersion, @Context ContainerRequestContext crc) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return backofficeRecommendationsService.revertTranslation(user, UUID.fromString(id), RecipeLanguage.valueOf(lang), baseVersion);
	}
}
