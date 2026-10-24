package com.expensebudgettrack.repository;

import com.expensebudgettrack.model.Category;
import com.expensebudgettrack.model.FinancialTransaction;
import com.expensebudgettrack.model.TransactionType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class TransactionRepository {
    private static final RowMapper<FinancialTransaction> ROW_MAPPER = (rs, rowNum) -> new FinancialTransaction(
            rs.getLong("id"),
            rs.getLong("user_id"),
            TransactionType.valueOf(rs.getString("type")),
            BigDecimal.valueOf(rs.getLong("amount_paise"), 2),
            Category.valueOf(rs.getString("category")),
            rs.getString("description"),
            LocalDate.parse(rs.getString("occurred_on")));

    private final JdbcTemplate jdbcTemplate;

    public TransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FinancialTransaction> findForUser(long userId, LocalDate from, LocalDate to, TransactionType type) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, user_id, type, amount_paise, category, description, occurred_on FROM transactions WHERE user_id = ?");
        java.util.ArrayList<Object> parameters = new java.util.ArrayList<>();
        parameters.add(userId);
        if (from != null) {
            sql.append(" AND occurred_on >= ?");
            parameters.add(from.toString());
        }
        if (to != null) {
            sql.append(" AND occurred_on <= ?");
            parameters.add(to.toString());
        }
        if (type != null) {
            sql.append(" AND type = ?");
            parameters.add(type.name());
        }
        sql.append(" ORDER BY occurred_on DESC, id DESC");
        return jdbcTemplate.query(sql.toString(), ROW_MAPPER, parameters.toArray());
    }

    public Optional<FinancialTransaction> findForUser(long userId, long id) {
        return jdbcTemplate.query(
                "SELECT id, user_id, type, amount_paise, category, description, occurred_on " +
                        "FROM transactions WHERE user_id = ? AND id = ?",
                ROW_MAPPER, userId, id).stream().findFirst();
    }

    public FinancialTransaction create(long userId, TransactionType type, BigDecimal amount,
                                       Category category, String description, LocalDate occurredOn) {
        jdbcTemplate.update(
                "INSERT INTO transactions(user_id, type, amount_paise, category, description, occurred_on) VALUES (?, ?, ?, ?, ?, ?)",
                userId, type.name(), toPaise(amount), category.name(), description, occurredOn.toString());
        return findLatestForUser(userId);
    }

    public Optional<FinancialTransaction> update(long userId, long id, TransactionType type, BigDecimal amount,
                                                  Category category, String description, LocalDate occurredOn) {
        int changed = jdbcTemplate.update(
                "UPDATE transactions SET type = ?, amount_paise = ?, category = ?, description = ?, occurred_on = ? " +
                        "WHERE user_id = ? AND id = ?",
                type.name(), toPaise(amount), category.name(), description, occurredOn.toString(), userId, id);
        return changed == 0 ? Optional.empty() : findForUser(userId, id);
    }

    public boolean delete(long userId, long id) {
        return jdbcTemplate.update("DELETE FROM transactions WHERE user_id = ? AND id = ?", userId, id) > 0;
    }

    public BigDecimal totalForUser(long userId, TransactionType type, LocalDate from, LocalDate to) {
        Long paise = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount_paise), 0) FROM transactions " +
                        "WHERE user_id = ? AND type = ? AND occurred_on >= ? AND occurred_on <= ?",
                Long.class, userId, type.name(), from.toString(), to.toString());
        return BigDecimal.valueOf(paise == null ? 0L : paise, 2);
    }

    public List<CategoryTotal> expenseTotalsByCategory(long userId, LocalDate from, LocalDate to) {
        return jdbcTemplate.query(
                "SELECT category, SUM(amount_paise) AS total_paise FROM transactions " +
                        "WHERE user_id = ? AND type = 'EXPENSE' AND occurred_on >= ? AND occurred_on <= ? " +
                        "GROUP BY category ORDER BY total_paise DESC",
                (rs, rowNum) -> new CategoryTotal(
                        Category.valueOf(rs.getString("category")),
                        BigDecimal.valueOf(rs.getLong("total_paise"), 2)),
                userId, from.toString(), to.toString());
    }

    private FinancialTransaction findLatestForUser(long userId) {
        return jdbcTemplate.query(
                "SELECT id, user_id, type, amount_paise, category, description, occurred_on " +
                        "FROM transactions WHERE user_id = ? ORDER BY id DESC LIMIT 1",
                ROW_MAPPER, userId).getFirst();
    }

    private long toPaise(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
    }

    public record CategoryTotal(Category category, BigDecimal amount) {
    }
}
