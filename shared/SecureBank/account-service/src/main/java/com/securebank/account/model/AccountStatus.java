package com.securebank.account.model;

/**
 * Lifecycle status of an account.
 * FROZEN accounts reject WITHDRAW/TRANSFER but still accept DEPOSIT.
 */
public enum AccountStatus {
    ACTIVE,
    FROZEN
}
