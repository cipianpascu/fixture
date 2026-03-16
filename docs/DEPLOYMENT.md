# Deployment Guide

This guide covers deploying the Backend Gateway to various environments.

## Table of Contents
- [Prerequisites](#prerequisites)
- [Environment Configuration](#environment-configuration)
- [Local Deployment](#local-deployment)
- [Docker Deployment](#docker-deployment)
- [Kubernetes Deployment](#kubernetes-deployment)
- [Production Considerations](#production-considerations)
- [Troubleshooting](#troubleshooting)

## Prerequisites

### Required
- Java 21 JDK
- Maven 3.6+
- PostgreSQL 12+ (for fixture mode)

### Optional
- Docker 20+
- Docker Compose 2+
- Kubernetes 1.20+
- kubectl

## Environment Configuration

### Environment Variables

| Variable | Description | Default | Required |
|----------|-------------|---------|----------|
| `SPRING_PROFILES_ACTIVE` | Active profile (routing/fixture) | - | Yes |
| `SPRING_DATASOURCE_URL` | Database JDBC URL | jdbc:postgresql://localhost:5432/backend_gateway | No |
| `SPRING_DATASOURCE_USERNAME` | Database username | postgres | No |
| `SPRING_DATASOURCE_PASSWORD` | Database password | postgres | No |
| `SERVER_PORT` | Application port | 8080 | No |
| `JAVA_OPTS` | JVM options | - | No |

### Configuration Profiles

#### Routing Mode
```bash
export SPRING_PROFILES_ACTIVE=routing
```

#### Fixture Mode
```bash
export SPRING_PROFILES_ACTIVE=fixture
export SPRING_DATASOURCE_URL=jdbc:postgresql://db-host:5432/backend_gateway
export SPRING_DATASOURCE_USERNAME=gateway_user
export SPRING_DATASOURCE_PASSWORD=secure_password
```

## Local Deployment

### Option 1: Maven

```bash
# Build the application
mvn clean package -DskipTests

# Run routing mode
java -jar target/backend-gateway-1.0.0-SNAPSHOT.jar --spring.profiles.active=routing

# Run fixture mode
java -jar target/backend-gateway-1.0.0-SNAPSHOT.jar \
  --spring.profiles.active=fixture \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/backend_gateway \
  --spring.datasource.username=postgres \
  --spring.datasource.password=postgres
```

### Option 2: Maven Spring Boot Plugin

```bash
# Routing mode
mvn spring-boot:run -Dspring-boot.run.profiles=routing

# Fixture mode
mvn spring-boot:run -Dspring-boot.run.profiles=fixture
```

### Option 3: IDE
- Import project as Maven project
- Set active profile in Run Configuration
- Configure environment variables
- Run `BackendGatewayApplication.java`

## Docker Deployment

### Build Docker Image

```bash
# Build the image
docker build -t backend-gateway:1.0.0 .

# Tag for registry
docker tag backend-gateway:1.0.0 your-registry/backend-gateway:1.0.0

# Push to registry
docker push your-registry/backend-gateway:1.0.0
```

### Run with Docker

#### Routing Mode
```bash
docker run -d \
  --name backend-gateway-routing \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=routing \
  backend-gateway:1.0.0
```

#### Fixture Mode
```bash
# Start PostgreSQL first
docker run -d \
  --name postgres \
  -e POSTGRES_DB=backend_gateway \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15-alpine

# Start gateway
docker run -d \
  --name backend-gateway-fixture \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=fixture \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/backend_gateway \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=postgres \
  --link postgres \
  backend-gateway:1.0.0
```

### Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f backend-gateway-routing
docker-compose logs -f backend-gateway-fixture

# Stop services
docker-compose down

# Remove volumes
docker-compose down -v
```

## Kubernetes Deployment

### Prerequisites
- Kubernetes cluster
- kubectl configured
- Docker registry access

### Create Namespace

```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: backend-gateway
```

```bash
kubectl apply -f namespace.yaml
```

### PostgreSQL Deployment (Fixture Mode)

```yaml
# postgres-deployment.yaml
apiVersion: v1
kind: Secret
metadata:
  name: postgres-secret
  namespace: backend-gateway
type: Opaque
stringData:
  password: your-secure-password

---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-pvc
  namespace: backend-gateway
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: postgres
  namespace: backend-gateway
spec:
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_DB
          value: backend_gateway
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: password
        volumeMounts:
        - name: postgres-storage
          mountPath: /var/lib/postgresql/data
      volumes:
      - name: postgres-storage
        persistentVolumeClaim:
          claimName: postgres-pvc

---
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: backend-gateway
spec:
  selector:
    app: postgres
  ports:
  - port: 5432
    targetPort: 5432
```

### Gateway Deployment

```yaml
# gateway-deployment.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: gateway-config
  namespace: backend-gateway
data:
  application-routing.yml: |
    gateway:
      mode: routing
      backends:
        - name: user-service
          baseUrl: http://user-service:8080
          path: /api/v1/users
          securityType: JWT
          enabled: true

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend-gateway-routing
  namespace: backend-gateway
spec:
  replicas: 3
  selector:
    matchLabels:
      app: backend-gateway
      mode: routing
  template:
    metadata:
      labels:
        app: backend-gateway
        mode: routing
    spec:
      containers:
      - name: gateway
        image: your-registry/backend-gateway:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: routing
        - name: JAVA_OPTS
          value: "-Xmx512m -Xms256m"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
        volumeMounts:
        - name: config
          mountPath: /app/config
      volumes:
      - name: config
        configMap:
          name: gateway-config

---
apiVersion: v1
kind: Service
metadata:
  name: backend-gateway-routing
  namespace: backend-gateway
spec:
  type: LoadBalancer
  selector:
    app: backend-gateway
    mode: routing
  ports:
  - port: 80
    targetPort: 8080

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend-gateway-fixture
  namespace: backend-gateway
spec:
  replicas: 2
  selector:
    matchLabels:
      app: backend-gateway
      mode: fixture
  template:
    metadata:
      labels:
        app: backend-gateway
        mode: fixture
    spec:
      containers:
      - name: gateway
        image: your-registry/backend-gateway:1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: fixture
        - name: SPRING_DATASOURCE_URL
          value: jdbc:postgresql://postgres:5432/backend_gateway
        - name: SPRING_DATASOURCE_USERNAME
          value: postgres
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: password
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5

---
apiVersion: v1
kind: Service
metadata:
  name: backend-gateway-fixture
  namespace: backend-gateway
spec:
  type: ClusterIP
  selector:
    app: backend-gateway
    mode: fixture
  ports:
  - port: 80
    targetPort: 8080
```

### Deploy to Kubernetes

```bash
# Deploy PostgreSQL
kubectl apply -f postgres-deployment.yaml

# Wait for PostgreSQL to be ready
kubectl wait --for=condition=ready pod -l app=postgres -n backend-gateway --timeout=60s

# Deploy Gateway
kubectl apply -f gateway-deployment.yaml

# Check status
kubectl get pods -n backend-gateway
kubectl get services -n backend-gateway

# View logs
kubectl logs -f deployment/backend-gateway-routing -n backend-gateway
```

### Ingress (Optional)

```yaml
# ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: backend-gateway-ingress
  namespace: backend-gateway
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
  - hosts:
    - gateway.example.com
    secretName: gateway-tls
  rules:
  - host: gateway.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: backend-gateway-routing
            port:
              number: 80
```

## Production Considerations

### Database

#### PostgreSQL Configuration
```sql
-- Create dedicated user
CREATE USER gateway_user WITH ENCRYPTED PASSWORD 'secure_password';

-- Create database
CREATE DATABASE backend_gateway OWNER gateway_user;

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE backend_gateway TO gateway_user;
```

#### Connection Pooling
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### JVM Tuning

```bash
JAVA_OPTS="-Xmx2g -Xms1g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=/app/logs/heapdump.hprof \
  -Duser.timezone=UTC"
```

### Security

#### Enable HTTPS
```yaml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:keystore.p12
    key-store-password: changeit
    key-store-type: PKCS12
```

#### Secure Actuator Endpoints
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
```

### Monitoring

#### Prometheus ServiceMonitor (Kubernetes)
```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: backend-gateway
  namespace: backend-gateway
spec:
  selector:
    matchLabels:
      app: backend-gateway
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
```

### Backup and Recovery

#### Database Backup
```bash
# Backup
pg_dump -U gateway_user -h localhost backend_gateway > backup.sql

# Restore
psql -U gateway_user -h localhost backend_gateway < backup.sql
```

### High Availability

- Deploy multiple replicas (minimum 3)
- Use load balancer for traffic distribution
- Configure pod anti-affinity
- Implement database replication
- Use persistent volumes for database

### Scaling

#### Horizontal Pod Autoscaler (Kubernetes)
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: backend-gateway-hpa
  namespace: backend-gateway
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: backend-gateway-routing
  minReplicas: 3
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

## Troubleshooting

### Common Issues

#### Database Connection Failed
```bash
# Check database connectivity
psql -h localhost -U postgres -d backend_gateway

# Check application logs
kubectl logs deployment/backend-gateway-fixture -n backend-gateway
```

#### Out of Memory
```bash
# Check heap usage
jmap -heap <pid>

# Increase heap size
export JAVA_OPTS="-Xmx2g -Xms1g"
```

#### High CPU Usage
- Check circuit breaker states
- Review slow queries
- Analyze thread dumps
- Monitor backend response times

### Health Checks

```bash
# Application health
curl http://localhost:8080/actuator/health

# Liveness probe
curl http://localhost:8080/actuator/health/liveness

# Readiness probe
curl http://localhost:8080/actuator/health/readiness
```

### Logs

```bash
# Docker
docker logs -f backend-gateway

# Kubernetes
kubectl logs -f deployment/backend-gateway-routing -n backend-gateway

# File system
tail -f /app/logs/backend-gateway.log
```

## Rollback

### Docker
```bash
docker stop backend-gateway
docker rm backend-gateway
docker run -d --name backend-gateway your-registry/backend-gateway:previous-version
```

### Kubernetes
```bash
# Rollback to previous version
kubectl rollout undo deployment/backend-gateway-routing -n backend-gateway

# Check rollout status
kubectl rollout status deployment/backend-gateway-routing -n backend-gateway
```

## Maintenance

### Zero-Downtime Deployment
1. Deploy new version alongside old
2. Health check new version
3. Shift traffic gradually
4. Monitor metrics
5. Remove old version

### Database Migration
1. Backup current database
2. Test migration on staging
3. Schedule maintenance window
4. Run migration scripts
5. Verify data integrity
6. Update application

---

For additional support, see [README.md](../README.md) or [ARCHITECTURE.md](ARCHITECTURE.md).
