# AGENTS Guide for DietWise Backend

This application is the backend for a mobile app, called "MyRecipeWatch" and a browser extension, called
"Responsible Cooking Alliance" (or RCA). Their functionality is similar, to assess a recipe from a web page and suggest
healthier and more sustainable alternatives.

User login is handled by a Keycloak server, the development version of which is built in the Maven module
`dietwise-docker-keycloak` (path: dietwise-docker/dietwise-docker-keycloak).

## Scope
These instructions apply to the entire repository.

## Required Reading
Before making code changes, read:
- `dietwise-architecture/src/site/markdown/CodingConventions.md`

Treat `dietwise-architecture/src/site/markdown/CodingConventions.md` as authoritative for coding style and conventions.

## Project Overview
- Build system: Maven (multi-module).
- Java version: 25.
- Main deployable Quarkus app module: `dietwise-container/dietwise`.
- Master Maven reactor: root `pom.xml`.

## Repository Structure
- `dietwise-architecture`: shared domain models and service interfaces.
- `dietwise-common`: shared utilities, common DAO abstractions, test utilities.
- `dietwise-container`: deployable/runtime modules.
  - `dietwise`: Quarkus application module (final executable artifact), effective configuration lives in `dietwise-container/dietwise/src/main/resources/application.properties`
  - `dietwise-services-model`: data objects shared internally between the DAO and service layers.
  - `dietwise-dao-hibernate-reactive`: DAO implementation and Liquibase changelog resources.
- `dietwise-docker`: peripheral Docker images and compose files.

## Data Objects Between Layers
Any data object shared internally between the DAO and service layers belongs in the `dietwise-services-model`
module (package `eu.dietwise.services.model.*`), e.g. `eu.dietwise.services.model.suggestions.AlternativeIngredient`.
DAO interfaces in `dietwise-dao` may return these types directly. Do not place such carriers in the `dietwise-dao`
module itself.

## Usage of Hibernate Reactive

This project contains a wrapper around Hibernate Reactive that provides a more neutral API, `ReactivePersistenceContext` and `ReactivePersistenceTxContext`.
Treat them as any other code using the database with Hibernate Reactive.

- Do not use Hibernate Reactive concurrently.
- Always use the JPA metamodel to construct queries.

## Build and Run
- Full build:
  - `mvn package`
- Full build without tests:
  - `mvn -DskipTests package`
- Build app module and required dependencies:
  - `mvn -pl dietwise-container/dietwise -am package`

## Docker
- Root image build uses `Dockerfile` at repository root.
- Build command:
  - `docker build -t dietwise .`
- Dockerfile POM copy block is auto-generated for cache-friendly dependency resolution.
- After adding/removing Maven modules, regenerate the block:
  - `./dietwise-architecture/src/scripts/update-dockerfile-pom-copy.sh`

## Liquibase
- Runtime Liquibase migrations are enabled in the Quarkus app.
- Changelog file used at runtime: `changelog.xml` (loaded from classpath via dependency resources from `dietwise-dao-hibernate-reactive`).
- Master changelog source file:
  - `dietwise-container/dietwise-dao-hibernate-reactive/src/main/resources/changelog.xml`

## Configuration Expectations
- DB properties are expected from environment/profile and default to invalid placeholders in source.
- Typical required runtime env vars:
  - `QUARKUS_DATASOURCE_USERNAME`
  - `QUARKUS_DATASOURCE_PASSWORD`
  - `QUARKUS_DATASOURCE_REACTIVE_URL`
  - `QUARKUS_DATASOURCE_JDBC_URL`

## Editing Conventions
- Keep changes minimal and module-local.
- Do not introduce unrelated refactors.
- Preserve existing XML/property formatting style.
- If adding a new Maven module, update the Docker auto-copy block by running the script above.

## Testing Conventions
- For tests that need `ReactivePersistenceContext`/`ReactivePersistenceTxContext`, prefer `MockReactivePersistenceContextFactory` from:
  - `dietwise-common/dietwise-common-testutils/src/main/java/eu/dietwise/common/test/jpa/MockReactivePersistenceContextFactory.java`
- Use `withoutTransaction(...)` when testing read-only paths (session context).
- Use `withTransaction(...)` when testing transactional paths and assertions on opened transactions/actions.
- When using a value to construct test data and this value will be used again to verify, prefer to make a constant out of it.
- When creating the same test data in many methods, prefer to make a constant; if the creation is parametric, prefer a factory method.

## Verification Checklist for Changes
- For Maven/module changes:
  - Build at least affected module with `-pl ... -am`.
- For Quarkus app changes:
  - Confirm app module packages.
- For Docker-related changes:
  - Re-run `./dietwise-architecture/src/scripts/update-dockerfile-pom-copy.sh` if modules changed.
  - Rebuild image.

## Mutiny usage

This project uses SmallRye Mutiny for asynchronous programming.

- The class `UniComprehensions` (`dietwise-common/dietwise-common-utils/src/main/java/eu/dietwise/common/utils/UniComprehensions.java`)
  includes helpers to make long `Uni.flatMap` chains easier to read. If there is a long chain but the relevant method is
  missing from `UniComprehensions`, it is ok to add it.
- The class `MultiComprehensions` contains similar helpers for `Multi`. As of now, they help for a much narrower set of
  cases.

## Authorization

For making authorization decisions inside the services implementation layer (module `dietwise-container/dietwise-services`),
use the `AuthorizationService` interface (defined in `dietwise-architecture/src/main/java/eu/dietwise/architecture/api/AuthorizationService.java`.
This interface is implemented by the `AuthorizationServiceImpl` class (defined in `dietwise-container/dietwise-services/src/main/java/eu/dietwise/services/impl/AuthorizationServiceImpl.java`).
