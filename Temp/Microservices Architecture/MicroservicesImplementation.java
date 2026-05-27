/**
 * Microservices Architecture - Java Implementation Demo
 *
 * This single file simulates the key components of a
 * microservices architecture running in-process, including:
 *
 *  1.  Service Registry (Eureka-like)
 *  2.  API Gateway (routing + auth)
 *  3.  User Service
 *  4.  Order Service
 *  5.  Payment Service
 *  6.  Notification Service
 *  7.  Circuit Breaker (Resilience4j-like)
 *  8.  Retry Mechanism
 *  9.  Load Balancer (Round-Robin)
 * 10.  Event Bus (Kafka-like async messaging)
 * 11.  Distributed Tracing (Trace ID / Span ID)
 * 12.  Config Server (centralized config)
 * 13.  JWT Auth (simplified)
 * 14.  SAGA Pattern (distributed transaction)
 *
 * NOTE: In a real project each numbered section below would
 *       be a separate Spring Boot application / module.
 */

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

public class MicroservicesImplementation {

    // ================================================================
    // 1. SERVICE REGISTRY
    //    Services register on startup; gateway/clients discover them.
    // ================================================================
    static class ServiceRegistry {
        // serviceName -> list of instance URLs
        private final Map<String, List<String>> registry = new ConcurrentHashMap<>();

        public void register(String serviceName, String instanceUrl) {
            registry.computeIfAbsent(serviceName, k -> new CopyOnWriteArrayList<>())
                    .add(instanceUrl);
            System.out.println("[Registry] Registered: " + serviceName + " -> " + instanceUrl);
        }

        public void deregister(String serviceName, String instanceUrl) {
            List<String> instances = registry.getOrDefault(serviceName, List.of());
            instances.remove(instanceUrl);
            System.out.println("[Registry] Deregistered: " + serviceName + " -> " + instanceUrl);
        }

        public List<String> getInstances(String serviceName) {
            return registry.getOrDefault(serviceName, Collections.emptyList());
        }

        public void printAll() {
            System.out.println("[Registry] Current registrations:");
            registry.forEach((svc, urls) ->
                    System.out.println("  " + svc + " -> " + urls));
        }
    }

    // ================================================================
    // 2. LOAD BALANCER — Round-Robin
    //    Distributes requests evenly across service instances.
    // ================================================================
    static class LoadBalancer {
        private final ServiceRegistry registry;
        private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

        LoadBalancer(ServiceRegistry registry) {
            this.registry = registry;
        }

        public String chooseInstance(String serviceName) {
            List<String> instances = registry.getInstances(serviceName);
            if (instances.isEmpty()) throw new RuntimeException("No instances for: " + serviceName);
            AtomicInteger counter = counters.computeIfAbsent(serviceName, k -> new AtomicInteger(0));
            int index = counter.getAndIncrement() % instances.size();
            return instances.get(index);
        }
    }

    // ================================================================
    // 3. DISTRIBUTED TRACING — Trace ID / Span ID
    //    Every request gets a Trace ID. Each service hop gets a Span ID.
    // ================================================================
    static class TracingContext {
        private final String traceId;
        private String spanId;

        TracingContext() {
            this.traceId = "trace-" + UUID.randomUUID().toString().substring(0, 8);
            this.spanId  = newSpanId();
        }

        TracingContext(String traceId) {
            this.traceId = traceId;
            this.spanId  = newSpanId();
        }

        private String newSpanId() {
            return "span-" + UUID.randomUUID().toString().substring(0, 6);
        }

        public TracingContext nextSpan() {
            return new TracingContext(this.traceId);
        }

        public String getTraceId() { return traceId; }
        public String getSpanId()  { return spanId; }

        @Override
        public String toString() {
            return "[traceId=" + traceId + ", spanId=" + spanId + "]";
        }
    }

