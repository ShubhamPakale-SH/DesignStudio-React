import java.util.*;
import java.util.function.*;

/**
 * Design Patterns - Complete Java Implementation
 *
 * CREATIONAL PATTERNS:
 *  1.  Singleton
 *  2.  Factory Method
 *  3.  Abstract Factory
 *  4.  Builder
 *  5.  Prototype
 *
 * STRUCTURAL PATTERNS:
 *  6.  Adapter
 *  7.  Bridge
 *  8.  Composite
 *  9.  Decorator
 * 10.  Facade
 * 11.  Flyweight
 * 12.  Proxy
 *
 * BEHAVIORAL PATTERNS:
 * 13.  Chain of Responsibility
 * 14.  Command
 * 15.  Iterator
 * 16.  Mediator
 * 17.  Memento
 * 18.  Observer
 * 19.  State
 * 20.  Strategy
 * 21.  Template Method
 * 22.  Visitor
 */
public class DesignPatternsImpl {

    // ================================================================
    // CREATIONAL PATTERNS
    // ================================================================

    // --------------------------------------------------------------
    // 1. SINGLETON — only one instance, thread-safe
    // --------------------------------------------------------------
    static class DatabaseConnection {
        private static volatile DatabaseConnection instance;
        private final String connectionId;
        private int queryCount = 0;

        private DatabaseConnection() {
            this.connectionId = "DB-CONN-" + UUID.randomUUID().toString().substring(0, 6);
            System.out.println("[Singleton] DatabaseConnection created: " + connectionId);
        }

        public static DatabaseConnection getInstance() {
            if (instance == null) {
                synchronized (DatabaseConnection.class) {
                    if (instance == null) instance = new DatabaseConnection();
                }
            }
            return instance;
        }

        public String executeQuery(String sql) {
            queryCount++;
            return "[" + connectionId + "] Executed: " + sql + " (query #" + queryCount + ")";
        }

        public String getConnectionId() { return connectionId; }
    }

    static void singletonDemo() {
        System.out.println("\n========== 1. SINGLETON ==========");
        DatabaseConnection c1 = DatabaseConnection.getInstance();
        DatabaseConnection c2 = DatabaseConnection.getInstance();
        DatabaseConnection c3 = DatabaseConnection.getInstance();
        System.out.println("Same instance? " + (c1 == c2 && c2 == c3));
        System.out.println(c1.executeQuery("SELECT * FROM users"));
        System.out.println(c2.executeQuery("SELECT * FROM orders"));
    }

    // --------------------------------------------------------------
    // 2. FACTORY METHOD — subclass decides which object to create
    // --------------------------------------------------------------
    interface Notification {
        void send(String message);
        String getType();
    }

    static class EmailNotification implements Notification {
        private final String email;
        EmailNotification(String email) { this.email = email; }
        public void send(String msg) {
            System.out.println("[Email -> " + email + "] " + msg);
        }
        public String getType() { return "EMAIL"; }
    }

    static class SmsNotification implements Notification {
        private final String phone;
        SmsNotification(String phone) { this.phone = phone; }
        public void send(String msg) {
            System.out.println("[SMS -> " + phone + "] " + msg);
        }
        public String getType() { return "SMS"; }
    }

    static class PushNotification implements Notification {
        private final String deviceId;
        PushNotification(String deviceId) { this.deviceId = deviceId; }
        public void send(String msg) {
            System.out.println("[PUSH -> " + deviceId + "] " + msg);
        }
        public String getType() { return "PUSH"; }
    }

    // Factory
    static class NotificationFactory {
        public static Notification create(String type, String target) {
            return switch (type.toUpperCase()) {
                case "EMAIL" -> new EmailNotification(target);
                case "SMS"   -> new SmsNotification(target);
                case "PUSH"  -> new PushNotification(target);
                default      -> throw new IllegalArgumentException("Unknown type: " + type);
            };
        }
    }

    static void factoryMethodDemo() {
        System.out.println("\n========== 2. FACTORY METHOD ==========");
        Notification n1 = NotificationFactory.create("EMAIL", "alice@example.com");
        Notification n2 = NotificationFactory.create("SMS",   "+1-555-0100");
        Notification n3 = NotificationFactory.create("PUSH",  "device-xyz-123");
        n1.send("Your order has been placed!");
        n2.send("OTP: 483920");
        n3.send("Flash sale starts now!");
        System.out.println("Types: " + n1.getType() + ", " + n2.getType() + ", " + n3.getType());
    }

    // --------------------------------------------------------------
    // 3. ABSTRACT FACTORY — family of related objects
    // --------------------------------------------------------------
    interface Button   { void render(); }
    interface Checkbox { void render(); }

    static class WindowsButton   implements Button   { public void render() { System.out.println("[Windows] Rendering Button"); } }
    static class WindowsCheckbox implements Checkbox { public void render() { System.out.println("[Windows] Rendering Checkbox"); } }
    static class MacButton        implements Button   { public void render() { System.out.println("[Mac] Rendering Button"); } }
    static class MacCheckbox      implements Checkbox { public void render() { System.out.println("[Mac] Rendering Checkbox"); } }

