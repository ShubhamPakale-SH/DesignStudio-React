# Changes Made to Improve the Project

> A running changelog of every fix applied to harden SecureBank, what was wrong before, and what problem each change solved.
>
> Use this file to **answer the inevitable interview question:** *"What did you improve and why?"*

---

## Index

| # | Change | Status | Score Impact |
|---|---|---|---|
| 1 | Convert single-broker Kafka to 3-broker cluster | ✅ Done | +0.5 overall |
| 2 | Add 11-test suite (unit + slice + integration + Streams) | ✅ Done | +0.5 overall |
| 3 | Restrict `JsonDeserializer.TRUSTED_PACKAGES` from `*` to model package | ✅ Done | +0.1 overall, big credibility win |
| 4 | Replace silent ack with `DefaultErrorHandler` + DLT (`transactions.DLT`) | ✅ Done | +0.5 overall — also resolves Issue #5 |
| 5 | Replace trivial count rule with 3 layered fraud rules (HIGH_VELOCITY + LARGE_AMOUNT + SPEND_SPIKE) | ✅ Done | +0.3 overall |
| 6 | Synchronous producer ack: `202 Accepted` → `201 Created` (with partition/offset) or `503 Service Unavailable` | ✅ Done | +0.2 overall |
| 7 | Confluent Schema Registry + Avro schemas + generated types across all 3 services | ✅ Done | +0.5 overall |

---

## Change #1 — Convert single Kafka broker to a 3-broker cluster

**Date applied:** 2026-05-31
**Severity before:** 🔴 Critical
**Status:** ✅ Resolved

### What was wrong

The original `docker-compose.yml` ran **a single Kafka broker** with replication factor 1 for every topic — internal (`__consumer_offsets`, `__transaction_state`) and user-created (`transactions`, `alerts`):

```yaml
KAFKA_BROKER_ID: 1
KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
KAFKA_MIN_INSYNC_REPLICAS: 1
KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
```

And topic creation commands used `--replication-factor 1`.

### The problem this caused

| Claim in docs | Reality with single broker |
|---|---|
| "`acks=all` for durability" | `acks=all` with 1 replica = `acks=1`. Meaningless. |
| "ISR shrinkage protects writes" | ISR has only 1 member. Nothing to shrink. |
| "Leader failover on broker death" | No followers exist to be promoted. |
| "`min.insync.replicas=2`" | Project ran with value `1`. |
| "Tolerates broker failure" | Single broker = SPOF. Lose it, lose everything. |

In an interview, *every* durability talking point would collapse the moment the interviewer asked *"What's your replication factor?"* — the honest answer was **1**, which invalidated the rest of the conversation.

### What was changed

#### 1. `docker-compose.yml` — full rewrite of the Kafka section

Replaced the single `kafka` service with **three brokers**: `kafka1`, `kafka2`, `kafka3`, each with:

- Unique `KAFKA_BROKER_ID` (1, 2, 3)
- Internal hostname (`kafka1:9092`, `kafka2:9092`, `kafka3:9092`) for inter-broker traffic over the Docker network
- Unique host port (`localhost:29092`, `29093`, `29094`) for client connections
- Shared Zookeeper (`zookeeper:2181`) for metadata + leader election
- Updated cluster-wide settings:
  ```
  KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
  KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
  KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
  KAFKA_MIN_INSYNC_REPLICAS: 2
  KAFKA_DEFAULT_REPLICATION_FACTOR: 3
  KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
  ```

#### 2. Disabled auto-create-topics

`KAFKA_AUTO_CREATE_TOPICS_ENABLE` flipped from `true` to `false`. Side benefit: typos in topic names now fail loudly instead of silently creating ghost topics.

#### 3. Kafka UI now knows about all 3 brokers

```yaml
KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka1:9092,kafka2:9092,kafka3:9092
```

#### 4. All 3 service `application.yml` files updated

`bootstrap-servers` changed from:
```yaml
bootstrap-servers: localhost:29092
```
to:
```yaml
bootstrap-servers: localhost:29092,localhost:29093,localhost:29094
```

Applied to:
- `transaction-service/src/main/resources/application.yml`
- `account-service/src/main/resources/application.yml`
- `fraud-detection/src/main/resources/application.yml`

Client passes multiple bootstrap servers so connection works even if 1 or 2 brokers are unreachable on startup.

#### 5. Topic creation commands updated

`README.md` and `PROJECT_GUIDE.md` now create topics with `--replication-factor 3`:
```bash
docker exec securebank-kafka1 kafka-topics \
  --bootstrap-server kafka1:9092 \
  --create --topic transactions --partitions 3 --replication-factor 3
```

Note the container name change too: `securebank-kafka` → `securebank-kafka1` (one of the 3 brokers).

#### 6. Added an ISR verification step

```bash
docker exec securebank-kafka1 kafka-topics \
  --bootstrap-server kafka1:9092 --describe --topic transactions
```

This confirms every partition has `Isr: 1,2,3` after topic creation.

#### 7. Documentation cleanup

- `README.md`: services table now lists 3 broker ports + a "Cluster" callout explaining RF=3 and MIN_ISR=2.
- `PROJECT_GUIDE.md`: cheat sheet now shows `RF=3`, `min.insync.replicas=2` (no longer "prod vs demo").
- `Actual Code based readme.md`: Issue #1 marked resolved, scores bumped.

### What this fix unlocked

| Capability | Before | After |
|---|---|---|
| Tolerate 1 broker failure | ❌ No | ✅ Yes (RF=3 + MIN_ISR=2) |
| `acks=all` is meaningful | ❌ Theoretical | ✅ Real (waits for 2 replicas) |
| ISR concept demonstrable | ❌ Not really | ✅ Show ISR shrink/grow live |
| Leader election demoable | ❌ Impossible | ✅ Kill kafka1, see leader move |
| Producer auto-reconnects on broker loss | ❌ Untestable | ✅ Demoable end-to-end |

### Interview talking points this fix unlocks

**Before:** *"My cluster has 1 broker. In production I'd use 3."* (weak)

**After:** *"I'm running a 3-broker cluster with RF=3 and min.insync.replicas=2. Each partition has 3 replicas: 1 leader + 2 followers. A producer with `acks=all` waits for the leader plus at least one ISR member to acknowledge before considering the write durable. If I kill any one broker live, the system continues operating with no data loss — let me show you."* (strong)

### The "kill a broker" demo (now possible)

```bash
# Pre: produce some traffic via Postman
# Verify ISR is healthy
docker exec securebank-kafka1 kafka-topics --bootstrap-server kafka1:9092 \
  --describe --topic transactions

# Kill broker 2
docker stop securebank-kafka2

# Re-check — Isr should now show only [1,3] for partitions kafka2 led
docker exec securebank-kafka1 kafka-topics --bootstrap-server kafka1:9092 \
  --describe --topic transactions

# Continue sending transactions — they still succeed
# Producer transparently re-routes to the new partition leaders

# Bring kafka2 back
docker start securebank-kafka2

# Within ~10s, ISR repairs back to [1,2,3]
```

