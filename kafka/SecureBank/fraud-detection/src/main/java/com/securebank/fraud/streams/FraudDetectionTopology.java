package com.securebank.fraud.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.securebank.avro.Alert;
import com.securebank.avro.RuleType;
import com.securebank.avro.Transaction;
import com.securebank.fraud.model.RollingStats;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * Three layered fraud detection rules over Avro-encoded Transactions:
 *
 *  Rule A — HIGH_VELOCITY:  more than {@code countThreshold} transactions in a {@code windowSeconds}
 *                           tumbling window. Catches card-testing bursts.
 *
 *  Rule B — LARGE_AMOUNT:   single transaction whose amount exceeds {@code largeAmountThreshold}.
 *                           Catches account-drain attempts.
 *
 *  Rule C — SPEND_SPIKE:    incoming amount &gt; {@code spikeMultiplier} × the account's rolling
 *                           average. Catches anomalous spend per customer baseline.
 *                           Activates only after {@code spikeMinHistory} prior transactions.
 *
 * Rolling stats per account live in a Streams state store (JSON-serialised). The state store is
 * backed by an internal changelog topic for fault tolerance.
 */
@Configuration
@Slf4j
public class FraudDetectionTopology {

    public static final String ROLLING_STATS_STORE = "rolling-stats-store";

    @Value("${app.topic.transactions}")
    private String transactionsTopic;

    @Value("${app.topic.alerts}")
    private String alertsTopic;

    @Value("${app.fraud.window-seconds:60}")
    private long windowSeconds;

    @Value("${app.fraud.count-threshold:5}")
    private long countThreshold;

    @Value("${app.fraud.large-amount-threshold:100000}")
    private BigDecimal largeAmountThreshold;

    @Value("${app.fraud.spike-multiplier:5.0}")
    private double spikeMultiplier;

    @Value("${app.fraud.spike-min-history:3}")
    private long spikeMinHistory;

    @Value("${app.schema-registry-url}")
    private String schemaRegistryUrl;

    @Bean
    public KStream<String, Transaction> kStream(StreamsBuilder builder) {
        Map<String, String> serdeConfig =
                Collections.singletonMap(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);

        SpecificAvroSerde<Transaction> txnSerde = new SpecificAvroSerde<>();
        txnSerde.configure(serdeConfig, false);
        SpecificAvroSerde<Alert> alertSerde = new SpecificAvroSerde<>();
        alertSerde.configure(serdeConfig, false);

        return buildTopology(builder, transactionsTopic, alertsTopic,
                windowSeconds, countThreshold,
                largeAmountThreshold, spikeMultiplier, spikeMinHistory,
                txnSerde, alertSerde);
    }

    public static KStream<String, Transaction> buildTopology(
            StreamsBuilder builder,
            String transactionsTopic,
            String alertsTopic,
            long windowSeconds,
            long countThreshold,
            BigDecimal largeAmountThreshold,
            double spikeMultiplier,
            long spikeMinHistory,
            SpecificAvroSerde<Transaction> txnSerde,
            SpecificAvroSerde<Alert> alertSerde) {

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonSerde<RollingStats> statsSerde = new JsonSerde<>(RollingStats.class, mapper);
        statsSerde.deserializer().setUseTypeHeaders(false);

        StoreBuilder<KeyValueStore<String, RollingStats>> rollingStatsStoreBuilder =
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(ROLLING_STATS_STORE),
                        Serdes.String(),
                        statsSerde
                );
        builder.addStateStore(rollingStatsStoreBuilder);

        KStream<String, Transaction> stream = builder.stream(
                transactionsTopic,
                Consumed.with(Serdes.String(), txnSerde)
        );

