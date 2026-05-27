import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

/**
 * Java Collection Framework - Complete Implementation Demo
 *
 * Covers:
 *  1.  ArrayList
 *  2.  LinkedList
 *  3.  Stack & ArrayDeque
 *  4.  HashSet, LinkedHashSet, TreeSet
 *  5.  HashMap, LinkedHashMap, TreeMap
 *  6.  PriorityQueue (min-heap & max-heap)
 *  7.  Comparable & Comparator
 *  8.  Iterator & ListIterator
 *  9.  Collections utility methods
 * 10.  Stream API with collections
 * 11.  Thread-safe collections
 */
public class CollectionFrameworkImpl {

    // ================================================================
    // MODEL CLASS — used across demos
    // ================================================================
    static class Student implements Comparable<Student> {
        String name;
        int marks;
        String city;

        Student(String name, int marks, String city) {
            this.name  = name;
            this.marks = marks;
            this.city  = city;
        }

        @Override
        public int compareTo(Student other) {
            return Integer.compare(this.marks, other.marks); // natural: ascending marks
        }

        @Override
        public String toString() {
            return String.format("Student{name='%s', marks=%d, city='%s'}", name, marks, city);
        }
    }

    // ================================================================
    // 1. ARRAYLIST DEMO
    // ================================================================
    static void arrayListDemo() {
        System.out.println("\n========== 1. ARRAYLIST ==========");

        ArrayList<String> fruits = new ArrayList<>();

        // Adding elements
        fruits.add("Apple");
        fruits.add("Banana");
        fruits.add("Cherry");
        fruits.add("Mango");
        fruits.add(1, "Grapes");   // insert at index 1
        System.out.println("After add: " + fruits);

        // Access
        System.out.println("Index 0   : " + fruits.get(0));
        System.out.println("Size      : " + fruits.size());
        System.out.println("Contains  : " + fruits.contains("Mango"));
        System.out.println("Index of  : " + fruits.indexOf("Cherry"));

        // Update
        fruits.set(0, "Avocado");
        System.out.println("After set : " + fruits);

        // Remove
        fruits.remove("Banana");
        fruits.remove(0);          // remove by index
        System.out.println("After remove: " + fruits);

        // SubList
        List<String> sub = fruits.subList(0, 2);
        System.out.println("SubList(0,2): " + sub);

        // Sort
        Collections.sort(fruits);
        System.out.println("Sorted    : " + fruits);

        // Iterate with for-each
        System.out.print("For-each  : ");
        for (String f : fruits) System.out.print(f + " ");
        System.out.println();

        // Convert to Array
        String[] arr = fruits.toArray(new String[0]);
        System.out.println("Array[0]  : " + arr[0]);

        // Clear
        fruits.clear();
        System.out.println("After clear isEmpty: " + fruits.isEmpty());
    }

    // ================================================================
    // 2. LINKEDLIST DEMO
    // ================================================================
    static void linkedListDemo() {
        System.out.println("\n========== 2. LINKEDLIST ==========");

        LinkedList<String> ll = new LinkedList<>();

        // As a List
        ll.add("A");
        ll.add("B");
        ll.add("C");
        System.out.println("List       : " + ll);

        // As a Queue (FIFO)
        ll.offer("D");             // enqueue
        System.out.println("After offer: " + ll);
        System.out.println("peek()     : " + ll.peek());   // view head
        System.out.println("poll()     : " + ll.poll());   // dequeue
        System.out.println("After poll : " + ll);

        // As a Stack (LIFO)
        ll.push("X");              // push to front
        ll.push("Y");
        System.out.println("After push : " + ll);
        System.out.println("pop()      : " + ll.pop());    // pop from front

        // Deque operations
        ll.addFirst("First");
        ll.addLast("Last");
        System.out.println("After addFirst/Last: " + ll);
        System.out.println("peekFirst  : " + ll.peekFirst());
        System.out.println("peekLast   : " + ll.peekLast());
        ll.removeFirst();
        ll.removeLast();
        System.out.println("After remove first/last: " + ll);
    }

