package com.securebank.transaction.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * HTTP request body shape — validated by Bean Validation.
 * Mapped into the Avro {@code com.securebank.avro.Transaction} before publishing to Kafka.
 *
 * Separation rationale: clients speak JSON over HTTP; the Kafka wire format is Avro.
 * Coupling the request schema to the wire schema would force clients to ship Avro libraries.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransactionRequest {

    private String transactionId;

    @NotBlank
    private String accountId;

    @NotNull
    private TransactionType type;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    private String city;

    public enum TransactionType {
        DEPOSIT, WITHDRAW, TRANSFER
    }
}
