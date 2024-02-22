# Aggregate-Service

---

[![Latest Release][release-badge]][release-latest]
[![Apache 2.0 License][license-badge]][license-url]
[![LOC][loc-badge]][loc-report]

[release-badge]: https://img.shields.io/github/v/tag/provenance-io/aggregate-service.svg
[release-latest]: https://github.com/provenance-io/aggregate-service/releases/latest
[license-badge]: https://img.shields.io/github/license/provenance-io/aggregate-service.svg
[license-url]: https://github.com/provenance-io/aggregate-service/blob/main/LICENSE
[loc-badge]: https://tokei.rs/b1/github/provenance-io/aggregate-service
[loc-report]: https://github.com/provenance-io/aggregate-service

---

The purpose of this service is to retrieve block data over time from the Provenance 
Blockchain so that we can compute aggregated data at a reasonable rate to perform 
business decision queries.

The aggregator service makes use of the [Event Stream Library](https://github.com/FigureTechnologies/event-stream) 
to stream in ordered blocks and transform the block's data into the business data needs.

## Local Setup

### 1. Database Setup

Run a postgres docker image:

```bash
$ docker run --name postgresdb -p 5432:5432 -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=password1 -e POSTGRES_DB=aggregate -d postgres
```

### 2. Running

The service can be run locally:

```bash
$ make run-local
```

To pass an arbitrary argument string to the service, run `make run-local` with a variable named `ARGS`:

```bash
$ make run-local ARGS="3017000"
```

---

To run the containerized service:

- pull the image from to get the latest version:
```
$ docker pull figuretechnologies/aggregate-service:latest
```
---

### 3. Deployment

#### Github Actions

The aggregator CI/CD process uses Github Actions to build the docker container and deploy the image onto [Docker Hub](https://hub.docker.com/r/figuretechnologies/aggregate-service) from where the docker container can be pulled from any deployment environment.



