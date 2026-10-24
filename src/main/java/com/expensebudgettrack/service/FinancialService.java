package com.expensebudgettrack.service;

import com.expensebudgettrack.model.TransactionType;
import com.expensebudgettrack.repository.BudgetRepository;
import com.expensebudgettrack.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class FinancialService {
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;

    public FinancialService(TransactionRepository transactionRepository, BudgetRepository budgetRepository) {
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
    }

    public MonthlySummary summary(long userId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        BigDecimal income = transactionRepository.totalForUser(userId, TransactionType.INCOME, start, end);
        BigDecimal expenses = transactionRepository.totalForUser(userId, TransactionType.EXPENSE, start, end);
        BigDecimal budget = budgetRepository.findAmount(userId, month.getYear(), month.getMonthValue());
        List<CategoryAmount> categories = transactionRepository.expenseTotalsByCategory(userId, start, end)
                .stream().map(total -> new CategoryAmount(total.category(), total.amount())).toList();
        return new MonthlySummary(month, income, expenses, budget, budget.subtract(expenses),
                income.subtract(expenses), categories);
    }

    public BigDecimal saveBudget(long userId, YearMonth month, BigDecimal amount) {
        return budgetRepository.save(userId, month.getYear(), month.getMonthValue(), amount);
    }

    public record CategoryAmount(com.expensebudgettrack.model.Category category, BigDecimal amount) {
    }

    public record MonthlySummary(YearMonth month, BigDecimal totalIncome, BigDecimal totalExpenses,
                                 BigDecimal monthlyBudget, BigDecimal remainingBudget, BigDecimal savings,
                                 List<CategoryAmount> categorySpending) {
    }
}