This is the **WOW moment** in a senior-engineer interview.

### Costs / trade-offs introduced

| Cost | Magnitude |
|---|---|
| Docker memory usage | ~1.5 GB total instead of ~500 MB (3 brokers vs 1) |
| Startup time | ~30s instead of ~20s |
| `docker-compose.yml` length | ~85 lines vs ~50 |
| Operational complexity | Slightly more containers to manage |

All acceptable trade-offs for the credibility gain.

### Files changed

| File | Change type |
|---|---|
| `docker-compose.yml` | Full rewrite of Kafka section |
| `transaction-service/src/main/resources/application.yml` | bootstrap-servers |
| `account-service/src/main/resources/application.yml` | bootstrap-servers |
| `fraud-detection/src/main/resources/application.yml` | bootstrap-servers |
| `README.md` | Services table, startup commands, ISR verification step |
| `PROJECT_GUIDE.md` | Startup section, cheat sheet |
| `Actual Code based readme.md` | Marked Issue #1 resolved, updated scores |

### Score impact

- **Replication / ISR durability** dimension: 2/10 → 9/10
- **Multi-broker cluster** dimension: 1/10 → 9/10
- **Kafka concept demonstration:** 6/10 → 7.5/10
- **Production-readiness:** 3/10 → 4.5/10
- **Interview readiness (2 YOE):** 8/10 → 8.5/10
- **OVERALL:** 6.5/10 → **7.0/10**

---

## Change #2 — Add a real test suite (11 tests)

**Date applied:** 2026-05-31
**Severity before:** 🔴 Critical
**Status:** ✅ Resolved

### What was wrong

The repo had **zero tests**. No `src/test/` directories anywhere, no test files in any module. For a banking application moving money via `AccountService.apply()`, this was the most damaging weakness in the project.

### The problem this caused

| Interviewer question | Honest answer before |
|---|---|
| "Show me the test for `AccountService.apply()`." | (silence) |
| "How do you know your idempotency check works?" | "I trust the code." |
| "How would you refactor safely?" | "I'd run Postman tests manually." |
| "What's your CI doing?" | "I don't have one yet." |

Any senior interviewer would consider this disqualifying for a banking role.

### What was changed

Added **11 tests** across 4 test classes, choosing a pragmatic mix instead of just one layer:

#### 1. Unit tests — `AccountServiceTest.java` (6 tests)
**Location:** `account-service/src/test/java/com/securebank/account/service/AccountServiceTest.java`
**Style:** JUnit 5 + Mockito + AssertJ. Repositories mocked with `@Mock`; `AccountService` injected with `@InjectMocks`.

Tests:
- `deposit_increasesBalance_andPersistsLog` — DEPOSIT adds amount; verifies both `accounts.save()` and `transaction_log.save()` capture correct values
- `withdraw_decreasesBalance_whenSufficientFunds` — basic happy path
- `withdraw_throws_whenInsufficientFunds` — asserts `IllegalStateException` thrown + **nothing saved**
- `apply_throws_whenAccountUnknown` — asserts on missing account
- `apply_skips_whenTransactionAlreadyProcessed` — proves idempotency: `existsById` returning true means `findById`/`save` are never invoked
- `transfer_debitsAccount_likeWithdraw` — covers the TRANSFER branch

This is **the most critical test class in the entire project** — it covers the function that moves money.

#### 2. Controller slice tests — `TransactionControllerTest.java` (2 tests)
**Location:** `transaction-service/src/test/java/com/securebank/transaction/controller/TransactionControllerTest.java`
**Style:** `@WebMvcTest` + `MockMvc` + `@MockBean KafkaTemplate`.

Tests:
- `validTransaction_isAccepted_andPublishedKeyedByAccountId` — valid POST returns 202, response has `transactionId`, KafkaTemplate is invoked with `topic="transactions"` and `key="ACC1001"` (proves partitioning by key)
- `invalidTransaction_isRejected` — missing accountId + negative amount returns 400 (proves Bean Validation wiring)

#### 3. EmbeddedKafka integration test — `TransactionFlowIntegrationTest.java` (1 test)
**Location:** `account-service/src/test/java/com/securebank/account/integration/TransactionFlowIntegrationTest.java`
**Style:** `@SpringBootTest` + `@EmbeddedKafka` + H2 + Awaitility.

Spring boot loads the full application context backed by:
- In-memory Kafka broker (started for the test)
- H2 in-memory DB (`MODE=PostgreSQL` for compatibility with the JPA entities)

Test flow:
1. Seeds account `ACC9001` with balance `1000.00` via `AccountRepository`
2. Publishes a `TransactionEvent` (DEPOSIT, 500.00) via `KafkaTemplate` to topic `transactions`
3. Uses Awaitility to poll the DB for up to 10 seconds
4. Asserts the balance is now `1500.00`

**Why it matters:** this test proves the **entire wiring is correct** — config classes, listener registration, JsonDeserializer settings, transactional behavior. It catches the kind of bugs unit tests miss.

#### 4. Streams topology tests — `FraudDetectionTopologyTest.java` (2 tests)
**Location:** `fraud-detection/src/test/java/com/securebank/fraud/streams/FraudDetectionTopologyTest.java`
**Style:** `TopologyTestDriver` — Kafka's official in-memory driver. No broker, no Spring, runs in milliseconds.

Tests:
- `atThreshold_noAlert` — 5 events for one account → output topic empty
- `aboveThreshold_emitsAlert` — 6 events for one account → `Alert` emitted, asserts `accountId`, `count=6`, and `reason` text

To enable these tests, refactored `FraudDetectionTopology` to expose a static `buildTopology(builder, ...)` method that the `@Bean` method delegates to. The test calls the same method directly. **No production behavior changed.**

### Files added / changed

| File | Change |
|---|---|
| `transaction-service/pom.xml` | Added `spring-boot-starter-test`, `spring-kafka-test` |
| `account-service/pom.xml` | Added `spring-boot-starter-test`, `spring-kafka-test`, `h2`, `awaitility` |
| `fraud-detection/pom.xml` | Added `spring-boot-starter-test`, `kafka-streams-test-utils` |
| `fraud-detection/.../FraudDetectionTopology.java` | Extracted `buildTopology(...)` static method for testability |
| `account-service/src/test/.../service/AccountServiceTest.java` | **NEW** — 6 unit tests |
| `transaction-service/src/test/.../controller/TransactionControllerTest.java` | **NEW** — 2 slice tests |
| `account-service/src/test/.../integration/TransactionFlowIntegrationTest.java` | **NEW** — 1 integration test |
| `account-service/src/test/resources/application-test.yml` | **NEW** — H2 + producer serializer config for integration test |
| `fraud-detection/src/test/.../streams/FraudDetectionTopologyTest.java` | **NEW** — 2 topology tests |

### How to run the tests

```powershell
cd D:\Temp\kafka\SecureBank
mvn test
```

