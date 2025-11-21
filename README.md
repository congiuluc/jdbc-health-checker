# SQL Batch Job (Java 21 + MSSQL)

A lightweight Java application that executes a SQL query (`SELECT 1`) every 30 seconds against an Azure SQL Database (or any MSSQL instance). It uses **HikariCP** for connection pooling and exposes HTTP endpoints for Kubernetes liveness/readiness probes.

## Prerequisites

- Java 21
- Maven
- Docker
- Kubernetes Cluster (AKS)
- Azure SQL Database

## Configuration

The application is configured via environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `DB_URL` | JDBC Connection String | `jdbc:sqlserver://localhost...` |
| `DB_USER` | Database Username | `sa` |
| `DB_PASSWORD` | Database Password | `yourStrong(!)Password` |
| `DB_POOL_SIZE` | HikariCP Max Pool Size | `2` |
| `DB_QUERY` | SQL Query to execute | `SELECT 1` |
| `INTERVAL_SECONDS` | Seconds between query runs | `30` |

## Build and Run Locally

1.  **Build the JAR:**
    ```bash
    mvn clean package
    ```

2.  **Run with Environment Variables:**
    ```bash
    export DB_URL="jdbc:sqlserver://localhost:1433;databaseName=master;encrypt=true;trustServerCertificate=true;"
    export DB_USER="db_user"
    export DB_PASSWORD="yourStrong(!)Password"
    
    java -jar target/sql-health-check-1.0.0-SNAPSHOT.jar
    ```

## Docker Build

1.  **Build the Image:**
    ```bash
    docker build -t sql-health-check:latest .
    ```

2.  **Run Container:**
    ```bash
    docker run -p 8080:8080 \
      -e DB_URL="jdbc:sqlserver://host.docker.internal:1433;..." \
      -e DB_USER="db_user" \
      -e DB_PASSWORD="password" \
      sql-health-check:latest
    ```

## Deploy to AKS

1.  **Update Secrets:**
    Edit `k8s/secret.yaml` with your actual database credentials.

2.  **Apply Secrets:**
    ```bash
    kubectl apply -f k8s/secret.yaml
    ```

3.  **Update Deployment Image:**
    Edit `k8s/deployment.yaml` to point to your container registry image (e.g., `myregistry.azurecr.io/sql-health-check:latest`).

4.  **Deploy:**
    ```bash
    kubectl apply -f k8s/deployment.yaml
    ```

## Health Checks

- **Liveness Probe:** `http://localhost:8080/health` (Returns 200 OK)
- **Readiness Probe:** `http://localhost:8080/ready` (Returns 200 READY)

## Azure Container Registry (CI) setup

Add the following minimal configuration to enable GitHub Actions to push images to your ACR:

- Create a service principal and give it access to the ACR (replace placeholders):

```bash
az ad sp create-for-rbac \
    --name "github-actions-acr" \
    --role contributor \
    --scopes /subscriptions/<SUBSCRIPTION_ID>/resourceGroups/<RESOURCE_GROUP>/providers/Microsoft.ContainerRegistry/registries/<ACR_NAME>
```

- Copy the JSON output and add it to your GitHub repository secrets as `AZURE_CREDENTIALS`.

- Add the ACR login server (example `myregistry.azurecr.io`) as the `ACR_LOGIN_SERVER` repository secret.

In `k8s/deployment.yaml` update the image to use `${ACR_LOGIN_SERVER}/sql-health-check:latest` or the exact image name you configured.
