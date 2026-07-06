# SecureBank — Complete Reference

> One document. Every component. Every flow. Every config. Everything you need to defend this project in an interview.

---

## Table of Contents

1. [Project at a Glance](#1-project-at-a-glance)
2. [High-Level Architecture](#2-high-level-architecture)
3. [Component-by-Component Deep Dive](#3-component-by-component-deep-dive)
   - 3.1 [Infrastructure (Docker)](#31-infrastructure-docker-compose)
   - 3.2 [Avro Schemas](#32-avro-schemas-shared-contract)
   - 3.3 [transaction-service](#33-transaction-service-producer)
   - 3.4 [account-service](#34-account-service-consumer--postgres)
   - 3.5 [fraud-detection](#35-fraud-detection-kafka-streams)
   - 3.6 [PostgreSQL Schema](#36-postgresql-schema)
4. [End-to-End Flow: What Happens When You Hit the Endpoint](#4-end-to-end-flow-what-happens-when-you-hit-the-endpoint)
   - 4.1 [The Happy Path (in 12 steps)](#41-the-happy-path-in-12-steps)
   - 4.2 [Sequence Diagram](#42-sequence-diagram)
   - 4.3 [Failure Path: Unknown Account → DLT](#43-failure-path-unknown-account--dlt)
   - 4.4 [Fraud Path: 7 Rapid Transactions → HIGH_VELOCITY Alert](#44-fraud-path-7-rapid-transactions--high_velocity-alert)
   - 4.5 [Fraud Path: Large Single Transaction → LARGE_AMOUNT](#45-fraud-path-large-single-transaction--large_amount)
   - 4.6 [Fraud Path: Spike vs Rolling Average → SPEND_SPIKE](#46-fraud-path-spike-vs-rolling-average--spend_spike)
5. [Key Configuration Choices Explained](#5-key-configuration-choices-explained)
6. [Common Gotchas We Hit & Fixed](#6-common-gotchas-we-hit--fixed)
7. [Interview Talking Points by Topic](#7-interview-talking-points-by-topic)

---

# 1. Project at a Glance

**SecureBank** is a banking-grade event-driven platform built on Apache Kafka.

| Aspect | Value |
|---|---|
| Language / Framework | Java 17 + Spring Boot 3.2.5 |
| Wire format | Avro (Confluent Schema Registry, BACKWARD compat) |
| Kafka cluster | 3 brokers, replication-factor=3, min.insync.replicas=2 |
| Stream processing | Kafka Streams (exactly_once_v2) |
| Database | PostgreSQL 15 |
| Build | Maven multi-module + avro-maven-plugin |
| Tests | 16 — unit + slice (@WebMvcTest) + integration (EmbeddedKafka + H2) + Streams (TopologyTestDriver) |
| Infrastructure | Docker Compose (Zookeeper + 3× Kafka + Schema Registry + Postgres + Kafka UI) |

---

# 2. High-Level Architecture

```
  ┌──────────┐    POST /api/transactions    ┌──────────────────────┐
  │ Postman  │ ─────────────────────────►   │ transaction-service  │
  │ Client   │  (JSON body)                 │  REST + Avro Producer│
  └──────────┘                              │     :8081            │
                                            └──────────┬───────────┘
                                                       │ keyed by accountId
                                                       │ acks=all, idempotent
                                                       ▼
                                  ┌──────────────────────────────────┐
                                  │ Kafka cluster (3 brokers)        │
                                  │ ┌──────────┬──────────┬────────┐ │
                                  │ │ kafka1   │ kafka2   │ kafka3 │ │
                                  │ └────┬─────┴────┬─────┴────┬───┘ │
                                  │      └────RF=3,MIN_ISR=2──┘     │
                                  │                                  │
                                  │  Topic: transactions (3 part.)   │
                                  │  Topic: alerts        (3 part.)  │
                                  │  Topic: transactions.DLT         │
                                  │  Topic: _schemas (internal)      │
                                  └──────┬───────────────────┬───────┘
                                         │                   │
                ┌────────────────────────┘                   └────────────────────────┐
                ▼                                                                     ▼
   ┌────────────────────────┐                                       ┌──────────────────────────┐
   │ account-service        │                                       │ fraud-detection          │
   │  Avro Consumer + JPA   │                                       │  Kafka Streams (EOS v2)  │
   │  DefaultErrorHandler   │                                       │  3 layered rules         │
   │  → PostgreSQL          │                                       │  + RocksDB state store   │
   │  :8082                 │                                       │  :8083                   │
   └────────┬───────────────┘                                       └──────────┬───────────────┘
            │ on IllegalStateException → DLT                                   │
            ▼                                                                  ▼
   ┌─────────────────┐                                              ┌─────────────────┐
   │  Postgres       │                                              │  Topic: alerts  │
   │  (balances +    │                                              │  (3 partitions) │
   │  txn log)       │                                              └─────────────────┘
   │  :5432          │
   └─────────────────┘

                  ┌───────────────────────────────────────────┐
                  │ Confluent Schema Registry  :8091          │
                  │   stores Transaction.avsc + Alert.avsc    │
                  │   compatibility = BACKWARD                │
                  └───────────────────────────────────────────┘
                  ┌───────────────────────────────────────────┐
                  │ Kafka UI  :8090                           │
                  │   browse topics + messages + schemas      │
                  └───────────────────────────────────────────┘
```

---

# 3. Component-by-Component Deep Dive

## 3.1 Infrastructure (`docker-compose.yml`)

### What it spins up

| Service | Image | Container name | Host port | Why |
|---|---|---|---|---|
| ZooKeeper | `confluentinc/cp-zookeeper:7.6.0` | `securebank-zookeeper` | 2181 | Cluster coordination + leader election + broker registry. Required for the non-KRaft Confluent setup. |
| Kafka broker 1 | `confluentinc/cp-kafka:7.6.0` | `securebank-kafka1` | 29092 | One of three brokers. Holds one replica of each partition. |
| Kafka broker 2 | `confluentinc/cp-kafka:7.6.0` | `securebank-kafka2` | 29093 | Second broker. Provides fault tolerance. |
| Kafka broker 3 | `confluentinc/cp-kafka:7.6.0` | `securebank-kafka3` | 29094 | Third broker. With RF=3 + MIN_ISR=2, we tolerate ANY 1 broker failure. |
| Schema Registry | `confluentinc/cp-schema-registry:7.6.0` | `securebank-schema-registry` | 8091 → 8081 | Stores Avro schemas in the internal `_schemas` topic. Enforces BACKWARD compatibility on every register. |
| Kafka UI | `provectuslabs/kafka-ui:latest` | `securebank-kafka-ui` | 8090 | Web UI to browse topics, messages, consumer groups, and schemas. |
| PostgreSQL | `postgres:15` | `securebank-postgres` | 5432 | Account balances + transaction_log (idempotency dedup). |

### Critical broker settings (per broker)

```yaml
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3      # __consumer_offsets is also durable
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2          # required for exactly_once_v2
KAFKA_MIN_INSYNC_REPLICAS: 2                    # acks=all needs 2 replicas in ISR
KAFKA_DEFAULT_REPLICATION_FACTOR: 3
KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"        # avoid ghost topics on typos
```

### Critical Schema Registry settings

```yaml
SCHEMA_REGISTRY_LISTENERS: http://0.0.0.0:8081
SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS: PLAINTEXT://kafka1:9092,...
SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR: 3
SCHEMA_REGISTRY_SCHEMA_COMPATIBILITY_LEVEL: backward
```

**Talking point:** *"The cluster tolerates ANY one broker failing. Both `acks=all` and `min.insync.replicas=2` are meaningful — a 201 from my producer means the message is durable on at least 2 of 3 replicas."*

---

## 3.2 Avro Schemas (shared contract)

### `schemas/avro/Transaction.avsc`

This is the **single source of truth** for transaction events.

```json
{
  "type": "record",
  "namespace": "com.securebank.avro",
  "name": "Transaction",
  "fields": [
    { "name": "transactionId", "type": "string" },
    { "name": "accountId",     "type": "string" },
    { "name": "type",
      "type": { "type": "enum", "name": "TransactionType",
                "symbols": ["DEPOSIT", "WITHDRAW", "TRANSFER"] }
    },
    { "name": "amount",
      "type": { "type": "bytes", "logicalType": "decimal",
                "precision": 18, "scale": 2 }
    },
    { "name": "city",      "type": ["null", "string"], "default": null },
    { "name": "timestamp", "type": { "type": "long",
                                    "logicalType": "timestamp-millis" } }
  ]
}
```

What the Avro logical types buy you:
- `decimal(18,2)` → generates a `BigDecimal` field — no precision loss for monetary values
- `timestamp-millis` → generates a `java.time.Instant` field
- The enum → a Java enum (compile-time type safety)
- The union `["null","string"]` → nullable String for `city`

### `schemas/avro/Alert.avsc`

```json
{
  "type": "record",
  "namespace": "com.securebank.avro",
  "name": "Alert",
  "fields": [
    { "name": "accountId",     "type": "string" },
    { "name": "transactionId", "type": ["null", "string"], "default": null },
    { "name": "ruleType",
      "type": { "type": "enum", "name": "RuleType",
                "symbols": ["HIGH_VELOCITY", "LARGE_AMOUNT", "SPEND_SPIKE"] }
    },
    { "name": "reason",     "type": "string" },
    { "name": "count",      "type": "long" },
    { "name": "detectedAt", "type": { "type": "long",
                                     "logicalType": "timestamp-millis" } }
  ]
}
```

### How schemas become Java classes

The `avro-maven-plugin` in each module's `pom.xml` runs during `mvn generate-sources`:
1. Reads `schemas/avro/*.avsc`
2. Generates Java classes into `target/generated-sources/avro/com/securebank/avro/`
3. Maven adds that directory as a source root
4. IntelliJ picks them up after `Maven → Reload Project`

Generated classes:
- `com.securebank.avro.Transaction`
- `com.securebank.avro.TransactionType` (enum)
- `com.securebank.avro.Alert`
- `com.securebank.avro.RuleType` (enum)

**Talking point:** *"All three services consume the same Avro schemas — there's no class-level drift between producer and consumer. Add a nullable field to the schema with `default: null`, and the BACKWARD compatibility check at deploy time ensures consumers running the old schema continue to read fine."*

---

## 3.3 `transaction-service` (Producer)

### File-by-file

#### `TransactionRequest.java` — HTTP request DTO
- Plain POJO with Bean Validation annotations (`@NotBlank`, `@DecimalMin`, `@NotNull`)
- This is the **HTTP API contract** — clients POST JSON shaped like this
- **NOT** the Avro type. The controller maps this → Avro before publishing.
- Reason: clients shouldn't have to ship Avro libraries; HTTP stays JSON.

#### `TransactionController.java` — REST endpoint
- `POST /api/transactions`
- Validates input via `@Valid`
- Generates `transactionId` (UUID) if client didn't provide one
- Maps `TransactionRequest` → Avro `Transaction` using the generated builder
- Sends to Kafka topic `transactions`, **keyed by `accountId`** (ordering per account)
- **Blocks for up to 5 seconds** on `future.get(5, SECONDS)` waiting for broker ack
- Returns:
  - **`201 Created`** + partition + offset on broker ack → message is durable in ISR
  - **`503 Service Unavailable`** with status `TIMEOUT`/`FAILED`/`INTERRUPTED` on error
- Restores interrupt flag on `InterruptedException` (proper Java concurrency hygiene)

#### `KafkaProducerConfig.java` — Producer factory
Producer settings, every one with a reason:
- `acks=all` → durability (leader + all ISRs must ack)
- `enable.idempotence=true` → no duplicates on retry (producer ID + sequence numbers)
- `retries=Integer.MAX_VALUE` → keep retrying transient errors
- `max.in.flight.requests.per.connection=5` → throughput while preserving ordering (with idempotence on)
- `batch.size=32768` + `linger.ms=10` → batch messages for higher throughput
- `compression.type=snappy` → reduce wire payload
- `KafkaAvroSerializer` for value + `schema.registry.url` set → registers schema on first send

#### `application.yml`
- `server.port: 8081`
- `spring.kafka.bootstrap-servers: localhost:29092,localhost:29093,localhost:29094` (multi-broker resilient)
- `app.schema-registry-url: http://localhost:8091`

#### Tests
- `TransactionControllerTest.java` (3 tests): valid → 201 with partition/offset, invalid → 400, Kafka failure → 503

---

## 3.4 `account-service` (Consumer + Postgres)

### File-by-file

#### `Account.java` (JPA Entity)
- Maps to `accounts` table
- Fields: `accountId` (PK), `holderName`, `balance` (BigDecimal), `updatedAt`

#### `TransactionLog.java` (JPA Entity)
- Maps to `transaction_log` table
- Fields: `transactionId` (PK), `accountId`, `type`, `amount`, `processedAt`
- **This is the idempotency table** — stores every processed transaction by ID

#### `AccountRepository.java`, `TransactionLogRepository.java`
- Standard Spring Data JPA repositories

#### `AccountService.java` — Business logic
The function that **actually moves money**:
1. Check `transaction_log.existsById(transactionId)` → if yes, **skip** (idempotency)
2. Load account from DB (throws `IllegalStateException("Unknown account")` if missing)
3. Compute new balance based on `TransactionType`:
   - `DEPOSIT` → add amount
   - `WITHDRAW`/`TRANSFER` → subtract amount (throws `IllegalStateException("Insufficient funds")` if balance too low)
4. Save updated account AND insert transaction_log row in **same DB transaction** (`@Transactional`)
5. If anything throws inside the @Transactional method → the DB transaction rolls back AND the exception propagates to the Kafka consumer → DefaultErrorHandler kicks in

#### `TransactionConsumer.java` — Kafka listener
- **Two-line method**: `accountService.apply(event)`. That's it.
- No try/catch — all error handling is centralised in the container factory.
- Annotated with `@KafkaListener` pointing at the `transactions` topic.

#### `KafkaConsumerConfig.java` — Where the real machinery lives
Three big components configured here:

**1. ConsumerFactory** — reads from Kafka:
- `KafkaAvroDeserializer` (wrapped in `ErrorHandlingDeserializer`) → poison messages don't crash the consumer
- `specific.avro.reader=true` → returns generated `Transaction` class, not `GenericRecord`
- `enable.auto.commit=false` → commits managed by the framework
- `JsonDeserializer.TRUSTED_PACKAGES=com.securebank.account.model` (defense-in-depth even though type headers are off)

**2. DLT plumbing**:
- `dltProducerFactory` — separate producer with `acks=all` + idempotence — DLT writes themselves are durable
- `dltKafkaTemplate` — used by the recoverer to publish failed records
- `DefaultErrorHandler` wired with `DeadLetterPublishingRecoverer` → routes failures to `transactions.DLT`
- **Same partition preserved** in DLT (preserves per-account ordering for replay)
- `ExponentialBackOffWithMaxRetries(3)` with 1s/2s/4s backoff for transient errors
- `IllegalStateException` marked **not-retryable** → straight to DLT (business rule failures won't fix themselves on retry)

**3. ConcurrentKafkaListenerContainerFactory**:
- `AckMode.RECORD` → offset committed after each successful processing
- `concurrency=3` → 3 consumer threads (matches 3 partitions on `transactions` topic)
- Wired with the DefaultErrorHandler above

#### `application.yml`
- `server.port: 8082`
- `spring.datasource` → Postgres at `localhost:5432`
- `spring.jpa.hibernate.ddl-auto: validate` (schema is created by `init-db.sql`)
- `app.topic.transactions-dlt: transactions.DLT`
- `app.schema-registry-url: http://localhost:8091`

#### Tests
- `AccountServiceTest.java` (6 tests) — unit tests of `apply()`: deposit, withdraw, insufficient funds, unknown account, idempotency skip, transfer
- `TransactionFlowIntegrationTest.java` (2 tests) — full Spring + EmbeddedKafka + H2 + mock Schema Registry:
  - Happy path: send Avro Transaction → DB balance updated
  - DLT path: send for unknown account → asserts the message appears in `transactions.DLT` with exception headers

---

## 3.5 `fraud-detection` (Kafka Streams)

### File-by-file

#### `RollingStats.java` (state store value)
- Per-account `sum` + `count`
- Derived `getAverage()` method
- **`@JsonIgnore`** on `getAverage()` — Jackson won't try to serialize/deserialize the computed field
- Serialized as JSON (not Avro — internal to one service, no need for cross-service contract)

#### `FraudDetectionTopology.java` — The DAG
Three branches consume the same input stream and merge their outputs:

**Rule A — HIGH_VELOCITY** (windowed count)
```java
stream
  .groupByKey(Grouped.with(Serdes.String(), txnSerde))
  .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(60)))
  .count(Materialized.as("txn-count-per-account"))
  .toStream()
  .filter((windowedKey, count) -> count > countThreshold)
  .map(...emit HIGH_VELOCITY Alert)
```

**Rule B — LARGE_AMOUNT** (stateless filter)
```java
stream
  .filter((k, txn) -> txn.getAmount().compareTo(largeAmountThreshold) > 0)
  .map(...emit LARGE_AMOUNT Alert with txn.getTransactionId())
```

**Rule C — SPEND_SPIKE** (stateful transformer using a state store)
```java
stream
  .transformValues(
    () -> new SpikeDetectorTransformer(spikeMultiplier, spikeMinHistory),
    ROLLING_STATS_STORE)
  .filter((k, v) -> v != null);
```

The `SpikeDetectorTransformer` is the most interesting code in the project:
1. Load `RollingStats` from the state store (`rolling-stats-store`)
2. If `count >= 3` (minHistory): compute `avg`, check if incoming `amount > 5× avg` → emit alert
3. Update stats (`sum += amount`, `count++`)
4. Save back to store
5. Returns Alert or null

State store is backed by an internal **changelog topic** (`fraud-detection-app-rolling-stats-store-changelog`), making it fault tolerant. On restart, Kafka Streams replays the changelog to rebuild the local RocksDB store.

All 3 streams merge into one output:
```java
countAlerts.merge(largeAmountAlerts).merge(spikeAlerts)
  .to(alertsTopic, Produced.with(Serdes.String(), alertSerde));
```

#### `application.yml`
- `server.port: 8083`
- `spring.kafka.streams.properties.schema.registry.url: http://localhost:8091`
- `processing.guarantee: exactly_once_v2` → atomic commit across consumer offset + state store + output topic
- `commit.interval.ms: 1000`
- `num.stream.threads: 2`
- `state.dir: /tmp/kafka-streams/fraud-detection`
- `app.fraud.window-seconds: 60`
- `app.fraud.count-threshold: 5`
- `app.fraud.large-amount-threshold: 100000`
- `app.fraud.spike-multiplier: 5.0`
- `app.fraud.spike-min-history: 3`

#### Tests
- `FraudDetectionTopologyTest.java` (5 tests using `TopologyTestDriver` + mock Schema Registry):
  - 5 txns in window → no alert
  - 6+ txns in window → HIGH_VELOCITY alert
  - Single 200,000 txn → LARGE_AMOUNT alert
  - History of 100s then 10,000 → SPEND_SPIKE alert
  - History of 100s then 200 (only 2× avg) → no alert

---

## 3.6 PostgreSQL Schema

Set up by `init-db.sql` (runs once when the postgres container first starts):

```sql
CREATE TABLE accounts (
    account_id   VARCHAR(50) PRIMARY KEY,
    holder_name  VARCHAR(100) NOT NULL,
    balance      NUMERIC(18, 2) NOT NULL DEFAULT 0.00,
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE transaction_log (
    transaction_id VARCHAR(50) PRIMARY KEY,    -- idempotency key
    account_id     VARCHAR(50) NOT NULL,
    type           VARCHAR(20) NOT NULL,
    amount         NUMERIC(18, 2) NOT NULL,
    processed_at   TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Seed data:
INSERT INTO accounts (account_id, holder_name, balance) VALUES
    ('ACC1001', 'Alice Johnson', 5000.00),
    ('ACC1002', 'Bob Smith',     7500.00),
    ('ACC1003', 'Carol Davis',   2000.00);
```

Why `transaction_log` matters:
- It's the **idempotency layer** at the consumer side.
- Even if Kafka redelivers a message (e.g., because the consumer crashed before committing), `AccountService.apply()` checks `transaction_log.existsById(transactionId)` first and skips.
- Combined with `acks=all` on the producer and `min.insync.replicas=2`, this gives **effectively exactly-once** semantics for money movement.

---

# 4. End-to-End Flow: What Happens When You Hit the Endpoint

## 4.1 The Happy Path (in 12 steps)

You hit:
```http
POST http://localhost:8081/api/transactions
Content-Type: application/json

{
  "accountId": "ACC1001",
  "type": "DEPOSIT",
  "amount": 250.00,
  "city": "Mumbai"
}
```

Here's exactly what happens, step by step.

### Step 1 — HTTP request hits Tomcat
- Postman's TCP connection lands on **Tomcat embedded server** inside `transaction-service` (port 8081).
- Spring's `DispatcherServlet` routes the request to `TransactionController.publish(...)`.

### Step 2 — Bean Validation
- `@Valid` triggers Bean Validation on `TransactionRequest`.
- Checks: `accountId @NotBlank`, `type @NotNull`, `amount @DecimalMin("0.01")`.
- If validation fails → `400 Bad Request` returned immediately. We never touch Kafka.

### Step 3 — DTO to Avro mapping
```java
String txnId = req.getTransactionId() != null ? req.getTransactionId() : UUID.randomUUID().toString();

Transaction avroTxn = Transaction.newBuilder()
    .setTransactionId(txnId)
    .setAccountId(req.getAccountId())
    .setType(TransactionType.valueOf(req.getType().name()))
    .setAmount(req.getAmount())
    .setCity(req.getCity())
    .setTimestamp(Instant.now())
    .build();
```
We now hold an in-memory Avro `Transaction` object.

### Step 4 — Kafka send (the interesting part)

```java
kafkaTemplate.send(topic, req.getAccountId(), avroTxn).get(5, TimeUnit.SECONDS);
```

Behind the scenes, this triggers:
- **Partitioner**: `hash("ACC1001") % 3 = some partition` (let's say partition 1). All future events for ACC1001 hit partition 1.
- **Serializer chain**:
  - Key serializer: `StringSerializer` → encodes "ACC1001" as UTF-8 bytes
  - Value serializer: `KafkaAvroSerializer` → checks if schema is registered, if not registers it with Schema Registry, then writes a 5-byte magic prefix (0x00 + 4-byte schema ID) + Avro binary payload
- **Schema Registry roundtrip** (first time only): producer calls `POST http://localhost:8091/subjects/transactions-value/versions` with the Avro schema. Schema Registry stores it in `_schemas` topic, returns ID=1. Producer caches this for future sends.

### Step 5 — Broker write with `acks=all`
- Producer client sends a `ProduceRequest` to **partition 1's leader** (let's say kafka2).
- kafka2 writes to its local log.
- kafka2 waits for **at least** `min.insync.replicas - 1 = 1` follower (kafka1 or kafka3) to replicate the record.
- Once 2 replicas have it (leader + 1 follower) → kafka2 sends a ProduceResponse with offset.
- **If fewer than 2 ISR are alive** → broker returns `NOT_ENOUGH_REPLICAS`, producer retries.

### Step 6 — Producer future completes
- `KafkaTemplate.send()` returns a `CompletableFuture<SendResult<...>>`.
- The future completes with the record metadata (partition, offset).
- Our controller's `.get(5, SECONDS)` unblocks.

### Step 7 — HTTP response
```json
HTTP/1.1 201 Created
Content-Type: application/json

{
  "transactionId": "<uuid>",
  "status": "PERSISTED",
  "partition": 1,
  "offset": 42
}
```
Postman sees this. From the client's perspective, **the transaction is now durably committed across 2 of 3 replicas** — surviving any single broker failure.

### Step 8 — Consumer poll (parallel, asynchronous to client)
Meanwhile, `account-service` and `fraud-detection` are continuously polling the Kafka broker.

- `account-service` has 3 consumer threads (`concurrency=3`), each owning one partition.
- The thread owning partition 1 polls and fetches our record.

### Step 9 — Deserialization
- `KafkaAvroDeserializer` reads the 5-byte prefix (magic + schema ID).
- Looks up schema ID 1 in Schema Registry → gets the Avro schema (cached after first lookup).
- Deserializes the binary payload into a `com.securebank.avro.Transaction` object (because `specific.avro.reader=true`).

### Step 10 — Business logic in @Transactional method
`AccountService.apply(transaction)` runs:
1. `txnLogRepo.existsById("<uuid>")` → false (first time we've seen this txn)
2. `accountRepo.findById("ACC1001")` → returns the Account with balance 5000.00
3. Compute new balance: 5000.00 + 250.00 = 5250.00
4. `accountRepo.save(updatedAccount)` (Hibernate prepares UPDATE)
5. `txnLogRepo.save(new TransactionLog(...))` (Hibernate prepares INSERT)
6. `@Transactional` commits the DB transaction → UPDATE + INSERT execute atomically.

### Step 11 — Kafka offset commit
- The listener method returns normally.
- `AckMode.RECORD` triggers Spring to commit the offset for partition 1 to the broker (`__consumer_offsets` topic).
- **The order matters**: DB commit happens BEFORE Kafka offset commit. If the JVM dies between step 10 and step 11, Kafka redelivers → step 10 runs again, but `txnLogRepo.existsById(...)` returns true → we skip safely.

### Step 12 — Parallel: fraud-detection processes it
At the same time, the Kafka Streams `fraud-detection-app` consumer also pulls the same record from partition 1:
- Rule A: counts events in 60s window for ACC1001 → currently 1 → below threshold → no alert
- Rule B: amount 250 vs threshold 100,000 → not large → no alert
- Rule C: load RollingStats for ACC1001 → count is 0 (first time) → no spike check → store updated stats (sum=250, count=1)

No alerts emitted. End of flow.

---

## 4.2 Sequence Diagram

```
Postman    transaction-service    Kafka cluster    Schema Registry    account-service    Postgres    fraud-detection
   │              │                     │                  │                   │              │              │
   │ POST /api/.. │                     │                  │                   │              │              │
   ├─────────────►│                     │                  │                   │              │              │
   │              │                     │                  │                   │              │              │
   │              │ Bean Validation     │                  │                   │              │              │
   │              │ (passes)            │                  │                   │              │              │
   │              │                     │                  │                   │              │              │
   │              │ Map → Avro          │                  │                   │              │              │
   │              │                     │                  │                   │              │              │
   │              │ Register schema (1st time only)        │                   │              │              │
   │              ├─────────────────────┼─────────────────►│                   │              │              │
   │              │                     │                  │                   │              │              │
   │              │◄────────────────────┼──────────────────┤ schema ID = 1     │              │              │
   │              │                     │                  │                   │              │              │
   │              │ Send (Avro binary + ID=1)              │                   │              │              │
   │              ├────────────────────►│                  │                   │              │              │
   │              │                     │                  │                   │              │              │
   │              │                     │ Replicate to 2 ISR (RF=3, MIN=2)     │              │              │
   │              │                     │ ◄─── ack ────►                       │              │              │
   │              │                     │                  │                   │              │              │
   │              │◄────────────────────┤ ProduceResponse (partition=1, offset=42)            │              │
   │              │                     │                  │                   │              │              │
   │  201 Created │                     │                  │                   │              │              │
   │◄─────────────┤                     │                  │                   │              │              │
   │              │                     │                  │                   │              │              │
   │              │                     │ Consumer poll    │                   │              │              │
   │              │                     │◄─────────────────┼───────────────────┤              │              │
   │              │                     │                  │                   │              │              │
   │              │                     │ ──── record ─────┼──────────────────►│              │              │
   │              │                     │                  │                   │              │              │
   │              │                     │ Lookup schema ID=1                   │              │              │
   │              │                     ├─────────────────►│                   │              │              │
   │              │                     │◄─────────────────┤                   │              │              │
   │              │                     │                  │                   │              │              │
   │              │                     │                  │  Deserialize Avro │              │              │
   │              │                     │                  │                   │              │              │
   │              │                     │                  │ existsById?       │              │              │
   │              │                     │                  │                   ├─────────────►│              │
   │              │                     │                  │                   │◄─── false ───┤              │
   │              │                     │                  │                   │              │              │
   │              │                     │                  │ findById, update, │              │              │
   │              │                     │                  │ insert log @Tx    │              │              │
   │              │                     │                  │                   ├─────────────►│              │
   │              │                     │                  │                   │◄── commit ───┤              │
   │              │                     │                  │                   │              │              │
   │              │                     │ commit offset    │                   │              │              │
   │              │                     │◄─────────────────┼───────────────────┤              │              │
   │              │                     │                  │                   │              │              │
   │              │                     │ ──── record ─────┼───────────────────┼──────────────┼─────────────►│
   │              │                     │                  │                   │              │              │
   │              │                     │                  │                   │              │              │ run 3 rules,
   │              │                     │                  │                   │              │              │ update RocksDB
   │              │                     │                  │                   │              │              │ no alert emit
```

---

## 4.3 Failure Path: Unknown Account → DLT

```
Postman → transaction-service → Kafka topic transactions → account-service
                                                                  │
                                                                  ▼
                                                         AccountService.apply()
                                                                  │
                                                                  ▼
                                                  accountRepo.findById("ACC9999")
                                                                  │
                                                                  ▼  Optional.empty()
                                                  throw new IllegalStateException("Unknown account")
                                                                  │
                                                                  ▼
                                          @Transactional rolls back (no DB writes)
                                                                  │
                                                                  ▼
                                          Exception propagates to KafkaListener
                                                                  │
                                                                  ▼
                                          DefaultErrorHandler catches it
                                                                  │
                                                                  ▼ classify exception
                                          isNotRetryable(IllegalStateException) = TRUE
                                                                  │
                                                                  ▼
                                          DeadLetterPublishingRecoverer
                                                                  │
                                                                  ▼ publish to transactions.DLT
                                  ┌────────────────────────────────────────────────┐
                                  │ transactions.DLT record:                       │
                                  │   key  = "ACC9999" (preserved)                 │
                                  │   value = original Avro Transaction bytes      │
                                  │   headers:                                     │
                                  │     kafka_dlt-exception-message = "Unknown..." │
                                  │     kafka_dlt-original-topic    = "transactions"│
                                  │     kafka_dlt-original-partition = 0           │
                                  │     kafka_dlt-original-offset    = 17          │
                                  │     kafka_dlt-exception-stacktrace = ...       │
                                  └────────────────────────────────────────────────┘
                                                                  │
                                                                  ▼
                                          DefaultErrorHandler commits the offset
                                          (so we don't re-process this record)
                                                                  │
                                                                  ▼
                                          Consumer proceeds with next record
```

**Postman sees** `201 Created` — the producer successfully wrote to Kafka. The failure happens downstream, asynchronously. In a real bank, the DLT would be monitored, alerts fired, and a human/process would investigate.

---

## 4.4 Fraud Path: 7 Rapid Transactions → HIGH_VELOCITY Alert

You send 7 transactions for ACC1002 within 60 seconds.

```
Postman ─► transaction-service ─► topic transactions (key=ACC1002)
                                                │
              ┌─────────────────────────────────┴─────────────────────────────────┐
              ▼                                                                    ▼
       account-service                                                       fraud-detection
       (updates balance 7 times)                                                  │
                                                                                  ▼
                                                              KSTREAM-SOURCE reads each record
                                                                                  │
                                                                                  ▼
                                                              groupByKey("ACC1002")
                                                                                  │
                                                                                  ▼
                                                              windowedBy(60s tumbling window)
                                                                                  │
                                                                                  ▼
                                                              count() → 1,2,3,4,5,6,7 (one update per event)
                                                                                  │
                                                              KTable updates emit to downstream
                                                                                  │
                                                                                  ▼
                                                              filter(count > 5)
                                                                                  │
                                                                                  ▼ at count=6
                                                              build Alert(HIGH_VELOCITY, count=6, ...)
                                                                                  │
                                                                                  ▼
                                                              Produced.to("alerts", Avro)
                                                                                  │
                                                                                  ▼
                                                              topic alerts receives the alert
                                                                                  │
                                                                                  ▼
                                                              (Kafka UI shows it)
```

Note: the **count** field in the emitted Alert is 6, not 7. Reason: the threshold check is `count > 5`. The 6th event causes the count to flip from 5 to 6, which is the first value > 5 → alert fires. Subsequent events (7th, 8th, ...) keep firing alerts since count stays > 5 — this is the "no dedup" issue noted in the audit.

---

## 4.5 Fraud Path: Large Single Transaction → LARGE_AMOUNT

You send:
```json
{ "accountId": "ACC1003", "type": "DEPOSIT", "amount": 250000.00 }
```

```
transaction-service ─► topic transactions
                              │
                              ▼
                      fraud-detection
                              │
                              ▼ (parallel branches)
        ┌─────────────────────┼─────────────────────┐
        ▼                     ▼                     ▼
  Rule A: count           Rule B: filter      Rule C: spike
  count = 1 (single       amount > 100,000    history < 3
  event) → not > 5        ↓                   (cold-start) →
  → no alert              250,000 > 100,000   no check
                          → ALERT             → no alert
                              │
                              ▼
                  Alert(LARGE_AMOUNT, txnId=<uuid>, reason="Large transaction: 250000 exceeds 100000")
                              │
                              ▼
                          topic alerts
```

This fires **immediately** on a single event, unlike HIGH_VELOCITY which needs the window to accumulate.

---

## 4.6 Fraud Path: Spike vs Rolling Average → SPEND_SPIKE

You send 3 transactions of 100, then 1 of 10,000 — all for ACC1004.

```
Event 1: amount=100        Event 2: amount=100        Event 3: amount=100        Event 4: amount=10000
       │                          │                          │                          │
       ▼                          ▼                          ▼                          ▼
SpikeDetectorTransformer.transform("ACC1004", txn)
       │
       ▼
load RollingStats from state store
       │
       ▼
   ┌──────────────────────────────────────────┬──────────────────────────────────────────┐
   │ if count >= 3:                            │  else:                                   │
   │    avg = sum / count                      │    skip spike check (cold-start safety)  │
   │    if amount > 5 × avg:                   │                                          │
   │       emit SPEND_SPIKE alert              │                                          │
   └──────────────────────────────────────────┴──────────────────────────────────────────┘
                              │
                              ▼
                  update RollingStats (sum += amount, count++)
                              │
                              ▼
                  store.put("ACC1004", stats)  ← writes to RocksDB + changelog topic
```

State progression for ACC1004:

| Event | Incoming amount | Stats before | Check fires? | Stats after | Alert? |
|---|---|---|---|---|---|
| 1 | 100 | count=0 | no (count < 3) | sum=100, count=1 | no |
| 2 | 100 | count=1 | no (count < 3) | sum=200, count=2 | no |
| 3 | 100 | count=2 | no (count < 3) | sum=300, count=3 | no |
| 4 | 10000 | count=3, avg=100, threshold=500 | YES (10000 > 500) | sum=10300, count=4 | **SPEND_SPIKE** |

The Alert carries:
```
accountId      = "ACC1004"
transactionId  = "<uuid-of-spike-event>"
ruleType       = SPEND_SPIKE
reason         = "Spike: 10000 > 5.0x rolling avg 100.00"
count          = 1
detectedAt     = <now>
```

---

# 5. Key Configuration Choices Explained

## Why `acks=all` AND `enable.idempotence=true`

`acks=all` alone gives durability but allows **duplicates** on retry (the broker may have committed but failed to ack the producer, who then retries). `enable.idempotence=true` adds a producer ID + sequence number to each message — the broker dedups on the server side. **Together** they give exactly-once delivery from producer to broker.

## Why `min.insync.replicas=2` with RF=3

With `acks=all`, the broker waits for `min.insync.replicas` to confirm. With RF=3, MIN_ISR=2 means:
- Leader + 1 follower must ack → message is durable on 2 of 3 replicas
- We can lose 1 broker without losing data
- We can also lose 1 broker without losing **availability** (still have 2 ISRs)

If MIN_ISR were 1, we'd allow writes that only landed on the leader → losing the leader == losing data.
If MIN_ISR were 3, we'd lose **availability** the moment any single broker went down (no writes accepted).

2 of 3 is the sweet spot.

## Why `AckMode.RECORD` not `MANUAL_IMMEDIATE`

Originally we used `MANUAL_IMMEDIATE` and called `ack.acknowledge()` after each DB write. After Issue #4 we let Spring's `DefaultErrorHandler` manage commits. Reasoning:
- `RECORD` mode: Spring commits after each successful method return
- On exception: error handler retries N times, then routes to DLT, then commits (so we don't re-process the failed record)
- Same correctness guarantee (commit only after success or DLT), simpler code

## Why `IllegalStateException` is marked NOT retryable

Business rule violations (insufficient funds, unknown account) **will fail the same way on retry**. Retrying 3 times wastes 7 seconds and blocks the partition. Route them straight to DLT.

`SQLException`, `ConnectionException`, etc. are different — they may resolve on retry (DB blip). Those get the 3× exponential backoff.

## Why `exactly_once_v2` in Kafka Streams

Streams uses transactions: consumer offset + state store + output topic write are all committed atomically. If the JVM dies mid-processing, on restart Streams either:
- Sees a committed transaction → state is fully applied, move on
- Sees an aborted/incomplete transaction → state is rolled back, reprocess the input from the last committed offset

This eliminates duplicate alerts even under failure.

## Why partition key = `accountId`

Three reasons:
1. **Ordering**: all events for one account land on the same partition → consumer sees them in order
2. **State store routing in Streams**: `groupByKey` for windowed count and the spike detector both rely on this routing — all events for ACC1001 go to the same Streams task, which holds the state for ACC1001
3. **Consumer parallelism**: different accounts go to different partitions → 3 consumer threads can process 3 accounts in parallel

Trade-off: hot accounts = hot partitions. Acceptable for a demo, addressable with composite keys (`accountId + day`) in production.

## Why `RollingStats` stays JSON while wire format is Avro

`RollingStats` is **internal state** — only the fraud-detection service ever reads or writes it. There's no cross-service contract. JSON is easier to debug (you can read it directly in the changelog topic). Avro would add complexity without compatibility value because there's only one producer + one consumer + same JVM.

The choice illustrates the principle: **Avro for shared contracts, JSON (or whatever) for internal state**.

---

# 6. Common Gotchas We Hit & Fixed

| Bug | Symptom | Root cause | Fix |
|---|---|---|---|
| Image pull failed | `unable to get image confluentic/cp-zookeeper` | Typo: `confluentic` vs `confluentinc` | Fix spelling in `docker-compose.yml` |
| Cannot find symbol | `com.securebank.avro.Transaction` shows red | Avro classes not generated; IntelliJ stale | Run `mvn clean compile`, then Maven Reload in IntelliJ |
| Streams crashes on startup | `One or more source topics were missing during rebalance` | Topics weren't created before Streams started | Create topics first, then start services |
| MalformedURLException with garbled URL | URL like `h0t0t0p0:0/0/0...` | `application.yml` saved as UTF-16 LE; Spring reads bytes as UTF-8 → null chars interleaved | Re-save YAML as UTF-8 (without BOM) |
| NOT_ENOUGH_REPLICAS | Producer keeps retrying | Topic created with RF=1 but cluster requires `min.insync.replicas=2` | Delete topic + recreate with `--replication-factor 3` |
| Schema retrieval error in Streams | `Error retrieving Avro schema for id 1` | Schema Registry URL not injected into Streams config | Set `spring.kafka.streams.properties.schema.registry.url` |
| UnrecognizedPropertyException: "average" | RollingStats deserialization fails | Jackson serializes the computed `getAverage()` field, can't deserialize it back | `@JsonIgnore` on `getAverage()`, wipe state directory + changelog topic |
| Postgres FATAL on startup | `invalid value for parameter "TimeZone":"Asia/Calcutta"` | Windows reports legacy IANA name; Postgres rejects | Add `-Duser.timezone=UTC` VM option + `TZ=UTC` in docker-compose |

**Talking point:** *"I learned more debugging this than building it. UTF-16 corruption breaking property injection, RF=1 vs min.insync.replicas=2 mismatch, Jackson silently serializing computed getters — these are the kinds of bugs you only see in production."*

---

# 7. Interview Talking Points by Topic

## "Walk me through your project"

> *"I built SecureBank — a 3-service event-driven banking platform on Apache Kafka. There's a transaction REST service that acts as a producer, an account service that consumes events and updates Postgres, and a fraud-detection service using Kafka Streams. Wire format is Avro with Confluent Schema Registry, the cluster has 3 brokers with replication factor 3 and min.insync.replicas=2, errors flow into a Dead Letter Topic, and the fraud detection has 3 layered rules including a stateful rolling-average detector. 16 tests covering unit, slice, integration, and Streams topology layers."*

## "What did you find most challenging?"

> *"Getting end-to-end exactly-once right. Producer-side idempotence alone wasn't enough — the consumer could crash after DB write but before offset commit, causing Kafka to redeliver. I solved it with consumer-side dedup via a `transaction_log` table, with the balance update and the log insert in the same DB transaction. Combined with the idempotent producer and acks=all, this gives effectively exactly-once for money movement."*

## "How do you handle schema evolution?"

> *"Avro with Confluent Schema Registry in BACKWARD compatibility mode. The single source of truth lives in `schemas/avro/*.avsc`, and avro-maven-plugin generates Java classes for all three services from those files at build time — no class-level drift between producer and consumer. New nullable fields with defaults are safe to add; the registry rejects incompatible changes at deploy time. For testing I use `mock://test` URLs which spin up an in-process MockSchemaRegistryClient — tests run offline."*

## "How do you handle failures?"

> *"Three layers. At the broker level, RF=3 + min.insync.replicas=2 tolerates one broker failure with no data loss. At the consumer level, Spring's DefaultErrorHandler classifies exceptions: business rule violations like insufficient funds are not retryable — they go straight to a Dead Letter Topic with full exception headers preserved. Transient infra errors retry 3 times with exponential backoff before going to DLT. At the HTTP level, the producer waits up to 5 seconds for Kafka ack and returns 503 if the broker doesn't confirm — clients know whether the transaction is durable before they get a success status."*

## "What would you improve next?"

> *"Three things. First, alert deduplication — right now a single fraud incident can fire up to 8 alerts because the three rules don't dedup against each other. Second, the HTTP producer blocks a Tomcat thread for up to 5 seconds — I'd refactor to DeferredResult to release the request thread. Third, retry topics for non-blocking retries on transient errors instead of in-process blocking retries that hold the partition. Those are scope decisions, not unknowns."*

## "What does `exactly-once` actually mean here?"

> *"It's three pieces working together: producer idempotence (no duplicates on retry via producer ID + sequence number), `acks=all` with min.insync.replicas=2 (durable across 2 replicas), and consumer-side dedup via the transaction_log table (skip if transactionId already processed). The Streams app uses processing.guarantee=exactly_once_v2 which atomically commits consumer offsets + state store updates + output topic writes."*

## "Why Kafka over RabbitMQ?"

> *"Three reasons specifically for banking: durable replayable log — we can audit and replay 30 days of transactions through a new fraud rule. Native stream processing with Kafka Streams — stateful windowed aggregations are first-class. And throughput — banks process millions of events per day and Kafka's append-only log scales linearly. RabbitMQ is great for traditional task queues but lacks replayability and built-in stream processing."*

## "What's the role of Schema Registry?"

> *"It's the contract between producers and consumers. Producers register schemas when they send; the registry rejects incompatible changes based on the configured compatibility mode — BACKWARD in my case. Each Kafka message carries a 5-byte schema ID prefix, so consumers can look up the exact schema they need. Consumers cache schemas after first lookup. The registry stores schemas in a compacted Kafka topic so it's itself durable and version-tracked. Without it, JSON-based pipelines drift silently — fields get renamed, types change, and bugs surface only in production."*

---

## Quick Reference Cheat Sheet

| Concept | Where it lives | Value |
|---|---|---|
| Durability | producer `acks` | `all` |
| No duplicate producer writes | `enable.idempotence` | `true` |
| Min replicas in sync | broker `min.insync.replicas` | `2` |
| Replica count per partition | topic `replication-factor` | `3` |
| Partitions per topic | topic `partitions` | `3` |
| Consumer parallelism | container factory `concurrency` | `3` |
| Manual offset commit | `enable.auto.commit` | `false` |
| Offset commit timing | `AckMode` | `RECORD` (commit after each success) |
| Retry policy | DefaultErrorHandler backoff | 1s / 2s / 4s |
| Not-retryable exceptions | `addNotRetryableExceptions` | `IllegalStateException` |
| Streams guarantee | `processing.guarantee` | `exactly_once_v2` |
| Window size | `TimeWindows.ofSizeWithNoGrace` | 60s |
| Velocity threshold | `count >` | 5 |
| Large amount threshold | filter | 100,000 |
| Spike multiplier | spike rule | 5× rolling avg |
| Min history before spike rule activates | spike rule | 3 transactions |
| Wire format | producer/consumer serdes | Avro |
| Schema Registry compat mode | broker-level | `BACKWARD` |
| State store name | fraud-detection | `rolling-stats-store` |
| State backing | implicit | RocksDB + changelog topic |

---

## Project File Tree (For Reference)

```
SecureBank/
├── docker-compose.yml                       7 containers
├── init-db.sql                              Postgres seed
├── pom.xml                                  Parent multi-module
├── README.md                                Quick start
├── PROJECT_GUIDE.md                         Interview prep
├── Actual Code based readme.md              Honest audit
├── Changes made to improve the project.md   Changelog of all fixes
├── SECUREBANK_COMPLETE_REFERENCE.md         (this file)
├── schemas/
│   └── avro/
│       ├── Transaction.avsc
│       └── Alert.avsc
├── transaction-service/                     port 8081 — REST + Avro producer
│   ├── pom.xml
│   └── src/main/java/com/securebank/transaction/
│       ├── TransactionServiceApplication.java
│       ├── controller/TransactionController.java
│       ├── config/KafkaProducerConfig.java
│       └── model/TransactionRequest.java
├── account-service/                         port 8082 — consumer + JPA + DLT
│   ├── pom.xml
│   └── src/main/java/com/securebank/account/
│       ├── AccountServiceApplication.java
│       ├── consumer/TransactionConsumer.java
│       ├── service/AccountService.java
│       ├── repository/{Account,TransactionLog}Repository.java
│       ├── model/{Account,TransactionLog}.java
│       └── config/KafkaConsumerConfig.java
└── fraud-detection/                         port 8083 — Kafka Streams
    ├── pom.xml
    └── src/main/java/com/securebank/fraud/
        ├── FraudDetectionApplication.java
        ├── streams/FraudDetectionTopology.java
        └── model/RollingStats.java
```

---

## Final Pitch

> *"This started as a learning project but I treated it like a real product. I built it, audited it honestly, found 8 weaknesses, prioritized them, and fixed them in order — from a single-broker setup to a fault-tolerant 3-broker cluster, from silent error-swallowing to a proper DLT pattern, from a trivial counter to a stateful fraud detector, from JSON drift to Avro with Schema Registry. The journey itself taught me more than the destination."*

That's how you talk about it.

Good luck. Go get that offer.
