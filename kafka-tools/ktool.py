#!/usr/bin/env python3
"""
CLI wrapper for Kafka console tools running in Kubernetes.
Executes Kafka console commands via kubectl exec on Kafka broker pods.
"""

import subprocess
import sys
from typing import List, Optional

import click


NAMESPACE = "kafka"
KAFKA_POD_LABEL = "strimzi.io/cluster=my-cluster,strimzi.io/kind=Kafka"
BOOTSTRAP_SERVERS = "my-cluster-kafka-bootstrap:9092"


def get_kafka_pod() -> str:
    """Get the name of a Kafka broker pod."""
    result = subprocess.run(
        [
            "kubectl",
            "get",
            "pod",
            "-n",
            NAMESPACE,
            "-l",
            KAFKA_POD_LABEL,
            "-o",
            "jsonpath={.items[0].metadata.name}",
        ],
        capture_output=True,
        text=True,
        check=True,
    )
    pod_name = result.stdout.strip()
    if not pod_name:
        click.echo(f"Error: No Kafka pod found with label {KAFKA_POD_LABEL}", err=True)
        sys.exit(1)
    return pod_name


def run_kafka_tool(
    tool_script: str, tool_args: List[str], stdin_data: Optional[str] = None
) -> subprocess.CompletedProcess:
    """
    Execute a Kafka console tool in the Kubernetes Kafka broker pod.

    Args:
        tool_script: The Kafka script to run (e.g., 'kafka-topics.sh')
        tool_args: Arguments to pass to the tool
        stdin_data: Optional data to pipe to the tool's stdin

    Returns:
        CompletedProcess with the result
    """
    pod_name = get_kafka_pod()

    kubectl_cmd = [
        "kubectl",
        "exec",
    ]

    # Add -i flag if we're providing stdin data
    if stdin_data:
        kubectl_cmd.append("-i")

    kubectl_cmd.extend([
        "-n",
        NAMESPACE,
        pod_name,
        "--",
        f"bin/{tool_script}",
        "--bootstrap-server",
        BOOTSTRAP_SERVERS,
    ])
    kubectl_cmd.extend(tool_args)

    click.echo(f"Executing on pod {pod_name}: {tool_script} {' '.join(tool_args)}", err=True)

    result = subprocess.run(
        kubectl_cmd,
        input=stdin_data,
        text=True,
        capture_output=False,  # Stream output directly
    )

    return result


@click.group()
def cli():
    """CLI wrapper for Kafka console tools running in Kubernetes."""
    pass


@cli.command()
def list_topics():
    """List all Kafka topics and metadata."""
    run_kafka_tool("kafka-topics.sh", ["--describe"])


@cli.command()
@click.argument("topic")
def describe(topic: str):
    """Describe a specific topic with detailed metadata."""
    run_kafka_tool("kafka-topics.sh", ["--describe", "--topic", topic])


@cli.command()
@click.argument("topic")
@click.option("-o", "--offset", default="end", help="Offset to start consuming from (beginning or end)")
@click.option("-p", "--partition", type=int, help="Specific partition to consume from")
@click.option("-c", "--count", type=int, help="Exit after consuming this many messages")
@click.option("--print-key", is_flag=True, help="Print message keys")
@click.option("--print-timestamp", is_flag=True, help="Print message timestamps")
@click.option("--timeout-ms", type=int, default=10000, help="Timeout in milliseconds (default: 10000)")
def consume(
    topic: str,
    offset: str,
    partition: Optional[int],
    count: Optional[int],
    print_key: bool,
    print_timestamp: bool,
    timeout_ms: int,
):
    """Consume and print messages from a topic."""
    args = ["--topic", topic]

    # Handle offset
    if offset == "beginning":
        args.append("--from-beginning")
    # 'end' is the default behavior, no flag needed

    # Handle partition
    if partition is not None:
        args.extend(["--partition", str(partition)])

    # Handle max messages
    if count:
        args.extend(["--max-messages", str(count)])

    # Formatting options
    if print_key:
        args.extend(["--property", "print.key=true"])
    if print_timestamp:
        args.extend(["--property", "print.timestamp=true"])

    # Timeout to prevent hanging
    args.extend(["--timeout-ms", str(timeout_ms)])

    run_kafka_tool("kafka-console-consumer.sh", args)


@cli.command()
@click.argument("topic")
@click.option("-k", "--key", help="Message key (enables key parsing)")
@click.option("-f", "--file", type=click.File("r"), help="File to read messages from (one per line)")
@click.argument("message", required=False)
def produce(topic: str, key: Optional[str], file, message: Optional[str]):
    """
    Produce messages to a topic.

    Provide MESSAGE as an argument, or use --file to read from a file,
    or pipe data via stdin.

    Note: Partition selection is automatic based on key hash. To control
    distribution, use different keys.

    Examples:
      ktool produce my-topic "Hello World"
      echo "Hello" | ktool produce my-topic
      ktool produce my-topic --key "user123" "User event"
      ktool produce my-topic --file messages.txt
    """
    args = ["--topic", topic]

    # Enable key parsing if key is provided
    if key:
        args.extend([
            "--property", "parse.key=true",
            "--property", "key.separator=:",
        ])

    # Determine input source
    stdin_data = None
    if file:
        stdin_data = file.read()
        if key and stdin_data:
            # Add key to each line
            lines = stdin_data.strip().split("\n")
            stdin_data = "\n".join(f"{key}:{line}" for line in lines)
    elif message:
        if key:
            stdin_data = f"{key}:{message}"
        else:
            stdin_data = message
    elif not sys.stdin.isatty():
        stdin_data = sys.stdin.read()
        if key and stdin_data:
            # Add key to each line
            lines = stdin_data.strip().split("\n")
            stdin_data = "\n".join(f"{key}:{line}" for line in lines)
    else:
        click.echo("Error: No message provided. Use MESSAGE argument, --file, or pipe via stdin.", err=True)
        sys.exit(1)

    run_kafka_tool("kafka-console-producer.sh", args, stdin_data=stdin_data)


@cli.command()
@click.argument("topic")
@click.option("-p", "--partition", type=int, help="Specific partition to query")
def query(topic: str, partition: Optional[int]):
    """Query topic/partition offsets (high water mark, etc)."""
    if partition is not None:
        topic_spec = f"{topic}:{partition}"
    else:
        topic_spec = topic

    args = ["--topic", topic_spec]

    run_kafka_tool("kafka-get-offsets.sh", args)


if __name__ == "__main__":
    cli()
