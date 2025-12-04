#!/usr/bin/env python3
"""
CLI wrapper for kcat running in Kubernetes.
Executes kcat commands via kubectl exec on the deployed kcat pod.
"""

import subprocess
import sys
from typing import List, Optional

import click


NAMESPACE = "kafka"
POD_LABEL = "app=kcat"
BOOTSTRAP_SERVERS = "my-cluster-kafka-bootstrap:9092"
KCAT_EXECUTABLE = "kcat"  # Using edenhill/kcat image


def get_kcat_pod() -> str:
    """Get the name of the kcat pod."""
    result = subprocess.run(
        [
            "kubectl",
            "get",
            "pod",
            "-n",
            NAMESPACE,
            "-l",
            POD_LABEL,
            "-o",
            "jsonpath={.items[0].metadata.name}",
        ],
        capture_output=True,
        text=True,
        check=True,
    )
    pod_name = result.stdout.strip()
    if not pod_name:
        click.echo(f"Error: No kcat pod found with label {POD_LABEL}", err=True)
        sys.exit(1)
    return pod_name


def run_kcat(kcat_args: List[str], stdin_data: Optional[str] = None) -> subprocess.CompletedProcess:
    """
    Execute kcat command in the Kubernetes pod.

    Args:
        kcat_args: Arguments to pass to kcat
        stdin_data: Optional data to pipe to kcat's stdin

    Returns:
        CompletedProcess with the result
    """
    pod_name = get_kcat_pod()

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
        KCAT_EXECUTABLE,
        "-b",
        BOOTSTRAP_SERVERS,
    ])
    kubectl_cmd.extend(kcat_args)

    click.echo(f"Executing on pod {pod_name}: {KCAT_EXECUTABLE} -b {BOOTSTRAP_SERVERS} {' '.join(kcat_args)}", err=True)

    result = subprocess.run(
        kubectl_cmd,
        input=stdin_data,
        text=True,
        capture_output=False,  # Stream output directly
    )

    return result


@click.group()
def cli():
    """CLI wrapper for kcat running in Kubernetes."""
    pass


@cli.command()
def list_topics():
    """List all Kafka topics and metadata."""
    run_kcat(["-L"])


@cli.command()
@click.argument("topic")
def describe(topic: str):
    """Describe a specific topic with detailed metadata."""
    run_kcat(["-L", "-t", topic])


@cli.command()
@click.argument("topic")
@click.option("-o", "--offset", default="beginning", help="Offset to start consuming from (beginning, end, stored, or numeric)")
@click.option("-c", "--count", type=int, help="Exit after consuming this many messages")
@click.option("-f", "--format", default=None, help="Output format (e.g., '%t [%p] at offset %o: key=%k value=%s\\n')")
@click.option("-e", "--exit-eof", is_flag=True, help="Exit when reaching end of partition")
def consume(topic: str, offset: str, count: Optional[int], format: Optional[str], exit_eof: bool):
    """Consume and print messages from a topic."""
    args = ["-C", "-t", topic, "-o", offset]

    if count:
        args.extend(["-c", str(count)])

    if format:
        args.extend(["-f", format])

    if exit_eof:
        args.append("-e")

    run_kcat(args)


@cli.command()
@click.argument("topic")
@click.option("-k", "--key", help="Message key")
@click.option("-p", "--partition", type=int, help="Partition to produce to")
@click.option("-f", "--file", type=click.File("r"), help="File to read messages from (one per line)")
@click.argument("message", required=False)
def produce(topic: str, key: Optional[str], partition: Optional[int], file, message: Optional[str]):
    """
    Produce messages to a topic.

    Provide MESSAGE as an argument, or use --file to read from a file,
    or pipe data via stdin.

    Examples:
      kcat_cli.py produce my-topic "Hello World"
      echo "Hello" | kcat_cli.py produce my-topic
      kcat_cli.py produce my-topic --file messages.txt
    """
    args = ["-P", "-t", topic]

    if key:
        args.extend(["-K", ":"])  # Use : as key delimiter

    if partition is not None:
        args.extend(["-p", str(partition)])

    # Determine input source
    stdin_data = None
    if file:
        stdin_data = file.read()
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

    run_kcat(args, stdin_data=stdin_data)


@cli.command()
@click.argument("topic")
@click.option("-p", "--partition", type=int, help="Specific partition to query")
def query(topic: str, partition: Optional[int]):
    """Query topic/partition offsets (high water mark, etc)."""
    args = ["-Q", "-t", topic]

    if partition is not None:
        args[2] = f"{topic}:{partition}"

    run_kcat(args)


if __name__ == "__main__":
    cli()
