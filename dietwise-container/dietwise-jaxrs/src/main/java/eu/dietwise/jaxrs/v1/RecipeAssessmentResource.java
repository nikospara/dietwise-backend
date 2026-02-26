package eu.dietwise.jaxrs.v1;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

import eu.dietwise.common.v1.model.User;
import eu.dietwise.services.v1.RecipeAssessmentService;
import eu.dietwise.services.v1.types.RecipeAssessmentMessage;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import eu.dietwise.v1.model.RecipeExtractionAndAssessmentParam;
import io.smallrye.mutiny.Multi;
import org.jboss.resteasy.reactive.RestMulti;

@Path("recipe/assess")
public class RecipeAssessmentResource {
	@Inject
	RecipeAssessmentService service;

	@POST
	@Path("markdown")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Multi<RecipeAssessmentMessage> assessMarkdownRecipe(@Context ContainerRequestContext crc, RecipeAssessmentParam param) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return RestMulti.fromMultiData(service.assessMarkdownRecipe(user, param)).encodeAsJsonArray(false).build();
	}

	@POST
	@Path("url")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Multi<RecipeAssessmentMessage> extractAndAssessRecipeFromUrl(@Context ContainerRequestContext crc, RecipeExtractionAndAssessmentParam param) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return RestMulti.fromMultiData(service.extractAndAssessRecipeFromUrl(user, param)).encodeAsJsonArray(false).build();
	}

	@POST
	@Path("url-dummy")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Multi<RecipeAssessmentMessage> extractAndAssessRecipeFromUrlDummy(@Context ContainerRequestContext crc, RecipeExtractionAndAssessmentParam param) {
		var user = (User) crc.getSecurityContext().getUserPrincipal();
		return RestMulti.fromMultiData(service.extractAndAssessRecipeFromUrlDummy(user, param)).encodeAsJsonArray(false).build();
	}
}
