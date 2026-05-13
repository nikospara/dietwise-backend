package eu.dietwise.services.keycloak;

import java.util.Map;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestResponse;

@RegisterRestClient(configKey = "keycloak-admin")
public interface KeycloakAdminClient {
	@POST
	@Path("/realms/{realm}/protocol/openid-connect/token")
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces(MediaType.APPLICATION_JSON)
	Uni<Map<String, Object>> getClientCredentialsToken(
			@PathParam("realm") String realm,
			@RestForm("grant_type") String grantType,
			@RestForm("client_id") String clientId,
			@RestForm("client_secret") String clientSecret
	);

	@DELETE
	@Path("/admin/realms/{realm}/users/{userId}")
	Uni<RestResponse<Void>> deleteUser(
			@PathParam("realm") String realm,
			@PathParam("userId") String userId,
			@HeaderParam("Authorization") String authorization
	);
}
