package com.expensebudgettrack.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    @Test
    void databaseFailureReturnsClearMessageWithoutDatabaseDetails() {
        var response = new ApiExceptionHandler().databaseUnavailable();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).containsEntry(
                "message", "The database is temporarily unavailable. Your data was not saved. Please try again.");
    }
}
