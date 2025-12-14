package eu.dietwise.common.test.testcontainers;

import org.testcontainers.utility.DockerImageName;

/**
 * Convenient constants for TestContainers Docker image names used in the application.
 */
public interface DockerImageNames {
	/** Postgres version used by the application, keep this synchronized with dietwise-docker-postgres/Dockerfile. */
	DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18-alpine").asCompatibleSubstituteFor("postgres");
}
