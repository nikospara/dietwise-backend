# DietWise

DietWise is an EU-funded initiative dedicated to revolutionizing food consumption patterns across Europe.

This project is part of the ICT solutions of DietWise.

## The build system

The build system is Maven.

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

## Running

**NOTE:** For the time being you need to run Ollama by hand. See below for details.

### Docker compose

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

### Ollama

The ideal situation is to activate CUDA and let Ollama run in the graphics card. A question that takes 50-100sec in a
fast CPU runs in around 6sec on a mid-range nVidia GPU. The [Docker version of Ollama](https://hub.docker.com/r/ollama/ollama)
supports CUDA but requires some extra installation steps that I haven't got down to do yet (install the
[NVIDIA Container Toolkit](https://docs.nvidia.com/datacenter/cloud-native/container-toolkit/latest/install-guide.html#installation)).

Until then running Ollama locally is ver easy: download, extract and run `./bin/ollama serve`