    // ================================================================
    // 3. STACK & ARRAYDEQUE DEMO
    // ================================================================
    static void stackAndDequeDemo() {
        System.out.println("\n========== 3. STACK & ARRAYDEQUE ==========");

        // Legacy Stack
        Stack<Integer> stack = new Stack<>();
        stack.push(10);
        stack.push(20);
        stack.push(30);
        System.out.println("Stack      : " + stack);
        System.out.println("peek()     : " + stack.peek());
        System.out.println("pop()      : " + stack.pop());
        System.out.println("After pop  : " + stack);

        // ArrayDeque as Stack (preferred over Stack class)
        ArrayDeque<Integer> dequeStack = new ArrayDeque<>();
        dequeStack.push(100);
        dequeStack.push(200);
        dequeStack.push(300);
        System.out.println("\nArrayDeque stack: " + dequeStack);
        System.out.println("peek()     : " + dequeStack.peek());
        System.out.println("pop()      : " + dequeStack.pop());

        // ArrayDeque as Queue (preferred over LinkedList)
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.offer("Task1");
        queue.offer("Task2");
        queue.offer("Task3");
        System.out.println("\nArrayDeque queue: " + queue);
        System.out.println("poll()     : " + queue.poll());
        System.out.println("After poll : " + queue);

        // Bracket Matching using Stack (classic use case)
        System.out.println("\nBracket check '({[]})': " + isBalanced("({[]})"));
        System.out.println("Bracket check '({[})' : " + isBalanced("({[})"));
    }

    static boolean isBalanced(String expr) {
        ArrayDeque<Character> stack = new ArrayDeque<>();
        for (char c : expr.toCharArray()) {
            if (c == '(' || c == '{' || c == '[') {
                stack.push(c);
            } else {
                if (stack.isEmpty()) return false;
                char top = stack.pop();
                if ((c == ')' && top != '(') ||
                    (c == '}' && top != '{') ||
                    (c == ']' && top != '[')) return false;
            }
        }
        return stack.isEmpty();
    }

    // ================================================================
    // 4. SET DEMOS — HashSet, LinkedHashSet, TreeSet
    // ================================================================
    static void setDemo() {
        System.out.println("\n========== 4. SET ==========");

        // HashSet — no order, no duplicates
        HashSet<String> hashSet = new HashSet<>();
        hashSet.add("Banana");
        hashSet.add("Apple");
        hashSet.add("Cherry");
        hashSet.add("Apple");      // duplicate — ignored
        System.out.println("HashSet (no order): " + hashSet);
        System.out.println("Contains Apple    : " + hashSet.contains("Apple"));
        hashSet.remove("Banana");
        System.out.println("After remove      : " + hashSet);

        // LinkedHashSet — insertion order
        LinkedHashSet<String> linkedSet = new LinkedHashSet<>();
        linkedSet.add("Banana");
        linkedSet.add("Apple");
        linkedSet.add("Cherry");
        linkedSet.add("Apple");    // duplicate — ignored
        System.out.println("\nLinkedHashSet (insertion order): " + linkedSet);

        // TreeSet — sorted order
        TreeSet<Integer> treeSet = new TreeSet<>();
        treeSet.add(50); treeSet.add(10); treeSet.add(30);
        treeSet.add(20); treeSet.add(40);
        System.out.println("\nTreeSet (sorted)  : " + treeSet);
        System.out.println("first()           : " + treeSet.first());
        System.out.println("last()            : " + treeSet.last());
        System.out.println("headSet(30)       : " + treeSet.headSet(30));    // < 30
        System.out.println("tailSet(30)       : " + treeSet.tailSet(30));    // >= 30
        System.out.println("subSet(20,40)     : " + treeSet.subSet(20, 40));
        System.out.println("floor(35)         : " + treeSet.floor(35));      // <= 35
        System.out.println("ceiling(35)       : " + treeSet.ceiling(35));    // >= 35
        System.out.println("higher(30)        : " + treeSet.higher(30));     // > 30
        System.out.println("lower(30)         : " + treeSet.lower(30));      // < 30

        // Set operations
        Set<Integer> set1 = new HashSet<>(Arrays.asList(1, 2, 3, 4));
        Set<Integer> set2 = new HashSet<>(Arrays.asList(3, 4, 5, 6));

        Set<Integer> union = new HashSet<>(set1);
        union.addAll(set2);
        System.out.println("\nUnion        : " + union);

        Set<Integer> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        System.out.println("Intersection : " + intersection);

        Set<Integer> difference = new HashSet<>(set1);
        difference.removeAll(set2);
        System.out.println("Difference   : " + difference);
    }

