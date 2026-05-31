# Kafka & Its Architecture

## Overview

Apache Kafka is a distributed, fault-tolerant, publish-subscribe streaming platform that handles high-throughput, real-time data feeds using an append-only commit log.

---

## Core Components

### 1. Producer
- Client application that publishes (writes) messages to Kafka topics.
- Decides which partition a message goes to (round-robin, key-hash, or custom).

### 2. Consumer
- Client application that subscribes to topics and processes messages.
- Belongs to a **Consumer Group** — Kafka distributes partitions across group members for parallel consumption.

### 3. Broker
- A Kafka server. A cluster is made of multiple brokers (typically 3+).
- Stores data, serves producer writes and consumer reads.
- Each broker handles thousands of partitions.

### 4. Topic
- A named logical channel/category for messages (e.g., `orders`, `payments`).
- Append-only, immutable log.

### 5. Partition
- Topics are split into partitions for scalability and parallelism.
- Each partition is an ordered, immutable sequence of messages.
- Messages within a partition have a unique **offset** (sequential ID).
- Ordering is guaranteed *within* a partition, not across partitions.

### 6. Replica (Leader & Follower)
- Each partition is replicated across N brokers (replication factor).
- One replica is the **Leader** (handles all reads/writes); others are **Followers** (replicate data).
- If a leader fails, a follower is promoted (ISR — In-Sync Replicas).

### 7. ZooKeeper / KRaft
- Older versions: **ZooKeeper** manages cluster metadata, leader election, configs.
- Newer versions (2.8+): **KRaft** (Kafka Raft) replaces ZooKeeper — Kafka manages its own metadata.

### 8. Consumer Offset
- Tracks the last message a consumer group has read in each partition.
- Stored in an internal Kafka topic `__consumer_offsets`.

### 9. Kafka Connect / Kafka Streams (ecosystem)
- **Connect**: framework for moving data in/out of Kafka (DBs, S3, etc.).
- **Streams**: client library for stream processing on top of Kafka.

---

## Message Flow

```
 Producer ──► Broker (Leader Partition) ──► Followers (replicate)
                       │
                       ▼
              Persistent Log (disk)
                       │
                       ▼
 Consumer Group ◄─ Pull messages (by offset) ◄─ Broker
```

### Step-by-step

1. **Produce**
   - Producer serializes the message → picks a partition (key hash or RR) → sends to the **leader broker** of that partition.
   - Producer waits for `acks` (0 = fire-and-forget, 1 = leader ack, `all` = leader + ISR ack).

2. **Store & Replicate**
   - Leader appends the message to its log file on disk (sequential I/O — very fast).
   - Followers pull from leader and replicate. Once in ISR, message is "committed."

3. **Consume**
   - Consumer subscribes to a topic and joins a consumer group.
   - **Group Coordinator** (a broker) assigns partitions to consumers in the group (rebalance).
   - Consumer **pulls** batches of messages from the leader, starting at its last committed offset.
   - After processing, consumer **commits the offset** (auto or manual).

4. **Fault Tolerance**
   - If a broker dies, leadership of its partitions transfers to an in-sync follower.
   - Consumers resume from the last committed offset — no data loss (with `acks=all`).

---

## Key Guarantees
- **Order:** preserved per partition.
- **Durability:** messages persisted to disk + replicated.
- **At-least-once** by default; **exactly-once** with idempotent producers + transactions.
- **Horizontal scale:** add partitions/brokers/consumers.

---

## Architecture Diagram