    interface GUIFactory {
        Button   createButton();
        Checkbox createCheckbox();
    }
    static class WindowsFactory implements GUIFactory {
        public Button   createButton()   { return new WindowsButton(); }
        public Checkbox createCheckbox() { return new WindowsCheckbox(); }
    }
    static class MacFactory implements GUIFactory {
        public Button   createButton()   { return new MacButton(); }
        public Checkbox createCheckbox() { return new MacCheckbox(); }
    }

    static void renderUI(GUIFactory factory) {
        factory.createButton().render();
        factory.createCheckbox().render();
    }

    static void abstractFactoryDemo() {
        System.out.println("\n========== 3. ABSTRACT FACTORY ==========");
        System.out.println("-- Windows UI --");
        renderUI(new WindowsFactory());
        System.out.println("-- Mac UI --");
        renderUI(new MacFactory());
    }

    // --------------------------------------------------------------
    // 4. BUILDER — construct complex objects step by step
    // --------------------------------------------------------------
    static class Pizza {
        private final String size;
        private final String crust;
        private final boolean cheese;
        private final boolean pepperoni;
        private final boolean mushrooms;
        private final boolean olives;

        private Pizza(Builder b) {
            this.size      = b.size;
            this.crust     = b.crust;
            this.cheese    = b.cheese;
            this.pepperoni = b.pepperoni;
            this.mushrooms = b.mushrooms;
            this.olives    = b.olives;
        }

        @Override
        public String toString() {
            List<String> toppings = new ArrayList<>();
            if (cheese)    toppings.add("Cheese");
            if (pepperoni) toppings.add("Pepperoni");
            if (mushrooms) toppings.add("Mushrooms");
            if (olives)    toppings.add("Olives");
            return "Pizza{size=" + size + ", crust=" + crust +
                    ", toppings=" + toppings + "}";
        }

        static class Builder {
            private String  size      = "Medium";
            private String  crust     = "Thin";
            private boolean cheese    = false;
            private boolean pepperoni = false;
            private boolean mushrooms = false;
            private boolean olives    = false;

            Builder size(String s)      { this.size = s;          return this; }
            Builder crust(String c)     { this.crust = c;         return this; }
            Builder cheese()            { this.cheese = true;     return this; }
            Builder pepperoni()         { this.pepperoni = true;  return this; }
            Builder mushrooms()         { this.mushrooms = true;  return this; }
            Builder olives()            { this.olives = true;     return this; }
            Pizza build()               { return new Pizza(this); }
        }
    }

    static void builderDemo() {
        System.out.println("\n========== 4. BUILDER ==========");
        Pizza p1 = new Pizza.Builder()
                .size("Large").crust("Thick").cheese().pepperoni().build();
        Pizza p2 = new Pizza.Builder()
                .size("Small").crust("Thin").cheese().mushrooms().olives().build();
        Pizza p3 = new Pizza.Builder().build(); // defaults
        System.out.println(p1);
        System.out.println(p2);
        System.out.println(p3);
    }

    // --------------------------------------------------------------
    // 5. PROTOTYPE — clone existing objects
    // --------------------------------------------------------------
    static class Employee implements Cloneable {
        String name;
        String department;
        List<String> skills;

        Employee(String name, String dept, List<String> skills) {
            this.name       = name;
            this.department = dept;
            this.skills     = new ArrayList<>(skills);
        }

        // Deep clone
        public Employee clone() {
            return new Employee(this.name, this.department, new ArrayList<>(this.skills));
        }

        @Override
        public String toString() {
            return "Employee{name='" + name + "', dept='" + department + "', skills=" + skills + "}";
        }
    }

    static void prototypeDemo() {
        System.out.println("\n========== 5. PROTOTYPE ==========");
        Employee original = new Employee("Alice", "Engineering",
                Arrays.asList("Java", "Spring", "SQL"));
        System.out.println("Original : " + original);

        Employee clone1 = original.clone();
        clone1.name = "Bob";
        clone1.skills.add("Kafka");

        Employee clone2 = original.clone();
        clone2.name = "Carol";
        clone2.department = "DevOps";

        System.out.println("Original : " + original); // unchanged
        System.out.println("Clone 1  : " + clone1);
        System.out.println("Clone 2  : " + clone2);
    }

    // ================================================================
    // STRUCTURAL PATTERNS
    // ================================================================

    // --------------------------------------------------------------
    // 6. ADAPTER — make incompatible interfaces compatible
    // --------------------------------------------------------------
    // Old (legacy) payment gateway
    static class LegacyPaymentGateway {
        public String makePaymentInDollars(double amount) {
            return "Legacy payment processed: $" + amount;
        }
    }

    // New interface expected by the system
    interface PaymentProcessor {
        String processPayment(double amount, String currency);
    }

    // Adapter wraps old gateway to match new interface
    static class PaymentAdapter implements PaymentProcessor {
        private final LegacyPaymentGateway legacy = new LegacyPaymentGateway();

