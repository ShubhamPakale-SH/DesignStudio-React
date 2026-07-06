package com.securebank.fraud.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.securebank.avro.Alert;
import com.securebank.avro.RuleType;
import com.securebank.avro.Transaction;
import com.securebank.fraud.model.LastLocation;
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
 * Four layered fraud detection rules over Avro-encoded Transactions:
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
 *  Rule D — GEO_IMPOSSIBLE: consecutive transactions from cities that would require travelling
 *                           faster than {@code geoMaxSpeedKmh}. Catches cloned-card usage.
 *
 * Per-account state (rolling stats, last-seen location) lives in Streams state stores
 * (JSON-serialised), each backed by an internal changelog topic for fault tolerance.
 */
@Configuration
@Slf4j
public class FraudDetectionTopology {

    public static final String ROLLING_STATS_STORE = "rolling-stats-store";
    public static final String LAST_LOCATION_STORE = "last-location-store";

    @Value("${app.topic.transactions}")
    private String transactionsTopic;

    @Value("${app.topic.alerts}")
    private String alertsTopic;

    @Value("${app.fraud.window-seconds:60}")
    private long windowSeconds;

    @Value("${app.fraud.window-grace-seconds:10}")
    private long windowGraceSeconds;

    @Value("${app.fraud.count-threshold:5}")
    private long countThreshold;

    @Value("${app.fraud.large-amount-threshold:100000}")
    private BigDecimal largeAmountThreshold;

    @Value("${app.fraud.spike-multiplier:5.0}")
    private double spikeMultiplier;

    @Value("${app.fraud.spike-min-history:3}")
    private long spikeMinHistory;

