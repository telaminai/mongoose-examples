Docker stack: Kafka, Postgres, Grafana

What this starts
- Kafka (KRaft, no ZooKeeper)
  - Internal (for other containers): kafka:9092
  - Host access: localhost:9094
- Postgres 16
  - Host access: localhost:5432
  - Credentials: user=app, password=app, database=appdb
- Grafana 10
  - Host access: http://localhost:3000
  - Credentials: admin / admin

Prerequisites
- Docker Desktop or Docker Engine with Compose V2 (docker compose)

Quick start
1) From this directory:
   docker compose up -d

2) Check containers:
   docker compose ps

3) Open Grafana:
   http://localhost:3000 (admin / admin)

4) Postgres connection details (from host):
   Host: localhost
   Port: 5432
   DB: appdb
   User: app
   Password: app

5) Kafka bootstrap servers:
   - From other containers: kafka:9092
   - From your host: localhost:9094

Verification (optional)
- Kafka: list topics
  docker compose exec kafka bash -lc "/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list"

- Kafka: create a topic and list it
  docker compose exec kafka bash -lc "/opt/bitnami/kafka/bin/kafka-topics.sh --create --topic demo --partitions 1 --replication-factor 1 --bootstrap-server localhost:9092"
  docker compose exec kafka bash -lc "/opt/bitnami/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list"

- Postgres: run a quick query
  docker compose exec postgres psql -U app -d appdb -c "SELECT now();"

- Grafana: login and add a datasource
  1) Go to http://localhost:3000 (admin/admin)
  2) Add a PostgreSQL datasource:
     Host: postgres:5432
     Database: appdb
     User: app
     Password: app

Stopping and cleaning up
- Stop stack (keep volumes/data):
  docker compose down

- Stop stack and remove volumes (delete all data):
  docker compose down -v

Notes
- Kafka is configured with two listeners:
  - PLAINTEXT on 9092 for internal container-to-container communication.
  - PLAINTEXT_HOST on 9094 for host-based clients (localhost).
- Credentials and ports are basic defaults for development. Change them as needed in docker-compose.yml.
