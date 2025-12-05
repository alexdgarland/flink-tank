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

## Building

### Prerequisites
- Docker
- Java 11+ (for local Gradle builds, but Docker build handles this)

### Build the Docker Image

```bash
./build.sh
```

This builds a Docker image `flink-event-processor:latest` containing:
- Base Flink 1.20 runtime
- Your compiled JAR at `/opt/flink/usrlib/flink-job-1.0.0.jar`

### Load to Kind (Local Development)

```bash
./load-to-kind.sh
```

This loads the Docker image into your local Kind cluster so Kubernetes can use it.

## Deploying

### 1. Ensure Session Cluster is Running

```bash
kubectl get flinkdeployment
# Should show flink-session-cluster as READY
```

If not deployed yet:
```bash
kubectl apply -f ../k8s/flink/session-cluster.yaml
```

### 2. Deploy the Job

```bash
kubectl apply -f ../k8s/flink/event-processor-job.yaml
```

### 3. Check Job Status

```bash
kubectl get flinksessionjob
kubectl describe flinksessionjob event-processor-job
```

### 4. View Job in Flink UI

```bash
kubectl port-forward svc/flink-session-cluster-rest 8081
```

Then visit http://localhost:8081

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

The job accepts these command-line arguments:
- `--kafka-bootstrap-servers` - Kafka broker address (default: `my-cluster-kafka-bootstrap.kafka.svc:9092`)
- `--input-topic` - Input topic name (default: `input-events`)
- `--output-topic` - Output topic name (default: `output-results`)
- `--error-topic` - Error topic name (default: `error-events`)
- `--consumer-group` - Consumer group ID (default: `flink-event-processor`)

These are configured in the session job manifest at `../k8s/flink/event-processor-job.yaml`.

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

After making code changes:

1. Rebuild the image:
   ```bash
   ./build.sh
   ```

2. Reload to Kind:
   ```bash
   ./load-to-kind.sh
   ```

3. Update the session cluster to use the new image:
   ```bash
   kubectl edit flinkdeployment flink-session-cluster
   # Change image to: flink-event-processor:latest
   ```

4. The Flink operator will automatically restart the session cluster with the new image, and your job will resume from the last savepoint.

## Troubleshooting

### View Job Logs

```bash
# Find the TaskManager pod
kubectl get pods | grep flink-session-cluster

# View logs
kubectl logs <taskmanager-pod-name>
```

### Job Not Starting

Check the session job status:
```bash
kubectl describe flinksessionjob event-processor-job
```

Common issues:
- JAR path incorrect - check that `/opt/flink/usrlib/flink-job-1.0.0.jar` exists in the image
- Not enough task slots - ensure parallelism (2) <= available slots (4)
- Image not loaded to Kind - run `./load-to-kind.sh`

### No Messages Processing

Check that:
1. Kafka topics exist: `uv run ktool list-topics`
2. Messages are in input topic: `uv run ktool consume input-events --offset beginning`
3. Job is running: Check Flink UI at http://localhost:8081