        public String processPayment(double amount, String currency) {
            double converted = currency.equals("EUR") ? amount * 1.08 : amount;
            return "[Adapter] " + legacy.makePaymentInDollars(converted);
        }
    }

    static void adapterDemo() {
        System.out.println("\n========== 6. ADAPTER ==========");
        PaymentProcessor processor = new PaymentAdapter();
        System.out.println(processor.processPayment(100.0, "USD"));
        System.out.println(processor.processPayment(100.0, "EUR"));
    }

    // --------------------------------------------------------------
    // 7. BRIDGE — decouple abstraction from implementation
    // --------------------------------------------------------------
    interface MessageSender {
        void sendMessage(String msg);
    }
    static class EmailSender implements MessageSender {
        public void sendMessage(String msg) { System.out.println("[EmailSender] " + msg); }
    }
    static class SlackSender implements MessageSender {
        public void sendMessage(String msg) { System.out.println("[SlackSender] " + msg); }
    }

    abstract static class Message {
        protected MessageSender sender;
        Message(MessageSender sender) { this.sender = sender; }
        abstract void send(String content);
    }
    static class UrgentMessage extends Message {
        UrgentMessage(MessageSender sender) { super(sender); }
        public void send(String content) { sender.sendMessage("[URGENT] " + content); }
    }
    static class NormalMessage extends Message {
        NormalMessage(MessageSender sender) { super(sender); }
        public void send(String content) { sender.sendMessage("[INFO] " + content); }
    }

    static void bridgeDemo() {
        System.out.println("\n========== 7. BRIDGE ==========");
        new UrgentMessage(new EmailSender()).send("Server is down!");
        new UrgentMessage(new SlackSender()).send("Server is down!");
        new NormalMessage(new EmailSender()).send("Weekly report ready.");
        new NormalMessage(new SlackSender()).send("Weekly report ready.");
    }

    // --------------------------------------------------------------
    // 8. COMPOSITE — tree structure, uniform treatment
    // --------------------------------------------------------------
    interface FileSystemItem {
        void display(String indent);
        long getSize();
    }

    static class File implements FileSystemItem {
        private final String name;
        private final long size;
        File(String name, long size) { this.name = name; this.size = size; }
        public void display(String indent) {
            System.out.println(indent + "FILE: " + name + " (" + size + " KB)");
        }
        public long getSize() { return size; }
    }

    static class Folder implements FileSystemItem {
        private final String name;
        private final List<FileSystemItem> items = new ArrayList<>();
        Folder(String name) { this.name = name; }
        public void add(FileSystemItem item) { items.add(item); }
        public void display(String indent) {
            System.out.println(indent + "FOLDER: " + name + " (" + getSize() + " KB)");
            items.forEach(i -> i.display(indent + "  "));
        }
        public long getSize() { return items.stream().mapToLong(FileSystemItem::getSize).sum(); }
    }

    static void compositeDemo() {
        System.out.println("\n========== 8. COMPOSITE ==========");
        Folder root = new Folder("root");
        Folder src  = new Folder("src");
        Folder test = new Folder("test");

        src.add(new File("Main.java",       15));
        src.add(new File("Service.java",    20));
        src.add(new File("Repository.java", 18));
        test.add(new File("MainTest.java",  10));
        test.add(new File("ServiceTest.java", 12));

        root.add(src);
        root.add(test);
        root.add(new File("pom.xml", 5));
        root.add(new File("README.md", 3));

        root.display("");
        System.out.println("Total size: " + root.getSize() + " KB");
    }

    // --------------------------------------------------------------
    // 9. DECORATOR — add behaviour dynamically
    // --------------------------------------------------------------
    interface TextFormatter {
        String format(String text);
    }
    static class PlainText implements TextFormatter {
        public String format(String text) { return text; }
    }
    static abstract class TextDecorator implements TextFormatter {
        protected TextFormatter wrapped;
        TextDecorator(TextFormatter w) { this.wrapped = w; }
    }
    static class BoldDecorator extends TextDecorator {
        BoldDecorator(TextFormatter w) { super(w); }
        public String format(String text) { return "<b>" + wrapped.format(text) + "</b>"; }
    }
    static class ItalicDecorator extends TextDecorator {
        ItalicDecorator(TextFormatter w) { super(w); }
        public String format(String text) { return "<i>" + wrapped.format(text) + "</i>"; }
    }
    static class UpperCaseDecorator extends TextDecorator {
        UpperCaseDecorator(TextFormatter w) { super(w); }
        public String format(String text) { return wrapped.format(text).toUpperCase(); }
    }
    static class TrimDecorator extends TextDecorator {
        TrimDecorator(TextFormatter w) { super(w); }
        public String format(String text) { return wrapped.format(text.trim()); }
    }

    static void decoratorDemo() {
        System.out.println("\n========== 9. DECORATOR ==========");
        TextFormatter plain  = new PlainText();
        TextFormatter bold   = new BoldDecorator(plain);
        TextFormatter italic = new ItalicDecorator(bold);
        TextFormatter upper  = new UpperCaseDecorator(italic);
        TextFormatter trim   = new TrimDecorator(upper);

        System.out.println(plain.format("Hello World"));
        System.out.println(bold.format("Hello World"));
        System.out.println(italic.format("Hello World"));
        System.out.println(upper.format("Hello World"));
        System.out.println(trim.format("  hello world  "));
    }

