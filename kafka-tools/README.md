# Kafka Tools

Python CLI tools for interacting with the Kafka cluster running in Kubernetes.

## ktool

A Python CLI wrapper around Kafka console tools running in Kubernetes broker pods, eliminating the need for port-forwarding.

### Installation

```bash
cd kafka-tools
uv pip install -e .
```

This installs dependencies in a uv-managed virtual environment and makes the `ktool` command available via `uv run ktool`.

### Quick Examples

```bash
uv run ktool list-topics
uv run ktool consume input-events --offset beginning --count 10
uv run ktool produce input-events "Hello Kafka"
echo "Hello!" | uv run ktool produce input-events
```

## Commands

### List Topics

List all Kafka topics and cluster metadata:
```bash
uv run ktool list-topics
```

### Describe Topic

Get detailed metadata about a specific topic:
```bash
uv run ktool describe input-events
```

### Consume Messages

Consume messages from a topic:
```bash
# Consume from end (latest messages) - default behavior
uv run ktool consume input-events

# Consume from beginning
uv run ktool consume input-events --offset beginning

# Consume first 10 messages
uv run ktool consume input-events --offset beginning --count 10

# Consume with keys and timestamps
uv run ktool consume input-events --offset beginning --print-key --print-timestamp

# Consume from specific partition
uv run ktool consume input-events --offset beginning --partition 0

# Adjust timeout (default 10 seconds)
uv run ktool consume input-events --timeout-ms 5000
```

**Options:**
- `-o, --offset`: Where to start consuming (`beginning` or `end`; default: `end`)
- `-p, --partition`: Specific partition to consume from
- `-c, --count`: Exit after consuming N messages
- `--print-key`: Print message keys
- `--print-timestamp`: Print message timestamps
- `--timeout-ms`: Timeout in milliseconds (default: 10000)

### Produce Messages

Send messages to a topic:

```bash
# Produce a single message
uv run ktool produce input-events "Hello Kafka"

# Produce with a key (for partition distribution)
uv run ktool produce input-events --key "user123" "User logged in"

# Pipe from stdin
echo "Message from pipe" | uv run ktool produce input-events

# Multiple messages via stdin
cat << EOF | uv run ktool produce input-events
Message 1
Message 2
Message 3
EOF

# Read from file
uv run ktool produce input-events --file messages.txt

# Load sample events for Flink testing
uv run ktool produce input-events --file sample-events.json

# Combine key with piped input (all messages get same key)
echo -e "msg1\nmsg2\nmsg3" | uv run ktool produce input-events --key "batch-123"
```

**Note:** Partition selection is automatic based on key hash. Different keys will distribute across partitions. To test partition distribution, send messages with different keys:
```bash
uv run ktool produce input-events --key "key1" "Message 1"
uv run ktool produce input-events --key "key2" "Message 2"
uv run ktool produce input-events --key "key3" "Message 3"
```

### Query Offsets

Query topic/partition offsets and watermarks:
```bash
# Query all partitions of a topic
uv run ktool query input-events

# Query specific partition
uv run ktool query input-events --partition 2
```

## Direct Usage (without installation)

If you don't want to install, you can run it directly:
```bash
cd kafka-tools
python ktool.py list-topics
python ktool.py consume input-events
```

## How It Works

The CLI:
1. Finds a Kafka broker pod using the label `strimzi.io/cluster=my-cluster,strimzi.io/kind=Kafka` in the `kafka` namespace
2. Executes `kubectl exec` to run Kafka console tools (kafka-topics.sh, kafka-console-consumer.sh, etc.) inside the pod
3. Streams output back to your terminal

This means:
- No port-forwarding needed
- No extra deployment needed - uses the Kafka broker pods directly
- Guaranteed version compatibility (tools match Kafka broker version)
- Direct access to Kafka using internal cluster DNS
