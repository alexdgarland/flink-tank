#!/bin/bash

set -ex

# Cert Manager (required for Flink webhooks)
kubectl apply -f https://github.com/jetstack/cert-manager/releases/download/v1.18.2/cert-manager.yaml
# Wait for cert-manager pods to be ready before proceeding
kubectl rollout status deployment/cert-manager -n cert-manager
kubectl rollout status deployment/cert-manager-webhook -n cert-manager
kubectl rollout status deployment/cert-manager-cainjector -n cert-manager

# Set up Flink operator and session cluster
kubectl create namespace flink || true
# Install the operator
helm repo add flink-operator-repo https://downloads.apache.org/flink/flink-kubernetes-operator-1.13.0/ -n flink
helm upgrade --install flink-kubernetes-operator flink-operator-repo/flink-kubernetes-operator -n flink
# Wait for the Flink operator deployments to be ready before applying the session cluster
kubectl rollout status deployment/flink-kubernetes-operator -n flink
# Create a Session Cluster
kubectl apply -f k8s/flink/session-cluster.yaml -n flink

# Set up Kafka base infrastructure
kubectl create namespace kafka || true
kubectl apply -f https://strimzi.io/install/latest?namespace=kafka -n kafka
kubectl apply -f https://strimzi.io/examples/latest/kafka/kafka-single-node.yaml -n kafka
