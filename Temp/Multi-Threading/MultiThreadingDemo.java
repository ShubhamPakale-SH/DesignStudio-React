import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// ================================================================
//           JAVA MULTI-THREADING DEMO
// ================================================================

// ---------------------------------------------------------------
// 1. Extending Thread class
// ---------------------------------------------------------------
class MyThread extends Thread {
    public MyThread(String name) {
        super(name);
    }

    @Override
    public void run() {
        for (int i = 1; i <= 3; i++) {
            System.out.println(getName() + " -> count: " + i);
            try {
                Thread.sleep(200); // pause for 200ms
            } catch (InterruptedException e) {
                System.out.println(getName() + " interrupted.");
            }
        }
    }
}

// ---------------------------------------------------------------
// 2. Implementing Runnable interface (preferred approach)
// ---------------------------------------------------------------
class MyRunnable implements Runnable {
    private String taskName;

    public MyRunnable(String taskName) {
        this.taskName = taskName;
    }

    @Override
    public void run() {
        for (int i = 1; i <= 3; i++) {
            System.out.println("Runnable [" + taskName + "] -> step: " + i);
        }
    }
}

// ---------------------------------------------------------------
// 3. Synchronization — shared BankAccount
// ---------------------------------------------------------------
class BankAccount {
    private int balance = 1000;

    // synchronized ensures only one thread accesses this at a time
    public synchronized void deposit(int amount, String threadName) {
        System.out.println(threadName + " depositing: " + amount);
        balance += amount;
        System.out.println(threadName + " new balance: " + balance);
    }

    public int getBalance() {
        return balance;
    }
}

// ---------------------------------------------------------------
// 4. Inter-thread communication — Producer & Consumer
// ---------------------------------------------------------------
class SharedBox {
    private int value;
    private boolean hasValue = false;

    // Producer puts a value
    public synchronized void produce(int val) throws InterruptedException {
        while (hasValue) {
            wait(); // wait until consumer takes the value
        }
        this.value = val;
        hasValue = true;
        System.out.println("Produced: " + val);
        notify(); // notify consumer
    }

    // Consumer takes the value
    public synchronized void consume() throws InterruptedException {
        while (!hasValue) {
            wait(); // wait until producer puts a value
        }
        System.out.println("Consumed: " + value);
        hasValue = false;
        notify(); // notify producer
    }
}

// ---------------------------------------------------------------
// Main class
// ---------------------------------------------------------------
public class MultiThreadingDemo {

    public static void main(String[] args) throws InterruptedException {

        // --- 1. Thread using extends Thread ---
        System.out.println("=== 1. Extending Thread ===");
        MyThread t1 = new MyThread("Thread-A");
        MyThread t2 = new MyThread("Thread-B");
        t1.start();
        t2.start();
        t1.join(); // wait for t1 to finish
        t2.join(); // wait for t2 to finish

        // --- 2. Thread using Runnable ---
        System.out.println("\n=== 2. Runnable Interface ===");
        Thread r1 = new Thread(new MyRunnable("Task-1"));
        Thread r2 = new Thread(new MyRunnable("Task-2"));
        r1.start();
        r2.start();
        r1.join();
        r2.join();

        // --- 3. Lambda Thread (Java 8+) ---
        System.out.println("\n=== 3. Lambda Thread ===");
        Thread lambdaThread = new Thread(() -> {
            System.out.println("Lambda thread running on: " + Thread.currentThread().getName());
        });
        lambdaThread.start();
        lambdaThread.join();

        // --- 4. Synchronization ---
        System.out.println("\n=== 4. Synchronization ===");
        BankAccount account = new BankAccount();
        Thread deposit1 = new Thread(() -> account.deposit(500, "Thread-X"));
        Thread deposit2 = new Thread(() -> account.deposit(300, "Thread-Y"));
        deposit1.start();
        deposit2.start();
        deposit1.join();
        deposit2.join();
        System.out.println("Final Balance: " + account.getBalance());

        // --- 5. Inter-thread Communication (Producer-Consumer) ---
        System.out.println("\n=== 5. Inter-Thread Communication ===");
        SharedBox box = new SharedBox();

        Thread producer = new Thread(() -> {
            try {
                for (int i = 1; i <= 3; i++) box.produce(i);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 3; i++) box.consume();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

        producer.start();
        consumer.start();
        producer.join();
        consumer.join();

        // --- 6. ExecutorService (Thread Pool) ---
        System.out.println("\n=== 6. ExecutorService (Thread Pool) ===");
        ExecutorService executor = Executors.newFixedThreadPool(3);
        for (int i = 1; i <= 5; i++) {
            final int taskId = i;
            executor.submit(() -> {
                System.out.println("Executor Task-" + taskId + " on " + Thread.currentThread().getName());
            });
        }
        executor.shutdown(); // stop accepting new tasks
        while (!executor.isTerminated()) {} // wait for all tasks to finish
        System.out.println("All executor tasks completed.");

        // --- 7. Thread Priority & isAlive ---
        System.out.println("\n=== 7. Thread Priority & isAlive ===");
        Thread lowPriority  = new Thread(() -> System.out.println("Low priority thread"), "Low");
        Thread highPriority = new Thread(() -> System.out.println("High priority thread"), "High");
        lowPriority.setPriority(Thread.MIN_PRIORITY);   // 1
        highPriority.setPriority(Thread.MAX_PRIORITY);  // 10
        lowPriority.start();
        highPriority.start();
        System.out.println("Is Low alive: " + lowPriority.isAlive());
        lowPriority.join();
        highPriority.join();

        System.out.println("\nMulti-Threading Demo Complete.");
    }
}