    // --------------------------------------------------------------
    // 10. FACADE — simplify complex subsystem
    // --------------------------------------------------------------
    static class InventoryService {
        boolean reserve(String product, int qty) {
            System.out.println("[Inventory] Reserved " + qty + "x " + product);
            return true;
        }
        void release(String product, int qty) {
            System.out.println("[Inventory] Released " + qty + "x " + product);
        }
    }
    static class PaymentGateway {
        boolean charge(String card, double amount) {
            System.out.println("[Payment] Charged $" + amount + " from card " + card);
            return true;
        }
    }
    static class ShippingService {
        String schedule(String address, String product) {
            String trackingId = "TRK-" + (int)(Math.random() * 90000 + 10000);
            System.out.println("[Shipping] Scheduled delivery to " + address + " | " + trackingId);
            return trackingId;
        }
    }
    static class EmailService {
        void sendConfirmation(String email, String trackingId) {
            System.out.println("[Email] Confirmation sent to " + email + " | Tracking: " + trackingId);
        }
    }

    // Facade hides all subsystem complexity
    static class OrderFacade {
        private final InventoryService inventory = new InventoryService();
        private final PaymentGateway   payment   = new PaymentGateway();
        private final ShippingService  shipping  = new ShippingService();
        private final EmailService     email     = new EmailService();

        public boolean placeOrder(String product, int qty, String card,
                                   double amount, String address, String userEmail) {
            System.out.println("[Facade] Placing order for: " + product);
            if (!inventory.reserve(product, qty)) { return false; }
            if (!payment.charge(card, amount))    { inventory.release(product, qty); return false; }
            String trackingId = shipping.schedule(address, product);
            email.sendConfirmation(userEmail, trackingId);
            System.out.println("[Facade] Order placed successfully!");
            return true;
        }
    }

    static void facadeDemo() {
        System.out.println("\n========== 10. FACADE ==========");
        OrderFacade facade = new OrderFacade();
        facade.placeOrder("MacBook Pro", 1, "VISA-4321",
                2499.99, "123 Main St, NY", "alice@example.com");
    }

    // --------------------------------------------------------------
    // 11. FLYWEIGHT — share common state to save memory
    // --------------------------------------------------------------
    static class TreeType {
        private final String name;
        private final String color;
        private final String texture;

        TreeType(String name, String color, String texture) {
            this.name = name; this.color = color; this.texture = texture;
            System.out.println("[Flyweight] Created TreeType: " + name);
        }
        void draw(int x, int y) {
            System.out.println("[Flyweight] Drawing " + name + "(" + color + ") at (" + x + "," + y + ")");
        }
    }

    static class TreeFactory {
        private static final Map<String, TreeType> cache = new HashMap<>();

        public static TreeType getTreeType(String name, String color, String texture) {
            String key = name + "-" + color;
            return cache.computeIfAbsent(key, k -> new TreeType(name, color, texture));
        }

        public static int getCacheSize() { return cache.size(); }
    }

    static class Tree {
        int x, y;
        TreeType type;
        Tree(int x, int y, TreeType type) { this.x = x; this.y = y; this.type = type; }
        void draw() { type.draw(x, y); }
    }

    static void flyweightDemo() {
        System.out.println("\n========== 11. FLYWEIGHT ==========");
        List<Tree> forest = new ArrayList<>();
        Random rand = new Random(42);

        String[][] treeTypes = {{"Oak","Green","Rough"},{"Pine","DarkGreen","Smooth"},{"Maple","Orange","Medium"}};
        for (int i = 0; i < 9; i++) {
            String[] t = treeTypes[i % 3];
            TreeType type = TreeFactory.getTreeType(t[0], t[1], t[2]);
            forest.add(new Tree(rand.nextInt(100), rand.nextInt(100), type));
        }
        forest.forEach(Tree::draw);
        System.out.println("Trees in forest: " + forest.size() + " | TreeType objects: " + TreeFactory.getCacheSize());
    }

    // --------------------------------------------------------------
    // 12. PROXY — control access to real object
    // --------------------------------------------------------------
    interface ImageLoader {
        void display();
        String getName();
    }

    static class RealImage implements ImageLoader {
        private final String filename;
        RealImage(String filename) {
            this.filename = filename;
            loadFromDisk(); // expensive operation
        }
        private void loadFromDisk() {
            System.out.println("[RealImage] Loading from disk: " + filename);
        }
        public void display() { System.out.println("[RealImage] Displaying: " + filename); }
        public String getName() { return filename; }
    }

    static class ProxyImage implements ImageLoader {
        private final String filename;
        private RealImage realImage;
        private static final Map<String, String> cache = new HashMap<>();

        ProxyImage(String filename) { this.filename = filename; }

