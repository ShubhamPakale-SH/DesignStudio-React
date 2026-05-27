import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Apache Kafka Implementation Demo
 *
 * DEPENDENCY (add to pom.xml):
 * <dependency>
 *   <groupId>org.apache.kafka</groupId>
 *   <artifactId>kafka-clients</artifactId>
 *   <version>3.7.0</version>
 * </dependency>
 *
 * SETUP — Run Kafka via Docker (KRaft mode, no ZooKeeper needed):
 *   docker run -d --name kafka -p 9092:9092 \
 *     -e KAFKA_CFG_NODE_ID=0 \
 *     -e KAFKA_CFG_PROCESS_ROLES=controller,broker \
 *     -e KAFKA_CFG_LISTENERS=PLAINTEXT://:9092,CONTROLLER://:9093 \
 *     -e KAFKA_CFG_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
 *     -e KAFKA_CFG_CONTROLLER_QUORUM_VOTERS=0@kafka:9093 \
 *     -e KAFKA_CFG_CONTROLLER_LISTENER_NAMES=CONTROLLER \
 *     bitnami/kafka:latest
 *
 * This demo covers:
 *  1. Admin Client      — create topics programmatically
 *  2. Simple Producer   — send messages with keys
 *  3. Simple Consumer   — consume with manual offset commit
 *  4. Consumer Group    — multiple consumers share partitions
 *  5. Idempotent Producer — exactly-once producer config
 *  6. Transactional Producer — atomic multi-message writes
 *  7. Offset Management — seek to beginning / specific offset
 */
public class KafkaImplementation {

    private static final String BOOTSTRAP_SERVERS = "localhost:9092";

