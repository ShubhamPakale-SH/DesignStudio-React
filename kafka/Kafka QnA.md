# Kafka Interview Questions & Answers

## Part 0 — Personal Questions

**1. What happens when the queue size limit is reached?**
Kafka doesn't have a fixed "queue" — it's a disk-backed log with **retention limits**, not in-memory queue limits. But size limits apply at several levels:

- **Topic/partition retention limit (`log.retention.bytes`)** → oldest segments are **deleted** (or compacted) once the limit is hit. New messages keep flowing.
- **Producer buffer (`buffer.memory`)** → if producer's in-memory buffer fills (broker is slow/down), `send()` **blocks** for up to `max.block.ms`, then throws `TimeoutException`.
- **Broker disk full** → broker stops accepting writes for that partition; producers get errors. Mitigate via retention tuning, more brokers, or tiered storage.
- **Consumer lag (no size limit, but unbounded growth)** → consumers fall behind; messages still retained until retention expires, then **lost if not consumed in time**.

**Key point:** Kafka doesn't reject new messages like a bounded queue — it **drops old ones** based on retention policy or **blocks the producer** if buffers/disk are exhausted.

---

**2. What happens to messages when the queue (Kafka broker/cluster) goes down?**
Depends on **what goes down** and **how Kafka is configured**:

- **Single broker down (cluster healthy)** → No data loss. Partition **leadership fails over** to an in-sync follower (ISR). Producers/consumers reconnect to the new leader automatically.
- **Entire cluster down** → Messages already **persisted to disk** are safe. On restart, brokers recover from their logs; consumers resume from last committed offset.
- **In-flight messages (producer side)** → Buffered in producer memory; retried automatically (if `retries > 0`). If `acks=all` + idempotent producer → no loss, no duplicates on retry.
- **Unacknowledged messages** → If `acks=0` and broker crashes before write → **lost**. If `acks=1` and leader crashes before followers replicate → **may be lost**. If `acks=all` + `min.insync.replicas ≥ 2` → **safe**.
- **Consumer side** → Last committed offset is stored in `__consumer_offsets` topic (replicated). On restart, consumer resumes from that offset — no progress lost.

**Key point:** Kafka is **durable by design** — messages on disk survive broker restarts. Loss only happens with weak `acks` settings or if all replicas of a partition are lost simultaneously.

---

**3. What happens when a consumer goes down?**
Kafka handles it gracefully via consumer groups and offset tracking:

- **Group Coordinator detects failure** → consumer misses heartbeats (`session.timeout.ms`) or exceeds `max.poll.interval.ms`.
- **Rebalance triggered** → the failed consumer's partitions are **reassigned to other consumers** in the same group.
- **No message loss** → new owner resumes from the **last committed offset** stored in `__consumer_offsets`.
- **Possible duplicates** → if the consumer processed messages but **crashed before committing**, those messages will be re-delivered (at-least-once). Mitigate with idempotent processing.
- **If it's the only consumer in the group** → partitions sit unassigned until the consumer (or a new one) joins. Messages **accumulate on the broker** (retained per retention policy) — nothing is lost.
- **When consumer restarts** → rejoins group → triggers another rebalance → resumes from last committed offset.

**Key point:** Consumer failures don't lose data — Kafka **persists messages independently of consumers**. Worst case is **duplicates** if processing wasn't idempotent, or **lag growth** if no replacement consumer joins.

---

**4. What happens when a consumer is not able to process a message (e.g., bad data, downstream failure)?**
Kafka itself doesn't care — it's the **consumer's responsibility** to handle failures. Common strategies:

- **Retry in-place** → catch the exception, retry N times with backoff. Risky: blocks the partition; if it keeps failing, lag grows ("poison pill" problem).
- **Skip and commit** → log the error, commit the offset, move on. Simple but **loses the message** silently. Bad for banking/critical data.
- **Dead Letter Topic (DLT)** → publish the failed message (with error metadata: exception, stacktrace, offset, partition) to a separate `topic.DLT` topic, then commit offset and move on. **Most production-grade pattern.**
- **Retry topics (tiered retries)** → publish to `topic.retry.5s` → `topic.retry.30s` → `topic.retry.5m` → `topic.DLT`. Used by Uber, LinkedIn for transient failures.
- **Pause & alert** → stop consuming the partition, alert ops, manual intervention. Used when failure indicates a systemic issue.
- **Stop the consumer entirely** → fail-fast; appropriate when message loss is unacceptable and processing must halt until fixed.

