package com.expensebudgettrack.repository;

import com.expensebudgettrack.model.UserAccount;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {
    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UserAccount> findByEmail(String email) {
        return jdbcTemplate.query(
                "SELECT id, email, password_hash FROM users WHERE email = ?",
                (rs, rowNum) -> new UserAccount(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("password_hash")),
                email).stream().findFirst();
    }

    public UserAccount create(String email, String passwordHash) {
        jdbcTemplate.update("INSERT INTO users(email, password_hash) VALUES (?, ?)", email, passwordHash);
        return findByEmail(email).orElseThrow(() -> new IllegalStateException("Created account could not be loaded"));
    }
}
