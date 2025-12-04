# Kafka Tools

Python CLI tools for interacting with the Kafka cluster running in Kubernetes.

## Installation

```bash
cd kafka-tools
uv pip install -e .
```

This installs dependencies in a uv-managed virtual environment.

## Tools

### podcat

A CLI wrapper around kcat that runs commands in the Kubernetes pod, eliminating the need for port-forwarding.

See [../KCAT_USAGE.md](../KCAT_USAGE.md) for full documentation.

**Quick examples:**
```bash
uv run podcat list-topics
uv run podcat consume input-events --offset beginning
echo "Hello!" | uv run podcat produce input-events
```
