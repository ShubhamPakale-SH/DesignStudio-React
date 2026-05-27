public class ExceptionHandlingDemo {

    // 1. Basic try-catch-finally
    public static void basicTryCatch() {
        try {
            int result = 10 / 0; // throws ArithmeticException
            System.out.println("Result: " + result);
        } catch (ArithmeticException e) {
            System.out.println("Caught ArithmeticException: " + e.getMessage());
        } finally {
            System.out.println("Finally block always executes.");
        }
    }

    // 2. Multiple catch blocks
    public static void multipleCatch() {
        try {
            String str = null;
            System.out.println(str.length()); // throws NullPointerException
        } catch (NullPointerException e) {
            System.out.println("Caught NullPointerException: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Caught general Exception: " + e.getMessage());
        }
    }

    // 3. Custom Exception
    static class InvalidAgeException extends Exception {
        public InvalidAgeException(String message) {
            super(message);
        }
    }

    public static void checkAge(int age) throws InvalidAgeException {
        if (age < 18) {
            throw new InvalidAgeException("Age must be 18 or above. Provided: " + age);
        }
        System.out.println("Valid age: " + age);
    }

    // 4. throws keyword — declaring checked exceptions
    public static void riskyMethod() throws Exception {
        throw new Exception("Something went wrong!");
    }

    public static void main(String[] args) {
        System.out.println("--- Basic Try-Catch-Finally ---");
        basicTryCatch();

        System.out.println("\n--- Multiple Catch Blocks ---");
        multipleCatch();

        System.out.println("\n--- Custom Exception ---");
        try {
            checkAge(15);
        } catch (InvalidAgeException e) {
            System.out.println("Caught custom exception: " + e.getMessage());
        }

        System.out.println("\n--- throws keyword ---");
        try {
            riskyMethod();
        } catch (Exception e) {
            System.out.println("Caught from riskyMethod: " + e.getMessage());
        }
    }
}
