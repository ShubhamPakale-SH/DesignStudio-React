package com.securebank.account.consumer;

import com.securebank.account.service.AccountService;
import com.securebank.avro.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes Avro-encoded transactions and applies them to the DB.
 *
 * Error handling is centralised in {@link com.securebank.account.config.KafkaConsumerConfig}:
 *   - IllegalStateException (business rule violations, incl. frozen accounts) → "transactions.DLT" immediately.
 *   - Transient infra exceptions → retried 3× with exponential backoff, then DLT.
 *
 * Offset commit is handled by AckMode.RECORD after this method returns successfully.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionConsumer {

    private final AccountService accountService;

    @KafkaListener(topics = "${app.topic.transactions}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(Transaction event) {
        accountService.apply(event);
    }
}