Each module runs its own tests. Expected output:
```
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0   ← AccountServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0   ← TransactionControllerTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0   ← TransactionFlowIntegrationTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0   ← FraudDetectionTopologyTest
[INFO] BUILD SUCCESS
```

In IntelliJ: right-click a test class → **Run 'XxxTest'**, or right-click the project → **Run All Tests**.

### Interview talking points this fix unlocks

**Before:** *"I haven't written tests yet, but I would."* (weak)

**After:** *"My test suite covers the money-moving function with 6 unit tests including idempotency and insufficient-funds branches. The REST layer has slice tests proving validation works. An EmbeddedKafka integration test proves the whole pipeline end-to-end with an in-memory broker and H2. The Kafka Streams topology has dedicated `TopologyTestDriver` tests for the fraud rule. Let me run `mvn test` for you."* (strong)

The live `mvn test` green-bar demo is the moment a senior interviewer's eyebrow goes up.

### What this fix unlocked

| Capability | Before | After |
|---|---|---|
| Catch regression in money-moving code | ❌ | ✅ 6 unit tests |
| Prove idempotency claim | ❌ | ✅ `apply_skips_whenTransactionAlreadyProcessed` |
| Prove validation works | ❌ | ✅ Slice test with negative amount |
| Prove producer keys by accountId | ❌ | ✅ ArgumentMatcher verifies key |
| Prove Kafka wiring works end-to-end | ❌ | ✅ EmbeddedKafka integration test |
| Prove fraud rule fires correctly | ❌ | ✅ TopologyTestDriver tests |
| Safe refactoring | ❌ Manual + risky | ✅ Test suite as safety net |
| CI-ready | ❌ | ✅ `mvn test` exits cleanly |

### Costs / trade-offs introduced

| Cost | Magnitude |
|---|---|
| Build time | ~30s extra for `mvn test` (EmbeddedKafka brings up an in-memory broker) |
| Maintenance | Tests need updating when production behavior changes (this is the right thing) |
| One small refactor | `FraudDetectionTopology.buildTopology()` extracted as static method — pure improvement |

### Score impact

- **Test coverage** dimension: 0/10 → 7/10
- **Production-readiness:** 4.5/10 → 5.5/10
- **Banking-domain seriousness:** 5/10 → 6/10
- **Interview readiness (2 YOE):** 8.5/10 → 9/10
- **Senior-eng scrutiny survival:** 6/10 → 7/10
- **OVERALL:** 7.0/10 → **7.5/10**

---

## Change #3 — Restrict `JsonDeserializer.TRUSTED_PACKAGES`

**Date applied:** 2026-05-31
**Severity before:** 🔴 Critical
**Status:** ✅ Resolved

### What was wrong

In `account-service/src/main/java/com/securebank/account/config/KafkaConsumerConfig.java`, the JsonDeserializer was configured with:

```java
props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
```

The wildcard `*` tells Jackson: **trust ANY class from ANY package**. For a banking project, this is a major defense-in-depth violation:

- If type-info headers ever get enabled (a one-line change), a malicious or misconfigured producer could send a header pointing to a gadget class on the classpath. The JVM would attempt to instantiate it.
- Static analysis tools (SonarQube, Snyk, Checkmarx) flag `TRUSTED_PACKAGES=*` automatically.
- Senior interviewers spot the wildcard immediately and lose trust in security judgment.

Worse: the project also sets `USE_TYPE_INFO_HEADERS=false` and `VALUE_DEFAULT_TYPE=TransactionEvent.class`, meaning the wildcard wasn't even doing anything useful — it was **simultaneously dangerous and unused**, the worst combination.

### What was changed

A single line in `KafkaConsumerConfig.java`:

```diff
- props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
+ // Whitelist only our model package (defense-in-depth).
+ // We also disable type-info headers below, but keeping a tight whitelist
+ // protects us if header handling ever changes.
+ props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.securebank.account.model");
```

### Why scope to `com.securebank.account.model` specifically

- The only DTO consumed from Kafka is `TransactionEvent`, which lives in that package.
- Restricting to a single package follows **principle of least privilege**.
- If someone later adds a new event type in the same package, no config change needed.
- If someone tries to import a class from a sister package without thinking, the deserializer will refuse — failure surfaces loudly instead of silently allowing it.

### What this fix unlocked

| Capability | Before | After |
|---|---|---|
| Pass SAST tooling | ❌ Flagged on `*` | ✅ Whitelist scoped |
| Defense-in-depth | ❌ Single layer (type headers disabled) | ✅ Two layers (header + whitelist) |
| Senior-eng credibility | ❌ Obvious red flag | ✅ Demonstrates security mindset |
| Future-proof against header config changes | ❌ Wide-open | ✅ Tight perimeter |

### Files changed

| File | Change |
|---|---|
| `account-service/.../config/KafkaConsumerConfig.java` | Wildcard → `com.securebank.account.model` + explanatory comment |
| `Actual Code based readme.md` | Marked Issue #3 resolved, security score 2/10 → 3/10 |

### Interview talking points this fix unlocks

**Before:** *(silence or "I'll fix that")* if asked why `*` is there.

**After:** *"I scoped trusted packages to my model package as defense-in-depth. The deserializer also ignores type-info headers and forces the default type to `TransactionEvent`, so there are three layers protecting the consumer from a malicious payload pointing at an arbitrary class on the classpath."*

### Costs / trade-offs

None. Zero behavior change (type-info headers are already disabled). One-minute fix with significant credibility upside.

### Score impact

- **Security** dimension: 2/10 → 3/10
- **OVERALL:** unchanged at 7.5/10 (cosmetic fix, but eliminates a kill-shot interview question)

---

## Change #4 — Replace silent ack with `DefaultErrorHandler` + DLT

**Date applied:** 2026-05-31
**Severity before:** 🔴 Critical
**Status:** ✅ Resolved
**Side-effect:** Also resolves Issue #5 (no DLT implementation).

### What was wrong

The `TransactionConsumer` had this catch block:

```java
} catch (IllegalStateException e) {
    log.error("Business rule violation for txn {}: {}", event.getTransactionId(), e.getMessage());
    ack.acknowledge();   // ← event vanishes
}
```

`IllegalStateException` is what `AccountService` throws on two banking-critical conditions:
- **Insufficient funds**
- **Unknown account**

When triggered, the message was logged and acked — gone forever. No audit trail, no notification, no recovery path, no DLT.

Additionally, `ErrorHandlingDeserializer` was wired in `KafkaConsumerConfig` but had no destination — so deserialization failures also silently disappeared.

### The problem this caused

| Concern | Impact |
|---|---|
| Compliance (RBI / regulator audit) | "Show all rejected transactions in March" → nothing to produce |
| Customer experience | Insufficient-funds events have no follow-up workflow |
| Operability | Poison messages (bad JSON) untraceable |
| Producer bugs | Bad payloads silently disappear — no signal something is wrong |
| Senior-eng credibility | Silent drop = career-ending in banking |

### What was changed

#### 1. `KafkaConsumerConfig` — added DLT plumbing

Three new beans:

