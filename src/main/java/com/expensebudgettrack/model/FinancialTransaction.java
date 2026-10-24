package com.expensebudgettrack.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialTransaction(
        long id,
        long userId,
        TransactionType type,
        BigDecimal amount,
        Category category,
        String description,
        LocalDate occurredOn) {
}
