package com.securebank.transaction.controller;

import com.securebank.avro.Transaction;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@TestPropertySource(properties = {
        "app.topic.transactions=transactions",
        "app.schema-registry-url=mock://test"
})
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KafkaTemplate<String, Transaction> kafkaTemplate;

    @Test
    @DisplayName("Valid transaction → 201 Created with partition + offset + PERSISTED status; published keyed by accountId")
    void validTransaction_isCreated_andPublishedKeyedByAccountId() throws Exception {
        RecordMetadata metadata = new RecordMetadata(
                new TopicPartition("transactions", 1),
                0L, 42, System.currentTimeMillis(), 0, 0
        );
        SendResult<String, Transaction> sendResult = new SendResult<>(
                new ProducerRecord<>("transactions", "ACC1001", null),
                metadata
        );
        CompletableFuture<SendResult<String, Transaction>> future =
                CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(anyString(), anyString(), any(Transaction.class)))
                .thenReturn(future);

        String body = """
                {
                  "accountId": "ACC1001",
                  "type": "DEPOSIT",
                  "amount": 250.00,
                  "city": "Mumbai"
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PERSISTED"))
                .andExpect(jsonPath("$.transactionId").exists())
                .andExpect(jsonPath("$.partition").value(1))
                .andExpect(jsonPath("$.offset").value(42));

        verify(kafkaTemplate).send(eq("transactions"), eq("ACC1001"), any(Transaction.class));
    }

    @Test
    @DisplayName("Missing accountId + negative amount → 400 Bad Request")
    void invalidTransaction_isRejected() throws Exception {
        String invalidBody = """
                {
                  "type": "DEPOSIT",
                  "amount": -50.00
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Kafka publish failure → 503 Service Unavailable with FAILED status")
    void kafkaFailure_returns503() throws Exception {
        CompletableFuture<SendResult<String, Transaction>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Broker not available"));
        when(kafkaTemplate.send(anyString(), anyString(), any(Transaction.class)))
                .thenReturn(future);

        String body = """
                {
                  "accountId": "ACC1001",
                  "type": "DEPOSIT",
                  "amount": 250.00
                }
                """;

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").exists());
    }
}
