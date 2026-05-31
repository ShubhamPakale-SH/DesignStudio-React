package com.securebank.transaction.controller;

import com.securebank.avro.Transaction;
import com.securebank.avro.TransactionType;
import com.securebank.transaction.model.TransactionRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private static final long PUBLISH_TIMEOUT_SECONDS = 5;

    private final KafkaTemplate<String, Transaction> kafkaTemplate;

    @Value("${app.topic.transactions}")
    private String topic;

    /**
     * Accepts a JSON HTTP body, maps it to the Avro {@code Transaction} record, and publishes
     * synchronously. The Avro schema is registered with Confluent Schema Registry on first send.
     *
     * Success (broker acked, message durable in ISR) → {@code 201 Created} with partition + offset.
     * Kafka unreachable / no ISR / timeout                → {@code 503 Service Unavailable}.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> publish(@Valid @RequestBody TransactionRequest req) {
        String txnId = req.getTransactionId() != null ? req.getTransactionId() : UUID.randomUUID().toString();

        Transaction avroTxn = Transaction.newBuilder()
                .setTransactionId(txnId)
                .setAccountId(req.getAccountId())
                .setType(TransactionType.valueOf(req.getType().name()))
                .setAmount(req.getAmount())
                .setCity(req.getCity())
                .setTimestamp(Instant.now())
                .build();

        try {
            SendResult<String, Transaction> result = kafkaTemplate
                    .send(topic, req.getAccountId(), avroTxn)
                    .get(PUBLISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            int partition = result.getRecordMetadata().partition();
            long offset = result.getRecordMetadata().offset();

            log.info("Published txn {} to partition {} offset {}", txnId, partition, offset);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("transactionId", txnId);
            body.put("status", "PERSISTED");
            body.put("partition", partition);
            body.put("offset", offset);
            return ResponseEntity.status(HttpStatus.CREATED).body(body);

        } catch (TimeoutException e) {
            log.error("Kafka publish timed out for txn {} after {}s", txnId, PUBLISH_TIMEOUT_SECONDS);
            return errorResponse(txnId, "TIMEOUT",
                    "Kafka did not acknowledge in time. Please retry.");

        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("Kafka publish failed for txn {}: {}", txnId, cause.getMessage());
            return errorResponse(txnId, "FAILED",
                    "Kafka publish failed: " + cause.getMessage());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Kafka publish interrupted for txn {}", txnId);
            return errorResponse(txnId, "INTERRUPTED",
                    "Request thread interrupted while waiting for broker.");
        }
    }

    private ResponseEntity<Map<String, Object>> errorResponse(String txnId, String status, String error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("transactionId", txnId);
        body.put("status", status);
        body.put("error", error);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
