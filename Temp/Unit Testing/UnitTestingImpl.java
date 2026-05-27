import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

/**
 * Unit Testing with JUnit 5 + Mockito — Complete Demo
 *
 * DEPENDENCIES (pom.xml):
 *
 * <dependency>
 *   <groupId>org.junit.jupiter</groupId>
 *   <artifactId>junit-jupiter</artifactId>
 *   <version>5.10.0</version>
 *   <scope>test</scope>
 * </dependency>
 * <dependency>
 *   <groupId>org.mockito</groupId>
 *   <artifactId>mockito-junit-jupiter</artifactId>
 *   <version>5.5.0</version>
 *   <scope>test</scope>
 * </dependency>
 *
 * OR Spring Boot (includes both automatically):
 * <dependency>
 *   <groupId>org.springframework.boot</groupId>
 *   <artifactId>spring-boot-starter-test</artifactId>
 *   <scope>test</scope>
 * </dependency>
 *
 * Run: mvn test  OR  gradle test
 *
 * This file demonstrates:
 *  1.  Domain models and production classes (SUT)
 *  2.  Basic JUnit 5 assertions
 *  3.  Lifecycle annotations (@BeforeEach, @AfterEach, etc.)
 *  4.  Parameterized tests
 *  5.  Nested tests
 *  6.  Exception testing
 *  7.  Mockito mocks and stubs
 *  8.  Mockito verify and argument captor
 *  9.  Mockito spy
 * 10.  BDD style (given/when/then)
 * 11.  Service layer tests
 * 12.  Repository layer tests
 * 13.  Edge case and boundary testing
 * 14.  Repeated and tagged tests
 */
public class UnitTestingImpl {

    // ================================================================
    // DOMAIN MODELS
    // ================================================================

    static class User {
        private Long id;
        private String name;
        private String email;
        private int age;
        private boolean active;

        User(Long id, String name, String email, int age) {
            this.id = id; this.name = name;
            this.email = email; this.age = age; this.active = true;
        }

        public Long getId()           { return id; }
        public void setId(Long id)    { this.id = id; }
        public String getName()       { return name; }
        public String getEmail()      { return email; }
        public int getAge()           { return age; }
        public boolean isActive()     { return active; }
        public void setActive(boolean a) { this.active = a; }

        @Override public String toString() {
            return "User{id=" + id + ", name='" + name + "', email='" + email + "', age=" + age + "}";
        }
    }

    static class Order {
        private Long id;
        private Long userId;
        private String product;
        private double amount;
        private String status;

        Order(Long id, Long userId, String product, double amount) {
            this.id = id; this.userId = userId;
            this.product = product; this.amount = amount;
            this.status = "PENDING";
        }

        public Long getId()           { return id; }
        public void setId(Long id)    { this.id = id; }
        public Long getUserId()       { return userId; }
        public String getProduct()    { return product; }
        public double getAmount()     { return amount; }
        public String getStatus()     { return status; }
        public void setStatus(String s) { this.status = s; }

        @Override public String toString() {
            return "Order{id=" + id + ", product='" + product + "', amount=" + amount + ", status='" + status + "'}";
        }
    }

    // ================================================================
    // CUSTOM EXCEPTIONS
    // ================================================================
    static class ResourceNotFoundException extends RuntimeException {
        ResourceNotFoundException(String message) { super(message); }
    }

    static class InvalidRequestException extends RuntimeException {
        InvalidRequestException(String message) { super(message); }
    }

    static class EmailAlreadyExistsException extends RuntimeException {
        EmailAlreadyExistsException(String message) { super(message); }
    }

    // ================================================================
    // REPOSITORY INTERFACES (dependencies to be mocked)
    // ================================================================
    interface UserRepository {
        Optional<User> findById(Long id);
        Optional<User> findByEmail(String email);
        List<User> findAll();
        List<User> findAllActive();
        User save(User user);
        void deleteById(Long id);
        boolean existsById(Long id);
        long count();
    }

    interface OrderRepository {
        Optional<Order> findById(Long id);
        List<Order> findByUserId(Long userId);
        Order save(Order order);
        void deleteById(Long id);
        boolean existsById(Long id);
    }

    interface EmailService {
        void sendWelcomeEmail(String email, String name);
        void sendOrderConfirmation(String email, Order order);
    }

    // ================================================================
    // PRODUCTION CLASSES (System Under Test — SUT)
    // ================================================================

