package eu.dietwise.jaxrs.v1;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

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
	@Path("html")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Multi<RecipeAssessmentMessage> assessHtmlRecipe(RecipeAssessmentParam param) {
		return RestMulti.fromMultiData(service.assessHtmlRecipe(param)).encodeAsJsonArray(false).build();
	}

	@POST
	@Path("markdown")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Multi<RecipeAssessmentMessage> assessMarkdownRecipe(RecipeAssessmentParam param) {
		return RestMulti.fromMultiData(service.assessMarkdownRecipe(param)).encodeAsJsonArray(false).build();
	}

	@POST
	@Path("url")
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.APPLICATION_JSON)
	public Multi<RecipeAssessmentMessage> extractAndAssessRecipeFromUrl(RecipeExtractionAndAssessmentParam param) {
		return RestMulti.fromMultiData(service.extractAndAssessRecipeFromUrl(param)).encodeAsJsonArray(false).build();
	}
}
