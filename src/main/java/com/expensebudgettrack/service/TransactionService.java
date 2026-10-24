package com.expensebudgettrack.service;

import com.expensebudgettrack.model.Category;
import com.expensebudgettrack.model.FinancialTransaction;
import com.expensebudgettrack.model.TransactionType;
import com.expensebudgettrack.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class TransactionService {
    private final TransactionRepository repository;

    public TransactionService(TransactionRepository repository) {
        this.repository = repository;
    }

    public List<FinancialTransaction> list(long userId, LocalDate from, LocalDate to, TransactionType type) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Start date must be on or before end date");
        }
        return repository.findForUser(userId, from, to, type);
    }

    public FinancialTransaction create(long userId, TransactionType type, BigDecimal amount,
                                       Category category, String description, LocalDate occurredOn) {
        return repository.create(userId, type, amount, category, description.trim(), occurredOn);
    }

    public FinancialTransaction update(long userId, long id, TransactionType type, BigDecimal amount,
                                       Category category, String description, LocalDate occurredOn) {
        return repository.update(userId, id, type, amount, category, description.trim(), occurredOn)
                .orElseThrow(TransactionNotFoundException::new);
    }

    public void delete(long userId, long id) {
        if (!repository.delete(userId, id)) {
            throw new TransactionNotFoundException();
        }
    }

    public static class TransactionNotFoundException extends RuntimeException {
    }
}
