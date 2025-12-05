# Flink Kubernetes Manifests

This directory contains Kubernetes manifests for Flink deployments managed by the Flink Kubernetes Operator.

## Session Cluster

The session cluster provides a long-running Flink cluster that can accept multiple jobs.

### Deploy the Session Cluster

```bash
kubectl apply -f k8s/flink/session-cluster.yaml
```

The Flink Kubernetes Operator handles RBAC setup automatically, so no separate service account creation is needed.

### Check Status

```bash
kubectl get flinkdeployment
kubectl describe flinkdeployment flink-session-cluster
```

### Access Flink UI

```bash
../../portforward-ui.sh
```

Then visit http://localhost:8081

### Configuration Details

The session cluster is configured with:
- **Image:** Stock `flink:1.20` (no custom image needed)
- **JobManager:** 2GB memory, 1 CPU
- **TaskManagers:** 2 replicas, 2GB memory each, 1 CPU, 2 task slots per TM
- **Total capacity:** 4 task slots (2 TMs × 2 slots)
- **Checkpointing:** Enabled with 60s interval to local filesystem
- **State backend:** Filesystem (local emptyDir volume)

For production, you'd want to:
- Use a distributed state backend (S3, HDFS, etc.)
- Configure proper resource limits
- Add network policies
- Enable high availability
- Set up proper monitoring

## Session Jobs

Once you have JVM code ready (Kotlin/Scala), you can submit session jobs to the cluster. Session jobs will be defined in separate manifest files and reference the session cluster.

See [event-processor-job.yaml](event-processor-job.yaml) for current example.

### JAR Serving Architecture

This project uses a JAR server approach to deliver application code:

**Why JAR Server?**
- The session cluster uses stock `flink:1.20` image (no custom builds needed)
- Flink Operator fetches JARs from its own network context, not from cluster pods (so while "baking in" the JAR to a custom image would work for an application cluster, it doesn't work for session clusters)
- JAR is served over HTTP from a simple nginx pod
- Allows experimentation with "production-like" patterns (similar to using S3/artifact repositories)

**How it Works:**
1. Two-stage Docker build: Gradle builds JAR → nginx serves it
2. JAR server deployed at `http://jar-server.default.svc:8080/flink-job-1.0.0.jar`
3. Session job manifest references this HTTP URI
4. Flink Operator fetches and deploys the JAR

See `../../flink-job/README.md` for full build and deployment instructions.

**Alternative Approaches:**
- `local://` - Requires custom operator image (too far down the stack)
- Application Mode - Simpler for local dev, but less like production
- S3/artifact repository - Production approach, JAR server mimics this pattern
