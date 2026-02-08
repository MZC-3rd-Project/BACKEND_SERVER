# Docker Local Development Environment

This directory contains Docker Compose configuration for running the Project03 backend infrastructure locally.

## Services

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL | 5432 | Main database (auth_db, user_db, test_db) |
| Redis | 6379 | Caching layer |
| Kafka | 29092 | Event streaming (localhost access) |
| Kafka | 9092 | Event streaming (inter-container) |
| Zookeeper | 2181 | Kafka coordination |
| Zipkin | 9411 | Distributed tracing UI |

## Quick Start

### 1. Start all services

```bash
cd docker
docker-compose up -d
```

### 2. Check service health

```bash
docker-compose ps
```

All services should show `healthy` status.

### 3. View logs

```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f postgres
docker-compose logs -f kafka
```

### 4. Stop services

```bash
docker-compose down
```

### 5. Stop and remove volumes (clean slate)

```bash
docker-compose down -v
```

## Database Setup

The PostgreSQL init script automatically creates three databases:
- `auth_db` - Authentication service database
- `user_db` - User service database
- `test_db` - Test server database

All databases have the `uuid-ossp` extension enabled.

### Connect to PostgreSQL

```bash
# Using docker exec
docker exec -it project03-postgres psql -U postgres -d auth_db

# Using psql on host (if installed)
psql -h localhost -U postgres -d auth_db
# Password: postgres
```

### Useful PostgreSQL Commands

```sql
-- List all databases
\l

-- Connect to a database
\c auth_db

-- List all tables
\dt

-- List all extensions
\dx
```

## Redis

### Connect to Redis CLI

```bash
docker exec -it project03-redis redis-cli
```

### Test Redis

```bash
# Ping
redis-cli ping
# Expected: PONG
```

## Kafka

### Access Kafka from Application

Use the following connection string:
```
localhost:29092
```

### Create a topic manually (optional)

```bash
docker exec -it project03-kafka kafka-topics \
  --create \
  --bootstrap-server localhost:9092 \
  --topic test-topic \
  --partitions 3 \
  --replication-factor 1
```

### List topics

```bash
docker exec -it project03-kafka kafka-topics \
  --list \
  --bootstrap-server localhost:9092
```

### Produce test message

```bash
docker exec -it project03-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic test-topic
```

### Consume messages

```bash
docker exec -it project03-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic test-topic \
  --from-beginning
```

## Zipkin

Access the Zipkin UI at: http://localhost:9411

Traces from your services will appear here when configured with:
```
spring.zipkin.base-url=http://localhost:9411
```

## Environment Variables

Copy `.env.example` to `.env` and customize if needed:

```bash
cp .env.example .env
```

Default values are suitable for local development.

## Troubleshooting

### Services not starting

Check logs:
```bash
docker-compose logs
```

### Port already in use

Edit `.env` to change port mappings.

### Kafka connection issues

Ensure you're using `localhost:29092` for connections from the host machine.
Services running inside Docker should use `kafka:9092`.

### PostgreSQL initialization didn't run

Remove the volume and restart:
```bash
docker-compose down -v
docker-compose up -d
```

### Check service health

```bash
# PostgreSQL
docker exec project03-postgres pg_isready -U postgres

# Redis
docker exec project03-redis redis-cli ping

# Kafka
docker exec project03-kafka kafka-topics --bootstrap-server localhost:9092 --list
```

## Data Persistence

Data is persisted in Docker volumes:
- `postgres-data` - PostgreSQL data
- `redis-data` - Redis data
- `kafka-data` - Kafka logs and data
- `zookeeper-data` - Zookeeper data

To completely reset:
```bash
docker-compose down -v
```

## Network

All services are connected via the `project03-network` bridge network, allowing inter-service communication using container names.

## Health Checks

All services include health checks to ensure proper startup order and readiness:
- PostgreSQL: pg_isready
- Redis: redis-cli ping
- Zookeeper: echo ruok
- Kafka: kafka-topics list
- Zipkin: HTTP health endpoint
