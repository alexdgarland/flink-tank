#!/bin/bash
set -e

NAMESPACE="${1:-default}"
LOCAL_PORT="${2:-8081}"

echo "Finding Flink session cluster pod..."

POD=$(kubectl get pods -n "$NAMESPACE" -l app=flink-session-cluster -o jsonpath='{.items[0].metadata.name}')

if [ -z "$POD" ]; then
    echo "❌ No pod found with label app=flink-session-cluster"
    exit 1
fi

echo "Found pod: $POD"
echo "Port-forwarding to http://localhost:$LOCAL_PORT"
echo ""

kubectl port-forward -n "$NAMESPACE" "$POD" "$LOCAL_PORT:8081"