- **`dltProducerFactory()`** — dedicated producer factory with `acks=all` + `enable.idempotence=true`, so DLT writes are themselves durable and de-duplicated.
- **`dltKafkaTemplate(...)`** — the publisher used by the recoverer.
- **`kafkaErrorHandler(...)`** — a `DefaultErrorHandler` configured with:
  - `DeadLetterPublishingRecoverer` that preserves the **original partition** so per-account ordering is maintained on the DLT.
  - `ExponentialBackOffWithMaxRetries(3)` → 1s / 2s / 4s backoff for **transient** failures (e.g., DB connection blips).
  - `addNotRetryableExceptions(IllegalStateException.class)` → **business rule violations go straight to DLT** (retrying a "insufficient funds" exception 3 times wastes resources — it'll fail identically every time).
- Switched `AckMode` from `MANUAL_IMMEDIATE` to `RECORD`. The DefaultErrorHandler handles offset commits cleanly after retry/DLT routing — no need for manual `Acknowledgment` plumbing.

#### 2. `TransactionConsumer` — shrunk to a 2-liner

```java
@KafkaListener(topics = "${app.topic.transactions}", containerFactory = "kafkaListenerContainerFactory")
public void consume(TransactionEvent event) {
    accountService.apply(event);
}
```

All retry/DLT logic is centralised in `KafkaConsumerConfig` where it belongs.

#### 3. `application.yml` (+ test variant)

Added the DLT topic name as a config property:

```yaml
app:
  topic:
    transactions: transactions
    transactions-dlt: transactions.DLT
```

#### 4. New integration test

Added `unknownAccount_routedToDLT()` to `TransactionFlowIntegrationTest`:
- Publishes an event for `ACC_DOES_NOT_EXIST`
- Opens a Kafka consumer on `transactions.DLT`
- Asserts the record arrives with:
  - Original key (`accountId`) preserved
  - Header `kafka_dlt-exception-message` containing `"Unknown account"`
  - Header `kafka_dlt-original-topic` = `"transactions"`
  - DB unchanged for the unknown account

#### 5. `README.md` + `PROJECT_GUIDE.md`

Added `transactions.DLT` topic creation to the startup steps (RF=3, 3 partitions).

### Files changed

| File | Change |
|---|---|
| `account-service/.../config/KafkaConsumerConfig.java` | Added DLT producer, KafkaTemplate, DefaultErrorHandler beans; switched AckMode to RECORD |
| `account-service/.../consumer/TransactionConsumer.java` | Removed try/catch + manual Acknowledgment param |
| `account-service/src/main/resources/application.yml` | Added `app.topic.transactions-dlt` |
| `account-service/src/test/resources/application-test.yml` | Added DLT topic to test config |
| `account-service/src/test/.../integration/TransactionFlowIntegrationTest.java` | Added `unknownAccount_routedToDLT()` test |
| `README.md`, `PROJECT_GUIDE.md` | Added DLT topic creation step |
| `Actual Code based readme.md` | Marked Issues #4 + #5 resolved, scores bumped |

### What this fix unlocked

| Capability | Before | After |
|---|---|---|
| Audit trail for rejected events | ❌ Silently dropped | ✅ Full record in DLT with exception context |
| Replay rejected events after fixing root cause | ❌ Impossible | ✅ Read DLT, fix data/code, re-publish |
| Survive transient DB blips | ❌ Infinite-loop on rethrow | ✅ 3 retries with backoff, then DLT |
| Distinguish business vs infra errors | ❌ Same path | ✅ Business errors skip retries (faster), infra retried |
| Handle deserialization poison pills | ❌ ErrorHandlingDeserializer caught them but no destination | ✅ Routed to DLT |
| Pass compliance review | ❌ Silent drop = red flag | ✅ Full traceability |

### Interview talking points this fix unlocks

**Before:** *"If the consumer can't process a message, I log and skip."* (kill-shot answer in banking)

**After:** *"My consumer has zero try/catch. Spring Kafka's `DefaultErrorHandler` centralises the policy: `IllegalStateException` is classified as not-retryable — those are business rule violations that won't succeed on retry, so they go straight to `transactions.DLT`. Other exceptions retry 3 times with exponential backoff before DLT'ing. The recoverer preserves the original partition so per-account ordering is maintained on the DLT, and adds headers with the exception class, message, original topic/partition/offset for full traceability. I have an integration test that asserts the DLT routing works."* (strong)

### The "demo this live" moment

1. Send a Postman request for an unknown account:
   ```json
   { "accountId": "ACC9999", "type": "DEPOSIT", "amount": 100 }
   ```
2. Open Kafka UI → `transactions.DLT` → message appears with `kafka_dlt-exception-message` header containing `Unknown account`.
3. Now stop Postgres mid-traffic for a few seconds → producer keeps publishing → consumer retries 3× → if Postgres still down, DLT receives those too. Restart Postgres → new messages flow through normally.

This is **textbook senior-engineer territory.**

### Costs / trade-offs

| Cost | Magnitude |
|---|---|
| Slightly higher producer config complexity | +1 small producer factory bean |
| Test runtime | +~3 seconds for the DLT integration test |
| Need to remember to create `transactions.DLT` topic on deploy | Mitigated by README + startup script |

### Score impact

- **Error handling** dimension: 4/10 → 8/10
- **Audit trail** dimension: 5/10 → 8/10
- **Error-handling deserializer** capability: 5/10 → 9/10
- **Dead Letter Topic** capability: 0/10 → 8/10
- **Test coverage:** 7/10 → 8/10 (one extra test)
- **Kafka concept demonstration:** 7.5/10 → 8.5/10
- **Production-readiness:** 5.5/10 → 6.5/10
- **Banking-domain seriousness:** 6/10 → 7.5/10
- **Senior-eng scrutiny survival:** 7/10 → 8/10
- **OVERALL:** 7.5/10 → **8.0/10**

---

## Change #5 — Three layered fraud detection rules

**Date applied:** 2026-05-31
**Severity before:** 🟠 Major
**Status:** ✅ Resolved

### What was wrong

`FraudDetectionTopology` had exactly one rule — a count of events per 60s window with a hard-coded threshold of 5. It **ignored the `amount`, `city`, and `type` fields** on the `Transaction` model even though the topology consumed them. The result was:

- A customer making 6 small grocery purchases on Saturday morning got flagged identically to a card-testing attack.
- A single ₹500,000 wire transfer didn't trigger anything (it's just 1 event, under the count threshold).
- A customer whose normal spend is ₹500 making a ₹50,000 purchase didn't trigger anything either.

The `PROJECT_GUIDE.md` had also overclaimed: it described "amount > 10× rolling avg" and "geo-impossible transactions" as features, but neither existed in code. **Code/docs misalignment hurts more than missing features.**

### The problem this caused in a banking context

| Fraud pattern | Old rule caught it? |
|---|---|
| Card-testing burst | ✅ |
| Single large drain (e.g., wire fraud) | ❌ |
| Spend anomaly (5× customer baseline) | ❌ |
| Geo-impossible activity | ❌ (not addressed by this change either) |
| Normal Saturday-morning shopping | 🚨 **False positive** |

