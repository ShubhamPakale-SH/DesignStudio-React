package com.securebank.transaction.controller;

import com.securebank.transaction.model.Transaction;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final KafkaTemplate<String, Transaction> kafkaTemplate;

    @Value("${app.topic.transactions}")
    private String topic;

    @PostMapping
    public ResponseEntity<Map<String, String>> publish(@Valid @RequestBody Transaction txn) {
        if (txn.getTransactionId() == null) {
            txn.setTransactionId(UUID.randomUUID().toString());
        }
        if (txn.getTimestamp() == null) {
            txn.setTimestamp(Instant.now());
        }

        // Key by accountId → preserves order per account on the same partition
        CompletableFuture<SendResult<String, Transaction>> future =
                kafkaTemplate.send(topic, txn.getAccountId(), txn);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish txn {}: {}", txn.getTransactionId(), ex.getMessage());
            } else {
                log.info("Published txn {} to partition {} offset {}",
                        txn.getTransactionId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return ResponseEntity.accepted().body(Map.of(
                "transactionId", txn.getTransactionId(),
                "status", "ACCEPTED"
        ));
    }
}
