# kcat CLI Usage

A Python CLI wrapper around kcat running in Kubernetes, so you don't have to manage port-forwards.

## Setup

1. **Deploy kcat to your cluster:**
   ```bash
   kubectl apply -f k8s/tools/kcat-deployment.yaml
   ```

2. **Install the Python CLI:**
   ```bash
   cd kafka-tools
   uv pip install -e .
   ```

   This creates a virtual environment and installs dependencies. Use `uv run podcat` to execute commands.

## Commands

### List Topics

List all Kafka topics and cluster metadata:
```bash
uv run podcat list-topics
```

### Describe Topic

Get detailed metadata about a specific topic:
```bash
uv run podcat describe input-events
```

### Consume Messages

Consume messages from a topic:
```bash
# Consume from beginning and stream continuously
uv run podcat consume input-events

# Consume last 10 messages and exit
uv run podcat consume input-events --offset end --count 10 --exit-eof

# Custom output format
uv run podcat consume input-events --format '%t [%p] at offset %o: key=%k value=%s\n'
```

**Options:**
- `-o, --offset`: Where to start consuming (`beginning`, `end`, `stored`, or numeric offset)
- `-c, --count`: Exit after consuming N messages
- `-f, --format`: Custom output format using printf-style placeholders
- `-e, --exit-eof`: Exit when reaching end of partition

### Produce Messages

Send messages to a topic:

```bash
# Produce a single message
uv run podcat produce input-events "Hello Kafka"

# Produce with a key
uv run podcat produce input-events --key "user123" "User logged in"

# Produce to specific partition
uv run podcat produce input-events --partition 2 "Message for partition 2"

# Pipe from stdin
echo "Message from pipe" | uv run podcat produce input-events

# Multiple messages via stdin
cat << EOF | uv run podcat produce input-events
Message 1
Message 2
Message 3
EOF

# Read from file
uv run podcat produce input-events --file messages.txt

# Combine key with piped input
echo -e "msg1\nmsg2\nmsg3" | uv run podcat produce input-events --key "batch-123"
```

### Query Offsets

Query topic/partition offsets and watermarks:
```bash
# Query all partitions of a topic
uv run podcat query input-events

# Query specific partition
uv run podcat query input-events --partition 2
```

## Direct Usage (without installation)

If you don't want to install, you can run it directly:
```bash
cd kafka-tools
python kcat_cli.py list-topics
python kcat_cli.py consume input-events
```

## How It Works

The CLI:
1. Finds the kcat pod using the label `app=kcat` in the `kafka` namespace
2. Executes `kubectl exec` to run kcat commands inside the pod
3. Streams output back to your terminal

This means:
- No port-forwarding needed
- No kcat installation on your local machine required (just Python + Click)
- Direct access to Kafka using internal cluster DNS

## Troubleshooting

**Pod not found:**
```bash
# Verify the kcat pod is running
kubectl get pods -n kafka -l app=kcat

# If not running, deploy it
kubectl apply -f k8s/tools/kcat-deployment.yaml
```

**Connection issues:**
The kcat pod is configured to connect to `my-cluster-kafka-bootstrap:9092`. If your Kafka cluster has a different name, update:
- `BOOTSTRAP_SERVERS` in `kafka-tools/kcat_cli.py`
- `KAFKA_BOOTSTRAP_SERVERS` env var in `k8s/tools/kcat-deployment.yaml`

## kcat Format String Reference

Common format placeholders for `--format`:
- `%s` - Message payload
- `%k` - Message key
- `%t` - Topic name
- `%p` - Partition number
- `%o` - Offset
- `%T` - Message timestamp
- `%h` - Headers

Example: `'Topic: %t, Partition: %p, Offset: %o, Key: %k, Value: %s\n'`
