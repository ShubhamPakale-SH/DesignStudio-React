import com.rabbitmq.client.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * RabbitMQ Implementation Demo
 *
 * DEPENDENCY (add to pom.xml):
 * <dependency>
 *   <groupId>com.rabbitmq</groupId>
 *   <artifactId>amqp-client</artifactId>
 *   <version>5.20.0</version>
 * </dependency>
 *
 * SETUP:
 * Run RabbitMQ via Docker:
 *   docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
 * Management UI: http://localhost:15672  (guest / guest)
 *
 * This demo covers:
 *  1. Direct Exchange  - exact routing key match
 *  2. Fanout Exchange  - broadcast to all queues
 *  3. Topic Exchange   - wildcard pattern routing
 *  4. Dead Letter Queue (DLQ) - handling failed messages
 *  5. Message TTL      - auto-expiry of messages
 */
public class RabbitMQImplementation {

    // ----------------------------------------------------------------
    // CONNECTION FACTORY — shared across all demos
    // ----------------------------------------------------------------
    private static ConnectionFactory createFactory() {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");
        factory.setVirtualHost("/");
        // Auto-recover connection on network failure
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(5000);
        return factory;
    }

    // ================================================================
    // 1. DIRECT EXCHANGE DEMO
    //    Producer sends to a specific routing key.
    //    Only the queue bound with that exact key receives it.
    // ================================================================
    static class DirectExchangeDemo {

        private static final String EXCHANGE_NAME = "direct_exchange";
        private static final String QUEUE_NAME    = "order_queue";
        private static final String ROUTING_KEY   = "order.created";

        // --- Producer ---
        static void produce(String message) throws IOException, TimeoutException {
            ConnectionFactory factory = createFactory();
            try (Connection conn = factory.newConnection();
                 Channel channel  = conn.createChannel()) {

                // Declare a durable exchange
                channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.DIRECT, true);

                // Declare a durable queue
                channel.queueDeclare(QUEUE_NAME, true, false, false, null);

                // Bind queue to exchange with routing key
                channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);

                // Publish persistent message
                channel.basicPublish(
                        EXCHANGE_NAME,
                        ROUTING_KEY,
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        message.getBytes(StandardCharsets.UTF_8)
                );

