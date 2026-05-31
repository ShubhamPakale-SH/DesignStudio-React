package com.securebank.account.repository;

import com.securebank.account.model.TransactionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionLogRepository extends JpaRepository<TransactionLog, String> {
}
