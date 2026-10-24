package com.expensebudgettrack.controller;

import com.expensebudgettrack.model.Category;
import com.expensebudgettrack.model.FinancialTransaction;
import com.expensebudgettrack.model.TransactionType;
import com.expensebudgettrack.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
    private final TransactionService transactionService;
    private final com.expensebudgettrack.repository.UserRepository userRepository;

    public TransactionController(TransactionService transactionService,
                                 com.expensebudgettrack.repository.UserRepository userRepository) {
        this.transactionService = transactionService;
        this.userRepository = userRepository;
    }

    @GetMapping
    List<FinancialTransaction> list(Authentication authentication,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(required = false) TransactionType type) {
        return transactionService.list(userId(authentication), from, to, type);
    }

    @PostMapping
    FinancialTransaction create(Authentication authentication, @Valid @RequestBody TransactionRequest request) {
        return transactionService.create(userId(authentication), request.type(), request.amount(),
                request.category(), request.description(), request.occurredOn());
    }

    @PutMapping("/{id}")
    FinancialTransaction update(Authentication authentication, @PathVariable long id,
                                @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(userId(authentication), id, request.type(), request.amount(),
                request.category(), request.description(), request.occurredOn());
    }

    @DeleteMapping("/{id}")
    void delete(Authentication authentication, @PathVariable long id) {
        transactionService.delete(userId(authentication), id);
    }

    private long userId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName()).orElseThrow().id();
    }

    public record TransactionRequest(@NotNull TransactionType type,
                                     @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "999999999999.99") BigDecimal amount,
                                     @NotNull Category category,
                                     @NotBlank @Size(max = 200) String description,
                                     @NotNull LocalDate occurredOn) {
    }
}
