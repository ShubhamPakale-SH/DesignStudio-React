import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * Core Java & OOP — Complete Implementation
 * (Java 8 → Java 21 Syntax)
 *
 * Covers:
 *  1.  Encapsulation
 *  2.  Inheritance
 *  3.  Polymorphism (Overloading + Overriding)
 *  4.  Abstraction (Abstract Class + Interface)
 *  5.  Mutability vs Immutability
 *  6.  Lambda Expressions
 *  7.  Functional Interfaces
 *  8.  Stream API
 *  9.  Optional
 * 10.  Method References
 * 11.  Records (Java 16+)
 * 12.  Sealed Classes (Java 17+)
 * 13.  Pattern Matching instanceof (Java 16+)
 * 14.  Switch Expressions (Java 14+)
 * 15.  Text Blocks (Java 15+)
 * 16.  var (Java 10+)
 * 17.  Generics
 * 18.  Exception Handling
 * 19.  String manipulation
 * 20.  Collections & Comparators
 */
public class CoreJavaOOPImpl {

    // ================================================================
    // 1. ENCAPSULATION
    // ================================================================
    static class BankAccount {
        private final String accountNumber;
        private double balance;
        private final String owner;

        BankAccount(String accountNumber, String owner, double initialBalance) {
            if (initialBalance < 0) throw new IllegalArgumentException("Balance cannot be negative");
            this.accountNumber = accountNumber;
            this.owner         = owner;
            this.balance       = initialBalance;
        }

        // Controlled access — validation in setter logic
        public void deposit(double amount) {
            if (amount <= 0) throw new IllegalArgumentException("Deposit must be positive");
            balance += amount;
            System.out.println("[BankAccount] Deposited: " + amount + " | Balance: " + balance);
        }

        public void withdraw(double amount) {
            if (amount <= 0)       throw new IllegalArgumentException("Withdrawal must be positive");
            if (amount > balance)  throw new IllegalStateException("Insufficient funds");
            balance -= amount;
            System.out.println("[BankAccount] Withdrew: " + amount + " | Balance: " + balance);
        }

        // Getters only — no setters for sensitive fields
        public double getBalance()       { return balance; }
        public String getOwner()         { return owner; }
        public String getAccountNumber() { return accountNumber; }

        @Override
        public String toString() {
            return "BankAccount{owner='" + owner + "', balance=" + balance + "}";
        }
    }

    static void encapsulationDemo() {
        System.out.println("\n========== 1. ENCAPSULATION ==========");
        BankAccount acc = new BankAccount("ACC-001", "Alice", 1000.0);
        acc.deposit(500.0);
        acc.withdraw(200.0);
        System.out.println("Balance: " + acc.getBalance());
        try {
            acc.withdraw(5000); // protected by encapsulation
        } catch (IllegalStateException e) {
            System.out.println("Caught: " + e.getMessage());
        }
    }

    // ================================================================
    // 2. INHERITANCE
    // ================================================================
    static class Vehicle {
        protected String brand;
        protected int year;
        protected double speed;

        Vehicle(String brand, int year) {
            this.brand = brand;
            this.year  = year;
            System.out.println("[Vehicle] Created: " + brand);
        }

        public void accelerate(double amount) {
            speed += amount;
            System.out.println("[Vehicle] " + brand + " speed: " + speed + " km/h");
        }

        public void brake() {
            speed = Math.max(0, speed - 30);
            System.out.println("[Vehicle] " + brand + " braked. Speed: " + speed);
        }

        public String getInfo() { return brand + " (" + year + ")"; }
    }

    static class Car extends Vehicle {
        private int doors;

        Car(String brand, int year, int doors) {
            super(brand, year);  // call parent constructor
            this.doors = doors;
        }

        public void honk() { System.out.println("[Car] " + brand + ": Beep beep!"); }

        @Override
        public String getInfo() {
            return super.getInfo() + " | Doors: " + doors; // extend parent
        }
    }

    static class ElectricCar extends Car {
        private int batteryLevel;

        ElectricCar(String brand, int year, int batteryLevel) {
            super(brand, year, 4);
            this.batteryLevel = batteryLevel;
        }

        public void charge() {
            batteryLevel = 100;
            System.out.println("[ElectricCar] " + brand + " fully charged.");
        }

        @Override
        public void accelerate(double amount) {
            batteryLevel -= (int)(amount * 0.5);
            super.accelerate(amount);
            System.out.println("[ElectricCar] Battery: " + batteryLevel + "%");
        }

        @Override
        public String getInfo() {
            return super.getInfo() + " | Battery: " + batteryLevel + "%";
        }
    }

    static void inheritanceDemo() {
        System.out.println("\n========== 2. INHERITANCE ==========");
        Car car = new Car("Toyota", 2022, 4);
        car.accelerate(60);
        car.honk();
        System.out.println(car.getInfo());

        ElectricCar tesla = new ElectricCar("Tesla", 2023, 90);
        tesla.accelerate(80);
        tesla.charge();
        System.out.println(tesla.getInfo());

        // Multilevel: ElectricCar -> Car -> Vehicle
        System.out.println("Is Car a Vehicle? " + (car instanceof Vehicle));
        System.out.println("Is Tesla a Car?   " + (tesla instanceof Car));
        System.out.println("Is Tesla a Vehicle? " + (tesla instanceof Vehicle));
    }