**Offset commit rule:**
- Commit **after** successful processing → at-least-once (may duplicate on retry).
- Commit **before** processing → at-most-once (may lose on failure).
- Use **manual commit** for control.

**Key point:** For **banking/financial systems**, never silently skip. Always route to **DLT** with full context, alert, and have a **replay tool** to reprocess after fixing the root cause.

---

**5. How does retry work in Kafka? What are the different ways to retry?**
Retries happen at **two levels** — producer side and consumer side — and there are several patterns.

### A. Producer-side retries (built-in)
- **`retries`** config → number of retry attempts on transient failures (default: `Integer.MAX_VALUE`).
- **`retry.backoff.ms`** → wait between retries (default 100ms).
- **`delivery.timeout.ms`** → total time bound for a send including retries.
- Triggered by: `NotLeaderForPartition`, network blips, broker timeouts, etc.
- **With `enable.idempotence=true`** → retries are safe (no duplicates).
- **Without idempotence** → retries can cause **duplicates** and **reordering**.

### B. Consumer-side retries (you implement)

**1. In-memory retry (simple loop)**
- Catch exception → `Thread.sleep(backoff)` → retry N times.
- Blocks the partition during retry → grows lag if many failures.
- OK for **transient downstream failures** (DB hiccup) with short backoff.

