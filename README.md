# Flink Tank

Messing about ~~in boats!~~ with distributed stream processing. 😆

## Pre-reqs:
- Kubernetes cluster (I use Kind via Docker Desktop)
- Helm and Kubectl installed (preferably also K9s) - context should be set to the cluster you want to use

See https://nightlies.apache.org/flink/flink-kubernetes-operator-docs-release-1.13/docs/try-flink-kubernetes-operator/quick-start/

## Cert Manager (required for Flink webhooks)

```bash
kubectl apply -f https://github.com/jetstack/cert-manager/releases/download/v1.18.2/cert-manager.yaml
```

## Install the actual operator

```bash
helm repo add flink-operator-repo https://downloads.apache.org/flink/flink-kubernetes-operator-1.13.0/
helm install flink-kubernetes-operator flink-operator-repo/flink-kubernetes-operator
```

## Create a Flink deployment

This is a basic example that runs the StateMachineExample code bundled into Flink on a dedicated application cluster.

We likely want to change this to run a custom job (that actually interacts with Kafka - see below) and maybe uses a session cluster that is easier for us to push ad-hoc jobs to iteratively.

```bash
kubectl create -f https://raw.githubusercontent.com/apache/flink-kubernetes-operator/release-1.13/examples/basic.yaml
```

## Kafka Infrastructure

Referencing https://strimzi.io/quickstarts/ at least for now.

```bash
kubectl create namespace kafka
```

```bash
kubectl create -f 'https://strimzi.io/install/latest?namespace=kafka' -n kafka
```

```bash
kubectl apply -f https://strimzi.io/examples/latest/kafka/kafka-single-node.yaml -n kafka 
```

### Creating topics

```bash
kubectl apply -f k8s/topics/
```

(See [k8s/topics/README.md](k8s/topics/README.md) for more details).

### Interacting with Kafka

See [kafka-tools/README.md](kafka-tools/README.md) for the `ktool` CLI that wraps Kafka console commands.
