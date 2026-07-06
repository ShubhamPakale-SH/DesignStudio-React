# Changes Made in This Session — 2026-07-06

> Two features added: **Change #8 — GEO_IMPOSSIBLE fraud rule + window grace period** and
> **Change #9 — closed fraud loop (alerts freeze accounts)**.
> These correspond to improvement points 4 and 5 from the fintech-readiness review.

---

## Change #8 — GEO_IMPOSSIBLE fraud rule + late-event grace period

**Severity before:** 🟠 Major (the `city` field existed in the schema but no rule ever read it — an obvious interview question)

### What was added

**Rule D — GEO_IMPOSSIBLE** in `FraudDetectionTopology`:
- A new Kafka Streams state store (`last-location-store`) keeps each account's last-seen city + event timestamp, JSON-serialised, backed by a changelog topic (same fault-tolerance pattern as `rolling-stats-store`).
- On every transaction, a `GeoImpossibleTransformer` compares the incoming city against the last one. Using the haversine distance between known city coordinates and the time elapsed between the two *event timestamps*, it computes the travel speed that would be required. If it exceeds `geo-max-speed-kmh` (default **900 km/h**, roughly a commercial flight), it emits a `GEO_IMPOSSIBLE` alert.
- ~19 cities are hardcoded (Indian metros + global hubs). **Unknown cities are skipped, never alerted on** — no false positives from typos.
- Out-of-order events (negative elapsed time) are treated as instantaneous travel → alert. You can't be in two cities at once.

**Grace period for late events:**
- The HIGH_VELOCITY tumbling window changed from `ofSizeWithNoGrace(60s)` to `ofSizeAndGrace(60s, 10s)` (`window-grace-seconds`, default 10).
- Events arriving up to 10s late (by event time) still count toward their original window instead of being silently dropped.

### Config (fraud-detection/application.yml)

```yaml
app:
  fraud:
    window-grace-seconds: 10
    geo-max-speed-kmh: 900
```

### Schema change

`schemas/avro/Alert.avsc`: `RuleType` enum gained the `GEO_IMPOSSIBLE` symbol.
Adding an enum symbol is **BACKWARD-compatible** — Schema Registry (mode `BACKWARD`) accepts it; the new reader schema can still read all old data.

### Files changed

| File | Change |
|---|---|
| `schemas/avro/Alert.avsc` | Added `GEO_IMPOSSIBLE` to `RuleType` |
| `fraud-detection/.../model/LastLocation.java` | **NEW** — city + timestamp state |
| `fraud-detection/.../streams/FraudDetectionTopology.java` | New store, `GeoImpossibleTransformer` (haversine + speed check), grace period, 2 new config params in `buildTopology(...)` |
| `fraud-detection/src/main/resources/application.yml` | `window-grace-seconds`, `geo-max-speed-kmh` |
| `fraud-detection/.../streams/FraudDetectionTopologyTest.java` | 4 new tests (see below) |

### Tests added

- `impossibleTravel_emitsGeoAlert` — Mumbai → London 10 min apart ⇒ `GEO_IMPOSSIBLE` alert with the second txn's id
- `plausibleTravel_noAlert` — Mumbai → Pune 2h apart (~60 km/h) ⇒ no alert
- `unknownCity_noGeoAlert` — unknown origin city ⇒ skipped, no alert
- `lateEventWithinGrace_stillCounted` — a 6th event arriving late (stream time already past window end, but within grace) still triggers HIGH_VELOCITY with count=6

### Interview talking points

- *"I keep last-seen location per account in a state store and flag consecutive transactions that would require faster-than-flight travel — the classic cloned-card signal."*
- *"I use event time, not processing time, so replays and consumer lag don't produce false positives."*
- *"The window has a 10-second grace period, so events delayed by the network still count toward their true window — with no grace, Kafka Streams silently drops late records."*

---

## Change #9 — Closed fraud loop: alerts freeze accounts

**Severity before:** 🟠 Major ("detection without action is just logging" — alerts went to a topic nobody consumed)

### What was added

- `AccountStatus` enum (`ACTIVE` / `FROZEN`) + `status` column on the `accounts` table and `Account` entity.
- `AlertConsumer` in **account-service** — a second `@KafkaListener` consuming the `alerts` topic (Avro `Alert`, its own consumer group `account-service-group-alerts`).
- `AccountService.freeze(alert)`:
  - Sets `status = FROZEN` on the account named in the alert.
  - **Idempotent** — an already-frozen account is skipped, so a burst that fires dozens of alerts produces exactly one state change (this is the alert dedup).
  - Unknown account → `IllegalStateException` → routed to `alerts.DLT` (audit trail preserved).