    // --- UserService ---
    static class UserService {
        private final UserRepository userRepository;
        private final EmailService emailService;

        UserService(UserRepository userRepository, EmailService emailService) {
            this.userRepository = userRepository;
            this.emailService   = emailService;
        }

        public User findById(Long id) {
            if (id == null || id <= 0)
                throw new InvalidRequestException("ID must be positive");
            return userRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        }

        public List<User> findAll() {
            return userRepository.findAll();
        }

        public List<User> findAllActive() {
            return userRepository.findAllActive();
        }

        public User createUser(String name, String email, int age) {
            if (name == null || name.isBlank())
                throw new InvalidRequestException("Name cannot be blank");
            if (email == null || !email.contains("@"))
                throw new InvalidRequestException("Invalid email format");
            if (age < 0 || age > 150)
                throw new InvalidRequestException("Age must be between 0 and 150");

            userRepository.findByEmail(email).ifPresent(u -> {
                throw new EmailAlreadyExistsException("Email already registered: " + email);
            });

            User user = new User(null, name.trim(), email.toLowerCase(), age);
            User saved = userRepository.save(user);
            emailService.sendWelcomeEmail(saved.getEmail(), saved.getName());
            return saved;
        }

        public User updateUser(Long id, String name, int age) {
            User user = findById(id);
            if (name != null && !name.isBlank()) {
                // reflection simulation — create updated user
            }
            return userRepository.save(user);
        }

        public void deleteUser(Long id) {
            if (!userRepository.existsById(id))
                throw new ResourceNotFoundException("User not found with id: " + id);
            userRepository.deleteById(id);
        }

        public long getUserCount() {
            return userRepository.count();
        }
    }

    // --- OrderService ---
    static class OrderService {
        private final OrderRepository orderRepository;
        private final UserRepository  userRepository;
        private final EmailService    emailService;

        OrderService(OrderRepository orderRepository,
                     UserRepository userRepository,
                     EmailService emailService) {
            this.orderRepository = orderRepository;
            this.userRepository  = userRepository;
            this.emailService    = emailService;
        }

        public Order createOrder(Long userId, String product, double amount) {
            if (amount <= 0)
                throw new InvalidRequestException("Amount must be positive");
            if (product == null || product.isBlank())
                throw new InvalidRequestException("Product cannot be blank");

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

            Order order = new Order(null, userId, product.trim(), amount);
            Order saved = orderRepository.save(order);
            emailService.sendOrderConfirmation(user.getEmail(), saved);
            return saved;
        }

        public List<Order> getOrdersByUser(Long userId) {
            return orderRepository.findByUserId(userId);
        }

        public Order cancelOrder(Long orderId) {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
            if ("CANCELLED".equals(order.getStatus()))
                throw new InvalidRequestException("Order is already cancelled");
            order.setStatus("CANCELLED");
            return orderRepository.save(order);
        }
    }

    // --- Calculator (pure logic — no dependencies) ---
    static class Calculator {
        public int add(int a, int b)        { return a + b; }
        public int subtract(int a, int b)   { return a - b; }
        public int multiply(int a, int b)   { return a * b; }
        public double divide(double a, double b) {
            if (b == 0) throw new ArithmeticException("Cannot divide by zero");
            return a / b;
        }
        public double power(double base, int exp) {
            return Math.pow(base, exp);
        }
        public boolean isPrime(int n) {
            if (n < 2) return false;
            for (int i = 2; i <= Math.sqrt(n); i++)
                if (n % i == 0) return false;
            return true;
        }
        public int factorial(int n) {
            if (n < 0) throw new IllegalArgumentException("Negative input");
            if (n == 0 || n == 1) return 1;
            return n * factorial(n - 1);
        }
    }

    // --- StringUtils ---
    static class StringUtils {
        public String reverse(String s) {
            if (s == null) throw new IllegalArgumentException("Input cannot be null");
            return new StringBuilder(s).reverse().toString();
        }
        public boolean isPalindrome(String s) {
            if (s == null || s.isEmpty()) return false;
            String clean = s.toLowerCase().replaceAll("[^a-z0-9]", "");
            return clean.equals(new StringBuilder(clean).reverse().toString());
        }
        public int countWords(String s) {
            if (s == null || s.isBlank()) return 0;
            return s.trim().split("\\s+").length;
        }
        public String capitalize(String s) {
            if (s == null || s.isEmpty()) return s;
            return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
        }
    }

