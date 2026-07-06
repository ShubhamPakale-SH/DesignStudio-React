# LinkedIn Post — SecureBank

> Final version, ready to paste. Attach `securebank-kafka-demo.webm` (or the MP4 export) as the video, and add your GitHub repo link before posting.

---

🏦 **I built a real-time fraud detection system — and it actually blocks the fraud.**

Meet **SecureBank** — an event-driven banking platform built the way banks actually run 👇

**🎯 Why Kafka is the perfect fit here:**

→ Every transaction is an **event** — Kafka's durable, replayable log means nothing is ever lost, and history can be reprocessed anytime a new fraud rule ships
→ Partitioning by accountId gives **strict per-account ordering** — deposits and withdrawals always apply in sequence
→ One transaction stream, **two independent consumers** — balance updates and fraud detection scale separately, deploy separately, fail separately
→ Kafka Streams runs **stateful fraud logic right on the stream** — no batch jobs, alerts fire in milliseconds

**⚡ The flow:**
REST API → Kafka (3-broker cluster) → parallel consumers for balance updates + real-time fraud detection

**🕵️ 4 fraud rules on Kafka Streams:**
→ High velocity (card-testing bursts)
→ Large amount (account-drain attempts)
→ Spend spike (5× a customer's rolling average — stateful, per account)
→ Geo-impossible travel (Mumbai → London in 10 minutes? Blocked.)

**🔒 And the loop is closed:**
Fraud detected → alert published → account frozen → withdrawals rejected → audit trail in a dead letter topic. By the time the fraudster tries again, the door is already locked. 🔐

**🛠️ Engineering under the hood:**
✅ Kill a broker mid-load → zero data loss, transactions keep flowing
✅ API returns 201 only when the write is durable on 2+ replicas — no comforting lies
✅ Crash mid-DB-write? Message replays, dedup table stops double-spending
✅ Failed messages never vanish — retries with backoff, then a dead letter topic built for replay
✅ Breaking schema changes rejected at deploy time, not discovered in production
✅ Exactly-once stream processing — fraud state survives crashes via changelog topics

**🌍 This mirrors how real systems work:**
The same event-driven backbone powers India's payment giants — **PhonePe, Razorpay, and Zerodha** all run Kafka at the heart of their transaction pipelines. Building this hands-on is the closest thing to working inside one of those systems.

Tech: Java 17 · Spring Boot · Apache Kafka · Kafka Streams · Avro · PostgreSQL · Docker

🔗 Code: [your GitHub link]

Happy to talk through any design decision 👇

#Kafka #Java #SpringBoot #Fintech #DistributedSystems #EventDrivenArchitecture

---

## Posting checklist

- [ ] Replace `[your GitHub link]` with the actual repo URL
- [ ] Attach the demo video (`securebank-kafka-demo.webm` in this folder, or export MP4 via Clipchamp / re-record with Win+Alt+R)
- [ ] Post on a weekday morning (Tue–Thu, 9–11 AM IST gets the best reach)
- [ ] Reply to every comment in the first 2 hours — boosts the post in the algorithm
