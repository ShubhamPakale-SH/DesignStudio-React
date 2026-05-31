# SecureBank — Complete Project Guide

> Your one-stop reference for running, understanding, demoing, and defending the SecureBank Kafka project in an interview.

---

## Table of Contents
1. [Project Overview](#1-project-overview)
2. [How to Start the Project](#2-how-to-start-the-project)
3. [How Each Module Works](#3-how-each-module-works)
4. [End-to-End Flow](#4-end-to-end-flow)
5. [How to Give the Demo](#5-how-to-give-the-demo)
6. [What to Showcase](#6-what-to-showcase)
7. [20 Interview Questions & Answers](#7-20-interview-questions--answers)

---

## 1. Project Overview

**SecureBank** is a real-time banking platform built on Apache Kafka that:
- Accepts customer transactions via REST API
- Updates account balances in PostgreSQL
- Detects suspicious activity (fraud) using Kafka Streams in real-time
- Publishes alerts back to Kafka for downstream consumers

### Architecture

```
  ┌────────┐    POST /api/transactions    ┌────────────────────┐
  │ Client │ ──────────────────────────►  │ transaction-service │
  └────────┘                              │  (REST + Producer)  │
                                          └──────────┬──────────┘
                                                     │ keyed by accountId
                                                     ▼
                                       ┌──────────────────────────┐
                                       │ Topic: transactions      │  (3 partitions)
                                       └──────┬──────┬────────────┘
                                              │      │
                       ┌──────────────────────┘      └────────────────────┐
                       ▼                                                   ▼
              ┌────────────────────┐                       ┌──────────────────────────┐
              │  account-service   │                       │   fraud-detection        │
              │  Consumer + JPA    │                       │   Kafka Streams (EOS v2) │
              │  → PostgreSQL      │                       │   windowed aggregation   │
              └────────────────────┘                       └────────────┬─────────────┘
                                                                        │ if count > 5/60s
                                                                        ▼
                                                              ┌──────────────────┐
                                                              │  Topic: alerts   │
                                                              └──────────────────┘
```

### Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2 |
| Messaging | Apache Kafka 3.6 |
| Stream Processing | Kafka Streams |
| Database | PostgreSQL 15 |
| Build | Maven (multi-module) |
| Infrastructure | Docker Compose |
| API Testing | Postman |

---

## 2. How to Start the Project

### Prerequisites
- Docker Desktop running
- Java 17 + Maven installed
- IntelliJ IDEA (Community or Ultimate)

### Step-by-Step Startup

**Step 1 — Start infrastructure (3 Kafka brokers + Schema Registry + Postgres + Kafka UI)**
```powershell
cd D:\Temp\kafka\SecureBank
docker compose up -d
```
Wait ~45 seconds for the cluster + Schema Registry to be ready. Verify with `docker ps` — should show 7 containers: `securebank-zookeeper`, `securebank-kafka1/2/3`, `securebank-schema-registry`, `securebank-postgres`, `securebank-kafka-ui`.

You can confirm Schema Registry is up with:
```powershell
curl http://localhost:8091/subjects
```
(Returns `[]` until the first producer publishes a message.)

**Step 2 — Create Kafka topics (3 partitions, replication-factor 3)**
```powershell
docker exec securebank-kafka1 kafka-topics --bootstrap-server kafka1:9092 --create --topic transactions --partitions 3 --replication-factor 3
docker exec securebank-kafka1 kafka-topics --bootstrap-server kafka1:9092 --create --topic alerts --partitions 3 --replication-factor 3
docker exec securebank-kafka1 kafka-topics --bootstrap-server kafka1:9092 --create --topic transactions.DLT --partitions 3 --replication-factor 3
```

Verify ISR has 3 brokers per partition:
```powershell
docker exec securebank-kafka1 kafka-topics --bootstrap-server kafka1:9092 --describe --topic transactions
```

**Step 3 — Run all 3 services in IntelliJ**

For each service, add VM option (to fix Windows timezone issue):
```
-Duser.timezone=UTC
```

Then start them via Run configs or right-click → Run:
- `TransactionServiceApplication` → port **8081**
- `AccountServiceApplication` → port **8082**
- `FraudDetectionApplication` → port **8083**

**Step 4 — Test with Postman**

`POST http://localhost:8081/api/transactions`
```json
{
  "accountId": "ACC1001",
  "type": "DEPOSIT",
  "amount": 250.00,
  "city": "Mumbai"
}
```

Expected success response (the controller waits for Kafka to acknowledge):
```
HTTP/1.1 201 Created
{
  "transactionId": "<uuid>",
  "status": "PERSISTED",
  "partition": 1,
  "offset": 42
}
```

If Kafka is unreachable / no ISR / takes > 5s, you'll get `503 Service Unavailable` with status `TIMEOUT` or `FAILED` — the client knows the write was NOT durable and should retry.

**Step 5 — Inspect**
- Kafka UI: <http://localhost:8090>
- DB: `docker exec -it securebank-postgres psql -U bank -d securebank -c "SELECT * FROM accounts;"`

### Shutting Down
```powershell
# Stop services in IntelliJ (red ■ button)
docker compose down        # stops containers
docker compose down -v     # also wipes data volumes (fresh start)
```

---

## 3. How Each Module Works

### Module 1: `transaction-service` (Producer)

**Purpose:** Exposes a REST API; converts HTTP requests into Kafka messages.

**Key files:**
- `TransactionController.java` → handles `POST /api/transactions`
- `KafkaProducerConfig.java` → producer configuration
- `Transaction.java` → message DTO with validation

**How it works:**
1. Client sends JSON to `POST /api/transactions`
2. Controller validates (Bean Validation), generates `transactionId` if missing
3. `KafkaTemplate.send(topic, accountId, txn)` publishes to `transactions` topic
4. Message is **keyed by `accountId`** → all events for one account always land on the same partition (preserves order)
5. Producer is configured with:
   - `acks=all` → leader + ISR must ack → durability
   - `enable.idempotence=true` → no duplicates on retry
   - `compression=snappy` → smaller wire payload
   - `linger.ms=10` + `batch.size=32KB` → throughput optimization

**Why this matters:** This is the **entry point** — it must guarantee no transaction is lost.

---

### Module 2: `account-service` (Consumer + DB)

**Purpose:** Reads transactions from Kafka, applies them to PostgreSQL.

**Key files:**
- `TransactionConsumer.java` → `@KafkaListener` consuming `transactions` topic
- `AccountService.java` → idempotent business logic (balance + log)
- `KafkaConsumerConfig.java` → consumer configuration
- `init-db.sql` → seeds 3 demo accounts

**How it works:**
1. `@KafkaListener` polls the `transactions` topic
2. For each message, `AccountService.apply()`:
   - **Checks `transaction_log`** for the `transactionId` → if already processed, skips (idempotency)
   - Loads the account, computes new balance
   - **In a single DB transaction:** updates `accounts` table AND inserts into `transaction_log`
3. **Only after DB commit succeeds** does the consumer call `ack.acknowledge()` to commit the Kafka offset
4. If the service crashes mid-process, Kafka redelivers — and the `transaction_log` dedup ensures no double-apply

**Why this matters:** This demonstrates **at-least-once delivery + consumer-side idempotency** = effectively exactly-once for the database.

---

### Module 3: `fraud-detection` (Kafka Streams)

**Purpose:** Real-time stateful analytics on the transaction stream.

**Key files:**
- `FraudDetectionTopology.java` → defines the Streams DAG
- `Alert.java` → alert message DTO

**How it works:**
1. Subscribes to `transactions` topic
2. Groups events by `accountId`
3. Applies a **60-second tumbling window** that counts transactions per account
4. If count **> 5 in 60 seconds**, publishes an `Alert` to the `alerts` topic
5. Uses **`processing.guarantee=exactly_once_v2`** → no duplicate alerts on retry
6. Maintains state in an embedded **RocksDB** store, backed by a Kafka changelog topic (auto-recovery on restart)

**Why this matters:** Shows you can do **real-time, stateful, fault-tolerant analytics** with Kafka Streams — not just point-to-point messaging.

---

### Infrastructure: `docker-compose.yml`

Spins up:
- **Zookeeper** (cluster metadata)
- **Kafka broker** (port 29092 for host clients)
- **Kafka UI** (port 8090, web UI for topics/messages)
- **PostgreSQL** (port 5432, with `init-db.sql` for seed data)

All services run in an isolated Docker network — no port conflicts with your machine.

---

## 4. End-to-End Flow

### Happy Path: Deposit
1. **Postman** → `POST /api/transactions` `{accountId: ACC1001, type: DEPOSIT, amount: 250}`
2. **transaction-service:**
   - Generates `transactionId` (UUID)
   - Publishes to Kafka topic `transactions` with `key=ACC1001`
   - Logs: `Published txn xxx to partition 0 offset 5`
3. **Kafka broker:** writes to partition (chosen by `hash(ACC1001) % 3`), replicates to ISR
4. **account-service consumer** receives the message:
   - Checks `transaction_log` → not found
   - Updates `accounts.balance` from 5000 → 5250
   - Inserts row into `transaction_log`
   - Commits offset
   - Logs: `Applied txn xxx → account ACC1001 new balance 5250.00`
5. **fraud-detection** receives the same message:
   - Increments count in 60s window for `ACC1001` → count=1 (under threshold, no alert)

### Fraud Path: 7 Rapid Withdrawals
1. Postman sends 7 transactions for `ACC1002` in quick succession
2. transaction-service publishes all 7 → all land in the same partition (same key)
3. account-service processes them sequentially → debits balance 7 times
4. fraud-detection's tumbling window counts: 1, 2, 3, 4, 5, **6** → exceeds threshold
5. fraud-detection publishes `Alert` to `alerts` topic
6. **Kafka UI** shows the alert message
7. (In production) a notification-service would consume `alerts` and send SMS/email

---

## 5. How to Give the Demo

### Demo Setup (5 minutes before the interview)

**Open all of these side-by-side on your screen:**
1. IntelliJ — with all 3 services already running (green dots in Services tab)
2. Postman — collection loaded with deposit/withdraw/fraud-trigger requests
3. Browser tab 1 — Kafka UI at <http://localhost:8090>
4. Browser tab 2 (optional) — Postgres adminer or DB IDE
5. IntelliJ terminal — ready for `docker exec psql` queries

**Pre-flight checklist:**
- Docker containers running (`docker ps` shows 4)
- Topics exist (Kafka UI shows `transactions` and `alerts`)
- Postman collection imported
- Audio/screen-share working

---

### Demo Script (15–20 minutes)

#### Part 1 — Architecture (3 min)
> "I built a real-time banking transaction processing platform on Kafka. There are 3 microservices: a REST producer, a consumer that updates PostgreSQL, and a Kafka Streams app that detects fraud — all communicating via Kafka topics."

**Show the architecture diagram** from this guide or your README.

---

#### Part 2 — Happy Path (5 min)
> "Let me walk you through a single transaction."

1. In Postman, send a deposit:
   ```json
   { "accountId": "ACC1001", "type": "DEPOSIT", "amount": 250, "city": "Mumbai" }
   ```
2. Switch to IntelliJ's **Transaction Service** log:
   > *"Producer published to partition 0, offset 5. Note the key is `ACC1001` — I'll explain why in a moment."*

3. Switch to **Account Service** log:
   > *"Consumer received it, applied to Postgres, committed the offset only after the DB write succeeded."*

4. Show the **DB query result** (balance updated to 5250).

5. Open **Kafka UI → transactions topic → Messages**:
   > *"Here's the message in Kafka — keyed by accountId so all events for this customer stay ordered."*

---

#### Part 3 — Fraud Detection (5 min)
> "Now let me trigger the fraud detection logic."

1. In Postman → run the **Fraud Trigger** request with **7 iterations** in Collection Runner.

2. Switch to **Fraud Detection** log:
   > *"Within seconds, my Kafka Streams app detected more than 5 transactions in a 60-second window. It's using a tumbling window with `exactly_once_v2` semantics."*

3. Open **Kafka UI → alerts topic**:
   > *"The alert is published to this topic. In production, a notification service would consume it and call PagerDuty or send an SMS."*

---

#### Part 4 — Code Walkthrough (3 min)
Open IntelliJ and walk through:

1. **`KafkaProducerConfig.java`** — point to:
   - `acks=all`
   - `enable.idempotence=true`
   - `retries=Integer.MAX_VALUE`
   > *"This combo gives me exactly-once writes from the producer side."*

2. **`AccountService.java`** — point to:
   - `if (txnLogRepo.existsById(...)) return;`
   - `@Transactional` annotation
   > *"This is my consumer-side idempotency. Even if Kafka redelivers, the dedup table prevents double-apply."*

3. **`FraudDetectionTopology.java`** — point to:
   - `windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(60)))`
   - `processing.guarantee=exactly_once_v2`
   > *"This is the heart of the fraud detection — a tumbling window aggregation with exactly-once semantics."*

---

#### Part 5 — Resilience Demo (3 min) — the WOW moment
> "Let me show you what happens when a service goes down."

1. **Stop Account Service** in IntelliJ.
2. Send 3 transactions via Postman — note Postman returns 202 successfully.
3. In **Kafka UI → Consumer Groups → account-service-group** → show **lag = 3**.
4. **Restart Account Service**.
5. Lag drops to 0 — all 3 transactions are now processed.
   > *"This proves Kafka's durability. The producer doesn't care about consumer downtime. Messages persist on disk and are redelivered when the consumer comes back — and my consumer is idempotent, so even duplicate deliveries are safe."*

---

## 6. What to Showcase

### Technical Strengths to Emphasize

| Strength | Talking Point |
|---|---|
| **Durability** | `acks=all` + replication = no data loss on broker failure |
| **Ordering** | Keyed by `accountId` → strict per-account ordering |
| **Idempotency** | Producer (`enable.idempotence=true`) + Consumer (`transaction_log` dedup) |
| **Exactly-once** | `exactly_once_v2` in Kafka Streams |
| **Parallelism** | 3 partitions + `concurrency=3` consumer threads |
| **Fault tolerance** | Manual offset commit only after DB write |
| **Real-time analytics** | Stateful tumbling window in Kafka Streams |
| **Operational maturity** | Kafka UI for observability, DB introspection, lag monitoring |

### Banking-Specific Concerns Addressed

- **No money lost** → idempotent producer + transactional DB update
- **No double-charging** → consumer dedup table
- **Audit trail** → all transactions persisted in Kafka + `transaction_log` table
- **Replay capability** → can reset offsets to replay history through a new fraud rule
- **Compliance-ready** → Kafka logs are immutable and time-ordered

### Engineering Principles Demonstrated

- Event-driven architecture
- Separation of concerns (each service has one job)
- Resilient by default (services can crash and recover)
- Observable (logs + metrics points + Kafka UI)
- Testable (each module independently buildable)

---

## 7. 20 Interview Questions & Answers

### Architecture & Design

**Q1. Why did you choose Kafka over RabbitMQ for this project?**
Kafka offers **higher throughput** (millions of msg/sec), **durable replayable logs** (critical for audit trails in banking), and **native stream processing** (Kafka Streams for fraud detection). RabbitMQ is great for traditional task queues but lacks Kafka's replayability and Streams API.

---

**Q2. Why did you key messages by `accountId`?**
To guarantee **strict ordering of events per account** while still parallelizing across accounts. All events for `ACC1001` hash to the same partition and are consumed sequentially. Different accounts go to different partitions, enabling horizontal scaling.

---

**Q3. What's the trade-off of partitioning by accountId?**
**Hot partitions** — if one account is extremely active, that partition becomes a bottleneck. Mitigation: composite keys (e.g., `accountId + day`) for skewed workloads, or accept the trade-off if no account is hot in practice.

---

**Q4. Why 3 partitions specifically?**
3 partitions = 3 parallel consumers in the group = good throughput for the demo. In production, I'd size partitions based on **peak TPS / per-partition throughput** (~10 MB/s) + room for future scaling — usually 10–50 partitions for medium-volume topics.

---

### Reliability & Guarantees

**Q5. How do you guarantee no message loss?**
Three things together:
1. **Producer:** `acks=all` (leader + ISR ack)
2. **Broker:** `replication.factor ≥ 2` + `min.insync.replicas ≥ 2`
3. **Consumer:** manual offset commit **after** successful DB write
Even if a broker/consumer crashes, the message is recoverable.

---

**Q6. How do you handle duplicate messages?**
**Two layers:**
1. **Producer side:** `enable.idempotence=true` → broker dedups by `(PID, sequence number)`
2. **Consumer side:** `transaction_log` table — if `transactionId` exists, skip processing
Together, this gives effectively exactly-once semantics for the DB.

---

**Q7. What's the difference between idempotence and exactly-once?**
**Idempotence** = producer doesn't write duplicates to a single partition on retry.
**Exactly-once** = end-to-end no duplicates AND no loss, across multiple partitions/topics, including consumer DB writes. Requires idempotence + transactions + consumer-side dedup.

---

**Q8. What happens if the Account Service crashes mid-transaction?**
- The Kafka offset is **not committed** (manual ack).
- On restart, Kafka **redelivers** the same message.
- The consumer checks `transaction_log` — if the transaction is already there (DB commit succeeded before crash), it **skips**. Otherwise, it processes fresh.
- **Net result:** no loss, no duplicates.

---

### Performance & Scaling

**Q9. How would you scale this to 1 million transactions per second?**
- Increase **partitions** to 50–100 to enable more consumer parallelism
- Add **brokers** to spread partition leadership and disk I/O
- Increase **producer `batch.size`** and `linger.ms` for batching
- Enable **compression** (`snappy`/`zstd`)
- Scale **consumers horizontally** (up to partition count)
- Move account service to **async DB writes** with connection pooling
- Use **tiered storage** for older partitions

---

**Q10. What metrics would you monitor in production?**
- **Consumer lag** per partition (most important)
- **Producer request latency** + error rate
- **Broker disk usage** + ISR shrinkage
- **Under-replicated partitions** (data at risk)
- **JVM GC pauses** on consumers
- **DB connection pool exhaustion** on account-service
Tools: Prometheus + Grafana + Burrow (lag) + JMX exporters.

---

**Q11. How would you tune the producer for low latency vs high throughput?**
**Low latency:** `linger.ms=0`, small batch size, `acks=1` (less durable).
**High throughput:** `linger.ms=10-20`, large batches (32KB+), compression on, async send, more in-flight requests.
For banking, I went with a balance: `linger.ms=10` + `acks=all` + idempotence.

---

### Fraud Detection (Kafka Streams)

**Q12. Why Kafka Streams instead of writing a plain consumer?**
Kafka Streams provides:
- **Stateful processing** (RocksDB + changelog topics) → I don't manage state manually
- **Windowed aggregations** out of the box
- **Exactly-once semantics** end-to-end (`exactly_once_v2`)
- **Auto-rebalancing** of partitions and state
Building this from scratch with a plain consumer would take weeks.

---

**Q13. What window types did you consider for fraud detection?**
- **Tumbling** (used) → non-overlapping 60s windows. Simple, predictable.
- **Hopping** → overlapping windows; smoother detection but more events emitted.
- **Sliding** → exact "last 60s from each event"; precise but more expensive.
- **Session** → activity-based, no fixed size; better for "burst" detection.
I chose tumbling for simplicity in the demo; in production, I'd consider hopping for smoother coverage.

---

**Q14. What if the fraud detection service crashes mid-window?**
The window state is stored in **RocksDB locally** AND backed by a Kafka **changelog topic**. On restart, Kafka Streams replays the changelog to rebuild state — **no state loss**. With `exactly_once_v2`, no duplicate alerts either.

---

### Operational

**Q15. How would you deploy this to production?**
- Each service → Docker image → deployed to Kubernetes
- Kafka → managed (Confluent Cloud / MSK) or self-hosted with Strimzi operator
- PostgreSQL → managed (RDS / Cloud SQL) with read replicas
- Schema Registry for Avro contracts
- CI/CD: build with Maven → push to ECR → ArgoCD deploy
- Observability: Prometheus + Grafana + Loki for logs + Jaeger for tracing

---

**Q16. How would you handle a schema change to the Transaction message?**
- Use **Avro + Confluent Schema Registry** instead of raw JSON
- Enforce **BACKWARD compatibility** → consumers can read old messages even after producer schema updates
- New optional fields only (no breaking changes)
- Test compatibility in CI before deploying producers

---

**Q17. What if you needed to replay last month's transactions through a new fraud rule?**
1. Deploy a **new fraud-detection service** with a **different `application.id`** (so it gets its own state)
2. Configure it with `auto.offset.reset=earliest`
3. It rebuilds state from the beginning of the topic
4. Once caught up, it operates in parallel with the old one — or you cut over
Kafka's **log retention** makes this possible — that's why I'd set retention to 30+ days for `transactions`.

---

**Q18. How would you implement a Dead Letter Queue?**
- Wrap deserialization in `ErrorHandlingDeserializer` (already done)
- For business failures, use Spring Kafka's `DefaultErrorHandler` with a `DeadLetterPublishingRecoverer`
- Route failed messages to `transactions.DLT` with enriched headers (exception, stacktrace, offset, partition)
- Build a separate consumer to read the DLT for manual inspection or replay
- Alert on DLT growth via Prometheus

---

### Security & Banking Concerns

**Q19. How would you secure this in a banking environment?**
- **Authentication:** SASL/SCRAM or mTLS between clients and Kafka
- **Authorization:** ACLs per topic per service
- **Encryption in transit:** TLS on all Kafka listeners
- **Encryption at rest:** disk encryption + envelope encryption for sensitive fields (PII/PCI)
- **Audit:** Kafka access logs + immutable transaction log topic
- **Compliance:** retention policies aligned with GDPR/RBI guidelines

---

**Q20. What was the hardest part of building this and how did you solve it?**
> Be honest and specific. Example answer:

The trickiest part was getting **end-to-end idempotency** right. Producer-side idempotence wasn't enough — I needed consumer-side dedup too, because the consumer could crash after DB write but before offset commit, causing Kafka to redeliver. I solved it by writing the offset and balance update **in the same DB transaction** using a `transaction_log` table — so the dedup check and balance update are atomic. This is essentially the **transactional outbox pattern** applied in reverse.

---

## Bonus: Closing Statement for the Interviewer

> *"This project showed me how nuanced production messaging really is. The Kafka APIs are simple, but the trade-offs — durability vs latency, ordering vs throughput, exactly-once vs simplicity — these are the real challenges. I'm comfortable reasoning about all of them and would love to apply this to real banking workloads."*

That's the kind of close that gets remembered.

---

## Quick Reference Cheat Sheet

| Topic | Key Config | Value |
|---|---|---|
| Durability | `acks` | `all` |
| Producer dedup | `enable.idempotence` | `true` |
| Consumer commit | `enable.auto.commit` | `false` (manual) |
| Streams EOS | `processing.guarantee` | `exactly_once_v2` |
| Replication | `replication.factor` | 3 |
| ISR safety | `min.insync.replicas` | 2 |
| Compression | `compression.type` | `snappy` |
| Window size | `TimeWindows.ofSize` | 60s tumbling |
| Fraud threshold | `count > N` | 5 |

---

**Last updated:** 2026-05-27
**Project root:** `D:\Temp\kafka\SecureBank\`
