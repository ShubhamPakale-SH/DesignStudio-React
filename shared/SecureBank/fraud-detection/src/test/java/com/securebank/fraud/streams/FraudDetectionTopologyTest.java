package com.securebank.fraud.streams;

import com.securebank.avro.Alert;
import com.securebank.avro.RuleType;
import com.securebank.avro.Transaction;
import com.securebank.avro.TransactionType;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class FraudDetectionTopologyTest {

    private static final String TXN_TOPIC = "transactions";
    private static final String ALERT_TOPIC = "alerts";
    private static final long WINDOW_SECONDS = 60;
    private static final long WINDOW_GRACE_SECONDS = 10;
    private static final long COUNT_THRESHOLD = 5;
    private static final BigDecimal LARGE_AMOUNT_THRESHOLD = new BigDecimal("100000");
    private static final double SPIKE_MULTIPLIER = 5.0;
    private static final long SPIKE_MIN_HISTORY = 3;
    private static final double GEO_MAX_SPEED_KMH = 900;
    private static final String SCHEMA_REGISTRY_URL = "mock://fraud-topology-test";

    private TopologyTestDriver testDriver;
    private TestInputTopic<String, Transaction> inputTopic;
    private TestOutputTopic<String, Alert> outputTopic;
    private SpecificAvroSerde<Transaction> txnSerde;
    private SpecificAvroSerde<Alert> alertSerde;

    @BeforeEach
    void setUp() {
        Map<String, String> serdeConfig =
                Collections.singletonMap(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL);

        txnSerde = new SpecificAvroSerde<>();
        txnSerde.configure(serdeConfig, false);
        alertSerde = new SpecificAvroSerde<>();
        alertSerde.configure(serdeConfig, false);

        StreamsBuilder builder = new StreamsBuilder();
        FraudDetectionTopology.buildTopology(builder, TXN_TOPIC, ALERT_TOPIC,
                WINDOW_SECONDS, WINDOW_GRACE_SECONDS, COUNT_THRESHOLD,
                LARGE_AMOUNT_THRESHOLD, SPIKE_MULTIPLIER, SPIKE_MIN_HISTORY,
                GEO_MAX_SPEED_KMH, txnSerde, alertSerde);
        Topology topology = builder.build();

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "fraud-detection-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL);

        testDriver = new TopologyTestDriver(topology, props);

        inputTopic = testDriver.createInputTopic(TXN_TOPIC, Serdes.String().serializer(), txnSerde.serializer());
        outputTopic = testDriver.createOutputTopic(ALERT_TOPIC, Serdes.String().deserializer(), alertSerde.deserializer());
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
        if (txnSerde != null) {
            txnSerde.close();
        }
        if (alertSerde != null) {
            alertSerde.close();
        }
    }

    // ─── Rule A: HIGH_VELOCITY ─────────────────────────────────────────────────

    @Test
    @DisplayName("Rule A: 5 transactions in window (at threshold) → no alert")
    void atThreshold_noAlert() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            inputTopic.pipeInput("ACC1001", buildTxn("T" + i, "ACC1001", "100"), base.plusSeconds(i));
        }
        inputTopic.pipeInput("ACC9999", buildTxn("DONE", "ACC9999", "1"), base.plusSeconds(WINDOW_SECONDS + 5));

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Rule A: 6+ transactions in window → HIGH_VELOCITY alert")
    void aboveThreshold_emitsHighVelocityAlert() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        for (int i = 0; i < 6; i++) {
            inputTopic.pipeInput("ACC1002", buildTxn("T" + i, "ACC1002", "50"), base.plusSeconds(i));
        }
        inputTopic.pipeInput("ACC9999", buildTxn("DONE", "ACC9999", "1"), base.plusSeconds(WINDOW_SECONDS + 5));

        assertThat(outputTopic.isEmpty()).isFalse();
        Alert alert = outputTopic.readValue();
        assertThat(alert.getAccountId()).isEqualTo("ACC1002");
        assertThat(alert.getRuleType()).isEqualTo(RuleType.HIGH_VELOCITY);
        assertThat(alert.getCount()).isEqualTo(6L);
        assertThat(alert.getReason()).contains("Too many transactions");
    }

    // ─── Rule B: LARGE_AMOUNT ──────────────────────────────────────────────────

    @Test
    @DisplayName("Rule B: single transaction above threshold → LARGE_AMOUNT alert")
    void largeAmountTransaction_emitsLargeAmountAlert() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        inputTopic.pipeInput("ACC1003", buildTxn("BIG-1", "ACC1003", "200000"), base);

        assertThat(outputTopic.isEmpty()).isFalse();
        Alert alert = outputTopic.readValue();
        assertThat(alert.getAccountId()).isEqualTo("ACC1003");
        assertThat(alert.getRuleType()).isEqualTo(RuleType.LARGE_AMOUNT);
        assertThat(alert.getTransactionId()).isEqualTo("BIG-1");
        assertThat(alert.getReason()).contains("Large transaction");
    }

    // ─── Rule C: SPEND_SPIKE ───────────────────────────────────────────────────

    @Test
    @DisplayName("Rule C: amount > 5x rolling avg after min history → SPEND_SPIKE alert")
    void spendSpike_emitsSpendSpikeAlert() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        for (int i = 0; i < 3; i++) {
            inputTopic.pipeInput("ACC1004", buildTxn("BASE-" + i, "ACC1004", "100"), base.plusSeconds(i));
        }
        inputTopic.pipeInput("ACC1004", buildTxn("SPIKE-1", "ACC1004", "10000"), base.plusSeconds(10));

        assertThat(outputTopic.isEmpty()).isFalse();
        Alert alert = outputTopic.readValue();
        assertThat(alert.getAccountId()).isEqualTo("ACC1004");
        assertThat(alert.getRuleType()).isEqualTo(RuleType.SPEND_SPIKE);
        assertThat(alert.getTransactionId()).isEqualTo("SPIKE-1");
        assertThat(alert.getReason()).contains("Spike");
    }

    @Test
    @DisplayName("Rule C: amount only 2x rolling avg → no alert")
    void belowSpikeMultiplier_noAlert() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        for (int i = 0; i < 3; i++) {
            inputTopic.pipeInput("ACC1005", buildTxn("BASE-" + i, "ACC1005", "100"), base.plusSeconds(i));
        }
        inputTopic.pipeInput("ACC1005", buildTxn("MILD-1", "ACC1005", "200"), base.plusSeconds(10));

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    // ─── Rule D: GEO_IMPOSSIBLE ────────────────────────────────────────────────

    @Test
    @DisplayName("Rule D: Mumbai then London 10 minutes later → GEO_IMPOSSIBLE alert")
    void impossibleTravel_emitsGeoAlert() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        inputTopic.pipeInput("ACC2001", buildTxn("GEO-1", "ACC2001", "100", "Mumbai", base), base);
        inputTopic.pipeInput("ACC2001", buildTxn("GEO-2", "ACC2001", "100", "London", base.plusSeconds(600)),
                base.plusSeconds(600));

        assertThat(outputTopic.isEmpty()).isFalse();
        Alert alert = outputTopic.readValue();
        assertThat(alert.getAccountId()).isEqualTo("ACC2001");
        assertThat(alert.getRuleType()).isEqualTo(RuleType.GEO_IMPOSSIBLE);
        assertThat(alert.getTransactionId()).isEqualTo("GEO-2");
        assertThat(alert.getReason()).contains("Impossible travel");
    }

    @Test
    @DisplayName("Rule D: Mumbai then Pune 2 hours later (60 km/h) → no alert")
    void plausibleTravel_noAlert() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        inputTopic.pipeInput("ACC2002", buildTxn("GEO-3", "ACC2002", "100", "Mumbai", base), base);
        inputTopic.pipeInput("ACC2002", buildTxn("GEO-4", "ACC2002", "100", "Pune", base.plusSeconds(7200)),
                base.plusSeconds(7200));

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Rule D: unknown city → skipped, no alert")
    void unknownCity_noGeoAlert() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        inputTopic.pipeInput("ACC2003", buildTxn("GEO-5", "ACC2003", "100", "Gotham", base), base);
        inputTopic.pipeInput("ACC2003", buildTxn("GEO-6", "ACC2003", "100", "London", base.plusSeconds(60)),
                base.plusSeconds(60));

        assertThat(outputTopic.isEmpty()).isTrue();
    }

    // ─── Windowing: grace period for late events ───────────────────────────────

    @Test
    @DisplayName("Late event arriving within the grace period still counts toward the window")
    void lateEventWithinGrace_stillCounted() {
        Instant base = Instant.parse("2026-01-01T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            inputTopic.pipeInput("ACC3001", buildTxn("T" + i, "ACC3001", "100"), base.plusSeconds(i));
        }
        // Advance stream time past the window end (60s) but within grace (60s + 10s).
        inputTopic.pipeInput("ACC9999", buildTxn("ADVANCE", "ACC9999", "1"), base.plusSeconds(65));
        // A late 6th event, timestamped inside the original window, arrives now.
        inputTopic.pipeInput("ACC3001", buildTxn("T-LATE", "ACC3001", "100"), base.plusSeconds(30));

        assertThat(outputTopic.isEmpty()).isFalse();
        Alert alert = outputTopic.readValue();
        assertThat(alert.getAccountId()).isEqualTo("ACC3001");
        assertThat(alert.getRuleType()).isEqualTo(RuleType.HIGH_VELOCITY);
        assertThat(alert.getCount()).isEqualTo(6L);
    }

    private Transaction buildTxn(String txnId, String accountId, String amount) {
        return buildTxn(txnId, accountId, amount, "Mumbai", Instant.now());
    }

    private Transaction buildTxn(String txnId, String accountId, String amount, String city, Instant timestamp) {
        return Transaction.newBuilder()
                .setTransactionId(txnId)
                .setAccountId(accountId)
                .setType(TransactionType.WITHDRAW)
                .setAmount(new BigDecimal(amount))
                .setCity(city)
                .setTimestamp(timestamp)
                .build();
    }
}