        public void display() {
            // Lazy loading — create real image only on first use
            if (realImage == null) {
                if (cache.containsKey(filename)) {
                    System.out.println("[Proxy] Cache hit for: " + filename);
                } else {
                    realImage = new RealImage(filename);
                    cache.put(filename, filename);
                }
            }
            if (realImage != null) realImage.display();
        }
        public String getName() { return filename; }
    }

    static void proxyDemo() {
        System.out.println("\n========== 12. PROXY ==========");
        ImageLoader img1 = new ProxyImage("photo1.jpg");
        ImageLoader img2 = new ProxyImage("photo2.jpg");
        ImageLoader img3 = new ProxyImage("photo1.jpg"); // same as img1

        System.out.println("-- First access --");
        img1.display();
        img2.display();
        System.out.println("-- Second access (lazy) --");
        img1.display(); // already loaded
        img3.display(); // cache hit
    }

    // ================================================================
    // BEHAVIORAL PATTERNS
    // ================================================================

    // --------------------------------------------------------------
    // 13. CHAIN OF RESPONSIBILITY — pass request along chain
    // --------------------------------------------------------------
    static abstract class RequestHandler {
        protected RequestHandler next;
        RequestHandler setNext(RequestHandler next) { this.next = next; return next; }
        abstract void handle(Map<String, Object> request);
    }

    static class AuthHandler extends RequestHandler {
        public void handle(Map<String, Object> request) {
            if (!Boolean.TRUE.equals(request.get("authenticated"))) {
                System.out.println("[Auth] REJECTED — not authenticated");
                return;
            }
            System.out.println("[Auth] PASSED");
            if (next != null) next.handle(request);
        }
    }
    static class RateLimitHandler extends RequestHandler {
        private final Map<String, Integer> counts = new HashMap<>();
        private final int limit;
        RateLimitHandler(int limit) { this.limit = limit; }
        public void handle(Map<String, Object> request) {
            String user = (String) request.get("user");
            int count = counts.merge(user, 1, Integer::sum);
            if (count > limit) {
                System.out.println("[RateLimit] REJECTED — too many requests from " + user);
                return;
            }
            System.out.println("[RateLimit] PASSED (" + count + "/" + limit + ")");
            if (next != null) next.handle(request);
        }
    }
    static class LoggingHandler extends RequestHandler {
        public void handle(Map<String, Object> request) {
            System.out.println("[Logging] Request logged: " + request.get("endpoint"));
            if (next != null) next.handle(request);
        }
    }
    static class BusinessHandler extends RequestHandler {
        public void handle(Map<String, Object> request) {
            System.out.println("[Business] Processing: " + request.get("endpoint") + " — SUCCESS");
        }
    }

    static void chainOfResponsibilityDemo() {
        System.out.println("\n========== 13. CHAIN OF RESPONSIBILITY ==========");
        RequestHandler auth = new AuthHandler();
        RequestHandler rate = new RateLimitHandler(2);
        RequestHandler log  = new LoggingHandler();
        RequestHandler biz  = new BusinessHandler();
        auth.setNext(rate).setNext(log).setNext(biz);

        Map<String, Object> req1 = Map.of("authenticated", true,  "user", "alice", "endpoint", "GET /orders");
        Map<String, Object> req2 = Map.of("authenticated", true,  "user", "alice", "endpoint", "POST /order");
        Map<String, Object> req3 = Map.of("authenticated", true,  "user", "alice", "endpoint", "DELETE /order"); // rate limited
        Map<String, Object> req4 = Map.of("authenticated", false, "user", "bob",   "endpoint", "GET /secret"); // unauth

        System.out.println("-- Request 1 --"); auth.handle(new HashMap<>(req1));
        System.out.println("-- Request 2 --"); auth.handle(new HashMap<>(req2));
        System.out.println("-- Request 3 (rate limited) --"); auth.handle(new HashMap<>(req3));
        System.out.println("-- Request 4 (unauthenticated) --"); auth.handle(new HashMap<>(req4));
    }

    // --------------------------------------------------------------
    // 14. COMMAND — encapsulate request as object (with undo)
    // --------------------------------------------------------------
    interface Command {
        void execute();
        void undo();
        String getName();
    }

    static class TextEditor {
        private final StringBuilder content = new StringBuilder();
        void append(String text)  { content.append(text); }
        void delete(int len)      { if (len <= content.length()) content.delete(content.length() - len, content.length()); }
        String getContent()       { return content.toString(); }
    }

    static class AppendCommand implements Command {
        private final TextEditor editor;
        private final String text;
        AppendCommand(TextEditor e, String text) { this.editor = e; this.text = text; }
        public void execute() { editor.append(text); }
        public void undo()    { editor.delete(text.length()); }
        public String getName() { return "Append('" + text + "')"; }
    }

    static class CommandHistory {
        private final Deque<Command> history = new ArrayDeque<>();
        void execute(Command cmd) { cmd.execute(); history.push(cmd); System.out.println("[Command] " + cmd.getName() + " executed"); }
        void undo() {
            if (!history.isEmpty()) {
                Command cmd = history.pop();
                cmd.undo();
                System.out.println("[Command] Undone: " + cmd.getName());
            }
        }
    }

