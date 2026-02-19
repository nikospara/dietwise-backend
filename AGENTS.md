# AGENTS Guide for DietWise Backend

## Scope
These instructions apply to the entire repository.

## Required Reading
Before making code changes, read:
- `AGENTS.md` (this file)
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
  - `dietwise`: Quarkus application module (final executable artifact).
  - `dietwise-dao-hibernate-reactive`: DAO implementation and Liquibase changelog resources.
- `dietwise-docker`: peripheral Docker images and compose files.

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
  - `./scripts/update-dockerfile-pom-copy.sh`

## Liquibase
- Runtime Liquibase migrations are enabled in the Quarkus app.
- Effective configuration lives in:
  - `dietwise-container/dietwise/src/main/resources/application.properties`
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

## Verification Checklist for Changes
- For Maven/module changes:
  - Build at least affected module with `-pl ... -am`.
- For Quarkus app changes:
  - Confirm app module packages.
- For Docker-related changes:
  - Re-run `./scripts/update-dockerfile-pom-copy.sh` if modules changed.
  - Rebuild image.
