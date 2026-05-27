package com.securebank.fraud.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.securebank.fraud.model.Alert;
import com.securebank.fraud.model.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import java.time.Instant;

/**
 * Detects suspicious activity using a tumbling window:
 * if an account has more than N transactions in T seconds,
 * publish an alert to "alerts" topic.
 */
@Configuration
@Slf4j
public class FraudDetectionTopology {

    @Value("${app.topic.transactions}")
    private String transactionsTopic;

    @Value("${app.topic.alerts}")
    private String alertsTopic;

    @Value("${app.fraud.window-seconds:60}")
    private long windowSeconds;

    @Value("${app.fraud.threshold:5}")
    private long threshold;

    @Bean
    public KStream<String, Transaction> kStream(StreamsBuilder builder) {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JsonSerde<Transaction> txnSerde = new JsonSerde<>(Transaction.class, mapper);
        JsonSerde<Alert> alertSerde = new JsonSerde<>(Alert.class, mapper);
        txnSerde.deserializer().setUseTypeHeaders(false);

        KStream<String, Transaction> stream = builder.stream(
                transactionsTopic,
                Consumed.with(Serdes.String(), txnSerde)
        );

        // Count transactions per account in a tumbling window
        stream
            .groupByKey(Grouped.with(Serdes.String(), txnSerde))
            .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofSeconds(windowSeconds)))
            .count(Materialized.as("txn-count-per-account"))
            .toStream()
            .filter((windowedKey, count) -> count != null && count > threshold)
            .map((windowedKey, count) -> {
                String accountId = windowedKey.key();
                Alert alert = new Alert(
                        accountId,
                        "Too many transactions: " + count + " in " + windowSeconds + "s",
                        count,
                        Instant.now()
                );
                log.warn("FRAUD ALERT: {}", alert);
                return KeyValue.pair(accountId, alert);
            })
            .to(alertsTopic, Produced.with(Serdes.String(), alertSerde));

        return stream;
    }
}
