# Kafka Tools

Python CLI tools for interacting with the Kafka cluster running in Kubernetes.

## Installation

```bash
cd kafka-tools
uv pip install -e .
```

This installs the `podcat` command in your environment.

## Tools

### podcat

A CLI wrapper around kcat that runs commands in the Kubernetes pod, eliminating the need for port-forwarding.

See [../KCAT_USAGE.md](../KCAT_USAGE.md) for full documentation.

**Quick examples:**
```bash
podcat list-topics
podcat consume input-events --offset beginning
echo "Hello!" | podcat produce input-events
```