    // --- TaxCalculator ---
    static class TaxCalculator {
        public double calculateTax(double income) {
            if (income < 0) throw new IllegalArgumentException("Income cannot be negative");
            if (income <= 10000)  return 0;
            if (income <= 50000)  return income * 0.10;
            if (income <= 100000) return income * 0.20;
            return income * 0.30;
        }
        public double netIncome(double grossIncome) {
            return grossIncome - calculateTax(grossIncome);
        }
    }

    // ================================================================
    // ================================================================
    // TEST CLASSES
    // ================================================================
    // ================================================================

    // ================================================================
    // 1. BASIC JUNIT 5 TESTS — Calculator
    // ================================================================
    @DisplayName("Calculator Tests")
    static class CalculatorTest {

        private Calculator calculator;

        @BeforeEach
        void setUp() {
            calculator = new Calculator();
            System.out.println("[Setup] Calculator initialized");
        }

        @AfterEach
        void tearDown() {
            System.out.println("[TearDown] Test completed");
        }

        @BeforeAll
        static void beforeAll() {
            System.out.println("[BeforeAll] Starting Calculator test suite");
        }

        @AfterAll
        static void afterAll() {
            System.out.println("[AfterAll] Calculator test suite complete");
        }

        @Test
        @DisplayName("Should add two positive numbers correctly")
        void shouldAddTwoPositiveNumbers() {
            // Arrange
            int a = 5, b = 3;
            // Act
            int result = calculator.add(a, b);
            // Assert
            assertEquals(8, result, "5 + 3 should equal 8");
        }

        @Test
        @DisplayName("Should subtract correctly")
        void shouldSubtract() {
            assertEquals(7, calculator.subtract(10, 3));
            assertEquals(-5, calculator.subtract(0, 5));
            assertEquals(0, calculator.subtract(5, 5));
        }

        @Test
        @DisplayName("Should multiply correctly")
        void shouldMultiply() {
            assertAll(
                () -> assertEquals(12, calculator.multiply(3, 4)),
                () -> assertEquals(0,  calculator.multiply(5, 0)),
                () -> assertEquals(-6, calculator.multiply(-2, 3)),
                () -> assertEquals(6,  calculator.multiply(-2, -3))
            );
        }

        @Test
        @DisplayName("Should divide correctly")
        void shouldDivide() {
            assertEquals(2.5, calculator.divide(5, 2), 0.001);
            assertEquals(0.0, calculator.divide(0, 5), 0.001);
        }

        @Test
        @DisplayName("Should throw ArithmeticException when dividing by zero")
        void shouldThrowWhenDividingByZero() {
            ArithmeticException ex = assertThrows(
                ArithmeticException.class,
                () -> calculator.divide(10, 0)
            );
            assertEquals("Cannot divide by zero", ex.getMessage());
        }

        @Test
        @DisplayName("Should calculate factorial correctly")
        void shouldCalculateFactorial() {
            assertEquals(1,   calculator.factorial(0));
            assertEquals(1,   calculator.factorial(1));
            assertEquals(6,   calculator.factorial(3));
            assertEquals(120, calculator.factorial(5));
        }

        @Test
        @DisplayName("Should throw for negative factorial input")
        void shouldThrowForNegativeFactorial() {
            assertThrows(IllegalArgumentException.class,
                () -> calculator.factorial(-1));
        }
    }

    // ================================================================
    // 2. PARAMETERIZED TESTS
    // ================================================================
    @DisplayName("Parameterized Tests")
    static class ParameterizedTests {

        private final Calculator calculator = new Calculator();
        private final StringUtils utils     = new StringUtils();

        @ParameterizedTest
        @DisplayName("Should identify prime numbers")
        @ValueSource(ints = {2, 3, 5, 7, 11, 13, 17, 19, 23})
        void shouldIdentifyPrimes(int number) {
            assertTrue(calculator.isPrime(number), number + " should be prime");
        }

        @ParameterizedTest
        @DisplayName("Should identify non-prime numbers")
        @ValueSource(ints = {0, 1, 4, 6, 8, 9, 10, 15, 25})
        void shouldIdentifyNonPrimes(int number) {
            assertFalse(calculator.isPrime(number), number + " should not be prime");
        }

        @ParameterizedTest
        @DisplayName("Should detect palindromes")
        @CsvSource({
            "racecar, true",
            "madam,   true",
            "hello,   false",
            "A man a plan a canal Panama, true",
            "Was it a car or a cat I saw, true",
            "world,   false"
        })
        void shouldDetectPalindromes(String input, boolean expected) {
            assertEquals(expected, utils.isPalindrome(input));
        }