                System.out.println("[Direct Producer] Sent: " + message);
            }
        }

        // --- Consumer ---
        static void consume() throws IOException, TimeoutException {
            ConnectionFactory factory = createFactory();
            Connection conn   = factory.newConnection();
            Channel channel   = conn.createChannel();

            channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.DIRECT, true);
            channel.queueDeclare(QUEUE_NAME, true, false, false, null);
            channel.queueBind(QUEUE_NAME, EXCHANGE_NAME, ROUTING_KEY);

            // Process only 1 unacknowledged message at a time (fair dispatch)
            channel.basicQos(1);

            System.out.println("[Direct Consumer] Waiting for messages...");

            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("[Direct Consumer] Received: " + msg);

                try {
                    // Simulate processing
                    Thread.sleep(500);
                    // ACK — tell broker the message was processed successfully
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    System.out.println("[Direct Consumer] ACK sent for: " + msg);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    // NACK with requeue=true on failure
                    channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
                }
            };

            // autoAck=false so we manually acknowledge
            channel.basicConsume(QUEUE_NAME, false, deliverCallback, tag -> {});
        }
    }

    // ================================================================
    // 2. FANOUT EXCHANGE DEMO
    //    Producer sends one message; ALL bound queues receive a copy.
    //    Useful for broadcasting notifications.
    // ================================================================
    static class FanoutExchangeDemo {

        private static final String EXCHANGE_NAME = "fanout_exchange";

        static void produce(String message) throws IOException, TimeoutException {
            ConnectionFactory factory = createFactory();
            try (Connection conn = factory.newConnection();
                 Channel channel  = conn.createChannel()) {

                channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true);

                // Routing key is ignored for fanout exchanges
                channel.basicPublish(
                        EXCHANGE_NAME, "",
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        message.getBytes(StandardCharsets.UTF_8)
                );
                System.out.println("[Fanout Producer] Broadcast: " + message);
            }
        }

        static void consume(String queueName) throws IOException, TimeoutException {
            ConnectionFactory factory = createFactory();
            Connection conn   = factory.newConnection();
            Channel channel   = conn.createChannel();

            channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.FANOUT, true);

            // Each consumer gets its own queue bound to the fanout exchange
            channel.queueDeclare(queueName, true, false, false, null);
            channel.queueBind(queueName, EXCHANGE_NAME, "");

            channel.basicQos(1);
            System.out.println("[Fanout Consumer:" + queueName + "] Waiting...");

            DeliverCallback cb = (tag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("[Fanout Consumer:" + queueName + "] Received: " + msg);
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            };
            channel.basicConsume(queueName, false, cb, tag -> {});
        }
    }

    // ================================================================
    // 3. TOPIC EXCHANGE DEMO
    //    Routes messages based on wildcard patterns.
    //    * matches exactly one word
    //    # matches zero or more words
    // ================================================================
    static class TopicExchangeDemo {

        private static final String EXCHANGE_NAME = "topic_exchange";

        static void produce(String routingKey, String message) throws IOException, TimeoutException {
            ConnectionFactory factory = createFactory();
            try (Connection conn = factory.newConnection();
                 Channel channel  = conn.createChannel()) {

                channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC, true);

                channel.basicPublish(
                        EXCHANGE_NAME, routingKey,
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        message.getBytes(StandardCharsets.UTF_8)
                );
                System.out.println("[Topic Producer] key=" + routingKey + " msg=" + message);
            }
        }

        /**
         * @param queueName   name of the queue
         * @param bindPattern e.g. "order.#", "*.payment.*", "order.created.us"
         */
        static void consume(String queueName, String bindPattern) throws IOException, TimeoutException {
            ConnectionFactory factory = createFactory();
            Connection conn   = factory.newConnection();
            Channel channel   = conn.createChannel();

            channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC, true);
            channel.queueDeclare(queueName, true, false, false, null);
            channel.queueBind(queueName, EXCHANGE_NAME, bindPattern);

            channel.basicQos(1);
            System.out.println("[Topic Consumer:" + queueName + "] Bound pattern: " + bindPattern);

            DeliverCallback cb = (tag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.printf("[Topic Consumer:%s] key=%s msg=%s%n",
                        queueName, delivery.getEnvelope().getRoutingKey(), msg);
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            };
            channel.basicConsume(queueName, false, cb, tag -> {});
        }
    }

    // ================================================================
    // 4. DEAD LETTER QUEUE (DLQ) DEMO
    //    Messages that are rejected or exceed TTL go to a DLX/DLQ.
    // ================================================================
    static class DeadLetterQueueDemo {

        private static final String MAIN_EXCHANGE = "main_exchange";
        private static final String MAIN_QUEUE    = "main_queue_dlq_demo";
        private static final String DLX_EXCHANGE  = "dead_letter_exchange";
        private static final String DLQ_QUEUE     = "dead_letter_queue";
        private static final String ROUTING_KEY   = "task";

        static void setup() throws IOException, TimeoutException {
            ConnectionFactory factory = createFactory();
            try (Connection conn = factory.newConnection();
                 Channel channel  = conn.createChannel()) {

                // 1. Declare the Dead Letter Exchange
                channel.exchangeDeclare(DLX_EXCHANGE, BuiltinExchangeType.DIRECT, true);

                // 2. Declare the Dead Letter Queue and bind it
                channel.queueDeclare(DLQ_QUEUE, true, false, false, null);
                channel.queueBind(DLQ_QUEUE, DLX_EXCHANGE, ROUTING_KEY);

                // 3. Declare the main exchange
                channel.exchangeDeclare(MAIN_EXCHANGE, BuiltinExchangeType.DIRECT, true);

                // 4. Declare the main queue with DLX and TTL settings
                Map<String, Object> args = new HashMap<>();
                args.put("x-dead-letter-exchange", DLX_EXCHANGE);
                args.put("x-dead-letter-routing-key", ROUTING_KEY);
                args.put("x-message-ttl", 10000);  // 10-second TTL

                channel.queueDeclare(MAIN_QUEUE, true, false, false, args);
                channel.queueBind(MAIN_QUEUE, MAIN_EXCHANGE, ROUTING_KEY);

                System.out.println("[DLQ Setup] Main queue and DLQ configured.");
            }
        }

        static void produce(String message) throws IOException, TimeoutException {
            ConnectionFactory factory = createFactory();
            try (Connection conn = factory.newConnection();
                 Channel channel  = conn.createChannel()) {

                channel.basicPublish(
                        MAIN_EXCHANGE, ROUTING_KEY,
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        message.getBytes(StandardCharsets.UTF_8)
                );
                System.out.println("[DLQ Producer] Sent: " + message);
            }
        }

        // Consumer that deliberately rejects messages to trigger DLQ
        static void consumeWithReject() throws IOException, TimeoutException {
            ConnectionFactory factory = createFactory();
            Connection conn   = factory.newConnection();
            Channel channel   = conn.createChannel();
            channel.basicQos(1);

            DeliverCallback cb = (tag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("[DLQ Consumer] Received: " + msg + " — REJECTING (requeue=false)");
                // requeue=false sends the message to DLX
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, false);
            };
            channel.basicConsume(MAIN_QUEUE, false, cb, tag -> {});
            System.out.println("[DLQ Consumer] Running — will reject all messages.");
        }

        // Consumer for the Dead Letter Queue
        static void consumeDLQ() throws IOException, TimeoutException {
            ConnectionFactory factory = createFactory();
            Connection conn   = factory.newConnection();
            Channel channel   = conn.createChannel();
            channel.basicQos(1);

            DeliverCallback cb = (tag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
                System.out.println("[DLQ] Dead-lettered message received: " + msg);
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            };
            channel.basicConsume(DLQ_QUEUE, false, cb, tag -> {});
            System.out.println("[DLQ Consumer] Monitoring dead letter queue.");
        }
    }

    // ================================================================
    // MAIN — runs all demos sequentially
    // ================================================================
    public static void main(String[] args) throws Exception {

        System.out.println("============================================================");
        System.out.println("          RABBITMQ IMPLEMENTATION DEMO");
        System.out.println("============================================================");

        // ----- Demo 1: Direct Exchange -----
        System.out.println("\n--- Demo 1: Direct Exchange ---");
        DirectExchangeDemo.consume();
        Thread.sleep(500);
        DirectExchangeDemo.produce("Order #1001 created");
        DirectExchangeDemo.produce("Order #1002 created");
        Thread.sleep(2000);

        // ----- Demo 2: Fanout Exchange -----
        System.out.println("\n--- Demo 2: Fanout Exchange ---");
        FanoutExchangeDemo.consume("fanout_queue_email");
        FanoutExchangeDemo.consume("fanout_queue_sms");
        Thread.sleep(500);
        FanoutExchangeDemo.produce("Flash Sale starts NOW!");
        Thread.sleep(2000);

        // ----- Demo 3: Topic Exchange -----
        System.out.println("\n--- Demo 3: Topic Exchange ---");
        TopicExchangeDemo.consume("all_orders_queue",    "order.#");
        TopicExchangeDemo.consume("us_orders_queue",     "order.*.us");
        TopicExchangeDemo.consume("payment_queue",       "#.payment");
        Thread.sleep(500);
        TopicExchangeDemo.produce("order.created.us",    "New US order");
        TopicExchangeDemo.produce("order.created.eu",    "New EU order");
        TopicExchangeDemo.produce("order.payment",       "Payment processed");
        Thread.sleep(2000);

        // ----- Demo 4: Dead Letter Queue -----
        System.out.println("\n--- Demo 4: Dead Letter Queue ---");
        DeadLetterQueueDemo.setup();
        DeadLetterQueueDemo.consumeWithReject();
        DeadLetterQueueDemo.consumeDLQ();
        Thread.sleep(500);
        DeadLetterQueueDemo.produce("Task that will fail");
        Thread.sleep(3000);

        System.out.println("\n============================================================");
        System.out.println("   ALL DEMOS COMPLETE");
        System.out.println("============================================================");

        // Keep main thread alive briefly so consumers can finish
        Thread.sleep(2000);
        System.exit(0);
    }
}
