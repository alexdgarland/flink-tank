#!/bin/bash
set -ex

echo "📝 Ensuring topics exist..."
kubectl delete -f k8s/topics/ || true
kubectl apply -f k8s/topics/

echo ""
echo "🏗️ Building JAR server..."
docker build -t jar-server:latest -f jobs/flink-job/Dockerfile jobs/

echo ""
echo "📦 Loading JAR server image to Kind..."
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
echo "🔄 (Re-)creating Flink event processor job:"
kubectl delete -f k8s/flink/event-processor-job.yaml || true
kubectl apply -f k8s/flink/event-processor-job.yaml

echo ""
echo "🔄 (Re-)creating Flink aggregation job:"
kubectl delete -f k8s/flink/aggregation-job.yaml || true
kubectl apply -f k8s/flink/aggregation-job.yaml

echo ""
echo "🏗️ Building event producer..."
docker build -t event-producer:latest -f jobs/producer/Dockerfile jobs/

echo ""
echo "📦 Loading producer image to Kind..."
kind load docker-image event-producer:latest --name desktop

echo ""
echo "🚀 (Re-)deploying event producer..."
kubectl delete -f k8s/producer/event-producer.yaml || true
kubectl apply -f k8s/producer/event-producer.yaml

echo ""
echo "⏳ Waiting for producer to be ready..."
kubectl wait --for=condition=available --timeout=60s deployment/event-producer -n producer
echo ""
echo "✅ Producer ready and generating events!"