    static void commandDemo() {
        System.out.println("\n========== 14. COMMAND ==========");
        TextEditor editor = new TextEditor();
        CommandHistory history = new CommandHistory();
        history.execute(new AppendCommand(editor, "Hello"));
        history.execute(new AppendCommand(editor, " World"));
        history.execute(new AppendCommand(editor, "!"));
        System.out.println("Content: " + editor.getContent());
        history.undo();
        System.out.println("After undo: " + editor.getContent());
        history.undo();
        System.out.println("After undo: " + editor.getContent());
    }

    // --------------------------------------------------------------
    // 15. MEDIATOR — centralize communication
    // --------------------------------------------------------------
    interface ChatMediator {
        void sendMessage(String msg, ChatUser sender);
        void addUser(ChatUser user);
    }

    static class ChatRoom implements ChatMediator {
        private final List<ChatUser> users = new ArrayList<>();
        public void addUser(ChatUser user) { users.add(user); }
        public void sendMessage(String msg, ChatUser sender) {
            users.stream()
                    .filter(u -> u != sender)
                    .forEach(u -> u.receive(sender.getName() + ": " + msg));
        }
    }

    static class ChatUser {
        private final String name;
        private final ChatMediator mediator;
        ChatUser(String name, ChatMediator mediator) {
            this.name = name; this.mediator = mediator;
        }
        public void send(String msg) {
            System.out.println("[" + name + "] Sends: " + msg);
            mediator.sendMessage(msg, this);
        }
        public void receive(String msg) { System.out.println("[" + name + "] Received: " + msg); }
        public String getName() { return name; }
    }

    static void mediatorDemo() {
        System.out.println("\n========== 15. MEDIATOR ==========");
        ChatMediator room = new ChatRoom();
        ChatUser alice = new ChatUser("Alice", room);
        ChatUser bob   = new ChatUser("Bob",   room);
        ChatUser carol = new ChatUser("Carol", room);
        room.addUser(alice); room.addUser(bob); room.addUser(carol);
        alice.send("Hello everyone!");
        bob.send("Hey Alice!");
    }

    // --------------------------------------------------------------
    // 16. MEMENTO — save and restore state (undo/redo)
    // --------------------------------------------------------------
    static class GameState {
        int level, health, score;
        GameState(int level, int health, int score) {
            this.level = level; this.health = health; this.score = score;
        }
        @Override public String toString() {
            return "GameState{level=" + level + ", health=" + health + ", score=" + score + "}";
        }
    }

    static class Game {
        private int level = 1, health = 100, score = 0;

        GameState save() { return new GameState(level, health, score); }

        void restore(GameState state) {
            this.level = state.level; this.health = state.health; this.score = state.score;
            System.out.println("[Game] Restored to: " + this);
        }

        void play(int scoreDelta, int healthDelta, int levelDelta) {
            score  += scoreDelta; health += healthDelta; level += levelDelta;
            System.out.println("[Game] After play: " + this);
        }

        @Override public String toString() {
            return "Game{level=" + level + ", health=" + health + ", score=" + score + "}";
        }
    }

    static void mementoDemo() {
        System.out.println("\n========== 16. MEMENTO ==========");
        Game game = new Game();
        Deque<GameState> saves = new ArrayDeque<>();

        game.play(100, 0, 0);
        saves.push(game.save()); System.out.println("[Memento] Game saved");

        game.play(200, -30, 1);
        saves.push(game.save()); System.out.println("[Memento] Game saved");

        game.play(50, -80, 0); // took heavy damage
        System.out.println("[Memento] Oops! Loading last save...");
        game.restore(saves.pop());

        game.restore(saves.pop()); // restore to earlier save
    }

    // --------------------------------------------------------------
    // 17. OBSERVER — notify dependents on state change
    // --------------------------------------------------------------
    interface StockObserver { void onPriceChange(String stock, double price); }

    static class StockMarket {
        private final Map<String, Double> prices  = new HashMap<>();
        private final List<StockObserver> observers = new ArrayList<>();

        void subscribe(StockObserver o)   { observers.add(o); }
        void unsubscribe(StockObserver o) { observers.remove(o); }

        void updatePrice(String stock, double price) {
            prices.put(stock, price);
            System.out.println("[StockMarket] " + stock + " price -> $" + price);
            observers.forEach(o -> o.onPriceChange(stock, price));
        }
    }

    static class StockAlertApp implements StockObserver {
        private final String name;
        private final double threshold;
        StockAlertApp(String name, double threshold) { this.name = name; this.threshold = threshold; }
        public void onPriceChange(String stock, double price) {
            if (price > threshold) System.out.println("[Alert:" + name + "] " + stock + " exceeded $" + threshold + "! Now: $" + price);
        }
    }

    static class PortfolioTracker implements StockObserver {
        private final Map<String, Double> portfolio;
        PortfolioTracker(Map<String, Double> portfolio) { this.portfolio = portfolio; }
        public void onPriceChange(String stock, double price) {
            if (portfolio.containsKey(stock)) {
                double value = portfolio.get(stock) * price;
                System.out.println("[Portfolio] " + stock + " holding value: $" + String.format("%.2f", value));
            }
        }
    }