    // ================================================================
    // 5. MAP DEMOS — HashMap, LinkedHashMap, TreeMap
    // ================================================================
    static void mapDemo() {
        System.out.println("\n========== 5. MAP ==========");

        // HashMap
        HashMap<String, Integer> scores = new HashMap<>();
        scores.put("Alice", 90);
        scores.put("Bob", 85);
        scores.put("Carol", 92);
        scores.put("Dave", 78);
        scores.put("Alice", 95);   // update existing key
        System.out.println("HashMap           : " + scores);
        System.out.println("get(Alice)        : " + scores.get("Alice"));
        System.out.println("getOrDefault(X,0) : " + scores.getOrDefault("X", 0));
        System.out.println("containsKey(Bob)  : " + scores.containsKey("Bob"));
        System.out.println("containsValue(92) : " + scores.containsValue(92));

        // putIfAbsent
        scores.putIfAbsent("Eve", 88);
        scores.putIfAbsent("Alice", 60); // not updated — Alice exists
        System.out.println("After putIfAbsent : " + scores);

        // Iterate entrySet
        System.out.println("Entries:");
        for (Map.Entry<String, Integer> e : scores.entrySet()) {
            System.out.println("  " + e.getKey() + " -> " + e.getValue());
        }

        // Java 8 forEach
        System.out.println("forEach:");
        scores.forEach((k, v) -> System.out.println("  " + k + ": " + v));

        // compute — increment value
        scores.compute("Bob", (k, v) -> v == null ? 1 : v + 5);
        System.out.println("After compute Bob+5: " + scores.get("Bob"));

        // merge
        scores.merge("Carol", 3, Integer::sum);
        System.out.println("After merge Carol+3: " + scores.get("Carol"));

        // LinkedHashMap (insertion order)
        LinkedHashMap<String, Integer> linked = new LinkedHashMap<>();
        linked.put("C", 3); linked.put("A", 1); linked.put("B", 2);
        System.out.println("\nLinkedHashMap     : " + linked); // C, A, B order

        // TreeMap (sorted by key)
        TreeMap<String, Integer> treeMap = new TreeMap<>();
        treeMap.put("Banana", 2);
        treeMap.put("Apple", 5);
        treeMap.put("Mango", 3);
        treeMap.put("Cherry", 1);
        System.out.println("\nTreeMap (sorted)  : " + treeMap);
        System.out.println("firstKey()        : " + treeMap.firstKey());
        System.out.println("lastKey()         : " + treeMap.lastKey());
        System.out.println("headMap(Mango)    : " + treeMap.headMap("Mango"));
        System.out.println("tailMap(Cherry)   : " + treeMap.tailMap("Cherry"));
        System.out.println("floorKey(Grape)   : " + treeMap.floorKey("Grape"));

        // Word frequency counter — classic HashMap use case
        String sentence = "the cat sat on the mat the cat";
        Map<String, Integer> freq = new HashMap<>();
        for (String word : sentence.split(" ")) {
            freq.merge(word, 1, Integer::sum);
        }
        System.out.println("\nWord Frequency    : " + freq);
    }

    // ================================================================
    // 6. PRIORITY QUEUE DEMO
    // ================================================================
    static void priorityQueueDemo() {
        System.out.println("\n========== 6. PRIORITY QUEUE ==========");

        // Min-Heap (default) — smallest element first
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        minHeap.offer(40); minHeap.offer(10); minHeap.offer(30); minHeap.offer(20);
        System.out.print("Min-Heap poll order: ");
        while (!minHeap.isEmpty()) System.out.print(minHeap.poll() + " ");
        System.out.println();

        // Max-Heap — largest element first
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(Collections.reverseOrder());
        maxHeap.offer(40); maxHeap.offer(10); maxHeap.offer(30); maxHeap.offer(20);
        System.out.print("Max-Heap poll order: ");
        while (!maxHeap.isEmpty()) System.out.print(maxHeap.poll() + " ");
        System.out.println();

        // PriorityQueue with custom object (Student by marks ascending)
        PriorityQueue<Student> studentPQ = new PriorityQueue<>();
        studentPQ.offer(new Student("Alice", 85, "NY"));
        studentPQ.offer(new Student("Bob",   92, "LA"));
        studentPQ.offer(new Student("Carol", 78, "SF"));
        System.out.println("Student PQ (ascending marks):");
        while (!studentPQ.isEmpty()) System.out.println("  " + studentPQ.poll());

        // Top K elements — find top 3 largest numbers
        int[] nums = {3, 1, 4, 1, 5, 9, 2, 6, 5, 3};
        PriorityQueue<Integer> topK = new PriorityQueue<>(); // min-heap of size K
        int k = 3;
        for (int n : nums) {
            topK.offer(n);
            if (topK.size() > k) topK.poll(); // remove smallest
        }
        System.out.println("Top " + k + " elements: " + topK);
    }

