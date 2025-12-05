# Flink Event Processor Job

A Kotlin-based Flink streaming job that processes events from Kafka.

## What It Does

This job:
1. Reads JSON events from the `input-events` Kafka topic
2. Parses and validates the events
3. Enriches valid events with processing metadata
4. Writes processed events to the `output-results` Kafka topic
5. Routes unparseable/invalid events to the `error-events` Kafka topic

## Event Schema

**Input Event:**
```json
{
  "id": "event-123",
  "type": "user-action",
  "timestamp": 1234567890000,
  "data": {
    "action": "click",
    "userId": 42
  }
}
```

**Output Event (Success):**
```json
{
  "originalId": "event-123",
  "eventType": "user-action",
  "processedAt": "2024-12-04T15:30:00Z",
  "processingDelay": 1234,
  "enrichedData": {
    "action": "click",
    "userId": 42,
    "original_timestamp": 1234567890000,
    "processing_pipeline": "flink-event-processor"
  }
}
```

**Error Event (Parse Failure):**
```json
{
  "rawMessage": "this is not valid JSON",
  "errorType": "PARSE_ERROR",
  "errorMessage": "Unrecognized token 'this': was expecting...",
  "timestamp": "2024-12-04T15:30:00Z"
}
```

## Architecture

The job uses a JAR server architecture:

1. **JAR Server**: An nginx-based pod that serves the compiled JAR over HTTP
2. **Session Cluster**: Uses stock `flink:1.20` image (no custom image needed)
3. **Flink Operator**: Fetches the JAR from `http://jar-server.default.svc:8080/flink-job-1.0.0.jar`

This approach allows the session cluster to use the standard Flink image while still providing custom job code.

## Building and Deploying

### Deploy the JAR Server, topics and job

_(Assumes all infra including Flink session cluster is running)._

```bash
# From top level of project
./deploy-job.sh
```

### Check Job Status

```bash
kubectl get flinksessionjob
kubectl describe flinksessionjob event-processor-job
```

### View Job in Flink UI

```bash
../portforward-ui.sh
```

Then visit http://localhost:8081.

## Testing the Job

### Send Test Events

```bash
cd ../kafka-tools

# Load sample valid events from file
uv run ktool produce input-events --file sample-events.json

# Or send a single test event
uv run ktool produce input-events '{"id":"test-1","type":"user-action","timestamp":1733328000000,"data":{"action":"click","userId":42}}'

# Test error handling with invalid JSON
uv run ktool produce input-events "this is not valid JSON"
```

### Read Processed Results

```bash
# Check successful results
uv run ktool consume output-results --offset beginning

# Check error events
uv run ktool consume error-events --offset beginning
```

## Configuration

The job accepts these command-line arguments (configured in `../k8s/flink/event-processor-job.yaml`):
- `--kafka-bootstrap-servers` - Kafka broker address (default: `my-cluster-kafka-bootstrap.kafka.svc:9092`)
- `--input-topic` - Input topic name (default: `input-events`)
- `--output-topic` - Output topic name (default: `output-results`)
- `--error-topic` - Error topic name (default: `error-events`)
- `--consumer-group` - Consumer group ID (default: `flink-event-processor`)

The JAR is fetched from `http://jar-server.default.svc:8080/flink-job-1.0.0.jar` as specified in the job manifest.

## Development

### Local Gradle Build

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

### Update the Job

After making code changes, rebuild and redeploy the JAR server:

```bash
./deploy-job.sh
```

The job will be restarted and the operator will fetch the updated JAR from the JAR server.

## Troubleshooting

### View Job Logs

```bash
# Find the TaskManager pod
kubectl get pods | grep flink-session-cluster

# View logs
kubectl logs <taskmanager-pod-name>
```
