package com.expensebudgettrack.model;

public record UserAccount(long id, String email, String passwordHash) {
}
