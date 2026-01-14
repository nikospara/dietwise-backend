package eu.dietwise.services.renderer;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/render")
@RegisterRestClient(configKey = "renderer")
public interface RendererClient {
	@POST
	Uni<RestResponse<RenderResponse>> render(RenderRequest request);
}
