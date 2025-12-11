#!/bin/bash
set -ex

echo "📝 Ensuring topics exist..."
kubectl delete -f k8s/topics/ || true
kubectl apply -f k8s/topics/

echo ""
echo "🏗️ Building JAR server..."
docker build -t jar-server:latest ./flink-job

echo ""
echo "📦 Loading image to Kind..."
kind load docker-image jar-server:latest --name desktop

echo ""
echo "🚀 Deploying JAR server..."
kubectl delete -f k8s/flink/jar-server.yaml || true
kubectl apply -f k8s/flink/jar-server.yaml

echo ""
echo "⏳ Waiting for JAR server to be ready..."
kubectl wait --for=condition=available --timeout=120s deployment/jar-server -n flink
echo ""
echo "✅ JAR server ready!"

echo ""
echo "🔄 (Re-)creating Flink job:"
kubectl delete -f k8s/flink/event-processor-job.yaml || true
kubectl apply -f k8s/flink/event-processor-job.yaml
