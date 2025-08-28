package eu.dietwise;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

@ApplicationPath(JaxRsAppV1.APPLICATION_PATH)
public class JaxRsAppV1 extends Application {
	/**
	 * The JAX-RS application path.
	 */
	public static final String APPLICATION_PATH = "/api/v1";
}