    static void observerDemo() {
        System.out.println("\n========== 17. OBSERVER ==========");
        StockMarket market = new StockMarket();
        market.subscribe(new StockAlertApp("HighAlert", 150.0));
        market.subscribe(new StockAlertApp("MegaAlert", 200.0));
        market.subscribe(new PortfolioTracker(Map.of("AAPL", 10.0, "GOOG", 5.0)));

        market.updatePrice("AAPL", 145.0);
        market.updatePrice("AAPL", 160.0);
        market.updatePrice("GOOG", 180.0);
        market.updatePrice("AAPL", 210.0);
    }

    // --------------------------------------------------------------
    // 18. STATE — change behaviour based on internal state
    // --------------------------------------------------------------
    interface OrderState {
        void next(OrderContext ctx);
        String getStateName();
    }
    static class CreatedState implements OrderState {
        public void next(OrderContext ctx) {
            System.out.println("[State] Order confirmed.");
            ctx.setState(new ConfirmedState());
        }
        public String getStateName() { return "CREATED"; }
    }
    static class ConfirmedState implements OrderState {
        public void next(OrderContext ctx) {
            System.out.println("[State] Order shipped.");
            ctx.setState(new ShippedState());
        }
        public String getStateName() { return "CONFIRMED"; }
    }
    static class ShippedState implements OrderState {
        public void next(OrderContext ctx) {
            System.out.println("[State] Order delivered.");
            ctx.setState(new DeliveredState());
        }
        public String getStateName() { return "SHIPPED"; }
    }
    static class DeliveredState implements OrderState {
        public void next(OrderContext ctx) { System.out.println("[State] Order already delivered."); }
        public String getStateName() { return "DELIVERED"; }
    }

    static class OrderContext {
        private OrderState state = new CreatedState();
        void setState(OrderState s) { this.state = s; }
        void next() {
            System.out.print("[Order:" + state.getStateName() + "] -> ");
            state.next(this);
        }
        String getState() { return state.getStateName(); }
    }

    static void stateDemo() {
        System.out.println("\n========== 18. STATE ==========");
        OrderContext order = new OrderContext();
        order.next(); // CREATED -> CONFIRMED
        order.next(); // CONFIRMED -> SHIPPED
        order.next(); // SHIPPED -> DELIVERED
        order.next(); // DELIVERED (no change)
    }

    // --------------------------------------------------------------
    // 19. STRATEGY — interchangeable algorithms
    // --------------------------------------------------------------
    interface SortStrategy { void sort(int[] arr); String getName(); }

    static class BubbleSort implements SortStrategy {
        public void sort(int[] arr) {
            int n = arr.length;
            for (int i = 0; i < n - 1; i++)
                for (int j = 0; j < n - i - 1; j++)
                    if (arr[j] > arr[j+1]) { int t=arr[j]; arr[j]=arr[j+1]; arr[j+1]=t; }
        }
        public String getName() { return "BubbleSort"; }
    }
    static class SelectionSort implements SortStrategy {
        public void sort(int[] arr) {
            int n = arr.length;
            for (int i = 0; i < n - 1; i++) {
                int min = i;
                for (int j = i + 1; j < n; j++) if (arr[j] < arr[min]) min = j;
                int t = arr[min]; arr[min] = arr[i]; arr[i] = t;
            }
        }
        public String getName() { return "SelectionSort"; }
    }
    static class JavaBuiltInSort implements SortStrategy {
        public void sort(int[] arr) { Arrays.sort(arr); }
        public String getName() { return "Arrays.sort (TimSort)"; }
    }

    static class Sorter {
        private SortStrategy strategy;
        Sorter(SortStrategy s) { this.strategy = s; }
        void setStrategy(SortStrategy s) { this.strategy = s; }
        void sort(int[] arr) {
            strategy.sort(arr);
            System.out.println("[Strategy:" + strategy.getName() + "] Sorted: " + Arrays.toString(arr));
        }
    }

    static void strategyDemo() {
        System.out.println("\n========== 19. STRATEGY ==========");
        int[] data1 = {5, 3, 8, 1, 9, 2};
        int[] data2 = {5, 3, 8, 1, 9, 2};
        int[] data3 = {5, 3, 8, 1, 9, 2};

        Sorter sorter = new Sorter(new BubbleSort());
        sorter.sort(data1);
        sorter.setStrategy(new SelectionSort());
        sorter.sort(data2);
        sorter.setStrategy(new JavaBuiltInSort());
        sorter.sort(data3);

        // Lambda strategy
        sorter.setStrategy(new SortStrategy() {
            public void sort(int[] a) { for (int i = 0; i < a.length / 2; i++) { int t=a[i]; a[i]=a[a.length-1-i]; a[a.length-1-i]=t; } }
            public String getName() { return "ReverseSort"; }
        });
        int[] data4 = {1, 2, 3, 4, 5};
        sorter.sort(data4);
    }