    // ================================================================
    // 3. POLYMORPHISM — Overloading + Overriding
    // ================================================================
    static class Printer {
        // OVERLOADING — compile-time polymorphism
        void print(int x)             { System.out.println("[Overload] int: " + x); }
        void print(double x)          { System.out.println("[Overload] double: " + x); }
        void print(String x)          { System.out.println("[Overload] String: " + x); }
        void print(int x, int y)      { System.out.println("[Overload] int,int: " + x + "," + y); }
        void print(String x, int n)   { System.out.println("[Overload] String x" + n + ": " + x.repeat(n)); }
    }

    static abstract class Animal {
        String name;
        Animal(String name) { this.name = name; }
        abstract void sound();  // must override
        void eat() { System.out.println(name + " eats food."); }
    }

    static class Dog extends Animal {
        Dog(String name) { super(name); }
        @Override public void sound() { System.out.println(name + " says: Woof!"); }
    }

    static class Cat extends Animal {
        Cat(String name) { super(name); }
        @Override public void sound() { System.out.println(name + " says: Meow!"); }
    }

    static class Duck extends Animal {
        Duck(String name) { super(name); }
        @Override public void sound() { System.out.println(name + " says: Quack!"); }
        @Override public void eat()   { System.out.println(name + " eats fish."); } // covariant
    }

    static void polymorphismDemo() {
        System.out.println("\n========== 3. POLYMORPHISM ==========");

        // Overloading
        Printer p = new Printer();
        p.print(42);
        p.print(3.14);
        p.print("Hello");
        p.print(3, 5);
        p.print("Java", 3);

        // Runtime polymorphism — dynamic dispatch
        System.out.println("\n-- Runtime Polymorphism --");
        List<Animal> animals = List.of(new Dog("Rex"), new Cat("Whiskers"), new Duck("Donald"));
        for (Animal a : animals) {
            a.sound(); // dispatched to correct subclass at runtime
            a.eat();
        }

        // Upcasting and downcasting
        Animal animal = new Dog("Buddy");  // upcast
        animal.sound();                    // Dog's sound()
        if (animal instanceof Dog d) {     // pattern matching (Java 16+)
            d.eat();                       // d is already Dog
        }
    }

    // ================================================================
    // 4. ABSTRACTION — Abstract Class + Interface
    // ================================================================
    static abstract class Shape {
        protected String color;

        Shape(String color) { this.color = color; }

        abstract double area();
        abstract double perimeter();

        // Concrete method shared by all shapes
        void describe() {
            System.out.printf("[Shape] %s | Color: %s | Area: %.2f | Perimeter: %.2f%n",
                    getClass().getSimpleName(), color, area(), perimeter());
        }
    }

    interface Resizable {
        void resize(double factor);
        default String resizeInfo() { return "Resizable shape"; }
    }

    interface Drawable {
        void draw();
        default void drawWithBorder() { System.out.println("--- Border ---"); draw(); System.out.println("--------------"); }
    }

    static class Circle extends Shape implements Drawable, Resizable {
        private double radius;

        Circle(String color, double radius) { super(color); this.radius = radius; }

        @Override public double area()       { return Math.PI * radius * radius; }
        @Override public double perimeter()  { return 2 * Math.PI * radius; }
        @Override public void draw()         { System.out.println("[Circle] Drawing circle r=" + radius); }
        @Override public void resize(double f) { radius *= f; System.out.println("[Circle] Resized to r=" + radius); }
    }

    static class Rectangle extends Shape implements Drawable, Resizable {
        private double width, height;

        Rectangle(String color, double w, double h) { super(color); this.width = w; this.height = h; }

        @Override public double area()       { return width * height; }
        @Override public double perimeter()  { return 2 * (width + height); }
        @Override public void draw()         { System.out.println("[Rectangle] Drawing " + width + "x" + height); }
        @Override public void resize(double f) { width *= f; height *= f; }
    }

    static void abstractionDemo() {
        System.out.println("\n========== 4. ABSTRACTION ==========");
        List<Shape> shapes = List.of(
                new Circle("Red", 5),
                new Rectangle("Blue", 4, 6)
        );
        shapes.forEach(Shape::describe);

        // Interface usage
        Circle c = new Circle("Green", 3);
        c.drawWithBorder();
        c.resize(2.0);
        c.describe();
        System.out.println(c.resizeInfo());
    }

    // ================================================================
    // 5. MUTABILITY vs IMMUTABILITY
    // ================================================================

    // Mutable
    static class MutablePoint {
        private int x, y;
        MutablePoint(int x, int y) { this.x = x; this.y = y; }
        public void setX(int x) { this.x = x; }
        public void setY(int y) { this.y = y; }
        public int getX() { return x; }
        public int getY() { return y; }
        @Override public String toString() { return "MutablePoint(" + x + "," + y + ")"; }
    }