    // ----------------------------------------------------------------
    // SHARED HELPER — build base producer properties
    // ----------------------------------------------------------------
    private static Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return props;
    }

    // ----------------------------------------------------------------
    // SHARED HELPER — build base consumer properties
    // ----------------------------------------------------------------
    private static Properties consumerProps(String groupId) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // manual commit
        return props;
    }

    // ================================================================
    // 1. ADMIN CLIENT — Create topics programmatically
    // ================================================================
    static class AdminDemo {

        static void createTopics(List<String> topicNames) {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);

            try (AdminClient admin = AdminClient.create(props)) {
                List<NewTopic> newTopics = new ArrayList<>();
                for (String name : topicNames) {
                    // 3 partitions, replication factor 1 (single broker)
                    newTopics.add(new NewTopic(name, 3, (short) 1));
                }

                CreateTopicsResult result = admin.createTopics(newTopics);
                result.all().get(10, TimeUnit.SECONDS);
                System.out.println("[Admin] Topics created: " + topicNames);

                // List all topics
                Set<String> topics = admin.listTopics().names().get(10, TimeUnit.SECONDS);
                System.out.println("[Admin] All topics on broker: " + topics);

            } catch (Exception e) {
                System.out.println("[Admin] Topic may already exist: " + e.getMessage());
            }
        }

        static void deleteTopics(List<String> topicNames) {
            Properties props = new Properties();
            props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);

            try (AdminClient admin = AdminClient.create(props)) {
                admin.deleteTopics(topicNames).all().get(10, TimeUnit.SECONDS);
                System.out.println("[Admin] Topics deleted: " + topicNames);
            } catch (Exception e) {
                System.out.println("[Admin] Delete error: " + e.getMessage());
            }
        }
    }

    // ================================================================
    // 2. SIMPLE PRODUCER — send messages with keys
    //    Messages with the same key always go to the same partition.
    // ================================================================
    static class SimpleProducer {

        private final String topic;
        private final KafkaProducer<String, String> producer;

        SimpleProducer(String topic) {
            this.topic    = topic;
            Properties props = producerProps();
            props.put(ProducerConfig.ACKS_CONFIG, "all");         // wait for all replicas
            props.put(ProducerConfig.RETRIES_CONFIG, 3);
            props.put(ProducerConfig.LINGER_MS_CONFIG, 5);        // batch small messages
            props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);   // 16 KB batch
            this.producer = new KafkaProducer<>(props);
        }

        // Async send with callback
        void sendAsync(String key, String value) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    System.err.println("[Producer] Send failed: " + exception.getMessage());
                } else {
                    System.out.printf("[Producer] Sent -> topic=%s partition=%d offset=%d key=%s value=%s%n",
                            metadata.topic(), metadata.partition(), metadata.offset(), key, value);
                }
            });
        }

        // Sync send — blocks until broker acknowledges
        void sendSync(String key, String value) throws Exception {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
            RecordMetadata metadata = producer.send(record).get();
            System.out.printf("[Producer-Sync] Sent -> partition=%d offset=%d%n",
                    metadata.partition(), metadata.offset());
        }

        void flush() { producer.flush(); }

        void close() { producer.close(); }
    }

    // ================================================================
    // 3. SIMPLE CONSUMER — manual offset commit
    // ================================================================
    static class SimpleConsumer implements Runnable {

        private final String topic;
        private final String groupId;
        private final AtomicBoolean running = new AtomicBoolean(true);
        private KafkaConsumer<String, String> consumer;

        SimpleConsumer(String topic, String groupId) {
            this.topic   = topic;
            this.groupId = groupId;
        }

        public void stop() { running.set(false); }

        @Override
        public void run() {
            consumer = new KafkaConsumer<>(consumerProps(groupId));
            consumer.subscribe(Collections.singletonList(topic));
            System.out.println("[Consumer:" + groupId + "] Started, subscribed to: " + topic);

            try {
                while (running.get()) {
                    ConsumerRecords<String, String> records =
                            consumer.poll(Duration.ofMillis(300));

                    for (ConsumerRecord<String, String> record : records) {
                        System.out.printf("[Consumer:%s] topic=%s partition=%d offset=%d key=%s value=%s%n",
                                groupId, record.topic(), record.partition(),
                                record.offset(), record.key(), record.value());
                        // Process message here...
                    }

                    if (!records.isEmpty()) {
                        // Manual synchronous commit after processing batch
                        consumer.commitSync();
                        System.out.println("[Consumer:" + groupId + "] Offsets committed.");
                    }
                }
            } finally {
                consumer.close();
                System.out.println("[Consumer:" + groupId + "] Closed.");
            }
        }
    }

    // ================================================================
    // 4. CONSUMER GROUP DEMO
    //    Multiple consumers in the same group share partitions.
    //    Each partition is handled by exactly one consumer.
    // ================================================================
    static class ConsumerGroupDemo {

        static List<Thread> startGroup(String topic, String groupId, int consumerCount) {
            List<Thread> threads = new ArrayList<>();
            List<SimpleConsumer> consumers = new ArrayList<>();

            for (int i = 0; i < consumerCount; i++) {
                SimpleConsumer c = new SimpleConsumer(topic, groupId);
                consumers.add(c);
                Thread t = new Thread(c, "consumer-group-" + i);
                threads.add(t);
                t.start();
            }

            // Register shutdown hook to stop all consumers cleanly
            Runtime.getRuntime().addShutdownHook(new Thread(() ->
                    consumers.forEach(SimpleConsumer::stop)));

            return threads;
        }
    }

    // ================================================================
    // 5. IDEMPOTENT PRODUCER
    //    Guarantees no duplicate messages even on retries.
    //    enable.idempotence=true + acks=all + retries > 0
    // ================================================================
    static class IdempotentProducer {

        private final KafkaProducer<String, String> producer;
        private final String topic;

        IdempotentProducer(String topic) {
            this.topic = topic;
            Properties props = producerProps();
            props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
            props.put(ProducerConfig.ACKS_CONFIG, "all");
            props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
            props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
            this.producer = new KafkaProducer<>(props);
        }

        void send(String key, String value) {
            producer.send(new ProducerRecord<>(topic, key, value), (meta, ex) -> {
                if (ex != null) System.err.println("[IdempotentProducer] Error: " + ex.getMessage());
                else System.out.printf("[IdempotentProducer] Sent partition=%d offset=%d%n",
                        meta.partition(), meta.offset());
            });
            producer.flush();
        }

        void close() { producer.close(); }
    }

    // ================================================================
    // 6. TRANSACTIONAL PRODUCER
    //    Writes to multiple topics/partitions atomically.
    //    Either ALL messages are committed or NONE are.
    // ================================================================
    static class TransactionalProducer {

        private final KafkaProducer<String, String> producer;

        TransactionalProducer(String transactionalId) {
            Properties props = producerProps();
            props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, transactionalId);
            props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
            props.put(ProducerConfig.ACKS_CONFIG, "all");
            this.producer = new KafkaProducer<>(props);
            producer.initTransactions();
        }

        void sendAtomically(List<ProducerRecord<String, String>> records) {
            producer.beginTransaction();
            try {
                for (ProducerRecord<String, String> record : records) {
                    producer.send(record);
                    System.out.printf("[TxProducer] Sending -> topic=%s key=%s value=%s%n",
                            record.topic(), record.key(), record.value());
                }
                producer.commitTransaction();
                System.out.println("[TxProducer] Transaction COMMITTED.");
            } catch (Exception e) {
                System.err.println("[TxProducer] Transaction ABORTED: " + e.getMessage());
                producer.abortTransaction();
            }
        }

        void close() { producer.close(); }
    }

    // ================================================================
    // 7. OFFSET MANAGEMENT — seek to beginning or specific offset
    // ================================================================
    static class OffsetManagementDemo {

        static void seekToBeginning(String topic, String groupId) {
            KafkaConsumer<String, String> consumer =
                    new KafkaConsumer<>(consumerProps(groupId));
            consumer.subscribe(Collections.singletonList(topic));

            // Poll once to trigger partition assignment
            consumer.poll(Duration.ofMillis(500));

            // Seek all assigned partitions back to the beginning
            consumer.seekToBeginning(consumer.assignment());
            System.out.println("[OffsetMgmt] Seeked to beginning for all partitions.");

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(2000));
            System.out.println("[OffsetMgmt] Re-read " + records.count() + " messages from beginning.");
            for (ConsumerRecord<String, String> r : records) {
                System.out.printf("[OffsetMgmt] offset=%d key=%s value=%s%n",
                        r.offset(), r.key(), r.value());
            }
            consumer.close();
        }

        static void seekToSpecificOffset(String topic, int partition, long offset, String groupId) {
            KafkaConsumer<String, String> consumer =
                    new KafkaConsumer<>(consumerProps(groupId));

            TopicPartition tp = new TopicPartition(topic, partition);
            consumer.assign(Collections.singletonList(tp));
            consumer.seek(tp, offset);
            System.out.printf("[OffsetMgmt] Seeked partition=%d to offset=%d%n", partition, offset);

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(2000));
            for (ConsumerRecord<String, String> r : records) {
                System.out.printf("[OffsetMgmt] offset=%d key=%s value=%s%n",
                        r.offset(), r.key(), r.value());
            }
            consumer.close();
        }
    }

    // ================================================================
    // MAIN — runs all demos
    // ================================================================
    public static void main(String[] args) throws Exception {

        System.out.println("============================================================");
        System.out.println("           APACHE KAFKA IMPLEMENTATION DEMO");
        System.out.println("============================================================");

        String orderTopic  = "order-events";
        String notifyTopic = "notification-events";

        // ----- Demo 1: Admin — Create Topics -----
        System.out.println("\n--- Demo 1: Admin Client - Create Topics ---");
        AdminDemo.createTopics(Arrays.asList(orderTopic, notifyTopic));
        Thread.sleep(1000);

        // ----- Demo 2: Simple Producer -----
        System.out.println("\n--- Demo 2: Simple Producer ---");
        SimpleProducer producer = new SimpleProducer(orderTopic);
        producer.sendAsync("order-101", "Order #101 placed by Alice");
        producer.sendAsync("order-102", "Order #102 placed by Bob");
        producer.sendAsync("order-101", "Order #101 payment received");  // same key -> same partition
        producer.sendAsync("order-103", "Order #103 placed by Carol");
        producer.sendSync("order-104", "Order #104 placed by Dave");
        producer.flush();

        // ----- Demo 3: Simple Consumer -----
        System.out.println("\n--- Demo 3: Simple Consumer ---");
        SimpleConsumer consumer1 = new SimpleConsumer(orderTopic, "order-processor-group");
        Thread consumerThread1 = new Thread(consumer1, "consumer-1");
        consumerThread1.start();
        Thread.sleep(3000);
        consumer1.stop();
        consumerThread1.join(3000);

        // ----- Demo 4: Consumer Group (2 consumers share 3 partitions) -----
        System.out.println("\n--- Demo 4: Consumer Group ---");
        // Produce more messages first
        for (int i = 5; i <= 10; i++) {
            producer.sendAsync("order-10" + i, "Order #10" + i + " placed");
        }
        producer.flush();

        List<Thread> groupThreads = ConsumerGroupDemo.startGroup(orderTopic, "group-demo", 2);
        Thread.sleep(4000);
        // Stop group consumers
        groupThreads.forEach(t -> t.interrupt());

        // ----- Demo 5: Idempotent Producer -----
        System.out.println("\n--- Demo 5: Idempotent Producer ---");
        IdempotentProducer idempotentProducer = new IdempotentProducer(orderTopic);
        idempotentProducer.send("order-201", "Idempotent order #201");
        idempotentProducer.send("order-202", "Idempotent order #202");
        idempotentProducer.close();

        // ----- Demo 6: Transactional Producer -----
        System.out.println("\n--- Demo 6: Transactional Producer ---");
        TransactionalProducer txProducer = new TransactionalProducer("tx-producer-1");
        List<ProducerRecord<String, String>> txRecords = Arrays.asList(
                new ProducerRecord<>(orderTopic,  "order-301", "Order #301 placed"),
                new ProducerRecord<>(notifyTopic, "user-alice", "Your order #301 is confirmed"),
                new ProducerRecord<>(orderTopic,  "order-302", "Order #302 placed"),
                new ProducerRecord<>(notifyTopic, "user-bob",  "Your order #302 is confirmed")
        );
        txProducer.sendAtomically(txRecords);
        txProducer.close();

        // ----- Demo 7: Offset Management -----
        System.out.println("\n--- Demo 7: Offset Management - Seek to Beginning ---");
        Thread.sleep(1000);
        OffsetManagementDemo.seekToBeginning(orderTopic, "replay-group");

        System.out.println("\n--- Demo 7b: Seek to Specific Offset (partition=0, offset=0) ---");
        OffsetManagementDemo.seekToSpecificOffset(orderTopic, 0, 0, "seek-group");

        // Cleanup
        producer.close();

        System.out.println("\n============================================================");
        System.out.println("   ALL KAFKA DEMOS COMPLETE");
        System.out.println("============================================================");

        System.exit(0);
    }
}