    // --------------------------------------------------------------
    // 20. TEMPLATE METHOD — define algorithm skeleton
    // --------------------------------------------------------------
    static abstract class ReportGenerator {
        // Template method — fixed skeleton
        final void generateReport(String data) {
            System.out.println("[Template] Starting report generation");
            String parsed    = parseData(data);
            String processed = processData(parsed);
            String formatted = formatOutput(processed);
            saveReport(formatted);
            System.out.println("[Template] Report done\n");
        }
        abstract String parseData(String data);
        abstract String processData(String data);
        String formatOutput(String data) { return "=== REPORT ===\n" + data + "\n=============="; }
        void saveReport(String report)   { System.out.println("[Template] Saved:\n" + report); }
    }

    static class CSVReportGenerator extends ReportGenerator {
        String parseData(String data)    { System.out.println("[CSV] Parsing CSV");     return data.replace(",", "|"); }
        String processData(String data)  { System.out.println("[CSV] Processing data");  return data.toUpperCase(); }
    }
    static class JSONReportGenerator extends ReportGenerator {
        String parseData(String data)    { System.out.println("[JSON] Parsing JSON");    return data.replace("{", "(").replace("}", ")"); }
        String processData(String data)  { System.out.println("[JSON] Processing data"); return data + " [PROCESSED]"; }
        @Override String formatOutput(String data) { return "{ \"report\": \"" + data + "\" }"; }
    }

    static void templateMethodDemo() {
        System.out.println("\n========== 20. TEMPLATE METHOD ==========");
        new CSVReportGenerator().generateReport("name,age,city");
        new JSONReportGenerator().generateReport("{name:Bob,age:25}");
    }

    // --------------------------------------------------------------
    // 21. VISITOR — add operations without modifying classes
    // --------------------------------------------------------------
    interface ShapeVisitor {
        void visit(Circle circle);
        void visit(Rectangle rectangle);
        void visit(Triangle triangle);
    }
    interface Shape { void accept(ShapeVisitor visitor); }

    static class Circle implements Shape {
        double radius;
        Circle(double r) { this.radius = r; }
        public void accept(ShapeVisitor v) { v.visit(this); }
    }
    static class Rectangle implements Shape {
        double width, height;
        Rectangle(double w, double h) { this.width = w; this.height = h; }
        public void accept(ShapeVisitor v) { v.visit(this); }
    }
    static class Triangle implements Shape {
        double base, height;
        Triangle(double b, double h) { this.base = b; this.height = h; }
        public void accept(ShapeVisitor v) { v.visit(this); }
    }

    static class AreaCalculator implements ShapeVisitor {
        public void visit(Circle c)    { System.out.printf("[Area] Circle     : %.2f%n", Math.PI * c.radius * c.radius); }
        public void visit(Rectangle r) { System.out.printf("[Area] Rectangle  : %.2f%n", r.width * r.height); }
        public void visit(Triangle t)  { System.out.printf("[Area] Triangle   : %.2f%n", 0.5 * t.base * t.height); }
    }
    static class PerimeterCalculator implements ShapeVisitor {
        public void visit(Circle c)    { System.out.printf("[Perimeter] Circle     : %.2f%n", 2 * Math.PI * c.radius); }
        public void visit(Rectangle r) { System.out.printf("[Perimeter] Rectangle  : %.2f%n", 2 * (r.width + r.height)); }
        public void visit(Triangle t)  { System.out.printf("[Perimeter] Triangle   : %.2f (approx base+2*side)%n", t.base + 2 * Math.sqrt(Math.pow(t.base/2,2) + Math.pow(t.height,2))); }
    }

    static void visitorDemo() {
        System.out.println("\n========== 21. VISITOR ==========");
        List<Shape> shapes = List.of(new Circle(5), new Rectangle(4, 6), new Triangle(3, 8));
        ShapeVisitor area      = new AreaCalculator();
        ShapeVisitor perimeter = new PerimeterCalculator();
        System.out.println("-- Areas --");
        shapes.forEach(s -> s.accept(area));
        System.out.println("-- Perimeters --");
        shapes.forEach(s -> s.accept(perimeter));
    }

    // ================================================================
    // MAIN
    // ================================================================
    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("        DESIGN PATTERNS - COMPLETE DEMO");
        System.out.println("============================================================");

        System.out.println("\n=================== CREATIONAL ===================");
        singletonDemo();
        factoryMethodDemo();
        abstractFactoryDemo();
        builderDemo();
        prototypeDemo();

        System.out.println("\n=================== STRUCTURAL ===================");
        adapterDemo();
        bridgeDemo();
        compositeDemo();
        decoratorDemo();
        facadeDemo();
        flyweightDemo();
        proxyDemo();

        System.out.println("\n=================== BEHAVIORAL ===================");
        chainOfResponsibilityDemo();
        commandDemo();
        mediatorDemo();
        mementoDemo();
        observerDemo();
        stateDemo();
        strategyDemo();
        templateMethodDemo();
        visitorDemo();

        System.out.println("\n============================================================");
        System.out.println("   ALL DESIGN PATTERN DEMOS COMPLETE");
        System.out.println("============================================================");
    }
}