    // Immutable — final class, final fields, no setters, defensive copy
    static final class ImmutablePoint {
        private final int x;
        private final int y;

        ImmutablePoint(int x, int y) { this.x = x; this.y = y; }

        public int getX() { return x; }
        public int getY() { return y; }

        // Returns NEW object — doesn't modify this
        public ImmutablePoint translate(int dx, int dy) {
            return new ImmutablePoint(x + dx, y + dy);
        }
        public ImmutablePoint scale(int factor) {
            return new ImmutablePoint(x * factor, y * factor);
        }
        @Override public String toString() { return "ImmutablePoint(" + x + "," + y + ")"; }
        @Override public boolean equals(Object o) {
            if (!(o instanceof ImmutablePoint p)) return false;
            return x == p.x && y == p.y;
        }
        @Override public int hashCode() { return Objects.hash(x, y); }
    }

    // Immutable class with mutable field — defensive copy
    static final class ImmutableStudent {
        private final String name;
        private final List<String> subjects;

        ImmutableStudent(String name, List<String> subjects) {
            this.name     = name;
            this.subjects = List.copyOf(subjects); // defensive copy
        }
        public String getName()         { return name; }
        public List<String> getSubjects() { return subjects; } // List.copyOf is already unmodifiable
        @Override public String toString() { return "ImmutableStudent{" + name + ", " + subjects + "}"; }
    }

    static void mutabilityDemo() {
        System.out.println("\n========== 5. MUTABILITY vs IMMUTABILITY ==========");

        // Mutable
        MutablePoint mp = new MutablePoint(1, 2);
        System.out.println("Mutable before: " + mp);
        mp.setX(10); mp.setY(20);
        System.out.println("Mutable after : " + mp);  // same object changed

        // Immutable
        ImmutablePoint ip = new ImmutablePoint(1, 2);
        ImmutablePoint translated = ip.translate(5, 5);
        System.out.println("Immutable original  : " + ip);          // unchanged!
        System.out.println("Immutable translated: " + translated);  // new object

        // String immutability
        String s1 = "Hello";
        String s2 = s1;
        s1 = s1 + " World";  // creates NEW String
        System.out.println("s1: " + s1);  // Hello World
        System.out.println("s2: " + s2);  // Hello (unchanged)

        // String pool
        String a = "java";
        String b = "java";
        String c = new String("java");
        System.out.println("a == b (pool):    " + (a == b));   // true
        System.out.println("a == c (new):     " + (a == c));   // false
        System.out.println("a.equals(c):      " + a.equals(c)); // true

        // Immutable with mutable field
        List<String> mutableList = new ArrayList<>(Arrays.asList("Math", "Science"));
        ImmutableStudent student = new ImmutableStudent("Alice", mutableList);
        mutableList.add("History"); // external list modified
        System.out.println("Student (defensive copy): " + student); // not affected!
    }

    // ================================================================
    // 6. LAMBDA EXPRESSIONS + METHOD REFERENCES
    // ================================================================
    static void lambdaDemo() {
        System.out.println("\n========== 6. LAMBDAS & METHOD REFERENCES ==========");

        // Basic lambda
        Runnable r = () -> System.out.println("[Lambda] Hello from Runnable!");
        r.run();

        // Comparator lambda
        List<String> names = new ArrayList<>(Arrays.asList("Charlie", "Alice", "Bob", "Dave"));
        names.sort((a, b) -> a.compareTo(b));
        System.out.println("Sorted: " + names);

        names.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
        System.out.println("Sorted by length: " + names);

        // Consumer lambda
        Consumer<String> shout = s -> System.out.println("[Lambda] " + s.toUpperCase() + "!");
        names.forEach(shout);

        // Method references
        System.out.println("\n-- Method References --");
        names.forEach(System.out::println);                // instance method on arg

        List<String> upper = names.stream()
                .map(String::toUpperCase)                  // instance method
                .collect(Collectors.toList());
        System.out.println("Upper: " + upper);

        List<Integer> nums = Arrays.asList(3, 1, 4, 1, 5, 9);
        nums.sort(Integer::compare);                       // static method
        System.out.println("Sorted nums: " + nums);

        Supplier<ArrayList<String>> listFactory = ArrayList::new;  // constructor ref
        ArrayList<String> newList = listFactory.get();
        System.out.println("New list: " + newList.getClass().getSimpleName());
    }

    // ================================================================
    // 7. FUNCTIONAL INTERFACES
    // ================================================================
    @FunctionalInterface
    interface TriFunction<A, B, C, R> {
        R apply(A a, B b, C c);
    }