        // ─── Rule A: HIGH_VELOCITY ─────────────────────────────────────────────
        KStream<String, Alert> countAlerts = stream
                .groupByKey(Grouped.with(Serdes.String(), txnSerde))
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(windowSeconds)))
                .count(Materialized.<String, Long, org.apache.kafka.streams.state.WindowStore<org.apache.kafka.common.utils.Bytes, byte[]>>as("txn-count-per-account")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.Long()))
                .toStream()
                .filter((windowedKey, count) -> count != null && count > countThreshold)
                .map((windowedKey, count) -> {
                    String accountId = windowedKey.key();
                    Alert alert = Alert.newBuilder()
                            .setAccountId(accountId)
                            .setTransactionId(null)
                            .setRuleType(RuleType.HIGH_VELOCITY)
                            .setReason("Too many transactions: " + count + " in " + windowSeconds + "s")
                            .setCount(count)
                            .setDetectedAt(Instant.now())
                            .build();
                    log.warn("FRAUD ALERT [HIGH_VELOCITY]: {}", alert);
                    return KeyValue.pair(accountId, alert);
                });

        // ─── Rule B: LARGE_AMOUNT ──────────────────────────────────────────────
        KStream<String, Alert> largeAmountAlerts = stream
                .filter((k, txn) -> txn != null
                        && txn.getAmount() != null
                        && txn.getAmount().compareTo(largeAmountThreshold) > 0)
                .map((k, txn) -> {
                    Alert alert = Alert.newBuilder()
                            .setAccountId(txn.getAccountId())
                            .setTransactionId(txn.getTransactionId())
                            .setRuleType(RuleType.LARGE_AMOUNT)
                            .setReason("Large transaction: " + txn.getAmount()
                                    + " exceeds " + largeAmountThreshold)
                            .setCount(1L)
                            .setDetectedAt(Instant.now())
                            .build();
                    log.warn("FRAUD ALERT [LARGE_AMOUNT]: {}", alert);
                    return KeyValue.pair(txn.getAccountId(), alert);
                });

        // ─── Rule C: SPEND_SPIKE ───────────────────────────────────────────────
        KStream<String, Alert> spikeAlerts = stream
                .transformValues(
                        () -> new SpikeDetectorTransformer(spikeMultiplier, spikeMinHistory),
                        ROLLING_STATS_STORE
                )
                .filter((k, v) -> v != null);

        countAlerts.merge(largeAmountAlerts).merge(spikeAlerts)
                .to(alertsTopic, Produced.with(Serdes.String(), alertSerde));

        return stream;
    }

    public static class SpikeDetectorTransformer
            implements ValueTransformerWithKey<String, Transaction, Alert> {

        private final double multiplier;
        private final long minHistory;
        private KeyValueStore<String, RollingStats> store;

        public SpikeDetectorTransformer(double multiplier, long minHistory) {
            this.multiplier = multiplier;
            this.minHistory = minHistory;
        }

        @Override
        public void init(ProcessorContext context) {
            this.store = context.getStateStore(ROLLING_STATS_STORE);
        }

        @Override
        public Alert transform(String accountId, Transaction txn) {
            if (txn == null || txn.getAmount() == null || accountId == null) {
                return null;
            }

            RollingStats stats = store.get(accountId);
            if (stats == null) {
                stats = new RollingStats();
            }

            Alert alert = null;
            if (stats.getCount() >= minHistory) {
                BigDecimal avg = stats.getAverage();
                BigDecimal threshold = avg.multiply(BigDecimal.valueOf(multiplier));
                if (txn.getAmount().compareTo(threshold) > 0) {
                    alert = Alert.newBuilder()
                            .setAccountId(accountId)
                            .setTransactionId(txn.getTransactionId())
                            .setRuleType(RuleType.SPEND_SPIKE)
                            .setReason("Spike: " + txn.getAmount()
                                    + " > " + multiplier + "x rolling avg " + avg)
                            .setCount(1L)
                            .setDetectedAt(Instant.now())
                            .build();
                    log.warn("FRAUD ALERT [SPEND_SPIKE]: {}", alert);
                }
            }

            stats.add(txn.getAmount());
            store.put(accountId, stats);

            return alert;
        }

        @Override
        public void close() {
            // no resources to release
        }
    }
}