Banks measure fraud detection in **false positives** as much as **true positives**. Every false positive locks out a real customer at a checkout. The original rule had no signal-to-noise discipline.

### What was changed

#### 1. `Alert` model — typed by rule

```java
public enum RuleType {
    HIGH_VELOCITY,   // count > N in tumbling window
    LARGE_AMOUNT,    // single txn > absolute threshold
    SPEND_SPIKE      // amount > N× rolling avg
}
```

Added `ruleType` and `transactionId` fields so downstream consumers can route or filter by rule. Added `@Builder` for clean construction.

#### 2. New `RollingStats` model

Per-account state holding `sum` and `count`. Used by Rule C. Lives in a state store (`rolling-stats-store`), automatically backed by an internal Kafka changelog topic for fault tolerance — if the fraud-detection service crashes, state is rebuilt from the changelog on restart.

#### 3. `FraudDetectionTopology` — three branches merging into one alerts topic

```
stream ─┬─► groupByKey + window + count > N        ─► HIGH_VELOCITY  ─┐
        ├─► filter amount > LARGE_AMOUNT_THRESHOLD ─► LARGE_AMOUNT   ─┤
        └─► transformValues (state store)          ─► SPEND_SPIKE    ─┴─► alerts topic
                                                                          (merged)
```

- **Rule A — HIGH_VELOCITY:** existing logic preserved. Still uses a 60s tumbling window with no grace.
- **Rule B — LARGE_AMOUNT:** stateless filter. Single transaction > 100,000 → alert. Cheapest possible signal.
- **Rule C — SPEND_SPIKE:** the showcase rule. Implemented via `ValueTransformerWithKey` reading + writing a `KeyValueStore<String, RollingStats>`. For each incoming transaction:
  1. Load the account's `RollingStats` from the store (initialise if absent).
  2. If `count >= spikeMinHistory` (default 3), check whether `amount > spikeMultiplier × avg` (default 5×). If yes, emit alert.
  3. Update the rolling stats (regardless of alert).
  4. Persist the updated stats back to the store.

  **Cold-start safety:** the rule won't fire on the first 2 transactions. New accounts need history before they can be judged "anomalous."

#### 4. `application.yml` — exposed knobs

```yaml
app:
  fraud:
    window-seconds: 60
    count-threshold: 5
    large-amount-threshold: 100000
    spike-multiplier: 5.0
    spike-min-history: 3
```

All thresholds are externalised — easy to tune per environment without code changes.

#### 5. Tests — went from 2 to 5

Existing 2 tests still pass (with assertions extended to verify `ruleType == HIGH_VELOCITY`). Added:

- `largeAmountTransaction_emitsLargeAmountAlert` — single 200,000 transaction → alert with `ruleType=LARGE_AMOUNT`, `transactionId` preserved
- `spendSpike_emitsSpendSpikeAlert` — 3 baseline events at 100, then one at 10,000 → alert with `ruleType=SPEND_SPIKE`
- `belowSpikeMultiplier_noAlert` — 3 baseline events at 100, then one at 200 (only 2× avg, below 5× threshold) → no alert

These tests exercise the state store directly via `TopologyTestDriver`, proving cold-start behavior and threshold logic without spinning up a real broker.

### Files changed

| File | Change |
|---|---|
| `fraud-detection/.../model/Alert.java` | Added `ruleType` + `transactionId`, `@Builder`, `RuleType` enum |
| `fraud-detection/.../model/RollingStats.java` | **NEW** — sum/count state per account |
| `fraud-detection/.../streams/FraudDetectionTopology.java` | Rewritten: 3 rules merging into alerts topic, plus inner `SpikeDetectorTransformer` |
| `fraud-detection/src/main/resources/application.yml` | New thresholds (`count-threshold`, `large-amount-threshold`, `spike-multiplier`, `spike-min-history`) |
| `fraud-detection/.../streams/FraudDetectionTopologyTest.java` | Existing 2 tests updated for new fields; 3 new tests added |
| `Actual Code based readme.md` | Marked Issue #6 resolved, bumped scores |

### What this fix unlocked

| Capability | Before | After |
|---|---|---|
| Detect single large transactions | ❌ | ✅ Rule B |
| Detect anomalous spend per customer | ❌ | ✅ Rule C with state store |
| Tag alerts by rule type for routing | ❌ | ✅ `Alert.RuleType` |
| Cold-start safety for new accounts | n/a | ✅ `spike-min-history` |
| Per-account stateful processing | ❌ | ✅ `rolling-stats-store` + changelog backing |
| Demonstrate real Kafka Streams stateful API | ❌ Just `count()` | ✅ `transformValues` + `KeyValueStore` + custom serdes |
| Code/docs alignment | ❌ Overclaimed | ✅ Matches PROJECT_GUIDE.md original promises |

### Interview talking points this fix unlocks

**Before:** *"I count events in a 60-second window."* (CS101 answer)

**After:** *"I run three rules in parallel on the same stream and merge their outputs. Rule A is a count-based burst detector for card-testing. Rule B is a stateless filter for single large transactions — cheapest possible signal. Rule C is the interesting one: it maintains rolling sum and count per account in a Kafka Streams state store (backed by a changelog topic for fault tolerance), and emits a SPEND_SPIKE alert when an incoming amount exceeds 5× the customer's running average. I made it cold-start safe by requiring 3 prior transactions before activating — new accounts shouldn't get flagged just because their first transaction is also their only data point. All three rules emit a typed `Alert` with `ruleType` so downstream consumers can route by category. I have tests using `TopologyTestDriver` that drive events through each rule and assert the right alerts fire — including a 'just below threshold' test to verify low false-positive behaviour."*

That's a **conversation senior engineers actually want to have** instead of moving on.

### Costs / trade-offs

| Cost | Magnitude |
|---|---|
| New state store + changelog topic | One internal topic auto-created (RF=3 by default) |
| Slightly heavier topology | Three branches instead of one; immeasurable at this scale |
| Extra complexity in tests | 3 new tests; well isolated |
| Alert volume | More rules = more alerts; in production you'd tune thresholds + add dedup |

### Score impact

- **Windowed aggregation** capability: 6/10 → 9/10
- **Test coverage:** 15 tests (was 12)
- **Banking-domain seriousness:** 7.5/10 → 8/10
- **Kafka concept demonstration:** 8.5/10 → 9/10
- **Senior-eng scrutiny survival:** 8/10 → 8.5/10
- **OVERALL:** 8.0/10 → **8.3/10**

---

## Change #6 — Synchronous producer ack with honest HTTP semantics

**Date applied:** 2026-05-31
**Severity before:** 🟠 Major
**Status:** ✅ Resolved

### What was wrong

`TransactionController.publish` returned `202 Accepted` immediately after firing `kafkaTemplate.send()`, before the broker had acknowledged the write:

```java
CompletableFuture<SendResult<String, Transaction>> future =
        kafkaTemplate.send(topic, txn.getAccountId(), txn);

future.whenComplete((result, ex) -> { /* just logs */ });

return ResponseEntity.accepted().body(Map.of(
        "transactionId", txn.getTransactionId(),
        "status", "ACCEPTED"
));
```

The HTTP response said *"accepted"* but the message wasn't guaranteed durable yet. If the broker was unreachable, ISR was below `min.insync.replicas`, or the JVM crashed before the producer flushed, **the client would still see `202 Accepted`** while the transaction silently disappeared.

For a banking endpoint that takes money, this is **the wrong contract** — *"I accepted your deposit, trust me bro"*.

### The problem this caused

| Scenario | Old behavior |
|---|---|
| Broker unreachable | `202 Accepted` returned. Producer logged the failure in its own logs. Client never knew. |
| Producer JVM killed between `.send()` and broker confirmation | `202 Accepted` returned. Message in producer's in-memory buffer, lost on JVM kill. |
| All ISR replicas offline (less than `min.insync.replicas=2`) | `202 Accepted` returned even though `acks=all` couldn't be satisfied. |
| Healthy path | `202 Accepted`, but semantically wrong: it means "I'll process this later" — there is no async pipeline, no GET endpoint to poll status. |

Senior interviewers spot this in 30 seconds and ask *"what happens if Kafka is down when the HTTP response leaves your server?"* — the old answer was *"we lose the transaction silently."*

### What was changed

Replaced the fire-and-forget pattern with a synchronous wait:

```java
SendResult<String, Transaction> result = kafkaTemplate
        .send(topic, txn.getAccountId(), txn)
        .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
        "transactionId", txn.getTransactionId(),
        "status", "PERSISTED",
        "partition", result.getRecordMetadata().partition(),
        "offset", result.getRecordMetadata().offset()
));
```

with explicit error mapping:

| Outcome | HTTP status | Body status field |
|---|---|---|
| Broker acked (durable in ISR) | `201 Created` | `PERSISTED` |
| Future timed out after 5s | `503 Service Unavailable` | `TIMEOUT` |
| Future completed exceptionally (broker error, no ISR, etc.) | `503 Service Unavailable` | `FAILED` |
| Thread interrupted while waiting | `503 Service Unavailable` | `INTERRUPTED` |

`Thread.currentThread().interrupt()` restores the interrupt flag — small detail, but the right thing to do for proper Java concurrency hygiene (and senior interviewers notice).

### Why "5 seconds" is a defensible number

- Healthy `acks=all` write to a 3-broker cluster: typically **2–15 ms**.
- 5s gives ~300× headroom for occasional GC pauses, network blips, leader re-election after a broker crash.
- If we hit 5s, **something is genuinely broken** and a 503 is the right answer — the client knows to retry instead of being told a comforting lie.

### How "201 Created" is now an honest contract

The response status reflects what's true in Kafka:

- **Producer config:** `acks=all` + `enable.idempotence=true` + RF=3 + `min.insync.replicas=2`.
- **What "acks=all" actually waits for:** leader plus at least `min.insync.replicas - 1 = 1` follower to confirm the write.
- **So a 201 means:** the message is durable on **at least 2 of 3 replicas**. Surviving a single broker crash is no longer a claim — it's a guarantee at HTTP response time.

### Tests

Updated `TransactionControllerTest`:

| Test | What it proves |
|---|---|
| `validTransaction_isCreated_andPublishedKeyedByAccountId` (updated) | Happy path returns 201 + partition + offset; producer keyed by `accountId` |
| `invalidTransaction_isRejected` (unchanged) | Bean Validation still rejects bad input with 400 |
| **`kafkaFailure_returns503` (NEW)** | When the future completes exceptionally, controller returns 503 with `status: FAILED` |

Test technique: instead of waiting 5 real seconds, the tests use `CompletableFuture.completedFuture(...)` (instant return) for the success path and `future.completeExceptionally(...)` (instant error) for the failure path. Total suite still runs in milliseconds.

### Files changed

| File | Change |
|---|---|
| `transaction-service/.../controller/TransactionController.java` | Sync `.get(5s)`, typed return body, HTTP 201 / 503 mapping, full exception handling with restored interrupt flag |
| `transaction-service/.../controller/TransactionControllerTest.java` | Updated happy-path assertions; added 3rd test for Kafka failure |
| `README.md` | New response example with `201` + partition/offset |
| `PROJECT_GUIDE.md` | Same |
| `Actual Code based readme.md` | Marked Issue #7 resolved, bumped scores |

### What this fix unlocked

| Capability | Before | After |
|---|---|---|
| Honest API contract | ❌ "accepted" was a lie | ✅ 201 only if durable |
| Client knows to retry on failure | ❌ Got 202 either way | ✅ 503 on failure |
| Broker outage surfaces immediately | ❌ Silent loss | ✅ 503 with `status: TIMEOUT` or `FAILED` |
| Response includes Kafka coordinates | ❌ Just transactionId | ✅ partition + offset returned |
| Pass "what if Kafka is down?" interview question | ❌ "We lose the transaction" | ✅ "Client gets 503 and retries" |

### Interview talking points this fix unlocks

**Before:** *"My endpoint returns 202 Accepted as soon as I call send() — the broker confirms asynchronously."* (interviewer: "so what if Kafka fails?")

**After:** *"My endpoint waits up to 5 seconds for the broker to acknowledge the write before responding. Because the producer is configured with `acks=all` and the cluster has `min.insync.replicas=2`, a 201 Created means the message is durable on at least 2 of 3 replicas — it survives a single broker crash. If the future times out or fails, I return 503 Service Unavailable so the client knows the write didn't succeed and should retry. I also restore the thread's interrupt flag on `InterruptedException` so any wrapping framework can react properly. I have a unit test that simulates Kafka failure and asserts the 503 response."*

### Costs / trade-offs

| Cost | Magnitude |
|---|---|
| HTTP request latency | +2–15ms in steady state (Kafka ack time). Negligible. |
| Tomcat thread pinned for the duration | True, but at the scale of this demo, irrelevant. Reactive `DeferredResult` would solve it under high load. |
| Test setup slightly more verbose | Need to mock `SendResult` with `RecordMetadata`. Worth it. |

### Score impact

- **Production-readiness:** 6.5/10 → 7/10
- **Banking-domain seriousness:** 8/10 → 8.5/10
- **Senior-eng scrutiny survival:** 8.5/10 → 9/10
- **Test coverage:** 15 tests → 16 tests
- **OVERALL:** 8.3/10 → **8.5/10**

---

## Change #7 — Avro + Confluent Schema Registry

**Date applied:** 2026-05-31
**Severity before:** 🟠 Major
**Status:** ✅ Resolved

### What was wrong

Each of the three services maintained its own handwritten DTO copy of the wire schema:

```
transaction-service/.../model/Transaction.java     (request DTO + Kafka payload)
account-service/.../model/TransactionEvent.java    (different class name, same shape)
fraud-detection/.../model/Transaction.java         (third copy)
fraud-detection/.../model/Alert.java               (only place Alert lived)
```