    // ================================================================
    // 4. CIRCUIT BREAKER
    //    CLOSED -> OPEN (on failures) -> HALF-OPEN (test) -> CLOSED
    // ================================================================
    static class CircuitBreaker {
        enum State { CLOSED, OPEN, HALF_OPEN }

        private final String name;
        private final int failureThreshold;
        private final long waitDurationMs;

        private State state = State.CLOSED;
        private int failureCount = 0;
        private long openedAt = 0;

        CircuitBreaker(String name, int failureThreshold, long waitDurationMs) {
            this.name             = name;
            this.failureThreshold = failureThreshold;
            this.waitDurationMs   = waitDurationMs;
        }

        public <T> T execute(Supplier<T> action, Supplier<T> fallback) {
            if (state == State.OPEN) {
                if (System.currentTimeMillis() - openedAt > waitDurationMs) {
                    state = State.HALF_OPEN;
                    System.out.println("[CircuitBreaker:" + name + "] -> HALF-OPEN");
                } else {
                    System.out.println("[CircuitBreaker:" + name + "] OPEN — using fallback");
                    return fallback.get();
                }
            }
            try {
                T result = action.get();
                onSuccess();
                return result;
            } catch (Exception e) {
                onFailure();
                System.out.println("[CircuitBreaker:" + name + "] failure: " + e.getMessage());
                return fallback.get();
            }
        }

        private void onSuccess() {
            failureCount = 0;
            if (state == State.HALF_OPEN) {
                state = State.CLOSED;
                System.out.println("[CircuitBreaker:" + name + "] -> CLOSED (recovered)");
            }
        }

        private void onFailure() {
            failureCount++;
            if (failureCount >= failureThreshold) {
                state    = State.OPEN;
                openedAt = System.currentTimeMillis();
                System.out.println("[CircuitBreaker:" + name + "] -> OPEN after "
                        + failureCount + " failures");
            }
        }

        public State getState() { return state; }
    }

    // ================================================================
    // 5. RETRY MECHANISM
    // ================================================================
    static class RetryExecutor {
        private final int maxAttempts;
        private final long waitMs;

        RetryExecutor(int maxAttempts, long waitMs) {
            this.maxAttempts = maxAttempts;
            this.waitMs      = waitMs;
        }

        public <T> T execute(Supplier<T> action, String operationName) {
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    T result = action.get();
                    if (attempt > 1)
                        System.out.println("[Retry:" + operationName + "] Succeeded on attempt " + attempt);
                    return result;
                } catch (Exception e) {
                    System.out.println("[Retry:" + operationName + "] Attempt " + attempt
                            + "/" + maxAttempts + " failed: " + e.getMessage());
                    if (attempt == maxAttempts) throw new RuntimeException("All retries exhausted", e);
                    try { Thread.sleep(waitMs); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            throw new RuntimeException("Retry failed");
        }
    }

    // ================================================================
    // 6. CONFIG SERVER — centralized configuration
    // ================================================================
    static class ConfigServer {
        private final Map<String, Map<String, String>> configs = new HashMap<>();

        ConfigServer() {
            // order-service config
            Map<String, String> orderConfig = new HashMap<>();
            orderConfig.put("max.items.per.order", "50");
            orderConfig.put("order.timeout.ms", "5000");
            configs.put("order-service", orderConfig);

            // payment-service config
            Map<String, String> paymentConfig = new HashMap<>();
            paymentConfig.put("payment.gateway.url", "https://gateway.payment.com");
            paymentConfig.put("payment.timeout.ms", "3000");
            configs.put("payment-service", paymentConfig);

            // notification-service config
            Map<String, String> notifConfig = new HashMap<>();
            notifConfig.put("smtp.host", "smtp.example.com");
            notifConfig.put("smtp.port", "587");
            configs.put("notification-service", notifConfig);
        }

        public String get(String serviceName, String key) {
            return configs.getOrDefault(serviceName, Map.of())
                    .getOrDefault(key, "NOT_FOUND");
        }

