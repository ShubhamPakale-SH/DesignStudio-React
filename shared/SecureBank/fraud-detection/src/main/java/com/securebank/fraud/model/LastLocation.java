package com.securebank.fraud.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Per-account last-seen location held in the Streams state store.
 * Backed by an internal changelog topic for fault tolerance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LastLocation {

    private String city;
    private Instant timestamp;
}