    // ================================================================
    // 7. COMPARABLE & COMPARATOR DEMO
    // ================================================================
    static void comparableAndComparatorDemo() {
        System.out.println("\n========== 7. COMPARABLE & COMPARATOR ==========");

        List<Student> students = new ArrayList<>(Arrays.asList(
                new Student("Alice", 85, "NY"),
                new Student("Bob",   92, "LA"),
                new Student("Carol", 78, "SF"),
                new Student("Dave",  92, "NY"),
                new Student("Eve",   85, "LA")
        ));

        // Comparable — natural order (marks ascending)
        Collections.sort(students);
        System.out.println("Natural order (marks asc):");
        students.forEach(s -> System.out.println("  " + s));

        // Comparator — by name
        students.sort(Comparator.comparing(s -> s.name));
        System.out.println("\nBy name:");
        students.forEach(s -> System.out.println("  " + s));

        // Comparator — marks descending, then name ascending
        students.sort(Comparator.comparingInt((Student s) -> s.marks)
                .reversed()
                .thenComparing(s -> s.name));
        System.out.println("\nMarks desc, name asc:");
        students.forEach(s -> System.out.println("  " + s));

        // Comparator — by city then by marks
        students.sort(Comparator.comparing((Student s) -> s.city)
                .thenComparingInt(s -> s.marks));
        System.out.println("\nBy city then marks:");
        students.forEach(s -> System.out.println("  " + s));

        // Min and Max using Comparator
        Student topStudent  = Collections.max(students, Comparator.comparingInt(s -> s.marks));
        Student lowStudent  = Collections.min(students, Comparator.comparingInt(s -> s.marks));
        System.out.println("\nHighest marks: " + topStudent);
        System.out.println("Lowest  marks: " + lowStudent);
    }

    // ================================================================
    // 8. ITERATOR & LISTITERATOR DEMO
    // ================================================================
    static void iteratorDemo() {
        System.out.println("\n========== 8. ITERATOR & LISTITERATOR ==========");

        List<String> items = new ArrayList<>(
                Arrays.asList("Alpha", "Beta", "Gamma", "Delta", "Epsilon"));

        // Iterator — safe removal during iteration
        Iterator<String> it = items.iterator();
        while (it.hasNext()) {
            String val = it.next();
            if (val.startsWith("G")) {
                it.remove(); // safe — no ConcurrentModificationException
            }
        }
        System.out.println("After iterator remove (G*): " + items);

        // ListIterator — forward and backward
        ListIterator<String> lit = items.listIterator();
        System.out.print("Forward  : ");
        while (lit.hasNext()) System.out.print(lit.next() + " ");
        System.out.println();

        System.out.print("Backward : ");
        while (lit.hasPrevious()) System.out.print(lit.previous() + " ");
        System.out.println();

        // ListIterator — add and set during iteration
        ListIterator<String> lit2 = items.listIterator();
        while (lit2.hasNext()) {
            String val = lit2.next();
            lit2.set(val.toUpperCase()); // modify current element
        }
        System.out.println("After set to upper: " + items);
    }

