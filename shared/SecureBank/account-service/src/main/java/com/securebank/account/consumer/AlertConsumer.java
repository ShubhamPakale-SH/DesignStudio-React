package com.securebank.account.consumer;

import com.securebank.account.service.AccountService;
import com.securebank.avro.Alert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Closes the fraud loop: consumes Avro alerts from fraud-detection and freezes the account.
 *
 * Lives in account-service (not a separate action-service) deliberately — the accounts table
 * has a single writer, so status changes go through the same service that owns balances.
 *
 * Error handling is centralised in {@link com.securebank.account.config.KafkaConsumerConfig}:
 *   - IllegalStateException (e.g. alert for an account not in our DB) → routed to "alerts.DLT".
 *   - Transient infra exceptions → retried 3× with exponential backoff, then DLT.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertConsumer {

    private final AccountService accountService;

    @KafkaListener(topics = "${app.topic.alerts}", containerFactory = "alertKafkaListenerContainerFactory")
    public void consume(Alert alert) {
        accountService.freeze(alert);
    }
}
