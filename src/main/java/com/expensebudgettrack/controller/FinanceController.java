package com.expensebudgettrack.controller;

import com.expensebudgettrack.repository.UserRepository;
import com.expensebudgettrack.service.FinancialService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.YearMonth;

@RestController
@RequestMapping("/api")
public class FinanceController {
    private final FinancialService financialService;
    private final UserRepository userRepository;

    public FinanceController(FinancialService financialService, UserRepository userRepository) {
        this.financialService = financialService;
        this.userRepository = userRepository;
    }

    @GetMapping("/dashboard")
    FinancialService.MonthlySummary dashboard(Authentication authentication,
                                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return financialService.summary(userId(authentication), month == null ? YearMonth.now() : month);
    }

    @GetMapping("/reports/monthly")
    FinancialService.MonthlySummary monthlyReport(Authentication authentication,
                                                   @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return financialService.summary(userId(authentication), month == null ? YearMonth.now() : month);
    }

    @GetMapping("/budgets")
    BudgetResponse budget(Authentication authentication,
                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        YearMonth selected = month == null ? YearMonth.now() : month;
        BigDecimal amount = financialService.summary(userId(authentication), selected).monthlyBudget();
        return new BudgetResponse(selected, amount);
    }

    @PutMapping("/budgets")
    BudgetResponse saveBudget(Authentication authentication, @Valid @RequestBody BudgetRequest request) {
        BigDecimal amount = financialService.saveBudget(userId(authentication), request.month(), request.amount());
        return new BudgetResponse(request.month(), amount);
    }

    private long userId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName()).orElseThrow().id();
    }

    public record BudgetRequest(@NotNull @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                @NotNull @DecimalMin(value = "0.01") @DecimalMax(value = "999999999999.99") BigDecimal amount) {
    }

    public record BudgetResponse(YearMonth month, BigDecimal amount) {
    }
}