Messages flew as **plain JSON**. Nothing forced these classes to stay aligned. If a developer:

- Renamed `amount` → `transactionAmount` on the producer side, consumers silently received `null`.
- Added a non-nullable field, all consumers crashed on deserialization.
- Changed `amount` from `BigDecimal` to `String`, quiet drift.

There was no central contract, no compatibility check, no schema versioning. Every change was a roll of the dice. The `PROJECT_GUIDE.md` had claimed Schema Registry/Avro as a feature, but neither existed in code.

### The problem this caused

| Concern | Before |
|---|---|
| Producers and consumers agree on the wire format | ❌ Hope-driven |
| Forward/backward compatibility enforced at deploy time | ❌ Find out in prod |
| Schema versioning + audit trail | ❌ None |
| Wire payload size | ❌ JSON (verbose) |
| Self-describing messages | ❌ No schema ID in headers |
| Pass senior-eng banking interview question "How do you handle schema evolution?" | ❌ "I send JSON. We see what breaks at runtime." |

### What was changed

#### 1. Schema Registry container

Added `confluentinc/cp-schema-registry:7.6.0` to `docker-compose.yml`:
- Listens on internal port `8081`; mapped to host port `8091` (avoiding clash with transaction-service:8081).
- Backed by `_schemas` Kafka topic with replication factor 3.
- Global compatibility mode: `BACKWARD`.
- Kafka UI also wired to know about it → schemas show up in the UI.

Confluent platform images (kafka, zookeeper, schema-registry) all bumped 7.5.0 → 7.6.0 for version alignment with Apache Kafka 3.6.x.

#### 2. Avro schemas (single source of truth)

```
schemas/avro/Transaction.avsc
schemas/avro/Alert.avsc
```

`Transaction.avsc` uses:
- `decimal(18,2)` logical type for `amount` → generates `BigDecimal` field
- `timestamp-millis` logical type for `timestamp` → generates `Instant` field
- `["null", "string"]` for nullable `city` → generates nullable `String`
- `enum` for `TransactionType` (DEPOSIT / WITHDRAW / TRANSFER)

`Alert.avsc` mirrors what the application code already produced:
- `accountId`, `transactionId` (nullable), `ruleType` enum (HIGH_VELOCITY / LARGE_AMOUNT / SPEND_SPIKE), `reason`, `count`, `detectedAt`.

Both share the namespace `com.securebank.avro` so generated classes land in a clean package and can be imported uniformly by all 3 services.

#### 3. Maven plumbing

Parent pom:
- Added Confluent Maven repository (`packages.confluent.io/maven`).
- New properties: `confluent.version=7.6.0`, `avro.version=1.11.3`.
- Dependency management for `kafka-avro-serializer`, `kafka-streams-avro-serde`, `kafka-schema-registry-client`, `avro`.
- Plugin management for `avro-maven-plugin`.

Per-service pom additions:
- `avro` runtime dependency.
- `kafka-avro-serializer` (transaction-service, account-service) or `kafka-streams-avro-serde` (fraud-detection).
- `avro-maven-plugin` execution bound to `generate-sources`, pointing at `${project.basedir}/../schemas/avro` so all 3 services share the same schema files. Options enabled: `stringType=String` (so generated fields are `String`, not `CharSequence`) and `enableDecimalLogicalType=true` (so decimals are `BigDecimal`).

Generated classes land in `target/generated-sources/avro/com/securebank/avro/`.

#### 4. transaction-service

- **Deleted** handwritten `Transaction.java`.
- **Added** `TransactionRequest.java` — pure HTTP request DTO with Bean Validation. Clients still POST JSON; nothing about the HTTP API changed.
- Controller maps `TransactionRequest` → Avro `com.securebank.avro.Transaction` before sending. This is the **right separation**: HTTP contract for clients (JSON), Kafka wire contract (Avro). Clients don't need Avro libraries.
- `KafkaProducerConfig` swapped `JsonSerializer` → `KafkaAvroSerializer` and added `schema.registry.url` config.
- `application.yml` adds `app.schema-registry-url`.
- Controller test mocks `KafkaTemplate<String, Transaction>` with the Avro class.

#### 5. account-service

- **Deleted** handwritten `TransactionEvent.java`.
- `TransactionConsumer` now accepts Avro `com.securebank.avro.Transaction` directly.
- `AccountService.apply()` adapted to the generated class:
  - `event.getAmount()` returns `BigDecimal` (logical type).
  - `event.getType()` returns `com.securebank.avro.TransactionType` enum; switch statement uses enum cases.
  - `type.name()` stored in `transaction_log.type` column (still `VARCHAR`).
