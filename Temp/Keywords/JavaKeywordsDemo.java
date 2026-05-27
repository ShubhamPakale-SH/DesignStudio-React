// Demonstrates all Top 30 Java Keywords

// 1. interface - contract for classes to implement
interface Describable {
    void describe(); // abstract by nature in interface
}

// 2. abstract - cannot be instantiated, must be subclassed
// 3. class - blueprint for objects
abstract class Animal {
    // 9. static - belongs to class, not instance
    static String kingdom = "Animalia";

    // 10. final - constant value
    final int legs;

    // 27. public, 28. private, 29. protected - access modifiers
    private String name;
    protected int age;

    // 8. super - used in child to call parent constructor
    // 7. this - refers to current instance
    public Animal(String name, int age, int legs) {
        this.name = name;
        this.age = age;
        this.legs = legs;
    }

    public String getName() {
        return name;
    }

    // abstract method - must be overridden in subclass
    public abstract String sound();
}

// 3. extends - inherits from Animal
// 4. implements - fulfills Describable contract
class Dog extends Animal implements Describable {

    private String breed;

    public Dog(String name, int age, String breed) {
        // 8. super - calls Animal's constructor
        super(name, age, 4);
        this.breed = breed;
    }

    // 12. return - returns a value from method
    // 11. void is NOT used here since we return a String
    @Override
    public String sound() {
        return "Woof!";
    }

    // 11. void - method returns nothing
    @Override
    public void describe() {
        System.out.println("Dog: " + getName() + ", Breed: " + breed + ", Age: " + age);
    }
}

// Custom Exception using throw/throws
class InvalidInputException extends Exception {
    public InvalidInputException(String message) {
        super(message);
    }
}

// 3. class - main demo class
public class JavaKeywordsDemo {

    // 9. static method
    // 26. throws - declares possible exception
    public static void validateAge(int age) throws InvalidInputException {
        // 13. if / 14. else
        if (age < 0) {
            // 25. throw - manually throw exception
            throw new InvalidInputException("Age cannot be negative: " + age);
        } else {
            System.out.println("Valid age: " + age);
        }
    }

    public static void main(String[] args) {

        // 6. new - creates a new object
        Dog dog = new Dog("Bruno", 3, "Labrador");

        // 30. instanceof - checks if object is of a type
        if (dog instanceof Animal) {
            System.out.println(dog.getName() + " is an Animal.");
        }

        dog.describe();
        System.out.println(dog.getName() + " says: " + dog.sound());
        System.out.println("Kingdom: " + Animal.kingdom);

        // 15. switch / 16. case - multi-branch selection
        String day = "MONDAY";
        switch (day) {
            case "MONDAY":
                System.out.println("Start of the week!");
                break; // 20. break - exits switch
            case "FRIDAY":
                System.out.println("End of the week!");
                break;
            default:
                System.out.println("Midweek day.");
        }

        // 17. for - counted loop
        System.out.print("for loop: ");
        for (int i = 1; i <= 5; i++) {
            if (i == 3) continue; // 21. continue - skip iteration
            System.out.print(i + " ");
        }
        System.out.println();

        // 18. while - condition-based loop
        System.out.print("while loop: ");
        int w = 1;
        while (w <= 3) {
            System.out.print(w + " ");
            w++;
        }
        System.out.println();

        // 19. do-while - executes at least once
        System.out.print("do-while loop: ");
        int d = 1;
        do {
            System.out.print(d + " ");
            d++;
        } while (d <= 3);
        System.out.println();

        // 22. try / 23. catch / 24. finally - exception handling
        try {
            validateAge(-5);
        } catch (InvalidInputException e) {
            System.out.println("Caught: " + e.getMessage());
        } finally {
            System.out.println("finally block executed.");
        }
    }
}