        @ParameterizedTest
        @DisplayName("Should reverse strings correctly")
        @MethodSource("reverseStringProvider")
        void shouldReverseStrings(String input, String expected) {
            assertEquals(expected, utils.reverse(input));
        }

        static Stream<Arguments> reverseStringProvider() {
            return Stream.of(
                Arguments.of("Hello",   "olleH"),
                Arguments.of("Java",    "avaJ"),
                Arguments.of("12345",   "54321"),
                Arguments.of("a",       "a"),
                Arguments.of("",        "")
            );
        }

        @ParameterizedTest
        @DisplayName("Tax calculator bracket tests")
        @CsvSource({
            "0,      0.0",
            "5000,   0.0",
            "10000,  0.0",
            "30000,  3000.0",
            "75000,  15000.0",
            "200000, 60000.0"
        })
        void shouldCalculateTaxByBracket(double income, double expectedTax) {
            TaxCalculator tc = new TaxCalculator();
            assertEquals(expectedTax, tc.calculateTax(income), 0.01);
        }
    }

    // ================================================================
    // 3. NESTED TESTS — UserService scenarios
    // ================================================================
    @DisplayName("UserService Nested Tests")
    @ExtendWith(MockitoExtension.class)
    static class UserServiceNestedTest {

        @Mock UserRepository userRepository;
        @Mock EmailService emailService;
        @InjectMocks UserService userService; // not used here directly but shown

        private UserService service;

        @BeforeEach
        void setUp() {
            service = new UserService(userRepository, emailService);
        }

        @Nested
        @DisplayName("findById()")
        class FindById {

            @Test
            @DisplayName("Should return user when ID exists")
            void shouldReturnUser_WhenIdExists() {
                User user = new User(1L, "Alice", "alice@example.com", 30);
                when(userRepository.findById(1L)).thenReturn(Optional.of(user));

                User result = service.findById(1L);

                assertNotNull(result);
                assertEquals("Alice", result.getName());
                verify(userRepository).findById(1L);
            }

            @Test
            @DisplayName("Should throw ResourceNotFoundException when ID not found")
            void shouldThrow_WhenIdNotFound() {
                when(userRepository.findById(99L)).thenReturn(Optional.empty());

                ResourceNotFoundException ex = assertThrows(
                    ResourceNotFoundException.class,
                    () -> service.findById(99L)
                );
                assertEquals("User not found with id: 99", ex.getMessage());
            }

            @Test
            @DisplayName("Should throw InvalidRequestException for null ID")
            void shouldThrow_WhenIdIsNull() {
                assertThrows(InvalidRequestException.class,
                    () -> service.findById(null));
                verifyNoInteractions(userRepository);
            }

            @Test
            @DisplayName("Should throw InvalidRequestException for negative ID")
            void shouldThrow_WhenIdIsNegative() {
                assertThrows(InvalidRequestException.class,
                    () -> service.findById(-1L));
                verifyNoInteractions(userRepository);
            }
        }

        @Nested
        @DisplayName("createUser()")
        class CreateUser {

            @Test
            @DisplayName("Should create user and send welcome email")
            void shouldCreateUserAndSendEmail() {
                User savedUser = new User(1L, "Alice", "alice@example.com", 30);
                when(userRepository.findByEmail("alice@example.com"))
                    .thenReturn(Optional.empty());
                when(userRepository.save(any(User.class))).thenReturn(savedUser);

                User result = service.createUser("Alice", "alice@example.com", 30);

                assertNotNull(result);
                assertEquals(1L, result.getId());
                assertEquals("Alice", result.getName());
                verify(userRepository).save(any(User.class));
                verify(emailService).sendWelcomeEmail("alice@example.com", "Alice");
            }

            @Test
            @DisplayName("Should throw when email already exists")
            void shouldThrow_WhenEmailAlreadyExists() {
                when(userRepository.findByEmail("existing@example.com"))
                    .thenReturn(Optional.of(new User(1L, "Bob", "existing@example.com", 25)));

                assertThrows(EmailAlreadyExistsException.class,
                    () -> service.createUser("Alice", "existing@example.com", 30));

                verify(userRepository, never()).save(any());
                verifyNoInteractions(emailService);
            }