    static void functionalInterfaceDemo() {
        System.out.println("\n========== 7. FUNCTIONAL INTERFACES ==========");

        // Function — transform
        Function<String, Integer> strLen = String::length;
        System.out.println("Length: " + strLen.apply("Hello"));

        // Function composition
        Function<Integer, Integer> doubleIt = x -> x * 2;
        Function<Integer, Integer> addTen   = x -> x + 10;
        Function<Integer, Integer> doubleThenAdd = doubleIt.andThen(addTen);
        Function<Integer, Integer> addThenDouble = doubleIt.compose(addTen);
        System.out.println("double then add: " + doubleThenAdd.apply(5)); // 20
        System.out.println("add then double: " + addThenDouble.apply(5)); // 30

        // Predicate
        Predicate<String> isLong    = s -> s.length() > 5;
        Predicate<String> startsWithJ = s -> s.startsWith("J");
        Predicate<String> javaLong  = startsWithJ.and(isLong);
        System.out.println("Java is long J: " + javaLong.test("JavaScript")); // true
        System.out.println("Java is long J: " + javaLong.test("JS"));         // false

        // Consumer + BiConsumer
        Consumer<String> log = msg -> System.out.println("[Log] " + msg);
        Consumer<String> save = msg -> System.out.println("[Save] " + msg);
        Consumer<String> logAndSave = log.andThen(save);
        logAndSave.accept("Order created");

        BiConsumer<String, Integer> repeat = (s, n) -> System.out.println(s.repeat(n));
        repeat.accept("Java! ", 3);

        // Supplier
        Supplier<Double> random = Math::random;
        System.out.println("Random: " + random.get());

        Supplier<List<String>> listSupplier = ArrayList::new;
        List<String> list = listSupplier.get();
        list.add("Hello");

        // UnaryOperator & BinaryOperator
        UnaryOperator<String> trim  = String::trim;
        UnaryOperator<String> upper = String::toUpperCase;
        UnaryOperator<String> trimAndUpper = trim.andThen(upper);
        System.out.println(trimAndUpper.apply("  hello world  "));

        BinaryOperator<Integer> max = (a, b) -> a > b ? a : b;
        System.out.println("Max(7,3): " + max.apply(7, 3));

        // Custom TriFunction
        TriFunction<String, Integer, Boolean, String> desc =
                (name, age, active) -> name + " age=" + age + " active=" + active;
        System.out.println(desc.apply("Alice", 30, true));
    }

    // ================================================================
    // 8. STREAM API — Comprehensive
    // ================================================================
    record Employee(String name, String dept, double salary, int age) {}

    static void streamDemo() {
        System.out.println("\n========== 8. STREAM API ==========");

        List<Employee> employees = List.of(
                new Employee("Alice",  "Engineering", 95000, 30),
                new Employee("Bob",    "Marketing",   75000, 25),
                new Employee("Carol",  "Engineering", 110000,35),
                new Employee("Dave",   "HR",          65000, 28),
                new Employee("Eve",    "Engineering", 90000, 32),
                new Employee("Frank",  "Marketing",   80000, 29),
                new Employee("Grace",  "HR",          70000, 26),
                new Employee("Henry",  "Engineering", 120000,40)
        );

        // Filter
        System.out.println("-- Engineers --");
        employees.stream()
                .filter(e -> e.dept().equals("Engineering"))
                .map(Employee::name)
                .forEach(System.out::println);

        // Map + Collect
        List<String> names = employees.stream()
                .map(e -> e.name().toUpperCase())
                .sorted()
                .collect(Collectors.toList());
        System.out.println("\nAll names (upper): " + names);

        // Average salary
        OptionalDouble avgSalary = employees.stream()
                .mapToDouble(Employee::salary)
                .average();
        System.out.printf("%nAverage salary: %.2f%n", avgSalary.orElse(0));

        // Max salary
        employees.stream()
                .max(Comparator.comparingDouble(Employee::salary))
                .ifPresent(e -> System.out.println("Highest paid: " + e.name() + " $" + e.salary()));

        // Group by department
        System.out.println("\n-- By Department --");
        Map<String, List<Employee>> byDept = employees.stream()
                .collect(Collectors.groupingBy(Employee::dept));
        byDept.forEach((dept, emps) ->
                System.out.println(dept + ": " + emps.stream().map(Employee::name).collect(Collectors.joining(", "))));

        // Average salary per department
        System.out.println("\n-- Avg Salary by Dept --");
        Map<String, Double> avgByDept = employees.stream()
                .collect(Collectors.groupingBy(Employee::dept, Collectors.averagingDouble(Employee::salary)));
        avgByDept.forEach((dept, avg) -> System.out.printf("  %-15s $%.2f%n", dept, avg));

        // Count per department
        Map<String, Long> countByDept = employees.stream()
                .collect(Collectors.groupingBy(Employee::dept, Collectors.counting()));
        System.out.println("\nCount by dept: " + countByDept);

        // Partition by salary > 80000
        Map<Boolean, List<String>> partitioned = employees.stream()
                .collect(Collectors.partitioningBy(
                        e -> e.salary() > 80000,
                        Collectors.mapping(Employee::name, Collectors.toList())));
        System.out.println("\nHigh earners: " + partitioned.get(true));
        System.out.println("Low  earners: " + partitioned.get(false));

        // Joining
        String nameList = employees.stream()
                .map(Employee::name)
                .collect(Collectors.joining(", ", "[", "]"));
        System.out.println("\nAll names: " + nameList);

        // FlatMap
        List<List<Integer>> nested = List.of(List.of(1,2,3), List.of(4,5), List.of(6,7,8));
        List<Integer> flat = nested.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        System.out.println("\nFlatMap: " + flat);

        // Reduce
        double totalSalary = employees.stream()
                .mapToDouble(Employee::salary)
                .sum();
        System.out.printf("Total salary: $%.2f%n", totalSalary);

        // Statistics
        DoubleSummaryStatistics stats = employees.stream()
                .mapToDouble(Employee::salary)
                .summaryStatistics();
        System.out.printf("Salary stats: min=%.0f max=%.0f avg=%.0f%n",
                stats.getMin(), stats.getMax(), stats.getAverage());

        // anyMatch / allMatch / noneMatch
        System.out.println("\nAny engineer?        " + employees.stream().anyMatch(e -> e.dept().equals("Engineering")));
        System.out.println("All salary > 50000?  " + employees.stream().allMatch(e -> e.salary() > 50000));
        System.out.println("None salary < 0?     " + employees.stream().noneMatch(e -> e.salary() < 0));

        // toMap
        Map<String, Double> nameToSalary = employees.stream()
                .collect(Collectors.toMap(Employee::name, Employee::salary));
        System.out.println("\nAlice salary: " + nameToSalary.get("Alice"));

        // sorted multi-field
        System.out.println("\nSorted by dept then salary desc:");
        employees.stream()
                .sorted(Comparator.comparing(Employee::dept)
                        .thenComparing(Comparator.comparingDouble(Employee::salary).reversed()))
                .forEach(e -> System.out.printf("  %-15s %-12s $%.0f%n", e.name(), e.dept(), e.salary()));

        // distinct + limit + skip
        List<Integer> numbers = List.of(1, 2, 2, 3, 3, 3, 4, 5, 5);
        System.out.println("\nDistinct: " + numbers.stream().distinct().collect(Collectors.toList()));
        System.out.println("Limit 3:  " + numbers.stream().limit(3).collect(Collectors.toList()));
        System.out.println("Skip 5:   " + numbers.stream().skip(5).collect(Collectors.toList()));

        // IntStream range
        int sumTo100 = IntStream.rangeClosed(1, 100).sum();
        System.out.println("\nSum 1-100: " + sumTo100);

        // Collectors.toUnmodifiableList (Java 10+)
        List<String> engineers = employees.stream()
                .filter(e -> e.dept().equals("Engineering"))
                .map(Employee::name)
                .collect(Collectors.toUnmodifiableList());
        System.out.println("Engineers (unmodifiable): " + engineers);
    }