        public Map<String, String> getAll(String serviceName) {
            return configs.getOrDefault(serviceName, Map.of());
        }
    }

    // ================================================================
    // 7. JWT AUTH — simplified token generation and validation
    // ================================================================
    static class JwtAuthService {
        private final String SECRET = "my-secret-key-2024";
        private final Map<String, String> tokenStore = new ConcurrentHashMap<>();

        public String generateToken(String userId, String role) {
            String payload = userId + ":" + role + ":" + System.currentTimeMillis();
            String token   = "Bearer " + Base64.getEncoder()
                    .encodeToString((payload + "." + SECRET).getBytes());
            tokenStore.put(token, userId + ":" + role);
            System.out.println("[Auth] Token generated for user: " + userId + " role: " + role);
            return token;
        }

        public boolean validateToken(String token) {
            return tokenStore.containsKey(token);
        }

        public String getUserId(String token) {
            String entry = tokenStore.getOrDefault(token, "unknown:USER");
            return entry.split(":")[0];
        }

        public String getRole(String token) {
            String entry = tokenStore.getOrDefault(token, "unknown:USER");
            return entry.split(":")[1];
        }
    }

    // ================================================================
    // 8. EVENT BUS — async messaging (Kafka/RabbitMQ simulation)
    // ================================================================
    static class EventBus {
        private final Map<String, List<EventHandler>> subscribers = new ConcurrentHashMap<>();
        private final ExecutorService executor = Executors.newFixedThreadPool(4);

        @FunctionalInterface
        interface EventHandler {
            void handle(String eventType, Map<String, Object> payload, TracingContext tracing);
        }

        public void subscribe(String eventType, EventHandler handler) {
            subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                    .add(handler);
        }

        public void publish(String eventType, Map<String, Object> payload, TracingContext tracing) {
            System.out.println("[EventBus] Publishing: " + eventType + " " + tracing);
            List<EventHandler> handlers = subscribers.getOrDefault(eventType, List.of());
            for (EventHandler handler : handlers) {
                TracingContext childSpan = tracing.nextSpan();
                executor.submit(() -> {
                    try {
                        handler.handle(eventType, payload, childSpan);
                    } catch (Exception e) {
                        System.err.println("[EventBus] Handler error: " + e.getMessage());
                    }
                });
            }
        }