            @Test
            @DisplayName("Should throw for blank name")
            void shouldThrow_WhenNameIsBlank() {
                assertThrows(InvalidRequestException.class,
                    () -> service.createUser("  ", "a@b.com", 25));
                verifyNoInteractions(userRepository);
            }

            @Test
            @DisplayName("Should throw for invalid email")
            void shouldThrow_WhenEmailIsInvalid() {
                assertThrows(InvalidRequestException.class,
                    () -> service.createUser("Alice", "not-an-email", 25));
            }

            @Test
            @DisplayName("Should throw for invalid age")
            void shouldThrow_WhenAgeIsInvalid() {
                assertThrows(InvalidRequestException.class,
                    () -> service.createUser("Alice", "a@b.com", -1));
                assertThrows(InvalidRequestException.class,
                    () -> service.createUser("Alice", "a@b.com", 200));
            }
        }

        @Nested
        @DisplayName("deleteUser()")
        class DeleteUser {

            @Test
            @DisplayName("Should delete user when ID exists")
            void shouldDeleteUser_WhenIdExists() {
                when(userRepository.existsById(1L)).thenReturn(true);
                doNothing().when(userRepository).deleteById(1L);

                assertDoesNotThrow(() -> service.deleteUser(1L));

                verify(userRepository).existsById(1L);
                verify(userRepository).deleteById(1L);
            }

            @Test
            @DisplayName("Should throw when user not found for delete")
            void shouldThrow_WhenUserNotFoundForDelete() {
                when(userRepository.existsById(99L)).thenReturn(false);

                assertThrows(ResourceNotFoundException.class,
                    () -> service.deleteUser(99L));

                verify(userRepository, never()).deleteById(any());
            }
        }
    }

    // ================================================================
    // 4. MOCKITO — VERIFY + ARGUMENT CAPTOR
    // ================================================================
    @DisplayName("Mockito Verification Tests")
    @ExtendWith(MockitoExtension.class)
    static class MockitoVerificationTest {

        @Mock UserRepository userRepository;
        @Mock EmailService   emailService;
        @Captor ArgumentCaptor<User> userCaptor;

        @Test
        @DisplayName("Should capture saved user and verify fields")
        void shouldCaptureUserOnSave() {
            User saved = new User(1L, "Bob", "bob@example.com", 25);
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
            when(userRepository.save(any())).thenReturn(saved);

            UserService service = new UserService(userRepository, emailService);
            service.createUser("Bob", "bob@example.com", 25);

            verify(userRepository).save(userCaptor.capture());
            User captured = userCaptor.getValue();
            assertEquals("Bob",               captured.getName());
            assertEquals("bob@example.com",   captured.getEmail());
            assertEquals(25,                  captured.getAge());
        }

        @Test
        @DisplayName("Should verify email sent once with correct args")
        void shouldVerifyEmailSentWithCorrectArgs() {
            @Captor ArgumentCaptor<String> emailCaptor;
            emailCaptor = ArgumentCaptor.forClass(String.class);

            User saved = new User(1L, "Carol", "carol@example.com", 28);
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
            when(userRepository.save(any())).thenReturn(saved);

            UserService service = new UserService(userRepository, emailService);
            service.createUser("Carol", "carol@example.com", 28);

            verify(emailService).sendWelcomeEmail(emailCaptor.capture(), anyString());
            assertEquals("carol@example.com", emailCaptor.getValue());
        }

        @Test
        @DisplayName("Should verify exact number of interactions")
        void shouldVerifyInteractionCount() {
            when(userRepository.findAll()).thenReturn(List.of(
                new User(1L, "A", "a@b.com", 20),
                new User(2L, "B", "b@b.com", 22)
            ));

            UserService service = new UserService(userRepository, emailService);
            service.findAll();
            service.findAll();
            service.findAll();

            verify(userRepository, times(3)).findAll();
            verify(userRepository, never()).deleteById(anyLong());
            verifyNoInteractions(emailService);
        }
    }

    // ================================================================
    // 5. MOCKITO SPY TEST
    // ================================================================
    @DisplayName("Mockito Spy Tests")
    @ExtendWith(MockitoExtension.class)
    static class MockitoSpyTest {