    // ================================================================
    // 9. COLLECTIONS UTILITY METHODS
    // ================================================================
    static void collectionsUtilDemo() {
        System.out.println("\n========== 9. COLLECTIONS UTILITY ==========");

        List<Integer> nums = new ArrayList<>(Arrays.asList(3, 1, 4, 1, 5, 9, 2, 6));

        System.out.println("Original          : " + nums);
        System.out.println("min               : " + Collections.min(nums));
        System.out.println("max               : " + Collections.max(nums));
        System.out.println("frequency(1)      : " + Collections.frequency(nums, 1));

        Collections.sort(nums);
        System.out.println("Sorted            : " + nums);
        System.out.println("binarySearch(5)   : " + Collections.binarySearch(nums, 5));

        Collections.reverse(nums);
        System.out.println("Reversed          : " + nums);

        Collections.shuffle(nums, new Random(42));
        System.out.println("Shuffled          : " + nums);

        Collections.swap(nums, 0, nums.size() - 1);
        System.out.println("After swap(0,n-1) : " + nums);

        Collections.fill(nums, 0);
        System.out.println("After fill(0)     : " + nums);

        List<String> nCopies = Collections.nCopies(4, "Hello");
        System.out.println("nCopies(4,Hello)  : " + nCopies);

        // Unmodifiable list
        List<String> fixed = Collections.unmodifiableList(
                new ArrayList<>(Arrays.asList("A", "B", "C")));
        System.out.println("Unmodifiable      : " + fixed);
        try {
            fixed.add("D");
        } catch (UnsupportedOperationException e) {
            System.out.println("Cannot modify unmodifiable list.");
        }

        // disjoint
        List<Integer> a = Arrays.asList(1, 2, 3);
        List<Integer> b = Arrays.asList(4, 5, 6);
        List<Integer> c = Arrays.asList(3, 4, 5);
        System.out.println("disjoint(a,b)     : " + Collections.disjoint(a, b)); // true
        System.out.println("disjoint(a,c)     : " + Collections.disjoint(a, c)); // false
    }

    // ================================================================
    // 10. STREAM API WITH COLLECTIONS
    // ================================================================
    static void streamDemo() {
        System.out.println("\n========== 10. STREAM API ==========");

        List<Student> students = Arrays.asList(
                new Student("Alice", 85, "NY"),
                new Student("Bob",   92, "LA"),
                new Student("Carol", 78, "SF"),
                new Student("Dave",  92, "NY"),
                new Student("Eve",   55, "LA"),
                new Student("Frank", 67, "SF")
        );

        // Filter — students with marks >= 80
        System.out.println("Marks >= 80:");
        students.stream()
                .filter(s -> s.marks >= 80)
                .forEach(s -> System.out.println("  " + s));

        // Map — extract names
        List<String> names = students.stream()
                .map(s -> s.name)
                .collect(Collectors.toList());
        System.out.println("\nNames: " + names);

        // Sorted by marks descending
        System.out.println("\nSorted by marks desc:");
        students.stream()
                .sorted(Comparator.comparingInt((Student s) -> s.marks).reversed())
                .forEach(s -> System.out.println("  " + s));

        // Count
        long passCount = students.stream().filter(s -> s.marks >= 60).count();
        System.out.println("\nPass count (>=60) : " + passCount);

        // Average marks
        OptionalDouble avg = students.stream()
                .mapToInt(s -> s.marks)
                .average();
        System.out.println("Average marks     : " + avg.orElse(0));

        // Max and Min
        students.stream()
                .max(Comparator.comparingInt(s -> s.marks))
                .ifPresent(s -> System.out.println("Top student       : " + s));

        // Group by city
        Map<String, List<Student>> byCity = students.stream()
                .collect(Collectors.groupingBy(s -> s.city));
        System.out.println("\nGrouped by city:");
        byCity.forEach((city, list) -> {
            System.out.println("  " + city + ": " +
                    list.stream().map(s -> s.name).collect(Collectors.joining(", ")));
        });

        // Average marks per city
        Map<String, Double> avgByCity = students.stream()
                .collect(Collectors.groupingBy(s -> s.city,
                        Collectors.averagingInt(s -> s.marks)));
        System.out.println("Avg marks by city : " + avgByCity);

        // Partition by pass/fail
        Map<Boolean, List<Student>> partitioned = students.stream()
                .collect(Collectors.partitioningBy(s -> s.marks >= 60));
        System.out.println("Pass list: " + partitioned.get(true).stream()
                .map(s -> s.name).collect(Collectors.toList()));
        System.out.println("Fail list: " + partitioned.get(false).stream()
                .map(s -> s.name).collect(Collectors.toList()));

        // Reduce — sum of all marks
        int totalMarks = students.stream()
                .map(s -> s.marks)
                .reduce(0, Integer::sum);
        System.out.println("Total marks       : " + totalMarks);

        // Collect to Map — name -> marks
        Map<String, Integer> nameToMarks = students.stream()
                .collect(Collectors.toMap(s -> s.name, s -> s.marks));
        System.out.println("Name->Marks map   : " + nameToMarks);

        // anyMatch / allMatch / noneMatch
        System.out.println("anyMatch(marks>90): " + students.stream().anyMatch(s -> s.marks > 90));
        System.out.println("allMatch(marks>50): " + students.stream().allMatch(s -> s.marks > 50));
        System.out.println("noneMatch(<0)     : " + students.stream().noneMatch(s -> s.marks < 0));

        // flatMap — flatten list of lists
        List<List<Integer>> nested = Arrays.asList(
                Arrays.asList(1, 2, 3),
                Arrays.asList(4, 5),
                Arrays.asList(6, 7, 8, 9));
        List<Integer> flat = nested.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        System.out.println("FlatMap           : " + flat);

        // Distinct and limit
        List<Integer> nums = Arrays.asList(1, 2, 2, 3, 3, 3, 4, 5);
        List<Integer> distinctLimited = nums.stream()
                .distinct()
                .limit(4)
                .collect(Collectors.toList());
        System.out.println("Distinct+Limit(4) : " + distinctLimited);
    }