        public void shutdown() throws InterruptedException {
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    // ================================================================
    // 9. DOMAIN MODELS
    // ================================================================
    static class User {
        String id, name, email;
        User(String id, String name, String email) {
            this.id = id; this.name = name; this.email = email;
        }
        @Override public String toString() {
            return "User{id='" + id + "', name='" + name + "', email='" + email + "'}";
        }
    }

    static class Order {
        String id, userId, product;
        double amount;
        String status;
        Order(String id, String userId, String product, double amount) {
            this.id = id; this.userId = userId;
            this.product = product; this.amount = amount;
            this.status = "CREATED";
        }
        @Override public String toString() {
            return "Order{id='" + id + "', userId='" + userId +
                    "', product='" + product + "', amount=" + amount + ", status='" + status + "'}";
        }
    }

    static class Payment {
        String id, orderId;
        double amount;
        String status;
        Payment(String id, String orderId, double amount) {
            this.id = id; this.orderId = orderId;
            this.amount = amount; this.status = "PENDING";
        }
        @Override public String toString() {
            return "Payment{id='" + id + "', orderId='" + orderId +
                    "', amount=" + amount + ", status='" + status + "'}";
        }
    }

    // ================================================================
    // 10. USER SERVICE
    // ================================================================
    static class UserService {
        private final Map<String, User> db = new ConcurrentHashMap<>();
        private final String instanceUrl;

        UserService(String instanceUrl) {
            this.instanceUrl = instanceUrl;
            db.put("U001", new User("U001", "Alice", "alice@example.com"));
            db.put("U002", new User("U002", "Bob",   "bob@example.com"));
            db.put("U003", new User("U003", "Carol", "carol@example.com"));
        }

        public User getUser(String userId, TracingContext tracing) {
            System.out.println("[UserService:" + instanceUrl + "] " + tracing
                    + " getUser: " + userId);
            User user = db.get(userId);
            if (user == null) throw new RuntimeException("User not found: " + userId);
            return user;
        }

        public User createUser(String name, String email, TracingContext tracing) {
            String id = "U" + String.format("%03d", db.size() + 1);
            User user = new User(id, name, email);
            db.put(id, user);
            System.out.println("[UserService:" + instanceUrl + "] " + tracing
                    + " Created: " + user);
            return user;
        }
    }

    // ================================================================
    // 11. ORDER SERVICE
    // ================================================================
    static class OrderService {
        private final Map<String, Order> db = new ConcurrentHashMap<>();
        private final EventBus eventBus;
        private final String instanceUrl;

        OrderService(String instanceUrl, EventBus eventBus) {
            this.instanceUrl = instanceUrl;
            this.eventBus    = eventBus;
        }

        public Order createOrder(String userId, String product,
                                 double amount, TracingContext tracing) {
            String id = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Order order = new Order(id, userId, product, amount);
            db.put(id, order);
            System.out.println("[OrderService:" + instanceUrl + "] " + tracing
                    + " Created: " + order);

            // Publish event for Payment and Notification services
            Map<String, Object> payload = new HashMap<>();
            payload.put("orderId", id);
            payload.put("userId",  userId);
            payload.put("product", product);
            payload.put("amount",  amount);
            eventBus.publish("ORDER_CREATED", payload, tracing);

            return order;
        }

        public Order getOrder(String orderId, TracingContext tracing) {
            System.out.println("[OrderService:" + instanceUrl + "] " + tracing
                    + " getOrder: " + orderId);
            Order order = db.get(orderId);
            if (order == null) throw new RuntimeException("Order not found: " + orderId);
            return order;
        }

        public void updateStatus(String orderId, String status) {
            Order order = db.get(orderId);
            if (order != null) {
                order.status = status;
                System.out.println("[OrderService] Order " + orderId + " status -> " + status);
            }
        }

        public List<Order> getAllOrders() { return new ArrayList<>(db.values()); }
    }

    // ================================================================
    // 12. PAYMENT SERVICE
    // ================================================================
    static class PaymentService {
        private final Map<String, Payment> db = new ConcurrentHashMap<>();
        private final EventBus eventBus;
        private final AtomicInteger failCount = new AtomicInteger(0);
        private final String instanceUrl;

        PaymentService(String instanceUrl, EventBus eventBus) {
            this.instanceUrl = instanceUrl;
            this.eventBus    = eventBus;
        }

        public Payment processPayment(String orderId, double amount,
                                      TracingContext tracing) {
            System.out.println("[PaymentService:" + instanceUrl + "] " + tracing
                    + " Processing payment for order: " + orderId);

            // Simulate occasional failure (every 3rd call)
            if (failCount.incrementAndGet() % 3 == 0) {
                throw new RuntimeException("Payment gateway timeout!");
            }

            String id = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            Payment payment = new Payment(id, orderId, amount);
            payment.status = "SUCCESS";
            db.put(id, payment);
            System.out.println("[PaymentService:" + instanceUrl + "] " + tracing
                    + " Payment successful: " + payment);

            Map<String, Object> payload = new HashMap<>();
            payload.put("paymentId", id);
            payload.put("orderId",   orderId);
            payload.put("amount",    amount);
            payload.put("status",    "SUCCESS");
            eventBus.publish("PAYMENT_PROCESSED", payload, tracing);

            return payment;
        }

        // Compensating transaction for SAGA rollback
        public void refundPayment(String orderId, TracingContext tracing) {
            db.values().stream()
                    .filter(p -> p.orderId.equals(orderId))
                    .findFirst()
                    .ifPresent(p -> {
                        p.status = "REFUNDED";
                        System.out.println("[PaymentService] " + tracing
                                + " Refunded payment for order: " + orderId);
                    });
        }
    }

    // ================================================================
    // 13. NOTIFICATION SERVICE
    // ================================================================
    static class NotificationService {
        private final List<String> sentNotifications = new CopyOnWriteArrayList<>();

        public void sendEmail(String to, String subject, String body, TracingContext tracing) {
            String notification = "EMAIL -> " + to + " | " + subject;
            sentNotifications.add(notification);
            System.out.println("[NotificationService] " + tracing + " " + notification);
        }

        public void sendSms(String phone, String message, TracingContext tracing) {
            String notification = "SMS -> " + phone + " | " + message;
            sentNotifications.add(notification);
            System.out.println("[NotificationService] " + tracing + " " + notification);
        }

        public List<String> getSentNotifications() {
            return Collections.unmodifiableList(sentNotifications);
        }
    }

    // ================================================================
    // 14. API GATEWAY
    //    Single entry point. Handles auth, routing, load balancing.
    // ================================================================
    static class ApiGateway {
        private final JwtAuthService authService;
        private final LoadBalancer   loadBalancer;
        private final UserService    userService;
        private final OrderService   orderService;
        private final PaymentService paymentService;
        private final CircuitBreaker paymentCB;
        private final RetryExecutor  retryExecutor;

        ApiGateway(JwtAuthService authService, LoadBalancer loadBalancer,
                   UserService userService, OrderService orderService,
                   PaymentService paymentService) {
            this.authService     = authService;
            this.loadBalancer    = loadBalancer;
            this.userService     = userService;
            this.orderService    = orderService;
            this.paymentService  = paymentService;
            this.paymentCB       = new CircuitBreaker("payment-cb", 2, 3000);
            this.retryExecutor   = new RetryExecutor(3, 500);
        }

        // Authenticate request
        private TracingContext authenticate(String token) {
            if (!authService.validateToken(token))
                throw new SecurityException("Invalid or expired token");
            TracingContext tracing = new TracingContext();
            System.out.println("\n[API Gateway] Request authenticated. " + tracing);
            return tracing;
        }

        // Route: GET /users/{id}
        public User routeGetUser(String token, String userId) {
            TracingContext tracing = authenticate(token);
            String instance = loadBalancer.chooseInstance("user-service");
            System.out.println("[API Gateway] Routing -> user-service at " + instance);
            return userService.getUser(userId, tracing.nextSpan());
        }

        // Route: POST /orders
        public Order routeCreateOrder(String token, String product, double amount) {
            TracingContext tracing = authenticate(token);
            String userId = authService.getUserId(token);
            String instance = loadBalancer.chooseInstance("order-service");
            System.out.println("[API Gateway] Routing -> order-service at " + instance);
            return orderService.createOrder(userId, product, amount, tracing.nextSpan());
        }

        // Route: POST /payments (with Circuit Breaker + Retry)
        public Payment routeProcessPayment(String token, String orderId, double amount) {
            TracingContext tracing = authenticate(token);
            String instance = loadBalancer.chooseInstance("payment-service");
            System.out.println("[API Gateway] Routing -> payment-service at " + instance);

            TracingContext span = tracing.nextSpan();
            return paymentCB.execute(
                    () -> retryExecutor.execute(
                            () -> paymentService.processPayment(orderId, amount, span),
                            "processPayment"),
                    () -> {
                        System.out.println("[API Gateway] Payment fallback: service unavailable");
                        Payment fallback = new Payment("FALLBACK", orderId, amount);
                        fallback.status = "PENDING_RETRY";
                        return fallback;
                    }
            );
        }
    }

    // ================================================================
    // 15. SAGA ORCHESTRATOR
    //    Manages the distributed transaction: Order → Payment → Notify
    //    With compensating transactions on failure.
    // ================================================================
    static class OrderSagaOrchestrator {
        private final OrderService   orderService;
        private final PaymentService paymentService;

        OrderSagaOrchestrator(OrderService orderService, PaymentService paymentService) {
            this.orderService   = orderService;
            this.paymentService = paymentService;
        }

        public void executeSaga(String userId, String product,
                                double amount, TracingContext tracing) {
            System.out.println("\n[SAGA] Starting saga for user=" + userId
                    + " product=" + product + " amount=" + amount);
            String orderId = null;
            try {
                // Step 1: Create Order
                Order order = orderService.createOrder(userId, product, amount, tracing);
                orderId = order.id;
                System.out.println("[SAGA] Step 1 SUCCESS: Order created " + orderId);

                // Step 2: Process Payment
                Payment payment = paymentService.processPayment(orderId, amount, tracing.nextSpan());
                System.out.println("[SAGA] Step 2 SUCCESS: Payment processed " + payment.id);

                // Step 3: Confirm Order
                orderService.updateStatus(orderId, "CONFIRMED");
                System.out.println("[SAGA] Step 3 SUCCESS: Order confirmed.");
                System.out.println("[SAGA] Saga COMPLETED successfully.");

            } catch (Exception e) {
                System.out.println("[SAGA] Step FAILED: " + e.getMessage());
                System.out.println("[SAGA] Starting COMPENSATION (rollback)...");

                // Compensate: cancel order if it was created
                if (orderId != null) {
                    orderService.updateStatus(orderId, "CANCELLED");
                    System.out.println("[SAGA] Compensation: Order " + orderId + " cancelled.");

                    // Compensate: refund payment if charged
                    paymentService.refundPayment(orderId, tracing);
                }
                System.out.println("[SAGA] Saga ROLLED BACK.");
            }
        }
    }

    // ================================================================
    // MAIN — Wire all components and run demos
    // ================================================================
    public static void main(String[] args) throws Exception {

        System.out.println("============================================================");
        System.out.println("       MICROSERVICES ARCHITECTURE - COMPLETE DEMO");
        System.out.println("============================================================");

        // --- Infrastructure Setup ---
        ServiceRegistry  registry    = new ServiceRegistry();
        EventBus         eventBus    = new EventBus();
        JwtAuthService   authService = new JwtAuthService();
        ConfigServer     configServer= new ConfigServer();

        // --- Register Service Instances (simulating multiple pods) ---
        System.out.println("\n--- Service Registration ---");
        registry.register("user-service",    "http://localhost:8081");
        registry.register("user-service",    "http://localhost:8082");
        registry.register("order-service",   "http://localhost:8083");
        registry.register("order-service",   "http://localhost:8084");
        registry.register("payment-service", "http://localhost:8085");
        registry.printAll();

        // --- Load Balancer ---
        LoadBalancer loadBalancer = new LoadBalancer(registry);

        // --- Config Server ---
        System.out.println("\n--- Config Server ---");
        System.out.println("order-service config  : " + configServer.getAll("order-service"));
        System.out.println("payment-service config: " + configServer.getAll("payment-service"));

        // --- Boot Services ---
        UserService         userService     = new UserService("localhost:8081");
        NotificationService notifService    = new NotificationService();
        OrderService        orderService    = new OrderService("localhost:8083", eventBus);
        PaymentService      paymentService  = new PaymentService("localhost:8085", eventBus);

        // --- Event Bus Subscriptions ---
        eventBus.subscribe("ORDER_CREATED", (type, payload, tracing) -> {
            System.out.println("[EventBus Handler] ORDER_CREATED received " + tracing);
            notifService.sendEmail(
                    "user@example.com",
                    "Order Placed: " + payload.get("orderId"),
                    "Your order for " + payload.get("product") + " is placed!",
                    tracing
            );
        });

        eventBus.subscribe("PAYMENT_PROCESSED", (type, payload, tracing) -> {
            System.out.println("[EventBus Handler] PAYMENT_PROCESSED received " + tracing);
            orderService.updateStatus((String) payload.get("orderId"), "PAID");
            notifService.sendSms("+1-555-0100",
                    "Payment of $" + payload.get("amount") + " confirmed!", tracing);
        });

        // --- API Gateway ---
        ApiGateway gateway = new ApiGateway(authService, loadBalancer,
                userService, orderService, paymentService);

        // === DEMO 1: JWT Authentication ===
        System.out.println("\n============================================================");
        System.out.println(" Demo 1: JWT Authentication");
        System.out.println("============================================================");
        String aliceToken = authService.generateToken("U001", "CUSTOMER");
        String adminToken = authService.generateToken("U000", "ADMIN");
        System.out.println("Token valid: " + authService.validateToken(aliceToken));
        System.out.println("Fake token : " + authService.validateToken("fake-token"));

        // === DEMO 2: Service Discovery + Load Balancing ===
        System.out.println("\n============================================================");
        System.out.println(" Demo 2: Load Balancing (Round-Robin)");
        System.out.println("============================================================");
        for (int i = 0; i < 4; i++) {
            System.out.println("Chosen user-service: " + loadBalancer.chooseInstance("user-service"));
        }

        // === DEMO 3: API Gateway — Get User ===
        System.out.println("\n============================================================");
        System.out.println(" Demo 3: API Gateway -> UserService");
        System.out.println("============================================================");
        User user = gateway.routeGetUser(aliceToken, "U001");
        System.out.println("Result: " + user);

        // === DEMO 4: API Gateway — Create Order + Async Events ===
        System.out.println("\n============================================================");
        System.out.println(" Demo 4: API Gateway -> OrderService + Async Events");
        System.out.println("============================================================");
        Order order = gateway.routeCreateOrder(aliceToken, "MacBook Pro", 2499.99);
        System.out.println("Result: " + order);
        Thread.sleep(500); // allow async event handlers to run

        // === DEMO 5: Circuit Breaker + Retry ===
        System.out.println("\n============================================================");
        System.out.println(" Demo 5: Circuit Breaker + Retry on Payment");
        System.out.println("============================================================");
        for (int i = 1; i <= 5; i++) {
            System.out.println("\n-- Payment attempt " + i + " --");
            Payment payment = gateway.routeProcessPayment(aliceToken, order.id, 2499.99);
            System.out.println("Payment result: " + payment);
            Thread.sleep(300);
        }

        // === DEMO 6: SAGA Pattern ===
        System.out.println("\n============================================================");
        System.out.println(" Demo 6: SAGA Pattern (Distributed Transaction)");
        System.out.println("============================================================");
        OrderSagaOrchestrator saga = new OrderSagaOrchestrator(orderService, paymentService);
        TracingContext sagaTrace   = new TracingContext();

        // Saga 1: should succeed (payment fail count resets between runs)
        saga.executeSaga("U002", "iPhone 15", 999.99, sagaTrace);
        Thread.sleep(300);

        // Saga 2: will trigger payment failure and rollback
        System.out.println();
        saga.executeSaga("U003", "iPad Pro", 1299.99, new TracingContext());
        Thread.sleep(300);

        // === DEMO 7: Notifications Summary ===
        System.out.println("\n============================================================");
        System.out.println(" Demo 7: All Notifications Sent");
        System.out.println("============================================================");
        Thread.sleep(500);
        notifService.getSentNotifications().forEach(n ->
                System.out.println("  " + n));

        // === DEMO 8: All Orders ===
        System.out.println("\n============================================================");
        System.out.println(" Demo 8: All Orders in Order Service");
        System.out.println("============================================================");
        orderService.getAllOrders().forEach(o -> System.out.println("  " + o));

        // Cleanup
        eventBus.shutdown();

        System.out.println("\n============================================================");
        System.out.println("   ALL MICROSERVICES DEMOS COMPLETE");
        System.out.println("============================================================");
    }
}
