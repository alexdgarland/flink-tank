# Flink Tank

Messing about ~~in boats!~~ with distributed stream processing. 😆

## Pre-reqs:
- Kubernetes cluster (I use Kind via Docker Desktop)
- Helm and Kubectl installed (preferably also K9s) - context should be set to the cluster you want to use
- Kind CLI (`brew install kind`)

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

## Create a Flink Session Cluster

Deploy a session cluster that can accept multiple jobs:

```bash
kubectl apply -f k8s/flink/session-cluster.yaml
```

Check the deployment status:
```bash
kubectl get flinkdeployment
kubectl port-forward svc/flink-session-cluster-rest 8081  # Access UI at localhost:8081
```

See [k8s/flink/README.md](k8s/flink/README.md) for more details about the session cluster setup and how to submit jobs.

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

## Deploy Flink Job

The repository includes a Kotlin-based Flink job that processes events from Kafka.

### Build and Deploy

```bash
./deploy-job.sh
```

### Test the Job

```bash
cd kafka-tools

# Load sample valid events
uv run ktool produce input-events --file sample-events.json

# Send an invalid (non-JSON) event to test error handling
uv run ktool produce input-events "this is not valid JSON"

# Check the processed results
uv run ktool consume output-results --offset beginning

# Check error events
uv run ktool consume error-events --offset beginning
```

See [flink-job/README.md](flink-job/README.md) for full documentation.