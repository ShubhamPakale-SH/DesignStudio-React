# SecureBank — Real-Time Transaction & Fraud Detection Platform

A Kafka-based, event-driven banking platform that processes account transactions in real-time and detects suspicious activity using Kafka Streams.

## Architecture

```
  ┌────────┐  POST /api/transactions    ┌────────────────────┐
  │ Client │ ─────────────────────────► │ transaction-service │
  └────────┘                            │  (REST + Producer)  │
                                        └──────────┬──────────┘
                                                   │ keyed by accountId
                                                   ▼
                                        ┌──────────────────────┐
                                        │ Topic: transactions  │  (3 partitions)
                                        └──────┬──────┬────────┘
                                               │      │
                          ┌────────────────────┘      └──────────────────┐
                          ▼                                              ▼
              ┌────────────────────┐                       ┌─────────────────────────┐
              │  account-service   │                       │   fraud-detection       │
              │  (Consumer + JPA)  │                       │   (Kafka Streams EOS)   │
              │  → PostgreSQL      │                       │   windowed aggregation  │
              └────────────────────┘                       └────────────┬────────────┘
                                                                        │ if count > 5/60s
                                                                        ▼
                                                              ┌──────────────────┐
                                                              │  Topic: alerts   │
                                                              └──────────────────┘
```

## Services

| Service | Port | Role |
|---|---|---|
| `transaction-service` | 8081 | REST API → Kafka producer (idempotent, acks=all) |
| `account-service` | 8082 | Kafka consumer → updates PostgreSQL balances |
| `fraud-detection` | 8083 | Kafka Streams app → real-time fraud rules |
| Kafka broker 1 | 29092 | Host listener |
| Kafka broker 2 | 29093 | Host listener |
| Kafka broker 3 | 29094 | Host listener |
| Schema Registry | 8091 | Confluent Schema Registry (host port) — REST API on `/subjects` |
| Postgres | 5432 | Account data |
| Kafka UI | 8090 | Web UI to browse topics + schemas |

**Cluster:** 3 Kafka brokers on a single Zookeeper, `replication-factor=3`, `min.insync.replicas=2`. Tolerates 1 broker failure with zero data loss.
**Wire format:** Avro, via Confluent Schema Registry (compatibility mode `BACKWARD`). Schemas live in `schemas/avro/`.

## Prerequisites

- Docker Desktop
- Java 17 + Maven
- (Optional) `curl` or Postman

## How to Run

### 1. Start the 3-broker Kafka cluster + Postgres
```bash
cd SecureBank
docker compose up -d
```
Wait ~30 seconds for all 3 brokers to elect a controller and become ready. Browse topics at <http://localhost:8090>.

### 2. Create the transactions, alerts and DLT topics (RF=3)
```bash
docker exec securebank-kafka1 kafka-topics \
  --bootstrap-server kafka1:9092 \
  --create --topic transactions --partitions 3 --replication-factor 3
docker exec securebank-kafka1 kafka-topics \
  --bootstrap-server kafka1:9092 \
  --create --topic alerts --partitions 3 --replication-factor 3
docker exec securebank-kafka1 kafka-topics \
  --bootstrap-server kafka1:9092 \
  --create --topic transactions.DLT --partitions 3 --replication-factor 3
```

Verify replication is healthy (`Isr:` should list 3 brokers per partition):
```bash
docker exec securebank-kafka1 kafka-topics \
  --bootstrap-server kafka1:9092 --describe --topic transactions
```

Once a producer publishes its first message, its Avro schema is auto-registered with Schema Registry. You can list registered subjects with:
```bash
curl http://localhost:8091/subjects
```
And view a specific schema:
```bash
curl http://localhost:8091/subjects/transactions-value/versions/latest
```

### 3. Build all modules
```bash
mvn -f pom.xml clean package -DskipTests
```

### 4. Run each service (3 separate terminals)
```bash
# Terminal 1
mvn -f transaction-service/pom.xml spring-boot:run

# Terminal 2
mvn -f account-service/pom.xml spring-boot:run

# Terminal 3
mvn -f fraud-detection/pom.xml spring-boot:run
```

### 5. Send a transaction
```bash
curl -X POST http://localhost:8081/api/transactions \
  -H "Content-Type: application/json" \
  -d '{
    "accountId": "ACC1001",
    "type": "DEPOSIT",
    "amount": 250.00,
    "city": "Mumbai"
  }'
```

Expected on success: `201 Created` with a body like
```json
{
  "transactionId": "<uuid>",
  "status": "PERSISTED",
  "partition": 1,
  "offset": 42
}
```
If Kafka is unreachable, you'll get `503 Service Unavailable` with `status: TIMEOUT` or `FAILED` — the controller waits up to 5s for broker acknowledgement before returning.

Watch the `account-service` logs apply it to Postgres. Check the balance:
```bash
docker exec -it securebank-postgres psql -U bank -d securebank -c "SELECT * FROM accounts;"
```

### 6. Trigger a fraud alert
Send 6+ transactions for the same account within 60 seconds:
```bash
for i in 1 2 3 4 5 6 7; do
  curl -X POST http://localhost:8081/api/transactions \
    -H "Content-Type: application/json" \
    -d "{\"accountId\": \"ACC1002\", \"type\": \"WITHDRAW\", \"amount\": 50, \"city\": \"Mumbai\"}"
done
```
Watch `fraud-detection` logs print **FRAUD ALERT** and the `alerts` topic in Kafka UI.

## Kafka Concepts Demonstrated

| Concept | Where |
|---|---|
| Partitioning by key | Producer keys by `accountId` → ordering per account |
| Idempotent producer | `enable.idempotence=true` + `acks=all` |
| Manual offset commit | Consumer commits only after DB write succeeds |
| Consumer group + parallelism | `concurrency=3` matches 3 partitions |
| Idempotent consumer | Skip if `transactionId` exists in `transaction_log` |
| Exactly-once Streams | `processing.guarantee=exactly_once_v2` |
| Windowed aggregation | Tumbling window counts per `accountId` |
| Error-handling deserializer | Poison messages caught at deserialization |

## Interview Talking Points

1. **"Why key by accountId?"** → All events for an account go to the same partition → strict ordering. Trade-off: hot account = hot partition.
2. **"How do I prevent duplicates?"** → Producer idempotence + consumer's `transaction_log` table dedup.
3. **"What happens if account-service crashes mid-DB write?"** → Offset isn't committed → next poll re-delivers → dedup table guards against double-apply.
4. **"How do I scale fraud detection?"** → Add partitions + run more Streams instances (same `application.id`) → Kafka auto-rebalances.
5. **"How do I reprocess old transactions for a new rule?"** → Reset consumer group offsets to earliest with `kafka-consumer-groups.sh`.

## Project Structure

```
SecureBank/
├── docker-compose.yml          Kafka + ZK + Postgres + Kafka UI
├── init-db.sql                 Seed accounts table
├── pom.xml                     Parent Maven module
├── transaction-service/        REST producer
├── account-service/            JPA consumer
├── fraud-detection/            Kafka Streams app
└── README.md
```

## Next Steps (Stretch Goals)

- Add a **dead letter topic** consumer for poison messages.
- Switch to **Avro + Schema Registry** for typed contracts.
- Add **Prometheus + Grafana** dashboards for consumer lag.
- Add a **notification-service** that consumes `alerts` and sends email/SMS.
- Add **Debezium CDC** from PostgreSQL → Kafka for the outbox pattern.
- Run **chaos test:** kill the broker mid-load and observe automatic failover.