**2. Stateless re-poll (don't commit offset)**
- On failure, don't commit → next poll re-reads the same message.
- Combined with `pause()`/`resume()` to throttle.
- Risk: infinite loop on poison pill.

**3. Dead Letter Topic (DLT) — single-step**
- Failed message → publish to `<topic>.DLT` → commit offset.
- Separate consumer or manual tool drains DLT later.
- **Most common production pattern.**

**4. Tiered retry topics (delayed retry)**
- `orders` → fails → `orders.retry.5s` → fails → `orders.retry.30s` → fails → `orders.retry.5m` → `orders.DLT`.
- Each retry topic has its own consumer that **waits before processing** (based on message timestamp).
- Decouples retry delay from main consumer throughput.
- Used by **Uber, LinkedIn, Confluent** for resilient pipelines.

**5. Spring Kafka `@RetryableTopic`**
- Auto-creates retry topics + DLT.
- Configurable: `attempts`, `backoff`, `exclude` exceptions.
- Example:
  ```java
  @RetryableTopic(attempts = "4", backoff = @Backoff(delay = 1000, multiplier = 2.0))
  @KafkaListener(topics = "orders")
  public void consume(Order order) { ... }
  ```

**6. External scheduler / delay queue**
- Publish failed message to a DB/Redis with `retryAt` timestamp.
- Cron job re-publishes to Kafka when time is due.
- Useful for **long delays** (hours/days) where retry topics aren't practical.

### Retry pitfalls to avoid
- **Blocking retries on the main consumer** → kills throughput, grows lag.
- **No max retry limit** → poison pills loop forever.
- **No jitter in backoff** → thundering herd on downstream recovery.
- **Retrying non-retryable errors** (validation failures, 4xx) → wasted cycles. Always classify: **transient vs permanent**.

**Key point:** Producer retries are **automatic and idempotent-safe**. Consumer retries are **your responsibility** — pick **DLT + tiered retry topics** for production-grade resilience.

---

**6. How does a distributed messaging queue work?**
A distributed messaging queue (Kafka, RabbitMQ cluster, Pulsar, SQS, etc.) is a **cluster of broker nodes** that accept, store, replicate, and deliver messages between producers and consumers — designed for **scale, fault tolerance, and decoupling**.

### Core working principles

**1. Producer → Broker**
- Producer connects to any broker (bootstrap server) → gets cluster metadata (which broker owns which partition/queue).
- Producer sends message to the **leader broker** of the target partition.
- Acknowledgement returned based on durability config (`acks=0/1/all`).

**2. Storage & Replication**
- Each broker stores messages on **local disk** (append-only log in Kafka, queue files in RabbitMQ).
- Messages are **replicated** to N other brokers for fault tolerance.
- A **leader** handles reads/writes; **followers** stay in sync (ISR in Kafka, mirror queues in RabbitMQ).
- If leader dies → follower is promoted via **consensus** (ZooKeeper, KRaft, Raft, Paxos).

**3. Partitioning / Sharding**
- A topic/queue is split into **partitions (shards)** distributed across brokers.
- Enables **horizontal scaling** — each partition can be on a different broker.
- Messages with the same **key** go to the same partition → preserves order per key.

**4. Consumer → Broker**
- Consumers either **pull** (Kafka, Pulsar) or get **pushed** (RabbitMQ, SQS) messages.
- **Consumer groups** distribute partitions across consumers for parallel consumption.
- Each consumer tracks its **offset** (position) → can resume after restart.

**5. Coordination & Metadata**
- A coordination service (**ZooKeeper, KRaft, etcd, Raft quorum**) manages:
  - Broker membership (who's alive)
  - Leader election for partitions
  - Configuration changes
  - Consumer group rebalancing

**6. Delivery Guarantees**
- **At-most-once** → fire and forget (may lose).
- **At-least-once** → retry until ack (may duplicate).
- **Exactly-once** → idempotency + transactions (Kafka EOS, RabbitMQ confirms + dedup).

### Conceptual Flow

```
   Producer ──► [Load Balancer / Metadata Lookup]
                          │
                          ▼
              ┌───────────────────────────┐
              │  Distributed Broker Pool   │
              │  ┌────┐  ┌────┐  ┌────┐    │
              │  │ B1 │  │ B2 │  │ B3 │    │  ← partitions sharded
              │  └────┘  └────┘  └────┘    │     + replicated
              │       Replication           │
              └──────────────┬──────────────┘
                             │
                Coordinator (ZK / KRaft / Raft)
                             │
                             ▼
                    Consumer Group(s)
                  pull / push messages
                  track offsets / acks
```

### Why distributed (vs single-node queue)?

| Property | Single-node Queue | Distributed Queue |
|---|---|---|
| Throughput | Limited by 1 box | Scales horizontally |
| Fault tolerance | SPOF | Replicas → no SPOF |
| Storage | Bounded by 1 disk | Aggregate of cluster |
| Ordering | Global (easy) | Per partition (trade-off) |
| Complexity | Low | High (consensus, rebalancing) |

### Key challenges solved
- **Fault tolerance** → replication + leader election.
- **Scalability** → partitioning + consumer groups.
- **Ordering** → per-partition guarantee (sacrifice global order).
- **Durability** → disk persistence + replication.
- **Consistency** → consensus protocols (Raft, ZAB).
- **Backpressure** → buffering, pull-based consumers, flow control.

**Key point:** A distributed messaging queue is essentially a **replicated, sharded, append-only log** with a **coordination layer** that handles failures, scaling, and ordering trade-offs — enabling decoupled, asynchronous, resilient communication between services.

---

**7. What is a Dead Letter Queue (DLQ)?**
A **Dead Letter Queue** (or Dead Letter Topic in Kafka) is a **separate queue/topic where messages that cannot be processed successfully are routed**, so the main consumer pipeline keeps flowing instead of getting stuck.

### Why it exists
Without a DLQ, a single bad message ("poison pill") can:
- Block the partition (consumer keeps failing on it).
- Cause infinite retry loops.
- Grow consumer lag indefinitely.
- Crash the consumer repeatedly.

### When messages go to DLQ
- **Deserialization failure** → malformed JSON/Avro, schema mismatch.
- **Validation failure** → required field missing, business rule violation.
- **Downstream failure after N retries** → DB/API unavailable beyond retry budget.
- **Unsupported message type** → unknown event version.
- **TTL/expiry** → message too old to process meaningfully.

### What gets stored in the DLQ
Best practice is to enrich the message with **failure context**:
- Original payload
- Original topic/partition/offset
- Exception type and stack trace
- Timestamp of failure
- Number of retry attempts
- Consumer/service that failed

### Typical flow

```
   ┌──────────┐
   │ Producer │
   └────┬─────┘
        ▼
  ┌──────────────┐      success     ┌──────────────┐
  │ Main Topic   │ ───────────────► │  Consumer    │
  │  "orders"    │                  │  processes   │
  └──────────────┘                  └──────┬───────┘
                                           │ failure (after retries)
                                           ▼
                                  ┌──────────────────┐
                                  │  DLQ Topic       │
                                  │  "orders.DLT"    │
                                  └──────────────────┘
                                           │
                                           ▼
                                  ┌──────────────────┐
                                  │ Alerting / Manual│
                                  │ Inspection /     │
                                  │ Replay Tool      │
                                  └──────────────────┘
```

### What to do with DLQ messages
1. **Alert** → notify ops team (Slack, PagerDuty) when DLQ grows.
2. **Inspect** → review failure metadata to find root cause.
3. **Fix** → patch consumer code, bad data, or downstream system.
4. **Replay** → re-publish DLQ messages back to main topic after fix.
5. **Archive/Discard** → for messages that are unrecoverable.

### In Kafka specifically
- DLQ is just **another Kafka topic** (no special type).
- Naming convention: `<original-topic>.DLT` or `<original-topic>.dead-letter`.
- Spring Kafka's `@RetryableTopic` and `DeadLetterPublishingRecoverer` automate routing.
- Kafka Connect has built-in DLQ support (`errors.deadletterqueue.topic.name`).

### DLQ vs Retry Topic
| Aspect | Retry Topic | Dead Letter Queue |
|---|---|---|
| Purpose | Temporary, will be retried | Terminal, needs human intervention |
| Delay | Delayed processing | No automatic processing |
| Outcome | Hopefully success | Manual inspection / replay |

**Key point:** A DLQ is the **safety net** of any production message pipeline — it isolates bad messages so they don't block good ones, while preserving them with full context for debugging, alerting, and recovery. **Mandatory for banking/financial systems** where silently dropping messages is unacceptable.

---

## Part 1 — 30 Most Asked Kafka Interview Questions

### Basics

**1. What is Apache Kafka?**
A distributed, fault-tolerant, publish-subscribe streaming platform that handles high-throughput, real-time data feeds using an append-only commit log.

**2. What are the core components of Kafka?**
Producer, Consumer, Broker, Topic, Partition, Replica, Consumer Group, and ZooKeeper/KRaft.

**3. What is a Topic?**
A named, append-only log/category to which producers write and consumers subscribe. Topics are split into partitions.

**4. What is a Partition?**
An ordered, immutable sequence of messages within a topic. Enables parallelism and scalability. Order is guaranteed only within a partition.

**5. What is an Offset?**
A unique, sequential ID assigned to each message within a partition. Consumers use it to track their read position.

---

### Cluster & Brokers

**6. What is a Broker?**
A Kafka server that stores data and serves client requests. A cluster has multiple brokers; each handles many partitions.

**7. What is the role of ZooKeeper in Kafka?**
Manages cluster metadata, broker registry, leader election, and configs. Being replaced by **KRaft** (Kafka Raft) in newer versions (2.8+).

**8. What is KRaft?**
Kafka's built-in consensus protocol that removes the ZooKeeper dependency — Kafka manages its own metadata via a Raft quorum.

**9. What is a Leader and Follower?**
For each partition, one replica is the **Leader** (handles all reads/writes); others are **Followers** that replicate data. On leader failure, a follower is promoted.

**10. What is ISR (In-Sync Replicas)?**
The set of replicas that are fully caught up with the leader. Only ISR members are eligible to become leader.

---

### Producers

**11. How does a producer decide which partition to write to?**
- If a **key** is provided → hash(key) % numPartitions
- If no key → round-robin (or sticky partitioner)
- Or a **custom partitioner**

**12. What are `acks` settings in producers?**
- `acks=0` → fire and forget (fastest, may lose data)
- `acks=1` → leader acknowledges (default)
- `acks=all` → leader + all ISR ack (safest, durable)

**13. What is an idempotent producer?**
A producer that ensures messages are written **exactly once** to a partition, even on retries. Enabled via `enable.idempotence=true`.

**14. What is a Kafka transaction?**
Atomic writes across multiple partitions/topics — either all succeed or none. Enables **exactly-once semantics (EOS)** end-to-end.

---

### Consumers

**15. What is a Consumer Group?**
A set of consumers sharing a `group.id` that collectively consume a topic. Kafka assigns partitions across members so each partition is read by only one consumer in the group.

**16. Can two consumers in the same group read the same partition?**
**No.** Within a group, each partition is consumed by exactly one consumer. Across different groups, yes.

**17. What is a consumer rebalance?**
Reassignment of partitions to consumers when a consumer joins/leaves the group or partitions change. Coordinated by the **Group Coordinator** broker.

**18. What is offset commit? Auto vs Manual?**
Saving the last-read offset so consumers resume after restart.
- **Auto**: periodic, simple, risk of duplicates/loss
- **Manual**: explicit (`commitSync` / `commitAsync`), full control

**19. Push or Pull model?**
**Pull** — consumers pull batches from brokers at their own pace, preventing overload.

---

### Reliability & Performance

**20. How does Kafka ensure durability?**
Messages are persisted to disk (sequential I/O) and replicated across brokers. With `acks=all` and `min.insync.replicas`, data isn't lost on broker failure.

**21. What is replication factor?**
Number of copies of each partition across brokers. RF=3 means 1 leader + 2 followers. Typical production setting.

**22. What delivery guarantees does Kafka offer?**
- **At-most-once** — may lose messages
- **At-least-once** (default) — may duplicate
- **Exactly-once** — idempotent producer + transactions

**23. Why is Kafka so fast?**
- Sequential disk I/O (append-only log)
- Zero-copy transfer (`sendfile`)
- Batching & compression
- OS page cache
- Partitioning for parallelism

**24. What is log compaction?**
A retention policy that keeps **only the latest value per key** instead of deleting by time/size — useful for change-data-capture and stateful stores.

**25. What is retention policy?**
How long Kafka keeps messages. Configured by **time** (`log.retention.hours`) or **size** (`log.retention.bytes`). Default: 7 days.

---

### Ecosystem & Operations

**26. What is Kafka Connect?**
A framework for streaming data between Kafka and external systems (DBs, S3, ES) using reusable **source/sink connectors** — no code required.

**27. What is Kafka Streams?**
A lightweight Java client library for building **stream processing applications** (filter, join, aggregate) directly on Kafka topics.

**28. How is Kafka different from RabbitMQ/JMS?**
| Kafka | RabbitMQ |
|---|---|
| Distributed log, pull-based | Broker/queue, push-based |
| High throughput, replayable | Lower throughput, transient |
| Stream platform | Traditional message queue |

**29. How do you scale Kafka consumers?**
Add more consumers to the group (up to the number of partitions). Beyond that, consumers sit idle — so **plan partition count up front**.

**30. What happens if a broker goes down?**
- ZooKeeper/KRaft detects failure
- Leadership for that broker's partitions transfers to in-sync followers
- Producers/consumers automatically reconnect to new leaders
- No data loss if `acks=all` and `min.insync.replicas ≥ 2`

---

## Part 2 — Advanced Topics

### Exactly-Once Semantics (EOS)

**1. How does Kafka achieve exactly-once semantics?**
Three pieces together:
- **Idempotent producer** (`enable.idempotence=true`) → no duplicates on retry (uses Producer ID + sequence number).
- **Transactions** (`transactional.id`) → atomic multi-partition writes.
- **`isolation.level=read_committed`** on consumer → reads only committed transactional messages.

**2. What is a Producer ID (PID) and sequence number?**
Broker assigns each producer a unique **PID**; producer tags each message with a monotonically increasing **sequence number** per partition. Broker discards duplicates with the same `(PID, seq)`.

**3. What is the transaction coordinator?**
A broker that manages transaction state (begin, commit, abort) via the internal `__transaction_state` topic. Producer registers via `transactional.id`.

---

### Rebalancing

**4. What triggers a consumer rebalance?**
- Consumer joins/leaves the group
- Consumer fails heartbeat (`session.timeout.ms`)
- Topic metadata changes (new partitions)
- Subscription changes

**5. What is the "stop-the-world" problem in rebalancing?**
During a classic rebalance, **all consumers pause processing** until partitions are reassigned — causing latency spikes.

**6. What is Cooperative (Incremental) Rebalancing?**
Introduced in Kafka 2.4. Only **affected partitions** are revoked/reassigned; unaffected consumers keep processing. Set `partition.assignment.strategy=CooperativeStickyAssignor`.

**7. What are the partition assignment strategies?**
- **RangeAssignor** (default) — assigns contiguous partitions per topic
- **RoundRobinAssignor** — balanced across all subscribed topics
- **StickyAssignor** — minimizes movement on rebalance
- **CooperativeStickyAssignor** — sticky + incremental

---

### Kafka Streams & Connect

**8. What's the difference between Kafka Streams and Kafka Consumer API?**
Streams provides higher-level **DSL** (map, filter, join, aggregate, windowing), **state stores** (RocksDB), and **exactly-once** processing — built on top of consumer/producer.

**9. What is a KTable vs KStream?**
- **KStream** — record stream (every event matters, append-only)
- **KTable** — changelog stream (latest value per key, like an updatable table)

**10. What is a state store?**
Local, embedded **RocksDB** instance in a Streams app that holds aggregations/joins. Backed by a Kafka **changelog topic** for fault tolerance.

**11. What is the difference between source and sink connectors?**
- **Source** — pulls data **into** Kafka (e.g., JDBC → Kafka)
- **Sink** — pushes data **out of** Kafka (e.g., Kafka → S3/Elasticsearch)

**12. What is Schema Registry?**
A service (Confluent) that stores **Avro/Protobuf/JSON schemas** for topics, enforces compatibility (BACKWARD/FORWARD/FULL), and enables safe schema evolution.

---

### Performance & Tuning

**13. What is consumer lag and how do you monitor it?**
`lag = log-end-offset − committed-offset` per partition.
Monitor via: `kafka-consumer-groups.sh`, **Burrow**, **Cruise Control**, JMX, or Prometheus exporters.

**14. How do you tune producer throughput?**
- Increase `batch.size` (e.g., 32KB+)
- Increase `linger.ms` (5–20ms) to batch more
- Enable `compression.type=lz4/snappy/zstd`
- Increase `buffer.memory`
- Use async `send()`

**15. How do you tune consumer throughput?**
- Increase `fetch.min.bytes` and `fetch.max.wait.ms`
- Increase `max.poll.records`
- Add more consumers (up to partition count)
- Process messages in parallel threads (careful with offset commits)

**16. What is `min.insync.replicas`?**
Minimum replicas that must ack a write when `acks=all`. With RF=3 and `min.insync.replicas=2`, you tolerate 1 broker down without losing availability or durability.

**17. What is unclean leader election?**
Allowing an **out-of-sync** replica to become leader when no ISR is available. Trades **data loss** for **availability**. Set `unclean.leader.election.enable=false` for safety in production.

---

### Storage & Internals

**18. What is a segment file?**
Each partition log is split into **segment files** on disk (`.log`, `.index`, `.timeindex`). Old segments are deleted/compacted per retention policy.

**19. How does Kafka achieve zero-copy?**
Uses Linux's `sendfile()` syscall to transfer bytes from page cache directly to socket — bypassing user space → huge performance gain.

**20. What is tiered storage?**
Newer Kafka feature (KIP-405) — older log segments offloaded to **object storage** (S3, GCS) while recent data stays on local disk. Enables near-infinite retention cheaply.

---

## Part 3 — Scenario-Based Questions

**21. Scenario: Your consumer lag is growing continuously. How do you debug?**
1. Check **consumer throughput** vs **producer rate**
2. Inspect **slow processing** (downstream DB, API)
3. Check **GC pauses** / **rebalances** in consumer logs
4. Increase **consumer instances** (up to partition count) or **partition count**
5. Tune `max.poll.records`, `fetch.min.bytes`
6. Consider parallel processing within consumer

---

**22. Scenario: A consumer in your group is processing messages slower than others, causing lag on its partitions only. What do you do?**
- Check for **hot partitions** (skewed key distribution)
- Use a better partitioning key, or
- Repartition the topic with more partitions and better hash distribution
- Move slow downstream work to async pipeline

---

**23. Scenario: You need strict ordering of events for a customer. How?**
Use the **customer ID as the message key** → all events for that customer land in the **same partition**, preserving order. Don't rely on cross-partition ordering.

---

**24. Scenario: You accidentally deployed a buggy consumer that committed offsets without processing. How do you reprocess?**
1. Stop the consumer group
2. Reset offsets using `kafka-consumer-groups.sh --reset-offsets`:
   - `--to-earliest`, `--to-datetime`, or `--shift-by -N`
3. Restart consumers — they replay from the new offset

---

**25. Scenario: A producer is timing out / failing to send. What could be wrong?**
- Broker down or unreachable (network, DNS)
- `acks=all` + ISR shrunk below `min.insync.replicas`
- `request.timeout.ms` too low
- Producer buffer full (`buffer.memory` exhausted, slow brokers)
- Authentication/ACL issue

---

**26. Scenario: How do you migrate a topic to a different cluster with zero downtime?**
Use **MirrorMaker 2** (or Confluent Replicator):
1. Start MM2 replicating source → target
2. Wait for lag to drain
3. Switch consumers to target cluster
4. Switch producers to target cluster
5. Decommission source

---

**27. Scenario: You need to process events with a 5-minute delay (e.g., fraud check window). How?**
Options:
- Use **Kafka Streams windowed aggregations** with `suppress()` to emit only after window closes
- Maintain a delay topic; consumer checks event timestamp and re-queues if too early
- Use external scheduler / DB queue

---

**28. Scenario: A partition's leader broker crashed. What happens to in-flight produces?**
- Producers get `NotLeaderForPartition` exception
- Controller (via ZK/KRaft) elects new leader from ISR
- Producer **retries** automatically (if retries > 0) to new leader
- With `acks=all` + idempotent producer → no loss, no duplicates

---

**29. Scenario: You need to add partitions to a live topic. What's the risk?**
- **Breaks message ordering by key** — existing keys may hash to different partitions after the change
- Don't add partitions on keyed topics in production unless ordering is non-critical
- Better: **pre-provision** enough partitions upfront

---

**30. Scenario: Your team needs exactly-once payments processing from Kafka → DB. How?**
Two patterns:
- **Kafka Transactions + Idempotent consumer**: store offset and DB write atomically (write offset to DB in same transaction; on restart, read offset from DB).
- **Outbox pattern with deduplication**: tag each message with a unique ID; DB enforces uniqueness constraint to drop duplicates.

---

**31. Scenario: Disk is filling up on a broker. What do you do?**
- Reduce `log.retention.hours` / `log.retention.bytes`
- Enable **log compaction** for keyed topics
- Add brokers and rebalance partitions (`kafka-reassign-partitions.sh`)
- Enable **tiered storage** if available
- Increase disk size (short-term)

---

**32. Scenario: Producer sends 1M messages/sec but consumers can only handle 100K/sec. How do you handle the backpressure?**
- Kafka **buffers** in the broker — no immediate failure
- Scale consumers horizontally (add instances + partitions)
- Use **dead letter queues** for poison messages
- Apply **rate limiting** at producer if downstream truly can't keep up
- Consider **batch processing** windows downstream

---

**33. Scenario: You see frequent rebalances slowing down processing. Fix?**
- Increase `session.timeout.ms` and `heartbeat.interval.ms`
- Increase `max.poll.interval.ms` (slow processing may cause leave-group)
- Reduce `max.poll.records` so each poll cycle completes faster
- Switch to **CooperativeStickyAssignor**
- Investigate consumer GC pauses / network issues

---

**34. Scenario: How would you design a system for real-time order tracking using Kafka?**
- Topic: `orders` partitioned by `orderId` (ordering per order)
- Producers: order service, shipment service, payment service
- Consumers: **Kafka Streams** app joins order+payment+shipment streams → materialized view in **KTable**
- Expose via REST API querying state store, or sink to a low-latency store (Redis/Elasticsearch)
- Use **exactly-once** for payment events

---

**35. Scenario: A consumer keeps crashing on a "poison pill" message. How do you handle it?**
- Wrap deserialization/processing in try-catch
- Send the bad message to a **Dead Letter Topic (DLT)** with metadata (error, offset, partition)
- **Commit the offset** to skip past it
- Investigate DLT offline; replay if fixable

---

## Key Signals Interviewers Look For

- You understand **ordering guarantees** are per-partition only.
- You can reason about **trade-offs** (latency vs durability, throughput vs ordering).
- You know **EOS isn't free** — it adds overhead and complexity.
- You think about **operational concerns** (lag, rebalances, disk, monitoring), not just APIs.
