package com.securebank.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Per-account rolling statistics held in the Streams state store.
 * Backed by an internal changelog topic for fault tolerance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RollingStats {

    private BigDecimal sum = BigDecimal.ZERO;
    private long count = 0L;

    public void add(BigDecimal amount) {
        this.sum = this.sum.add(amount);
        this.count++;
    }

    public BigDecimal getAverage() {
        if (count == 0L) {
            return BigDecimal.ZERO;
        }
        return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
