#!/bin/bash
set -e

echo "🏗️  Building JAR server..."
docker build -t jar-server:latest ./flink-job

echo ""
echo "📦 Loading image to Kind..."
kind load docker-image jar-server:latest --name desktop

echo ""
echo "🚀 Deploying JAR server..."
kubectl apply -f k8s/flink/jar-server.yaml

echo ""
echo "⏳ Waiting for JAR server to be ready..."
kubectl wait --for=condition=available --timeout=60s deployment/jar-server

echo ""
echo "✅ JAR server ready!"
echo ""
echo "(Re-)creating Flink job:"

kubectl delete -f k8s/flink/event-processor-job.yaml
kubectl apply -f k8s/flink/event-processor-job.yaml
