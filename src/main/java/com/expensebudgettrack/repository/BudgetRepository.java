package com.expensebudgettrack.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Repository
public class BudgetRepository {
    private final JdbcTemplate jdbcTemplate;

    public BudgetRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public BigDecimal findAmount(long userId, int year, int month) {
        return jdbcTemplate.query(
                "SELECT amount_paise FROM budgets WHERE user_id = ? AND year = ? AND month = ?",
                (rs, rowNum) -> BigDecimal.valueOf(rs.getLong("amount_paise"), 2),
                userId, year, month).stream().findFirst().orElse(BigDecimal.ZERO.setScale(2));
    }

    public BigDecimal save(long userId, int year, int month, BigDecimal amount) {
        long paise = amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        jdbcTemplate.update(
                "INSERT INTO budgets(user_id, year, month, amount_paise) VALUES (?, ?, ?, ?) " +
                        "ON CONFLICT(user_id, year, month) DO UPDATE SET amount_paise = excluded.amount_paise",
                userId, year, month, paise);
        return Optional.of(findAmount(userId, year, month)).orElseThrow();
    }
}
