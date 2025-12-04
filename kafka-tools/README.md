# Kafka Tools

Python CLI tools for interacting with the Kafka cluster running in Kubernetes.

## Installation

```bash
cd kafka-tools
uv pip install -e .
```

This installs dependencies in a uv-managed virtual environment.

## Tools

### ktool

A CLI wrapper around Kafka console tools that runs commands in Kubernetes broker pods, eliminating the need for port-forwarding.

See [../KCAT_USAGE.md](../KCAT_USAGE.md) for full documentation.

**Quick examples:**
```bash
uv run ktool list-topics
uv run ktool consume input-events --offset beginning --count 10
uv run ktool produce input-events "Hello Kafka"
echo "Hello!" | uv run ktool produce input-events
```
