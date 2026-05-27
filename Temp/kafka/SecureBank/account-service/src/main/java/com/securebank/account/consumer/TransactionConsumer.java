package com.securebank.account.consumer;

import com.securebank.account.model.TransactionEvent;
import com.securebank.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransactionConsumer {

    private final AccountService accountService;

    @KafkaListener(topics = "${app.topic.transactions}", containerFactory = "kafkaListenerContainerFactory")
    public void consume(TransactionEvent event, Acknowledgment ack) {
        try {
            accountService.apply(event);
            ack.acknowledge(); // commit offset only after successful DB write
        } catch (IllegalStateException e) {
            // Business failure (insufficient funds, unknown account) — log + skip
            // In production: route to DLT topic for replay/audit
            log.error("Business rule violation for txn {}: {}", event.getTransactionId(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            // Transient infra failure — do NOT commit; consumer will retry from same offset
            log.error("Transient failure for txn {}: {} (will retry)", event.getTransactionId(), e.getMessage());
            throw e;
        }
    }
}
