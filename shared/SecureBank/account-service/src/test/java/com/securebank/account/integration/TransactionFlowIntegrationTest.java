package com.securebank.account.integration;

import com.securebank.account.model.Account;
import com.securebank.account.model.AccountStatus;
import com.securebank.account.repository.AccountRepository;
import com.securebank.avro.Alert;
import com.securebank.avro.RuleType;
import com.securebank.avro.Transaction;
import com.securebank.avro.TransactionType;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full Spring-boot integration test with Avro + mock Schema Registry:
 * - EmbeddedKafka broker (in-memory)
 * - H2 in-memory DB
 * - KafkaAvroSerializer/Deserializer pointed at "mock://test" Schema Registry
 * - Real @KafkaListener consuming the Avro Transaction
 * - DefaultErrorHandler routing failures to transactions.DLT
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(
        partitions = 1,
        topics = {"transactions", "transactions.DLT", "alerts", "alerts.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class TransactionFlowIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Value("${app.schema-registry-url}")
    private String schemaRegistryUrl;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    private KafkaTemplate<String, Object> avroTemplate;
    private Consumer<String, Object> dltConsumer;

    @BeforeEach
    void setUp() {
        accountRepository.deleteAll();
        accountRepository.save(new Account("ACC9001", "Test User", new BigDecimal("1000.00"),
                AccountStatus.ACTIVE, Instant.now()));

        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        producerProps.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        ProducerFactory<String, Object> pf = new DefaultKafkaProducerFactory<>(producerProps);
        avroTemplate = new KafkaTemplate<>(pf);

        // DLT consumer reads raw Avro back as a SpecificRecord (Transaction)
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "dlt-test-group-" + System.nanoTime(), "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        consumerProps.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
        dltConsumer = new DefaultKafkaConsumerFactory<String, Object>(
                consumerProps, new StringDeserializer(), new KafkaAvroDeserializer()
        ).createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, "transactions.DLT");
    }

    @AfterEach
    void tearDown() {
        if (dltConsumer != null) {
            dltConsumer.close();
        }
        if (avroTemplate != null) {
            avroTemplate.destroy();
        }
    }

    @Test
    @DisplayName("Publishing an Avro DEPOSIT event updates the account balance in DB")
    void depositEvent_updatesAccountBalance() {
        Transaction event = buildEvent("IT-T-001", "ACC9001", TransactionType.DEPOSIT, "500.00");

        avroTemplate.send("transactions", "ACC9001", event);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Optional<Account> updated = accountRepository.findById("ACC9001");
            assertThat(updated).isPresent();
            assertThat(updated.get().getBalance()).isEqualByComparingTo("1500.00");
        });
    }

    @Test
    @DisplayName("Unknown account → IllegalStateException → routed to transactions.DLT with original key + exception headers")
    void unknownAccount_routedToDLT() {
        Transaction event = buildEvent("IT-T-002", "ACC_DOES_NOT_EXIST", TransactionType.DEPOSIT, "100.00");

        avroTemplate.send("transactions", "ACC_DOES_NOT_EXIST", event);

        ConsumerRecord<String, Object> dltRecord =
                KafkaTestUtils.getSingleRecord(dltConsumer, "transactions.DLT", Duration.ofSeconds(15));

        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.key()).isEqualTo("ACC_DOES_NOT_EXIST");

        String exceptionMessage = headerAsString(dltRecord, "kafka_dlt-exception-message");
        assertThat(exceptionMessage).contains("Unknown account");

        String originalTopic = headerAsString(dltRecord, "kafka_dlt-original-topic");
        assertThat(originalTopic).isEqualTo("transactions");

        assertThat(accountRepository.findById("ACC_DOES_NOT_EXIST")).isEmpty();
    }

    @Test
    @DisplayName("Publishing a fraud Alert freezes the account, and a later WITHDRAW is rejected to the DLT")
    void fraudAlert_freezesAccount_andBlocksWithdraw() {
        Alert alert = Alert.newBuilder()
                .setAccountId("ACC9001")
                .setTransactionId(null)
                .setRuleType(RuleType.HIGH_VELOCITY)
                .setReason("Too many transactions: 6 in 60s")
                .setCount(6L)
                .setDetectedAt(Instant.now())
                .build();

        avroTemplate.send("alerts", "ACC9001", alert);

        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Optional<Account> frozen = accountRepository.findById("ACC9001");
            assertThat(frozen).isPresent();
            assertThat(frozen.get().getStatus()).isEqualTo(AccountStatus.FROZEN);
        });

        // Money-out is now blocked: the WITHDRAW must not change the balance.
        Transaction withdraw = buildEvent("IT-T-003", "ACC9001", TransactionType.WITHDRAW, "100.00");
        avroTemplate.send("transactions", "ACC9001", withdraw);

        ConsumerRecord<String, Object> dltRecord =
                KafkaTestUtils.getSingleRecord(dltConsumer, "transactions.DLT", Duration.ofSeconds(15));
        assertThat(headerAsString(dltRecord, "kafka_dlt-exception-message")).contains("frozen");
        assertThat(accountRepository.findById("ACC9001").get().getBalance())
                .isEqualByComparingTo("1000.00");
    }

    private Transaction buildEvent(String txnId, String accountId, TransactionType type, String amount) {
        return Transaction.newBuilder()
                .setTransactionId(txnId)
                .setAccountId(accountId)
                .setType(type)
                .setAmount(new BigDecimal(amount))
                .setCity(null)
                .setTimestamp(Instant.now())
                .build();
    }

    private String headerAsString(ConsumerRecord<?, ?> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value());
    }
}
