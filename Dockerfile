# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /workspace

# Copy only POM files first so dependency resolution can be cached.
# Regenerate this block with: ./scripts/update-dockerfile-pom-copy.sh
# BEGIN auto-pom-copy
COPY dietwise-architecture/dietwise-model-json/pom.xml dietwise-architecture/dietwise-model-json/pom.xml
COPY dietwise-architecture/dietwise-model/pom.xml dietwise-architecture/dietwise-model/pom.xml
COPY dietwise-architecture/dietwise-service-interfaces/pom.xml dietwise-architecture/dietwise-service-interfaces/pom.xml
COPY dietwise-architecture/pom.xml dietwise-architecture/pom.xml
COPY dietwise-common/dietwise-common-dao-reactive-hibernate/pom.xml dietwise-common/dietwise-common-dao-reactive-hibernate/pom.xml
COPY dietwise-common/dietwise-common-dao-reactive/pom.xml dietwise-common/dietwise-common-dao-reactive/pom.xml
COPY dietwise-common/dietwise-common-testutils/pom.xml dietwise-common/dietwise-common-testutils/pom.xml
COPY dietwise-common/dietwise-common-types/pom.xml dietwise-common/dietwise-common-types/pom.xml
COPY dietwise-common/dietwise-common-utils/pom.xml dietwise-common/dietwise-common-utils/pom.xml
COPY dietwise-common/pom.xml dietwise-common/pom.xml
COPY dietwise-container/dietwise-dao-hibernate-reactive/pom.xml dietwise-container/dietwise-dao-hibernate-reactive/pom.xml
COPY dietwise-container/dietwise-dao/pom.xml dietwise-container/dietwise-dao/pom.xml
COPY dietwise-container/dietwise-jaxrs/pom.xml dietwise-container/dietwise-jaxrs/pom.xml
COPY dietwise-container/dietwise-services/pom.xml dietwise-container/dietwise-services/pom.xml
COPY dietwise-container/dietwise/pom.xml dietwise-container/dietwise/pom.xml
COPY dietwise-container/pom.xml dietwise-container/pom.xml
COPY dietwise-docker/dietwise-docker-keycloak/pom.xml dietwise-docker/dietwise-docker-keycloak/pom.xml
COPY dietwise-docker/dietwise-docker-postgres/pom.xml dietwise-docker/dietwise-docker-postgres/pom.xml
COPY dietwise-docker/pom.xml dietwise-docker/pom.xml
COPY pom.xml pom.xml
# END auto-pom-copy

RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -DskipTests dependency:go-offline

COPY . .

# Build the full multi-module project and skip test execution.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /opt/quarkus

COPY --from=build /workspace/dietwise-container/dietwise/target/quarkus-app/lib/ ./lib/
COPY --from=build /workspace/dietwise-container/dietwise/target/quarkus-app/*.jar ./
COPY --from=build /workspace/dietwise-container/dietwise/target/quarkus-app/app/ ./app/
COPY --from=build /workspace/dietwise-container/dietwise/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8180

ENTRYPOINT ["java", "-jar", "/opt/quarkus/quarkus-run.jar"]