    @Value("${app.fraud.geo-max-speed-kmh:900}")
    private double geoMaxSpeedKmh;

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
                windowSeconds, windowGraceSeconds, countThreshold,
                largeAmountThreshold, spikeMultiplier, spikeMinHistory,
                geoMaxSpeedKmh, txnSerde, alertSerde);
    }

    public static KStream<String, Transaction> buildTopology(
            StreamsBuilder builder,
            String transactionsTopic,
            String alertsTopic,
            long windowSeconds,
            long windowGraceSeconds,
            long countThreshold,
            BigDecimal largeAmountThreshold,
            double spikeMultiplier,
            long spikeMinHistory,
            double geoMaxSpeedKmh,
            SpecificAvroSerde<Transaction> txnSerde,
            SpecificAvroSerde<Alert> alertSerde) {

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonSerde<RollingStats> statsSerde = new JsonSerde<>(RollingStats.class, mapper);
        statsSerde.deserializer().setUseTypeHeaders(false);
        JsonSerde<LastLocation> locationSerde = new JsonSerde<>(LastLocation.class, mapper);
        locationSerde.deserializer().setUseTypeHeaders(false);

        StoreBuilder<KeyValueStore<String, RollingStats>> rollingStatsStoreBuilder =
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(ROLLING_STATS_STORE),
                        Serdes.String(),
                        statsSerde
                );
        builder.addStateStore(rollingStatsStoreBuilder);

        StoreBuilder<KeyValueStore<String, LastLocation>> lastLocationStoreBuilder =
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(LAST_LOCATION_STORE),
                        Serdes.String(),
                        locationSerde
                );
        builder.addStateStore(lastLocationStoreBuilder);

        KStream<String, Transaction> stream = builder.stream(
                transactionsTopic,
                Consumed.with(Serdes.String(), txnSerde)
        );

        // ─── Rule A: HIGH_VELOCITY ─────────────────────────────────────────────
        KStream<String, Alert> countAlerts = stream
                .groupByKey(Grouped.with(Serdes.String(), txnSerde))
                .windowedBy(TimeWindows.ofSizeAndGrace(
                        Duration.ofSeconds(windowSeconds), Duration.ofSeconds(windowGraceSeconds)))
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

        // ─── Rule D: GEO_IMPOSSIBLE ────────────────────────────────────────────
        KStream<String, Alert> geoAlerts = stream
                .transformValues(
                        () -> new GeoImpossibleTransformer(geoMaxSpeedKmh),
                        LAST_LOCATION_STORE
                )
                .filter((k, v) -> v != null);

        countAlerts.merge(largeAmountAlerts).merge(spikeAlerts).merge(geoAlerts)
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

    public static class GeoImpossibleTransformer
            implements ValueTransformerWithKey<String, Transaction, Alert> {

        /** Known city coordinates (lat, lon). Unknown cities are skipped, never alerted on. */
        private static final Map<String, double[]> CITY_COORDINATES = Map.ofEntries(
                Map.entry("mumbai", new double[]{19.0760, 72.8777}),
                Map.entry("delhi", new double[]{28.6139, 77.2090}),
                Map.entry("bangalore", new double[]{12.9716, 77.5946}),
                Map.entry("bengaluru", new double[]{12.9716, 77.5946}),
                Map.entry("hyderabad", new double[]{17.3850, 78.4867}),
                Map.entry("chennai", new double[]{13.0827, 80.2707}),
                Map.entry("kolkata", new double[]{22.5726, 88.3639}),
                Map.entry("pune", new double[]{18.5204, 73.8567}),
                Map.entry("ahmedabad", new double[]{23.0225, 72.5714}),
                Map.entry("london", new double[]{51.5074, -0.1278}),
                Map.entry("new york", new double[]{40.7128, -74.0060}),
                Map.entry("singapore", new double[]{1.3521, 103.8198}),
                Map.entry("dubai", new double[]{25.2048, 55.2708}),
                Map.entry("tokyo", new double[]{35.6762, 139.6503}),
                Map.entry("sydney", new double[]{-33.8688, 151.2093}),
                Map.entry("paris", new double[]{48.8566, 2.3522}),
                Map.entry("frankfurt", new double[]{50.1109, 8.6821}),
                Map.entry("san francisco", new double[]{37.7749, -122.4194}),
                Map.entry("hong kong", new double[]{22.3193, 114.1694})
        );

        private static final double EARTH_RADIUS_KM = 6371.0;

        private final double maxSpeedKmh;
        private KeyValueStore<String, LastLocation> store;

        public GeoImpossibleTransformer(double maxSpeedKmh) {
            this.maxSpeedKmh = maxSpeedKmh;
        }

        @Override
        public void init(ProcessorContext context) {
            this.store = context.getStateStore(LAST_LOCATION_STORE);
        }

        @Override
        public Alert transform(String accountId, Transaction txn) {
            if (txn == null || accountId == null || txn.getCity() == null || txn.getTimestamp() == null) {
                return null;
            }

            LastLocation last = store.get(accountId);
            Alert alert = null;

            if (last != null && last.getCity() != null
                    && !txn.getCity().equalsIgnoreCase(last.getCity())) {
                double[] from = CITY_COORDINATES.get(last.getCity().toLowerCase());
                double[] to = CITY_COORDINATES.get(txn.getCity().toLowerCase());
                if (from != null && to != null) {
                    double distanceKm = haversineKm(from, to);
                    long elapsedMs = Duration.between(last.getTimestamp(), txn.getTimestamp()).toMillis();
                    // Out-of-order or same-millisecond events collapse to 1ms — treated as instantaneous travel.
                    double hours = Math.max(elapsedMs, 1L) / 3_600_000.0;
                    double requiredSpeedKmh = distanceKm / hours;
                    if (requiredSpeedKmh > maxSpeedKmh) {
                        alert = Alert.newBuilder()
                                .setAccountId(accountId)
                                .setTransactionId(txn.getTransactionId())
                                .setRuleType(RuleType.GEO_IMPOSSIBLE)
                                .setReason("Impossible travel: " + last.getCity() + " -> " + txn.getCity()
                                        + " (" + Math.round(distanceKm) + " km) would require "
                                        + Math.round(requiredSpeedKmh) + " km/h")
                                .setCount(1L)
                                .setDetectedAt(Instant.now())
                                .build();
                        log.warn("FRAUD ALERT [GEO_IMPOSSIBLE]: {}", alert);
                    }
                }
            }

            store.put(accountId, new LastLocation(txn.getCity(), txn.getTimestamp()));
            return alert;
        }

        @Override
        public void close() {
            // no resources to release
        }

        private static double haversineKm(double[] from, double[] to) {
            double latDelta = Math.toRadians(to[0] - from[0]);
            double lonDelta = Math.toRadians(to[1] - from[1]);
            double a = Math.sin(latDelta / 2) * Math.sin(latDelta / 2)
                    + Math.cos(Math.toRadians(from[0])) * Math.cos(Math.toRadians(to[0]))
                    * Math.sin(lonDelta / 2) * Math.sin(lonDelta / 2);
            return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
        }
    }
}