```
                           ┌─────────────────────────────────────────┐
                           │           PRODUCERS                     │
                           │  ┌─────────┐  ┌─────────┐  ┌─────────┐  │
                           │  │Producer1│  │Producer2│  │Producer3│  │
                           │  └────┬────┘  └────┬────┘  └────┬────┘  │
                           └───────┼────────────┼────────────┼───────┘
                                   │            │            │
                                   │  (1) Publish (key, value, topic)
                                   ▼            ▼            ▼
        ┌────────────────────────────────────────────────────────────────────┐
        │                       KAFKA CLUSTER                                │
        │                                                                    │
        │  ┌───────────────────┐ ┌───────────────────┐ ┌───────────────────┐ │
        │  │    Broker 1       │ │    Broker 2       │ │    Broker 3       │ │
        │  │ ───────────────── │ │ ───────────────── │ │ ───────────────── │ │
        │  │ Topic: orders     │ │ Topic: orders     │ │ Topic: orders     │ │
        │  │                   │ │                   │ │                   │ │
        │  │ ┌───────────────┐ │ │ ┌───────────────┐ │ │ ┌───────────────┐ │ │
        │  │ │ Partition 0   │ │ │ │ Partition 0   │ │ │ │ Partition 0   │ │ │
        │  │ │  ★ LEADER     │◄┼─┼─┤   Follower    │◄┼─┼─┤   Follower    │ │ │
        │  │ │ [0,1,2,3,4..] │ │ │ │  (replicates) │ │ │ │  (replicates) │ │ │
        │  │ └───────────────┘ │ │ └───────────────┘ │ │ └───────────────┘ │ │
        │  │                   │ │                   │ │                   │ │
        │  │ ┌───────────────┐ │ │ ┌───────────────┐ │ │ ┌───────────────┐ │ │
        │  │ │ Partition 1   │ │ │ │ Partition 1   │ │ │ │ Partition 1   │ │ │
        │  │ │   Follower    │ │ │ │  ★ LEADER     │ │ │ │   Follower    │ │ │
        │  │ └───────────────┘ │ │ └───────────────┘ │ │ └───────────────┘ │ │
        │  │                   │ │                   │ │                   │ │
        │  │ ┌───────────────┐ │ │ ┌───────────────┐ │ │ ┌───────────────┐ │ │
        │  │ │ Partition 2   │ │ │ │ Partition 2   │ │ │ │ Partition 2   │ │ │
        │  │ │   Follower    │ │ │ │   Follower    │ │ │ │  ★ LEADER     │ │ │
        │  │ └───────────────┘ │ │ └───────────────┘ │ │ └───────────────┘ │ │
        │  └─────────┬─────────┘ └─────────┬─────────┘ └─────────┬─────────┘ │
        │            │                     │                     │           │
        │            └─────────────────────┼─────────────────────┘           │
        │                                  │                                 │
        │                    ┌─────────────▼─────────────┐                   │
        │                    │  ZooKeeper / KRaft Quorum │                   │
        │                    │  • Cluster metadata       │                   │
        │                    │  • Leader election        │                   │
        │                    │  • Broker registry        │                   │
        │                    └───────────────────────────┘                   │
        └─────────────────────────┬──────────────────────────────────────────┘
                                  │
                                  │  (2) Consumers PULL by offset
                                  ▼
        ┌──────────────────────────────────────────────────────────────┐
        │                  CONSUMER GROUP: "order-service"             │
        │                                                              │
        │   ┌────────────┐      ┌────────────┐      ┌────────────┐     │
        │   │ Consumer A │      │ Consumer B │      │ Consumer C │     │
        │   │  reads P0  │      │  reads P1  │      │  reads P2  │     │
        │   └────────────┘      └────────────┘      └────────────┘     │
        │                                                              │
        │   Offsets committed to → __consumer_offsets (internal topic) │
        └──────────────────────────────────────────────────────────────┘


  ┌────────────────────────── PARTITION INTERNAL VIEW ───────────────────────┐
  │                                                                          │
  │   Append-only log (offsets are sequential, immutable):                   │
  │                                                                          │
  │     ┌────┬────┬────┬────┬────┬────┬────┬────┬────┐                       │
  │     │ 0  │ 1  │ 2  │ 3  │ 4  │ 5  │ 6  │ 7  │ 8  │ ◄── new writes append │
  │     └────┴────┴────┴────┴────┴────┴────┴────┴────┘                       │
  │         ▲              ▲                     ▲                           │
  │         │              │                     │                           │
  │   consumer X       consumer Y         log end offset                     │
  │   (committed=1)    (committed=4)                                         │
  └──────────────────────────────────────────────────────────────────────────┘
```

---

## Flow Legend

| Step | Action |
|------|--------|
| **1** | Producer sends message → routed to **partition leader** (key hash or round-robin) |
| **2** | Leader appends to log on disk → followers replicate (ISR) |
| **3** | Leader acknowledges based on `acks` setting (0 / 1 / all) |
| **4** | Consumer in a group **pulls** messages from leader of assigned partitions |
| **5** | Consumer commits offset → stored in `__consumer_offsets` |
| **6** | On broker failure, ZooKeeper/KRaft promotes an in-sync follower to leader |

---

## Key Visual Takeaways

- **★ LEADER** handles all reads/writes for its partition; **Followers** just replicate.
- **Partitions are distributed** across brokers → horizontal scale.
- **Replication factor = 3** in the diagram (each partition on 3 brokers).
- **One consumer per partition** within a group → parallelism = partition count.
- **ZooKeeper/KRaft** is the brain managing cluster state, not the data path.
