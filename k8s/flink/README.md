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
kubectl port-forward svc/flink-session-cluster-rest 8081
```

Then visit http://localhost:8081

### Configuration Details

The session cluster is configured with:
- **Flink version:** 1.20
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

Example session job structure (to be created later):

```yaml
apiVersion: flink.apache.org/v1beta1
kind: FlinkSessionJob
metadata:
  name: your-job-name
  namespace: default
spec:
  deploymentName: flink-session-cluster
  job:
    jarURI: <location-of-your-jar>
    parallelism: 2
    upgradeMode: savepoint
    state: running
```

### Jar URI Options

The `jarURI` can point to:
- `local:///opt/flink/usrlib/your-job.jar` - jar baked into custom Docker image - but this has to be done in the **operator** image if using a session cluster, which is something we shouldn't redeploy with every job logic change
- `https://example.com/your-job.jar` - remote HTTP(S) URL
- `s3://bucket/your-job.jar` - S3 bucket (requires credentials)

Current approach - we deploy a minimal HTTP server to make the JAR(s) available while still using a session cluster. This isn't the simplest approach for a local dev setup (using a dedicated application cluster would be easier) but allows some experimentation with "production-like" infra architecture patterns.
