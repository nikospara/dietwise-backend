# DietWise

DietWise is an EU-funded initiative dedicated to revolutionizing food consumption patterns across Europe.

This project is part of the ICT solutions of DietWise.

## The build system

The build system is Maven.

### Build properties

The following properties are local to an environment; they can be specified as `-Dpropname=propvalue` command line arguments,
or placed in a local Maven profile in `~/.m2/settings.xml`.

- `database.dietwise.jdbc.url`: The JDBC URL of the database
- `database.dietwise.reactive.url`: The Hibernate *reactive* URL of the database
- `database.dietwise.username`: The DB username
- `database.dietwise.password`: The DB password
- **(TODO)** `db.env` (default: `dev`): Needed only by Liquibase to indicate which environment-specific
[contexts](https://www.liquibase.org/documentation/contexts.html) will it activate; e.g. `dev` will activate the `data-dev` context

- Example:

```xml
<settings>
	<profiles>
		<profile>
			<id>dw-local-postgres</id>
			<properties>
				<database.dietwise.jdbc.url>jdbc:postgresql://localhost/dietwise</database.dietwise.jdbc.url>
				<database.dietwise.reactive.url>vertx-reactive:postgresql://localhost/dietwise</database.dietwise.reactive.url>
				<database.dietwise.username>dietwise</database.dietwise.username>
				<database.dietwise.password>dietwise</database.dietwise.password>
			</properties>
		</profile>
		<profile>
			<id>dw-docker-postgres</id>
			<properties>
				<database.dietwise.jdbc.url>jdbc:postgresql://postgres/dietwise</database.dietwise.jdbc.url>
				<database.dietwise.reactive.url>vertx-reactive:postgresql://postgres/dietwise</database.dietwise.reactive.url>
				<database.dietwise.username>dietwise</database.dietwise.username>
				<database.dietwise.password>dietwise</database.dietwise.password>
			</properties>
		</profile>
	</profiles>
</settings>
```

Both profiles use Postgresql. One is to run the entire application through `docker-compose`, in which case
Postgresql is in the `postgres` host - see `dietwise-docker/src/main/docker-compose/docker-compose.yml` (**TODO**).
The other is to run only the peripherals in Docker - see `dietwise-docker/src/main/docker-compose/docker-compose-peripherals.yml`.

### Build profiles

- `dietwise-quarkus-dev`: Activate `quarkus:dev` for the respective microservice; do not activate more than one
  microservice in the same command
- `docker`: Activate the Docker image build

### Updating dependencies

The versions of all dependencies are controlled by Maven properties in the form `version.<uniqueId>`,
where `<uniqueId>` is a unique identifier for the dependency, preferably the artifact id, but anything
unique and sufficiently descriptive will do. All version properties are defined in the parent pom.
As such, detecting updates is as simple as running (`-N` for non-recursive build, since all version properties are
in the parent pom):

```shell
mvn -N versions:display-property-updates
mvn -N versions:display-plugin-updates
```

Some versions are affected by the requirements of Quarkus. We want our artifacts to be as environment-independent as
possible therefore, we do not use Quarkus dependencies anywhere, except from the deployment modules.
However, we need to stay compatible; so we mark version properties that actually depend on Quarkus with an XML comment.
Be careful when upgrading those dependencies.

## Building

Build with Maven as usual, `package` is enough:

```shell
mvn package
# -OR-
mvn package -Pdocker # to build the docker images too
```

> **NOTE/WARNING:** As of the date of this writing, the Docker images are for development purposes only!

### Creating/updating the DB

Assuming that the properties are defined through a Maven profile e.g., like the `dw-local-postgres` in
`~/.m2/settings.xml` that was described above, run the following:

```shell
mvn process-resources -Pdbupdate,dw-local-postgres
```

The `dev` context adds data for the development environment; add `-Dliquibase.contexts=dev` to the previous command to activate.

If you don't use the profile, you have to specify the properties by command line, a much more cumbersome option:

```shell
mvn process-resources -Pdbupdate -Ddatabase.dietwise.jdbc.url=... -Ddatabase.dietwise.username=... -D...
```

#### Rolling back changes

Occasionally you may want to roll back some changes. Change directory to `dietwise-dao-hibernate-reactive` and run:

```shell
mvn org.liquibase:liquibase-maven-plugin:rollback \
	-Dliquibase.rollbackCount=... -Dliquibase.changeLogFile=src/main/resources/changelog.xml \
	-Dliquibase.promptOnNonLocalDatabase=false -Dliquibase.driver=org.postgresql.Driver \
	-Dliquibase.url=jdbc:postgresql://localhost/dietwise -Dliquibase.username=dietwise \
	-Dliquibase.password=dietwise
```

Full info [here](https://docs.liquibase.com/tools-integrations/maven/commands/maven-rollback.html).

## Running

**NOTE:** For the time being you need to run Ollama by hand. See below for details.

### Prerequisite: Docker compose

> **NOTE/WARNING:** As of the date of this writing, the Docker images are for development purposes only!

This will be the first thing you need to run in a local development environment, as it launches all the
necessary peripheral services (e.g., the database), except for Ollama, which needs to be started by hand.

```shell
cd dietwise-docker/src/main/docker-compose/
docker compose -f docker-compose-peripherals.yml -p dietwise up -d    # the first time
docker compose -f docker-compose-peripherals.yml -p dietwise start    # to start
docker compose -f docker-compose-peripherals.yml -p dietwise stop     # to stop
docker compose -f docker-compose-peripherals.yml -p dietwise down     # to remove the containers, without removing the persistent volumes
docker compose -f docker-compose-peripherals.yml -p dietwise down -v  # to remove the containers, also removing the persistent volumes
```

### Prerequisite: Ollama

The ideal situation is to activate CUDA and let Ollama run in the graphics card. A question that takes 50-100sec in a
fast CPU runs in around 6sec on a mid-range nVidia GPU. The [Docker version of Ollama](https://hub.docker.com/r/ollama/ollama)
supports CUDA but requires some extra installation steps that I haven't got down to do yet (install the
[NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html#installation)).

Until then running Ollama locally is ver easy: download, extract and run `./bin/ollama serve`

For bigger context (default is 4096): `OLLAMA_CONTEXT_LENGTH=8192 ./bin/ollama serve`

### From IDE

Create a Quarkus run configuration. You need to specify the DB connection parameters (and any other runtime parameters)
from the run configuration. Select "Modify options" and check "Environment variables." Override the following
configuration properties:

- `quarkus.datasource.username`
- `quarkus.datasource.password`
- `quarkus.datasource.reactive.url`
- `quarkus.datasource.jdbc.url`

The table would look like (see [Quarkus configuration reference](https://quarkus.io/guides/config-reference)):

| Name                            | Value                                          |
|---------------------------------|------------------------------------------------|
| QUARKUS_DATASOURCE_USERNAME     | dietwise                                       |
| QUARKUS_DATASOURCE_PASSWORD     | dietwise                                       |
| QUARKUS_DATASOURCE_REACTIVE_URL | vertx-reactive:postgresql://localhost/dietwise |
| QUARKUS_DATASOURCE_JDBC_URL     | jdbc:postgresql://localhost/dietwise           |

Or define them inline using semicolon as the separator:
`QUARKUS_DATASOURCE_USERNAME=dietwise;QUARKUS_DATASOURCE_PASSWORD=dietwise;QUARKUS_DATASOURCE_REACTIVE_URL=vertx-reactive:postgresql://localhost/dietwise;QUARKUS_DATASOURCE_JDBC_URL=jdbc:postgresql://localhost/dietwise`

You need to make sure the IDE runner resolves workspace artifacts.

### From Docker

**TODO**
