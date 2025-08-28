package eu.dietwise.jaxrs.v1;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import eu.dietwise.services.v1.RecipeAssessmentService;
import eu.dietwise.v1.model.RecipeAssessmentParam;
import io.smallrye.mutiny.Uni;

@Path("recipe/assess")
public class RecipeAssessmentResource {
	@Inject
	RecipeAssessmentService service;

	@POST
	@Path("html")
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Uni<Response> assessHtmlRecipe(RecipeAssessmentParam param) {
		return service.assessHtmlRecipe(param)
				.map(response -> Response.ok().entity(response).build());
	}
}