        @Test
        @DisplayName("Spy — real add works, but size overridden")
        void spyShouldUseRealMethodsUnlessOverridden() {
            List<String> realList = new ArrayList<>();
            List<String> spyList  = spy(realList);

            // real method
            spyList.add("Apple");
            spyList.add("Banana");
            assertEquals(2, spyList.size()); // real size
            assertEquals("Apple", spyList.get(0)); // real get

            // override size
            doReturn(99).when(spyList).size();
            assertEquals(99, spyList.size()); // stubbed
            assertEquals("Apple", spyList.get(0)); // still real

            verify(spyList).add("Apple");
            verify(spyList).add("Banana");
        }

        @Test
        @DisplayName("Spy — partial mock on real service")
        void spyPartialMock() {
            Calculator realCalc  = new Calculator();
            Calculator spyCalc   = spy(realCalc);

            // real method
            assertEquals(8, spyCalc.add(3, 5));

            // override specific method
            doReturn(999).when(spyCalc).add(anyInt(), anyInt());
            assertEquals(999, spyCalc.add(3, 5)); // stubbed

            // other methods still real
            assertEquals(15, spyCalc.multiply(3, 5));
        }
    }

    // ================================================================
    // 6. BDD STYLE — given / when / then
    // ================================================================
    @DisplayName("BDD Style Tests")
    @ExtendWith(MockitoExtension.class)
    static class BDDStyleTest {

        @Mock  OrderRepository orderRepository;
        @Mock  UserRepository  userRepository;
        @Mock  EmailService    emailService;

        @Test
        @DisplayName("Should create order — BDD style")
        void shouldCreateOrder_BDDStyle() {
            // given
            User user = new User(1L, "Alice", "alice@example.com", 30);
            Order savedOrder = new Order(10L, 1L, "Laptop", 999.99);

            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            given(orderRepository.save(any(Order.class))).willReturn(savedOrder);

            OrderService orderService = new OrderService(orderRepository, userRepository, emailService);

            // when
            Order result = orderService.createOrder(1L, "Laptop", 999.99);

            // then
            assertNotNull(result);
            assertEquals(10L,      result.getId());
            assertEquals("Laptop", result.getProduct());
            assertEquals(999.99,   result.getAmount(), 0.001);

            then(orderRepository).should().save(any(Order.class));
            then(emailService).should().sendOrderConfirmation(eq("alice@example.com"), any(Order.class));
            then(userRepository).should().findById(1L);
        }

        @Test
        @DisplayName("Should cancel order — BDD style")
        void shouldCancelOrder_BDDStyle() {
            // given
            Order order = new Order(5L, 1L, "Phone", 699.99);
            given(orderRepository.findById(5L)).willReturn(Optional.of(order));
            given(orderRepository.save(any())).willAnswer(i -> i.getArgument(0));

            OrderService orderService = new OrderService(orderRepository, userRepository, emailService);

            // when
            Order cancelled = orderService.cancelOrder(5L);

            // then
            assertEquals("CANCELLED", cancelled.getStatus());
            then(orderRepository).should().save(any());
        }

        @Test
        @DisplayName("Should throw when cancelling already cancelled order")
        void shouldThrowWhenCancellingAlreadyCancelledOrder() {
            // given
            Order order = new Order(5L, 1L, "Phone", 699.99);
            order.setStatus("CANCELLED");
            given(orderRepository.findById(5L)).willReturn(Optional.of(order));

            OrderService orderService = new OrderService(orderRepository, userRepository, emailService);

            // when + then
            assertThrows(InvalidRequestException.class,
                () -> orderService.cancelOrder(5L));
            then(orderRepository).should(never()).save(any());
        }
    }

    // ================================================================
    // 7. STUBBING — Multiple return values and custom answers
    // ================================================================
    @DisplayName("Advanced Stubbing Tests")
    @ExtendWith(MockitoExtension.class)
    static class AdvancedStubbingTest {

        @Mock UserRepository userRepository;
        @Mock EmailService emailService;

        @Test
        @DisplayName("Should return different values on consecutive calls")
        void shouldReturnDifferentValuesOnConsecutiveCalls() {
            when(userRepository.count())
                .thenReturn(1L)
                .thenReturn(2L)
                .thenReturn(3L);

            UserService service = new UserService(userRepository, emailService);
            assertEquals(1L, service.getUserCount());
            assertEquals(2L, service.getUserCount());
            assertEquals(3L, service.getUserCount());
            assertEquals(3L, service.getUserCount()); // stays at last value
        }