    // ================================================================
    // 11. THREAD-SAFE COLLECTIONS
    // ================================================================
    static void threadSafeDemo() throws InterruptedException {
        System.out.println("\n========== 11. THREAD-SAFE COLLECTIONS ==========");

        // ConcurrentHashMap
        ConcurrentHashMap<String, Integer> concMap = new ConcurrentHashMap<>();
        Runnable writer = () -> {
            for (int i = 0; i < 5; i++) {
                concMap.merge(Thread.currentThread().getName() + "-key" + i, 1, Integer::sum);
            }
        };
        Thread t1 = new Thread(writer, "T1");
        Thread t2 = new Thread(writer, "T2");
        t1.start(); t2.start();
        t1.join();  t2.join();
        System.out.println("ConcurrentHashMap size: " + concMap.size());

        // CopyOnWriteArrayList — safe iteration without locking
        CopyOnWriteArrayList<String> cowList = new CopyOnWriteArrayList<>();
        cowList.add("A"); cowList.add("B"); cowList.add("C");
        // Can safely iterate while another thread modifies
        for (String s : cowList) {
            cowList.add("New");   // no ConcurrentModificationException
            break;                // just to demonstrate
        }
        System.out.println("CopyOnWriteArrayList: " + cowList);

        // BlockingQueue — producer-consumer pattern
        LinkedBlockingQueue<String> blockingQueue = new LinkedBlockingQueue<>(5);

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 3; i++) {
                    blockingQueue.put("Task-" + i);
                    System.out.println("[Producer] Put: Task-" + i);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 3; i++) {
                    String task = blockingQueue.take(); // blocks if empty
                    System.out.println("[Consumer] Got: " + task);
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });

        producer.start();
        Thread.sleep(100);
        consumer.start();
        producer.join();
        consumer.join();

        // Collections.synchronizedList
        List<Integer> syncList = Collections.synchronizedList(new ArrayList<>());
        Runnable adder = () -> {
            for (int i = 0; i < 100; i++) syncList.add(i);
        };
        Thread ta = new Thread(adder);
        Thread tb = new Thread(adder);
        ta.start(); tb.start();
        ta.join();  tb.join();
        System.out.println("SynchronizedList size (expected 200): " + syncList.size());
    }

    // ================================================================
    // MAIN
    // ================================================================
    public static void main(String[] args) throws InterruptedException {

        System.out.println("============================================================");
        System.out.println("       JAVA COLLECTION FRAMEWORK - COMPLETE DEMO");
        System.out.println("============================================================");

        arrayListDemo();
        linkedListDemo();
        stackAndDequeDemo();
        setDemo();
        mapDemo();
        priorityQueueDemo();
        comparableAndComparatorDemo();
        iteratorDemo();
        collectionsUtilDemo();
        streamDemo();
        threadSafeDemo();

        System.out.println("\n============================================================");
        System.out.println("   ALL COLLECTION DEMOS COMPLETE");
        System.out.println("============================================================");
    }
}
