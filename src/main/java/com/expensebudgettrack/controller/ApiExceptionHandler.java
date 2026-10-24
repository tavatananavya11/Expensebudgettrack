package com.expensebudgettrack.controller;

import com.expensebudgettrack.service.AccountService;
import com.expensebudgettrack.service.TransactionService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> invalidInput(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getDefaultMessage()).orElse("Please check your input");
        return ResponseEntity.badRequest().body(Map.of("message", message));
    }

    @ExceptionHandler(AuthController.InvalidLoginException.class)
    ResponseEntity<Map<String, String>> invalidLogin() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Email or password is incorrect. New users can choose Sign up."));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalidArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }

    @ExceptionHandler(AccountService.EmailAlreadyRegisteredException.class)
    ResponseEntity<Map<String, String>> duplicateEmail() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "An account with this email already exists"));
    }

    @ExceptionHandler(TransactionService.TransactionNotFoundException.class)
    ResponseEntity<Map<String, String>> missingTransaction() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Transaction not found"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> invalidStoredValue() {
        return ResponseEntity.badRequest().body(Map.of("message", "The submitted value is not valid"));
    }
}