- `AccountService.apply()` now rejects **WITHDRAW/TRANSFER on frozen accounts** with `IllegalStateException` → routed to `transactions.DLT`. **Deposits stay allowed** (money in is never the fraud).
- The `DeadLetterPublishingRecoverer` was generalised: DLT is now derived from the source topic (`<topic>.DLT`), so both listeners share one error-handling policy. New topic required: `alerts.DLT`.

### Architecture decision (worth saying in the interview)

The alert consumer was deliberately placed **inside account-service, not a separate action-service**: the `accounts` table must have a **single writer**. Account status is account state, so the service that owns balances also owns freezes. One service per table = no cross-service write conflicts.

### Files changed

| File | Change |
|---|---|
| `account-service/.../model/AccountStatus.java` | **NEW** — ACTIVE / FROZEN |
| `account-service/.../model/Account.java` | Added `status` field (`@Enumerated(STRING)`) |
| `account-service/.../service/AccountService.java` | Frozen check in `apply()`, new `freeze()` method |
| `account-service/.../consumer/AlertConsumer.java` | **NEW** — listener on `alerts` |
| `account-service/.../config/KafkaConsumerConfig.java` | Extracted shared consumer props, added alert consumer + container factories, DLT topic now derived per source topic |
| `account-service/src/main/resources/application.yml` | Added `app.topic.alerts`; removed obsolete `transactions-dlt` |
| `account-service/src/test/resources/application-test.yml` | Same |
| `init-db.sql` | `status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'` on `accounts` |
| `README.md` | Updated diagram, topic creation (alerts.DLT), freeze + geo demo steps, concepts table |

### Tests added

Unit (`AccountServiceTest`, now 10 tests):
- `freeze_setsStatusFrozen`
- `freeze_skips_whenAlreadyFrozen` (idempotency / dedup)
- `withdraw_rejected_whenAccountFrozen`
- `deposit_allowed_whenAccountFrozen`

Integration (`TransactionFlowIntegrationTest`, now 3 tests):
- `fraudAlert_freezesAccount_andBlocksWithdraw` — publishes an Avro `Alert` to `alerts`, awaits `status = FROZEN` in the DB, then proves a WITHDRAW is rejected to `transactions.DLT` with the "frozen" exception header and the balance is unchanged.

### Interview talking points

- *"My fraud pipeline is a closed loop: detect → alert → freeze → block. The alerts topic has a real consumer with a real side effect."*
- *"Freezing is idempotent, which doubles as alert deduplication — 50 alerts from one burst cause one state change."*
- *"Frozen-account rejections aren't dropped; they go to the DLT with exception headers, so compliance can list every blocked transaction."*

---

## ⚠️ Migration notes (do this before running)

1. **Recreate the Postgres volume** — the `accounts` table gained a `status` column and `init-db.sql` only runs on a fresh volume:
   ```bash
   docker compose down -v && docker compose up -d
   ```
2. **Re-create all topics** (down -v wiped them), including the new one:
   ```bash
   docker exec securebank-kafka1 kafka-topics --bootstrap-server kafka1:9092 \
     --create --topic alerts.DLT --partitions 3 --replication-factor 3
   ```
   (plus `transactions`, `alerts`, `transactions.DLT` as before — see README step 2)
3. **Rebuild** so Avro classes regenerate with the new enum symbol:
   ```bash
   mvn clean package -DskipTests
   ```

## ⚠️ Test status

The new/updated tests (**+9 tests**: 4 fraud topology, 4 unit, 1 integration) were written but **not yet executed** in this session — Maven was not available on the shell PATH. Run them before the next commit/demo:

```bash
mvn test
```

Expected: fraud-detection 9 tests, account-service 10 unit + 3 integration, transaction-service 3 — all green.

---

## Demo script for interviews (2 minutes)

1. Send 6 rapid withdrawals for `ACC1002` → fraud-detection logs `FRAUD ALERT [HIGH_VELOCITY]`.
2. `SELECT account_id, status FROM accounts;` → `ACC1002 | FROZEN`.
3. Send one more withdrawal for `ACC1002` → check `transactions.DLT` in Kafka UI → the rejected event with `kafka_dlt-exception-message: Account frozen...`.
4. Send a Mumbai txn then a London txn for `ACC1003` → `FRAUD ALERT [GEO_IMPOSSIBLE]` with the computed km/h in the reason.
