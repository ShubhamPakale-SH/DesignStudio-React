# Kafka Project Idea — For Banking Interviews

## Project: **Real-Time Fraud Detection & Transaction Processing System**

A banking-grade, event-driven microservices project that showcases the full Kafka toolkit — perfect for interviews with banks/fintechs.

---

## Project Name
**"SecureBank — Real-Time Transaction & Fraud Detection Platform"**

## Why This Project Wins for Banking Interviews

- **Domain relevance:** Banks live and breathe transactions, fraud, audit, compliance — interviewers instantly connect.
- **Covers every Kafka concept:** producers, consumers, partitioning, EOS, Streams, Connect, DLQs, replay.
- **Demonstrates non-functional thinking:** durability, ordering, idempotency, audit — exactly what banks care about.
- **Talking points for hours:** every component maps to a real interview question.

---

## High-Level Architecture

```
 ┌─────────────┐     ┌──────────────┐     ┌──────────────────┐
 │  Customer   │────►│ Transaction  │────►│  Kafka Topic:    │
 │  (REST API) │     │  Service     │     │  transactions    │
 └─────────────┘     └──────────────┘     │  (key=accountId) │
                                          └────────┬─────────┘
                                                   │
                ┌──────────────────────────────────┼──────────────────────────┐
                │                                  │                          │
                ▼                                  ▼                          ▼
        ┌───────────────┐               ┌────────────────────┐      ┌─────────────────┐
        │ Fraud         │               │ Account Balance    │      │ Notification    │
        │ Detection     │               │ Service            │      │ Service         │
        │ (Kafka        │               │ (Consumer +        │      │ (Consumer →     │
        │  Streams)     │               │  PostgreSQL)       │      │  Email/SMS)     │
        └──────┬────────┘               └────────────────────┘      └─────────────────┘
               │
       ┌───────┴────────┐
       ▼                ▼
 ┌──────────┐    ┌──────────────┐
 │ Topic:   │    │ Topic:       │
 │  alerts  │    │  flagged-txn │
 └──────────┘    └──────┬───────┘
                        │
                        ▼
                ┌─────────────────┐
                │ Kafka Connect → │
                │  Elasticsearch  │  (audit/search)
                │  + S3 (archive) │
                └─────────────────┘
```

---

## Core Microservices (Spring Boot + Kafka)

### 1. Transaction Producer Service
- REST API: `POST /transaction` (deposit, withdraw, transfer)
- Publishes to `transactions` topic, **keyed by `accountId`** → ordering per account
- **Idempotent producer** + `acks=all` → no loss, no duplicates
- Validates input, attaches `transactionId` (UUID)

### 2. Account Balance Service
- Consumes `transactions` → updates PostgreSQL balance
- **Manual offset commit after DB write** → at-least-once
- Demonstrates **transactional outbox pattern** or **exactly-once via DB+Kafka tx**

### 3. Fraud Detection Service (Kafka Streams)
- Consumes `transactions` stream
- Rules using **windowed aggregations**:
  - More than 5 transactions in 1 minute from same account
  - Transaction amount > 10× rolling avg
  - Geo-impossible transactions (two cities within 5 min)
- Stateful processing with **KTable + RocksDB state store**
- Publishes to `flagged-transactions` and `alerts` topics

### 4. Notification Service
- Consumes `alerts` → sends email/SMS (mock with logs)
- Demonstrates **consumer groups, multiple instances, partition assignment**

### 5. Audit & Search Service (Kafka Connect)
- **Sink connector**: `flagged-transactions` → Elasticsearch (searchable audit)
- **Sink connector**: `transactions` → S3/MinIO (long-term archive, compliance)
- **Source connector**: PostgreSQL `accounts` → Kafka (CDC with Debezium)

### 6. Dead Letter Queue Handler
- Poison messages routed to `transactions.DLT`
- Separate consumer with retry + manual replay tool

---

## Kafka Concepts Demonstrated

| Concept | Where It's Used |
|---|---|
| **Partitioning by key** | `transactions` keyed by `accountId` for order guarantee |
| **Idempotent producer** | Transaction service |
| **Exactly-once semantics** | Fraud detection (Streams EOS) + Account service (tx outbox) |
| **Consumer groups** | Multiple instances of notification service |
| **Kafka Streams** | Stateful fraud detection with windows + KTable joins |
| **Kafka Connect** | ES sink, S3 sink, Debezium CDC source |
| **Schema Registry** | Avro schemas for `Transaction`, `Alert` |
| **DLQ pattern** | Poison message handling |
| **Log compaction** | `account-snapshots` topic (latest balance per account) |
| **Replay / offset reset** | Reprocess historical transactions for new fraud rule |
| **Monitoring** | Prometheus + Grafana for lag, throughput, errors |

---

## Tech Stack

- **Language:** Java 17 / Spring Boot 3 (or Python with FastAPI + confluent-kafka)
- **Kafka:** Confluent Platform / Apache Kafka 3.x + KRaft
- **Stream Processing:** Kafka Streams
- **Storage:** PostgreSQL (accounts), Elasticsearch (audit), MinIO/S3 (archive)
- **Serialization:** Avro + Confluent Schema Registry
- **Orchestration:** Docker Compose (for demo) → Kubernetes (bonus)
- **Monitoring:** Prometheus + Grafana + JMX exporter
- **Testing:** Testcontainers for Kafka integration tests

---

## Suggested Repo Structure

```
securebank/
├── docker-compose.yml          # Kafka, ZK/KRaft, Schema Registry, Connect, ES, Postgres
├── transaction-service/         # Producer
├── account-service/             # Consumer + DB
├── fraud-detection/             # Kafka Streams
├── notification-service/        # Consumer
├── connectors/                  # Connect configs (ES sink, S3 sink, Debezium)
├── schemas/                     # Avro .avsc files
├── monitoring/                  # Prometheus + Grafana dashboards
├── load-generator/              # Simulate transaction traffic
└── README.md                    # Architecture, setup, demo guide
```

---

## Stretch Goals (Stand Out)

- **MirrorMaker 2** → simulate multi-region DR setup
- **Saga pattern** for cross-account transfers
- **Tiered storage** demo (if using Confluent)
- **Chaos test:** kill a broker mid-load, show producer retries + leader failover
- **Streams interactive queries** → REST API exposing real-time fraud stats
- **OpenTelemetry tracing** across producer → Streams → consumer

---

## What to Highlight in the Interview

1. **"Why I keyed by accountId"** → ordering, parallelism trade-offs
2. **"How I handle exactly-once for money movement"** → idempotent producer + transactional consumer
3. **"What happens when a broker dies during a transfer"** → leader election, retries, ISR
4. **"How I prevent duplicate fraud alerts"** → Streams EOS + dedup window
5. **"How auditors can query 6-month-old transactions"** → S3 archive + ES audit index
6. **"How I'd scale to 1M TPS"** → partition count, consumer count, batching, compression

---

## Estimated Effort

- **Minimum viable demo:** 2–3 weekends (transaction service + 1 consumer + fraud Streams)
- **Full project with monitoring + Connect:** 3–4 weeks part-time
- **Stretch goals:** ongoing

---

## Bonus: One-Liner Pitch for Resume

> *"Built an event-driven banking platform on Apache Kafka processing 50K+ transactions/sec, featuring real-time fraud detection with Kafka Streams (windowed aggregations, stateful joins), exactly-once payment processing, Debezium CDC, and Elasticsearch-backed audit search — demonstrating production-grade durability, ordering, and replay guarantees."*
