# Kafka Topics

Kafka topic definitions managed by Strimzi operator.

## Apply topics

```bash
kubectl apply -f k8s/topics/
```

Or from this directory:
```bash
kubectl apply -f .
```

## Verify topics were created

```bash
# List all KafkaTopic resources
kubectl get kafkatopics -n kafka

# Describe a specific topic
kubectl describe kafkatopic input-events -n kafka

# Or check directly in Kafka
KAFKA_POD=$(kubectl get pod -n kafka -l strimzi.io/cluster=my-cluster,strimzi.io/kind=Kafka -o jsonpath='{.items[0].metadata.name}')
kubectl exec -n kafka "$KAFKA_POD" -- bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
kubectl exec -n kafka "$KAFKA_POD" -- bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe
```

## Important Notes

- The `strimzi.io/cluster` label **must match** your Kafka cluster name (likely `my-cluster` from the quickstart)
- Verify your cluster name with: `kubectl get kafka -n kafka`
- For single-node clusters, `replicas` must be 1
- Strimzi will reconcile these topics - if you delete the KafkaTopic resource, the actual Kafka topic gets deleted too

## Configuration Options

Common topic configs you can add to `spec.config`:
- `retention.ms`: How long to retain messages (milliseconds)
- `retention.bytes`: Max size of partition before old segments are deleted
- `segment.ms`: How often to roll a new segment
- `compression.type`: `snappy`, `lz4`, `gzip`, `zstd`, or `uncompressed`
- `cleanup.policy`: `delete` or `compact`
- `min.insync.replicas`: Minimum replicas that must ACK writes
- `max.message.bytes`: Max message size

See: https://kafka.apache.org/documentation/#topicconfigs