        @Test
        @DisplayName("Should use custom answer to auto-assign ID on save")
        void shouldUseCustomAnswerToAssignId() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User u = invocation.getArgument(0);
                u.setId(42L); // simulate DB auto-increment
                return u;
            });

            UserService service = new UserService(userRepository, emailService);
            User result = service.createUser("Dave", "dave@example.com", 35);

            assertEquals(42L, result.getId());
            assertEquals("Dave", result.getName());
        }

        @Test
        @DisplayName("Should throw on first call, succeed on second (retry simulation)")
        void shouldThrowThenSucceed() {
            User user = new User(1L, "Eve", "eve@example.com", 28);
            when(userRepository.findById(1L))
                .thenThrow(new RuntimeException("Transient DB error"))
                .thenReturn(Optional.of(user));

            UserService service = new UserService(userRepository, emailService);

            // first call throws
            assertThrows(RuntimeException.class, () -> service.findById(1L));

            // second call succeeds
            User result = service.findById(1L);
            assertEquals("Eve", result.getName());
        }
    }

    // ================================================================
    // 8. EDGE CASES AND BOUNDARY TESTS
    // ================================================================
    @DisplayName("Edge Cases and Boundary Tests")
    static class EdgeCaseTest {

        private final StringUtils utils       = new StringUtils();
        private final TaxCalculator taxCalc   = new TaxCalculator();
        private final Calculator calculator   = new Calculator();

        @Test
        @DisplayName("Should handle empty string for reverse")
        void shouldHandleEmptyStringForReverse() {
            assertEquals("", utils.reverse(""));
        }

        @Test
        @DisplayName("Should throw for null reverse input")
        void shouldThrowForNullReverseInput() {
            assertThrows(IllegalArgumentException.class, () -> utils.reverse(null));
        }

        @Test
        @DisplayName("Should count words correctly including whitespace cases")
        void shouldCountWordsBoundary() {
            assertEquals(0, utils.countWords(null));
            assertEquals(0, utils.countWords(""));
            assertEquals(0, utils.countWords("   "));
            assertEquals(1, utils.countWords("Hello"));
            assertEquals(3, utils.countWords("Hello World Java"));
            assertEquals(3, utils.countWords("  Hello   World   Java  ")); // extra spaces
        }

        @Test
        @DisplayName("Tax boundary — exact bracket limits")
        void shouldCalculateTaxAtBoundaries() {
            assertEquals(0.0,     taxCalc.calculateTax(0),      0.001);
            assertEquals(0.0,     taxCalc.calculateTax(10000),  0.001);
            assertEquals(1000.1,  taxCalc.calculateTax(10001),  0.001); // just above first bracket
            assertEquals(5000.0,  taxCalc.calculateTax(50000),  0.001);
            assertEquals(20000.0, taxCalc.calculateTax(100000), 0.001);
        }

        @Test
        @DisplayName("Tax should throw for negative income")
        void shouldThrowForNegativeIncome() {
            assertThrows(IllegalArgumentException.class,
                () -> taxCalc.calculateTax(-1));
        }

        @Test
        @DisplayName("Power edge cases")
        void shouldCalculatePowerEdgeCases() {
            assertEquals(1.0,  calculator.power(5, 0),  0.001); // n^0 = 1
            assertEquals(5.0,  calculator.power(5, 1),  0.001); // n^1 = n
            assertEquals(0.04, calculator.power(0.2, 2), 0.001);
        }
    }

    // ================================================================
    // 9. REPEATED AND TAGGED TESTS
    // ================================================================
    @DisplayName("Repeated and Tagged Tests")
    static class RepeatedAndTaggedTest {

        @RepeatedTest(value = 3, name = "Run {currentRepetition} of {totalRepetitions}")
        @DisplayName("Should generate unique IDs each run")
        void shouldGenerateUniqueIds(RepetitionInfo info) {
            String id = UUID.randomUUID().toString();
            assertNotNull(id);
            assertFalse(id.isEmpty());
            System.out.println("Run " + info.getCurrentRepetition() + " -> ID: " + id);
        }

        @Test
        @Tag("fast")
        @DisplayName("Fast test — tagged")
        void fastTest() {
            Calculator calc = new Calculator();
            assertEquals(10, calc.add(6, 4));
        }

        @Test
        @Tag("slow")
        @Disabled("Disabled until performance issue is resolved")
        @DisplayName("Slow test — disabled")
        void slowTest() {
            fail("This should not run");
        }
    }

    // ================================================================
    // 10. ASSERTION SHOWCASE
    // ================================================================
    @DisplayName("JUnit 5 Assertions Showcase")
    static class AssertionsShowcaseTest {

        @Test
        @DisplayName("All assertion types demonstrated")
        void allAssertions() {
            // Basic
            assertEquals(42, 42);
            assertNotEquals(42, 43);
            assertTrue(5 > 3);
            assertFalse(5 < 3);
            assertNull(null);
            assertNotNull("value");

            // Reference
            String s1 = "hello";
            String s2 = s1;
            assertSame(s1, s2);

            // Arrays
            assertArrayEquals(new int[]{1,2,3}, new int[]{1,2,3});

            // Collections
            assertIterableEquals(List.of("a","b","c"), List.of("a","b","c"));

            // Grouped — all run even if some fail
            assertAll("user checks",
                () -> assertEquals("Alice", "Alice"),
                () -> assertTrue(30 > 18),
                () -> assertNotNull("alice@example.com")
            );

            // Timeout
            assertTimeout(java.time.Duration.ofMillis(100), () -> {
                int sum = 0;
                for (int i = 0; i < 1000; i++) sum += i;
                assertEquals(499500, sum);
            });
        }

        @Test
        @DisplayName("Exception assertions")
        void exceptionAssertions() {
            Calculator calc = new Calculator();

            // Assert exception type and message
            ArithmeticException ex = assertThrows(ArithmeticException.class,
                () -> calc.divide(10, 0));
            assertEquals("Cannot divide by zero", ex.getMessage());

            // Assert no exception
            assertDoesNotThrow(() -> calc.divide(10, 2));
        }
    }

    // ================================================================
    // MAIN — runs demo summaries (tests are run by JUnit runner)
    // ================================================================
    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("   UNIT TESTING — JUnit 5 + Mockito Implementation Demo");
        System.out.println("================================================================");
        System.out.println();
        System.out.println("This file contains the following test classes:");
        System.out.println();
        System.out.println("  1.  CalculatorTest          - Basic assertions, lifecycle");
        System.out.println("  2.  ParameterizedTests      - @ValueSource, @CsvSource, @MethodSource");
        System.out.println("  3.  UserServiceNestedTest   - @Nested, @Mock, @InjectMocks");
        System.out.println("  4.  MockitoVerificationTest - verify(), ArgumentCaptor");
        System.out.println("  5.  MockitoSpyTest          - @Spy, partial mocking");
        System.out.println("  6.  BDDStyleTest            - given/when/then with Mockito BDD");
        System.out.println("  7.  AdvancedStubbingTest    - multiple returns, custom answers");
        System.out.println("  8.  EdgeCaseTest            - boundary values, null checks");
        System.out.println("  9.  RepeatedAndTaggedTest   - @RepeatedTest, @Tag, @Disabled");
        System.out.println("  10. AssertionsShowcaseTest  - All assertion types");
        System.out.println();
        System.out.println("To run tests:");
        System.out.println("  mvn test                    (Maven)");
        System.out.println("  gradle test                 (Gradle)");
        System.out.println("  IDE: Right-click -> Run Tests");
        System.out.println();

        // Quick sanity demo — run some production logic directly
        Calculator calc = new Calculator();
        System.out.println("Quick demo (Calculator):");
        System.out.println("  add(5,3)        = " + calc.add(5, 3));
        System.out.println("  divide(10,4)    = " + calc.divide(10, 4));
        System.out.println("  isPrime(17)     = " + calc.isPrime(17));
        System.out.println("  factorial(5)    = " + calc.factorial(5));

        StringUtils utils = new StringUtils();
        System.out.println("\nQuick demo (StringUtils):");
        System.out.println("  reverse('Hello')         = " + utils.reverse("Hello"));
        System.out.println("  isPalindrome('racecar')  = " + utils.isPalindrome("racecar"));
        System.out.println("  countWords('Hi there')   = " + utils.countWords("Hi there"));
        System.out.println("  capitalize('jAVA')       = " + utils.capitalize("jAVA"));

        TaxCalculator tax = new TaxCalculator();
        System.out.println("\nQuick demo (TaxCalculator):");
        System.out.println("  tax(5000)   = " + tax.calculateTax(5000));
        System.out.println("  tax(30000)  = " + tax.calculateTax(30000));
        System.out.println("  tax(75000)  = " + tax.calculateTax(75000));
        System.out.println("  tax(200000) = " + tax.calculateTax(200000));

        System.out.println("\n================================================================");
        System.out.println("   Run with JUnit runner to see full test results and reports");
        System.out.println("================================================================");
    }
}
