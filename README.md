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
blockchain so that we can compute aggregated data at a reasonable rate to perform 
business decision queries.

The aggregator service makes use of the [Provenance Event Stream Library](https://github.com/provenance-io/event-stream) 
to stream in ordered blocks and transform the block's data into the business data needs.

## Local Setup

---

To run the service outside of a container:

### 1. Install AWS CLI tool

You will need to install the AWS CLI, which can be downloaded from https://aws.amazon.com/cli, or
installed via homebrew:

```bash
$ brew install awscli
```

Once installed, run `aws configure` to begin token and region configurations.

### 2. Environment setup

Prior to running Localstack (see below), set the environment variables `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` to `test`, otherwise you can setup a specific profile:

```bash
$ aws configure --profile <profile_name>
```

if you are permissioned to an AWS instance.

### 3. Running LocalStack

When developing locally, we utilize LocalStack, a tool that mocks AWS services like S3 and DynamoD.

#### Starting

```bash
$ make localstack-start
```

#### Status

Check if LocalStack is running:

```bash
$ make localstack-status

> {
  "dynamodbstreams": "running",
  "kinesis": "running",
  "s3": "running",
  "dynamodb": "running"
}
```

#### Stopping

```bash
$ make localstack-stop
```
---
### 4.A Use the node public IP

In the [local.env.properties](hhttps://github.com/provenance-io/aggregate-service/blob/a0257f85f203cc65f3a63eeee3d3332dedec133c/src/main/resources/local.env.properties#L8) you could set the public IP address of the
query node that you wish to stream from.

### 4.B Port-forwarding for Provenance/Tendermint API

Port `26657` is expected to be open for RPC and web-socket traffic, which will
require port-forwarding to the Provenance Kubernetes cluster.

#### Using `figcli`

This can be accomplished easily using the internal Figure command line tool, `figcli`:

1. If it does not already exist, add a `[port_forward]` entry to you local `figcli`
   configuration file:

   ```
   [port_forward]
   context = "gke_provenance-io-test_us-east1-b_p-io-cluster"
   ```

   This example will use the Provenance test cluster.

2. Create a pod in the cluster to perform port-forwarding on a specific remote host and port:

   ```bash
   $ figcli port-forward 26657:$REMOTE_HOST:26657
   ```
   where `$REMOTE_HOST` is a private IP within the cluster network, e.g. `192.168.xxx.xxx`

3. If successful, you should see the pod listed in the output of `kubectl get pods`:

   ```bash
   $ kubectl get pods
   
   NAME                                READY   STATUS    RESTARTS   AGE
   datadog-agent-29nvm                 1/1     Running   2          26d
   datadog-agent-44rdl                 1/1     Running   1          26d
   datadog-agent-xcj48                 1/1     Running   1          26d
   ...
   datadog-agent-xfkmw                 1/1     Running   1          26d
   figcli-temp-port-forward-fi8dlktx   1/1     Running   0          15m   <<<
   nginx-test                          1/1     Running   0          154d
   ```
   
Traffic on `localhost:26657` will now be forwarded to the Provenance cluster

---

### 5. Block Caching

The aggregate service also supports the ability to cache block data within a local NoSQL database if a cloud data warehouse is not desired.

The aggregator currently supports [RavenDB](https://ravendb.net/) but can support others if necessary.

To run RavenDB locally:
```bash
$ docker run -p 8080:8080 ravendb/ravendb:ubuntu-latest
```
Once ravenDB is running could access its GUI interface to set up the database at http://localhost:8080, then you could make changes to the [local.env.properties](https://github.com/provenance-io/aggregate-service/blob/main/src/main/resources/local.env.properties) to support the desired configurations. 


---

### 6. Running

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
$ docker pull ghcr.io/provenance-io/aggregate-service:latest
```
---

### 6. Deployment

#### Github Actions

The aggregator CI/CD process uses Github Actions to build the docker container and deploy the image onto [GHCR](https://github.com/features/packages) (Github Container Registry) from where the docker container can be pulled from any deployment environment.

#### Serverless

We utilize `serverless` for our AWS infrastructure deployment via github actions: https://serverless.com. Sample serverless yaml could be found [here](https://github.com/provenance-io/aggregate-service/blob/main/serverless_example.yml).

Install the serverless package to test and develop with new configurations:

```bash
$ npm install -g serverless
```


