# Flink Tank

Messing about ~~in boats!~~ with distributed stream processing. 😆

## Pre-reqs:
- Kubernetes cluster (I use Kind via Docker Desktop)
- Helm and Kubectl installed (preferably also K9s) - context should be set to the cluster you want to use
- Kind CLI (`brew install kind`)

## Create base infrastructure

For Flink (https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-release-1.13/docs/try-flink-kubernetes-operator/quick-start/) and Kafka (https://strimzi.io/quickstarts/).

```bash
./create-infra.sh
```

### Check the Flink deployment status
```bash
kubectl get flinkdeployment
./portforward-ui.sh  # Access UI at localhost:8081
```

See [k8s/flink/README.md](k8s/flink/README.md) for more details about the session cluster setup and how to submit jobs.

### Interacting with Kafka

See [kafka-tools/README.md](kafka-tools/README.md) for the `ktool` CLI that wraps Kafka console commands.

## Deploy Flink Job

The repository includes a Kotlin-based Flink job that processes events from Kafka.

### Build and Deploy

```bash
./deploy-job.sh
```

See [flink-job/README.md](flink-job/README.md) for full documentation.