    // ================================================================
    // 9. OPTIONAL
    // ================================================================
    static Optional<Employee> findByName(List<Employee> employees, String name) {
        return employees.stream()
                .filter(e -> e.name().equalsIgnoreCase(name))
                .findFirst();
    }

    static void optionalDemo() {
        System.out.println("\n========== 9. OPTIONAL ==========");

        List<Employee> employees = List.of(
                new Employee("Alice", "Engineering", 95000, 30),
                new Employee("Bob",   "Marketing",   75000, 25)
        );

        // Present
        Optional<Employee> found = findByName(employees, "Alice");
        System.out.println("Found:    " + found.isPresent());
        found.ifPresent(e -> System.out.println("Employee: " + e.name()));

        // Map and filter
        String dept = findByName(employees, "Alice")
                .map(Employee::dept)
                .map(String::toUpperCase)
                .orElse("UNKNOWN");
        System.out.println("Dept: " + dept);

        // Empty
        Optional<Employee> notFound = findByName(employees, "Carol");
        System.out.println("Not found: " + notFound.isEmpty());
        Employee defaultEmp = notFound.orElse(new Employee("DEFAULT", "N/A", 0, 0));
        System.out.println("Default: " + defaultEmp.name());

        // orElseGet (lazy — supplier only called if empty)
        Employee lazy = notFound.orElseGet(() -> new Employee("LAZY", "N/A", 0, 0));
        System.out.println("Lazy: " + lazy.name());

        // orElseThrow
        try {
            Employee emp = notFound.orElseThrow(() -> new NoSuchElementException("Employee not found"));
        } catch (NoSuchElementException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        // filter
        Optional<Employee> highEarner = findByName(employees, "Alice")
                .filter(e -> e.salary() > 90000);
        System.out.println("Alice high earner: " + highEarner.isPresent());

        // Optional.ofNullable
        String nullableStr = null;
        String result = Optional.ofNullable(nullableStr)
                .map(String::toUpperCase)
                .orElse("was null");
        System.out.println("Nullable: " + result);

        // ifPresentOrElse (Java 9+)
        findByName(employees, "Bob").ifPresentOrElse(
                e -> System.out.println("Bob's salary: " + e.salary()),
                () -> System.out.println("Bob not found")
        );
    }

    // ================================================================
    // 10. RECORDS (Java 16+)
    // ================================================================
    record Point(int x, int y) {
        // Compact constructor — validation
        Point {
            if (x < 0 || y < 0) throw new IllegalArgumentException("Coordinates must be non-negative");
        }
        // Custom method
        double distanceTo(Point other) {
            return Math.sqrt(Math.pow(x - other.x, 2) + Math.pow(y - other.y, 2));
        }
        Point translate(int dx, int dy) { return new Point(x + dx, y + dy); }
    }

    record Person(String name, int age, String city) implements Comparable<Person> {
        // Custom static factory
        static Person of(String name, int age) { return new Person(name, age, "Unknown"); }
        @Override public int compareTo(Person o) { return Integer.compare(this.age, o.age); }
    }

    static void recordsDemo() {
        System.out.println("\n========== 10. RECORDS (Java 16+) ==========");

        Point p1 = new Point(3, 4);
        Point p2 = new Point(6, 8);
        System.out.println("p1: " + p1);                         // Point[x=3, y=4]
        System.out.println("p1.x(): " + p1.x());                // accessor
        System.out.println("Distance: " + p1.distanceTo(p2));    // 5.0
        System.out.println("Translated: " + p1.translate(2, 2)); // Point[x=5, y=6]
        System.out.println("p1 equals Point(3,4): " + p1.equals(new Point(3, 4))); // true

        try {
            new Point(-1, 2); // validation in compact constructor
        } catch (IllegalArgumentException e) {
            System.out.println("Caught: " + e.getMessage());
        }

        List<Person> people = Arrays.asList(
                new Person("Alice", 30, "NY"),
                Person.of("Bob", 25),
                new Person("Carol", 35, "SF")
        );
        Collections.sort(people);
        System.out.println("Sorted by age: " + people);
    }

    // ================================================================
    // 11. SEALED CLASSES (Java 17+) + PATTERN MATCHING SWITCH (Java 21)
    // ================================================================
    sealed interface Expr permits Num, Add, Mul, Neg {}
    record Num(double value)      implements Expr {}
    record Add(Expr left, Expr right) implements Expr {}
    record Mul(Expr left, Expr right) implements Expr {}
    record Neg(Expr expr)         implements Expr {}

    static double evaluate(Expr expr) {
        return switch (expr) {
            case Num(double v)          -> v;
            case Add(Expr l, Expr r)    -> evaluate(l) + evaluate(r);
            case Mul(Expr l, Expr r)    -> evaluate(l) * evaluate(r);
            case Neg(Expr e)            -> -evaluate(e);
        };
    }

    sealed interface Shape2D permits Circle2D, Rect2D, Triangle2D {}
    record Circle2D(double radius)           implements Shape2D {}
    record Rect2D(double width, double height) implements Shape2D {}
    record Triangle2D(double base, double height) implements Shape2D {}

    static double area(Shape2D shape) {
        return switch (shape) {
            case Circle2D(double r)        -> Math.PI * r * r;
            case Rect2D(double w, double h)-> w * h;
            case Triangle2D(double b, double h) -> 0.5 * b * h;
        };
    }

    static void sealedAndPatternDemo() {
        System.out.println("\n========== 11. SEALED CLASSES + PATTERN MATCHING (Java 17/21) ==========");

        // Sealed class + switch expression
        Expr expr = new Add(new Mul(new Num(3), new Num(4)), new Neg(new Num(2)));
        System.out.println("(3 * 4) + (-2) = " + evaluate(expr));

        // Shape area with pattern matching switch
        List<Shape2D> shapes = List.of(new Circle2D(5), new Rect2D(4, 6), new Triangle2D(3, 8));
        shapes.forEach(s -> System.out.printf("Area of %s: %.2f%n", s.getClass().getSimpleName(), area(s)));
    }

    // ================================================================
    // 12. PATTERN MATCHING instanceof (Java 16+)
    // ================================================================
    static void patternMatchingDemo() {
        System.out.println("\n========== 12. PATTERN MATCHING instanceof (Java 16+) ==========");

        List<Object> objects = List.of(42, "Hello", 3.14, List.of(1,2,3), true, new Point(1,2));

        for (Object obj : objects) {
            String desc = switch (obj) {
                case Integer i  -> "Integer: " + i * 2;
                case String s   -> "String (" + s.length() + "): " + s.toUpperCase();
                case Double d   -> String.format("Double: %.4f", d);
                case List<?> l  -> "List with " + l.size() + " elements";
                case Boolean b  -> "Boolean: " + (b ? "YES" : "NO");
                case Point p    -> "Point at (" + p.x() + "," + p.y() + ")";
                default         -> "Unknown: " + obj.getClass().getSimpleName();
            };
            System.out.println(desc);
        }

        // instanceof with condition (guarded pattern)
        Object value = "Hello World";
        if (value instanceof String s && s.length() > 5) {
            System.out.println("\nLong string: " + s.toUpperCase());
        }
    }

    // ================================================================
    // 13. SWITCH EXPRESSIONS (Java 14+)
    // ================================================================
    enum Day { MON, TUE, WED, THU, FRI, SAT, SUN }

    static void switchExpressionsDemo() {
        System.out.println("\n========== 13. SWITCH EXPRESSIONS (Java 14+) ==========");

        // Arrow syntax
        for (Day day : Day.values()) {
            String type = switch (day) {
                case MON, TUE, WED, THU, FRI -> "Weekday";
                case SAT, SUN                -> "Weekend";
            };
            System.out.println(day + " -> " + type);
        }

        // Yield for multi-statement blocks
        Day today = Day.WED;
        int hoursOfWork = switch (today) {
            case MON, TUE, WED, THU, FRI -> {
                System.out.println("Working day: " + today);
                yield 8;
            }
            case SAT -> { yield 4; }
            case SUN -> { yield 0; }
        };
        System.out.println("Hours: " + hoursOfWork);

        // Switch expression as method argument
        System.out.println("Discount: " + switch (today) {
            case SAT, SUN -> "20%";
            case FRI      -> "10%";
            default       -> "0%";
        });
    }

    // ================================================================
    // 14. TEXT BLOCKS (Java 15+)
    // ================================================================
    static void textBlocksDemo() {
        System.out.println("\n========== 14. TEXT BLOCKS (Java 15+) ==========");

        String json = """
                {
                    "name": "Alice",
                    "age": 30,
                    "city": "New York"
                }
                """;
        System.out.println("JSON:\n" + json);

        String html = """
                <html>
                    <body>
                        <h1>Hello, World!</h1>
                    </body>
                </html>
                """;
        System.out.println("HTML:\n" + html);

        String sql = """
                SELECT u.name, o.product, o.amount
                FROM   users u
                JOIN   orders o ON u.id = o.user_id
                WHERE  o.amount > 100
                ORDER  BY o.amount DESC
                """;
        System.out.println("SQL:\n" + sql);

        // Text block with formatted()
        String name = "Bob";
        double price = 29.99;
        String receipt = """
                Receipt
                -------
                Item   : %s
                Price  : $%.2f
                Status : Paid
                """.formatted(name, price);
        System.out.println(receipt);
    }

    // ================================================================
    // 15. VAR — Local Variable Type Inference (Java 10+)
    // ================================================================
    static void varDemo() {
        System.out.println("\n========== 15. VAR (Java 10+) ==========");

        var number     = 42;                          // int
        var name       = "Alice";                     // String
        var list       = new ArrayList<String>();     // ArrayList<String>
        var map        = new HashMap<String, Integer>(); // HashMap<String, Integer>
        var pi         = 3.14159;                     // double

        list.add("Java"); list.add("Python"); list.add("Go");
        map.put("a", 1);  map.put("b", 2);

        System.out.println("number: " + number + " (" + ((Object)number).getClass().getSimpleName() + ")");
        System.out.println("name  : " + name);
        System.out.println("list  : " + list);

        // var in for-each
        for (var item : list) {
            System.out.println("  item: " + item);
        }

        // var in try-with-resources
        // try (var br = new java.io.BufferedReader(new java.io.FileReader("file.txt"))) { ... }

        // var infers the most specific type
        var point = new Point(3, 4);
        System.out.println("Point: " + point.x() + "," + point.y());
    }

    // ================================================================
    // 16. GENERICS
    // ================================================================
    static class Pair<A, B> {
        private final A first;
        private final B second;
        Pair(A first, B second) { this.first = first; this.second = second; }
        public A getFirst()  { return first; }
        public B getSecond() { return second; }
        @Override public String toString() { return "(" + first + ", " + second + ")"; }

        static <X, Y> Pair<X, Y> of(X x, Y y) { return new Pair<>(x, y); }
        Pair<B, A> swap() { return new Pair<>(second, first); }
    }

    static class BoundedStack<T extends Comparable<T>> {
        private final List<T> items = new ArrayList<>();
        void push(T item) { items.add(item); }
        T pop()           { return items.isEmpty() ? null : items.remove(items.size() - 1); }
        T max()           { return items.stream().max(Comparator.naturalOrder()).orElse(null); }
        T min()           { return items.stream().min(Comparator.naturalOrder()).orElse(null); }
    }

    // Wildcard — upper bound
    static double sumList(List<? extends Number> list) {
        return list.stream().mapToDouble(Number::doubleValue).sum();
    }

    // Wildcard — lower bound
    static void addNumbers(List<? super Integer> list) {
        list.add(1); list.add(2); list.add(3);
    }

    static void genericsDemo() {
        System.out.println("\n========== 16. GENERICS ==========");

        Pair<String, Integer> p = Pair.of("Age", 30);
        System.out.println("Pair: " + p);
        System.out.println("Swapped: " + p.swap());

        BoundedStack<Integer> stack = new BoundedStack<>();
        stack.push(5); stack.push(2); stack.push(8); stack.push(1);
        System.out.println("Max: " + stack.max() + " | Min: " + stack.min());
        System.out.println("Pop: " + stack.pop());

        // Wildcards
        List<Integer> ints     = List.of(1, 2, 3);
        List<Double>  doubles  = List.of(1.5, 2.5, 3.5);
        System.out.println("Sum ints:   " + sumList(ints));
        System.out.println("Sum doubles:" + sumList(doubles));

        List<Number> numbers = new ArrayList<>();
        addNumbers(numbers);
        System.out.println("Added numbers: " + numbers);
    }

    // ================================================================
    // 17. EXCEPTION HANDLING
    // ================================================================
    static class InsufficientFundsException extends Exception {
        private final double shortfall;
        InsufficientFundsException(double shortfall) {
            super(String.format("Insufficient funds. Shortfall: %.2f", shortfall));
            this.shortfall = shortfall;
        }
        double getShortfall() { return shortfall; }
    }

    static void exceptionHandlingDemo() {
        System.out.println("\n========== 17. EXCEPTION HANDLING ==========");

        // Multi-catch (Java 7+)
        try {
            String s = null;
            s.length();
        } catch (NullPointerException | IllegalArgumentException e) {
            System.out.println("Caught multi: " + e.getClass().getSimpleName());
        }

        // Custom checked exception
        try {
            double balance  = 100;
            double withdraw = 200;
            if (withdraw > balance)
                throw new InsufficientFundsException(withdraw - balance);
        } catch (InsufficientFundsException e) {
            System.out.println("Custom exception: " + e.getMessage());
            System.out.println("Shortfall: " + e.getShortfall());
        }

        // Try-with-resources (auto-close)
        System.out.println("\n-- Try-with-resources --");
        class Resource implements AutoCloseable {
            String name;
            Resource(String name) { this.name = name; System.out.println("[Resource] Opened: " + name); }
            void use() { System.out.println("[Resource] Using: " + name); }
            @Override public void close() { System.out.println("[Resource] Closed: " + name); }
        }

        try (var r1 = new Resource("DB Connection");
             var r2 = new Resource("File Handle")) {
            r1.use();
            r2.use();
        } // both auto-closed in reverse order

        // Finally always runs
        try {
            int result = 10 / 2;
            System.out.println("\nResult: " + result);
        } catch (ArithmeticException e) {
            System.out.println("Math error");
        } finally {
            System.out.println("Finally always runs.");
        }
    }

    // ================================================================
    // 18. STRING MANIPULATION
    // ================================================================
    static void stringDemo() {
        System.out.println("\n========== 18. STRING MANIPULATION ==========");

        String s = "  Hello, Java World!  ";

        System.out.println("trim()        : '" + s.trim() + "'");
        System.out.println("strip()       : '" + s.strip() + "'");        // Java 11
        System.out.println("isBlank()     : " + "   ".isBlank());          // Java 11
        System.out.println("toUpper()     : " + s.strip().toUpperCase());
        System.out.println("replace()     : " + s.strip().replace("Java", "Python"));
        System.out.println("contains()    : " + s.contains("Java"));
        System.out.println("startsWith()  : " + s.strip().startsWith("Hello"));
        System.out.println("indexOf()     : " + s.indexOf("Java"));
        System.out.println("substring()   : " + s.strip().substring(7, 11));
        System.out.println("split()       : " + Arrays.toString(s.strip().split(", ")));
        System.out.println("repeat()      : " + "ab".repeat(4));            // Java 11

        // String.join
        System.out.println("join()        : " + String.join(" | ", "Java", "Python", "Go"));

        // StringBuilder
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            sb.append(i);
            if (i < 5) sb.append(",");
        }
        System.out.println("StringBuilder : " + sb);
        sb.reverse();
        System.out.println("Reversed      : " + sb);
        sb.insert(2, "XX");
        System.out.println("After insert  : " + sb);
        sb.delete(2, 4);
        System.out.println("After delete  : " + sb);

        // String.format
        System.out.printf("Formatted     : Name=%-10s Age=%3d Salary=$%,.2f%n", "Alice", 30, 95000.5);

        // chars() stream (Java 9+)
        long vowels = "Hello World".chars()
                .filter(c -> "aeiouAEIOU".indexOf(c) >= 0)
                .count();
        System.out.println("Vowel count   : " + vowels);

        // lines() — Java 11
        "Line 1\nLine 2\nLine 3".lines()
                .forEach(line -> System.out.println("  Line: " + line));
    }

    // ================================================================
    // MAIN
    // ================================================================
    public static void main(String[] args) {
        System.out.println("============================================================");
        System.out.println("       CORE JAVA & OOP — COMPLETE DEMO");
        System.out.println("       (Java 8 → Java 21)");
        System.out.println("============================================================");

        encapsulationDemo();
        inheritanceDemo();
        polymorphismDemo();
        abstractionDemo();
        mutabilityDemo();
        lambdaDemo();
        functionalInterfaceDemo();
        streamDemo();
        optionalDemo();
        recordsDemo();
        sealedAndPatternDemo();
        patternMatchingDemo();
        switchExpressionsDemo();
        textBlocksDemo();
        varDemo();
        genericsDemo();
        exceptionHandlingDemo();
        stringDemo();

        System.out.println("\n============================================================");
        System.out.println("   ALL CORE JAVA & OOP DEMOS COMPLETE");
        System.out.println("============================================================");
    }
}