- `KafkaConsumerConfig` swapped `JsonDeserializer` → `KafkaAvroDeserializer` (wrapped in `ErrorHandlingDeserializer`). Added `SPECIFIC_AVRO_READER_CONFIG=true` so deserializer returns `Transaction` instead of `GenericRecord`. Removed `TRUSTED_PACKAGES` (Avro doesn't need it).
- **DLT producer** also uses `KafkaAvroSerializer` — failed Avro records get re-serialized into the DLT preserving the schema.
- Unit tests (`AccountServiceTest`) build Avro objects via `Transaction.newBuilder()...build()`.
- Integration test (`TransactionFlowIntegrationTest`) uses Avro producer/consumer with `mock://test` Schema Registry URL — `MockSchemaRegistryClient` keeps tests fast and offline.
- `application-test.yml` adds `app.schema-registry-url: mock://test`.

#### 6. fraud-detection

- **Deleted** handwritten `Transaction.java` and `Alert.java`.
- Topology now uses `SpecificAvroSerde<Transaction>` and `SpecificAvroSerde<Alert>` (from `io.confluent:kafka-streams-avro-serde`) instead of `JsonSerde`.
- `RollingStats` **stays JSON** (internal state store value, never crosses service boundaries — no benefit to Avro-ising it).
- `buildTopology(...)` signature extended to take both serdes as parameters → tests inject mock-registry-backed serdes.
- The windowed-count Materialized now declares explicit `Serdes.Long()` for the count value (was relying on default).
- `application.yml`: added `schema.registry.url` in streams properties + `app.schema-registry-url`.
- Topology tests use `SpecificAvroSerde` configured with `mock://fraud-topology-test` URL.

#### 7. Compatibility story

With Schema Registry running in `BACKWARD` mode:
- A producer trying to deploy a schema where a non-optional field was added, or a field's type was incompatibly changed, will fail registration at startup.
- New consumers can read messages produced with old schemas.
- Adding nullable fields with defaults is fine.

This is what banks actually do. The talking point isn't theoretical anymore.

### Files changed

| File | Change |
|---|---|
| `docker-compose.yml` | Added `schema-registry` service; bumped Confluent images 7.5.0 → 7.6.0; kafka-ui wired to Schema Registry |
| `schemas/avro/Transaction.avsc` | **NEW** — canonical transaction schema |
| `schemas/avro/Alert.avsc` | **NEW** — canonical alert schema |
| `pom.xml` (parent) | Confluent repo + dependency management + plugin management |
| `transaction-service/pom.xml` | Avro plugin + Avro deps |
| `transaction-service/.../model/Transaction.java` | **DELETED** |
| `transaction-service/.../model/TransactionRequest.java` | **NEW** — HTTP request DTO |
| `transaction-service/.../controller/TransactionController.java` | Maps `TransactionRequest` → Avro `Transaction` |
| `transaction-service/.../config/KafkaProducerConfig.java` | `KafkaAvroSerializer` + schema registry URL |
| `transaction-service/src/main/resources/application.yml` | `app.schema-registry-url` |
| `transaction-service/.../controller/TransactionControllerTest.java` | Uses generated Avro `Transaction` |
| `account-service/pom.xml` | Avro plugin + Avro deps |
| `account-service/.../model/TransactionEvent.java` | **DELETED** |
| `account-service/.../config/KafkaConsumerConfig.java` | `KafkaAvroDeserializer` + `specific.avro.reader=true` + DLT producer uses Avro serializer |
| `account-service/.../consumer/TransactionConsumer.java` | Accepts Avro `Transaction` |
| `account-service/.../service/AccountService.java` | Uses generated `TransactionType` enum + `BigDecimal` from logical type |
| `account-service/src/main/resources/application.yml` | `app.schema-registry-url` |
| `account-service/.../service/AccountServiceTest.java` | Builds Avro objects with builder |
| `account-service/.../integration/TransactionFlowIntegrationTest.java` | Avro producer/consumer + `mock://test` Schema Registry |
| `account-service/src/test/resources/application-test.yml` | `schema-registry-url: mock://test` |
| `fraud-detection/pom.xml` | Avro plugin + `kafka-streams-avro-serde` |
| `fraud-detection/.../model/Transaction.java` | **DELETED** |
| `fraud-detection/.../model/Alert.java` | **DELETED** |
| `fraud-detection/.../streams/FraudDetectionTopology.java` | `SpecificAvroSerde` for transactions + alerts; takes serdes as params for testability |
| `fraud-detection/src/main/resources/application.yml` | Schema Registry URL in streams + app properties |
| `fraud-detection/.../streams/FraudDetectionTopologyTest.java` | Configured serdes with `mock://fraud-topology-test` |
| `README.md`, `PROJECT_GUIDE.md` | Schema Registry in services list, REST API examples |
| `Actual Code based readme.md` | Marked Issue #8 resolved, scores bumped |

### What this fix unlocked

| Capability | Before | After |
|---|---|---|
| Single source of truth for wire format | ❌ 3 hand-copied classes | ✅ 2 `.avsc` files |
| Schema enforcement at deploy time | ❌ | ✅ Schema Registry rejects incompatible producers |
| Schema versioning + audit | ❌ | ✅ Every schema is versioned in `_schemas` topic |
| Schema evolution conversation in interviews | ❌ Stumble | ✅ "BACKWARD compatibility, add nullable fields with defaults" |
| Smaller wire payloads | ❌ JSON verbose | ✅ Avro binary (~30-50% smaller) |
| Self-describing messages | ❌ | ✅ Each record carries a 5-byte schema ID |
| Tooling (Kafka UI) shows schemas | ❌ | ✅ Schemas visible in Kafka UI |
| CI can block incompatible producer deploys | ❌ | ✅ `mvn schema-registry:test-compatibility` (future setup) |

### Interview talking points this fix unlocks

**Before:** *"I send JSON. We agree on field names in code review."* (cringe)

**After:** *"Wire format is Avro with Confluent Schema Registry running BACKWARD compatibility. The single source of truth for `Transaction` and `Alert` lives in `schemas/avro/` — Maven's avro-maven-plugin generates Java classes for all three services from those `.avsc` files at build time, so there's no drift possible. The producer registers its schema on first send, and Schema Registry will reject deploys whose schema isn't backward-compatible with the current registered version — that means I can safely add nullable fields, but a renamed or type-changed field is caught at deploy time, not in production. For testing I use `mock://test` which gives me an in-process MockSchemaRegistryClient — tests run offline, no network calls. The HTTP API still speaks JSON because clients shouldn't have to ship Avro libraries — there's a `TransactionRequest` POJO that gets mapped to the Avro `Transaction` before publishing."*

### The "show me a schema" demo moment

After running a transaction, you can show the interviewer:

```
curl http://localhost:8091/subjects
→ ["transactions-value"]

curl http://localhost:8091/subjects/transactions-value/versions/latest
→ {"subject":"transactions-value","version":1,"id":1,"schema":"..."}
```

And in Kafka UI: the Schema Registry tab shows version history with diffs. **That's textbook senior banking platform engineering.**

### Costs / trade-offs

| Cost | Magnitude |
|---|---|
| 1 new Docker container | +250 MB RAM, +5s startup |
| Build complexity (Avro plugin code-gen) | First `mvn compile` ~30s extra to generate sources |
| IntelliJ may need "Generate Sources Root" mark | Usually auto-detected after Maven import |
| Confluent platform version coupling | We bumped 7.5 → 7.6 to align with Kafka 3.6.x |
| Test complexity | `mock://` URL convention + serde wiring; well-isolated though |
| Lost: human-readable wire payload | Use Kafka UI's "Avro" view or `kafka-avro-console-consumer` to inspect |

### IntelliJ setup note

After pulling these changes, you'll need to:
1. **Reload Maven Project** (right-click pom.xml → Maven → Reload).
2. Run `mvn clean compile` once to trigger Avro code generation. Output lands in `target/generated-sources/avro/`.
3. If IntelliJ doesn't auto-detect the generated sources, **right-click `target/generated-sources/avro` → Mark Directory as → Generated Sources Root**. (Usually unnecessary; the avro-maven-plugin updates Maven's source roots automatically.)
4. Restart docker: `docker compose down -v && docker compose up -d`. Wait ~45 seconds.
5. Re-create topics (because `down -v` wipes volumes).

### Score impact

- **Schema Registry / Avro** capability: 0/10 → 9/10
- **Kafka concept demonstration:** 9/10 → 9.5/10
- **Production-readiness:** 7/10 → 8/10
- **Banking-domain seriousness:** 8.5/10 → 9/10
- **Documentation quality:** 8/10 → 8.5/10
- **Senior-eng scrutiny survival:** 9/10 → 9.5/10
- **OVERALL:** 8.5/10 → **9.0/10**

---

## All Major Issues Resolved 🎉

🔴 **Critical (4/4 resolved)** + 🟠 **Major (4/4 resolved)** = **8 of 8 ranked issues complete.**

Remaining minor polish (from original audit) — none of these block interview-ready status:
- Spring Actuator + Prometheus metrics
- Per-service Dockerfiles + K8s manifests
- MDC correlation ID propagation
- Late-event grace period on tumbling windows
- Alert deduplication in fraud detection
- OpenAPI/Swagger docs
- CI/CD pipeline (GitHub Actions)
- Testcontainers for end-to-end DB testing

Pick any of these for further polish, or stop here — the project has reached a strong **9.0/10**.
