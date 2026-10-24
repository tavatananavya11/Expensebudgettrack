package com.expensebudgettrack.controller;

import com.expensebudgettrack.model.UserAccount;
import com.expensebudgettrack.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accountService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();

    public AuthController(AccountService accountService, AuthenticationManager authenticationManager) {
        this.accountService = accountService;
        this.authenticationManager = authenticationManager;
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken());
    }

    @PostMapping("/register")
    ResponseEntity<AccountResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserAccount account = accountService.register(request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(new AccountResponse(account.id(), account.email()));
    }

    @PostMapping("/login")
    AccountResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest,
                          HttpServletResponse servletResponse) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email().trim().toLowerCase(Locale.ROOT), request.password()));
        } catch (BadCredentialsException exception) {
            throw new InvalidLoginException();
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        servletRequest.getSession(true);
        servletRequest.changeSessionId();
        securityContextRepository.saveContext(context, servletRequest, servletResponse);
        UserAccount account = accountService.findByEmail(authentication.getName());
        return new AccountResponse(account.id(), account.email());
    }

    @GetMapping("/me")
    AccountResponse me(Authentication authentication) {
        UserAccount account = accountService.findByEmail(authentication.getName());
        return new AccountResponse(account.id(), account.email());
    }

    public record RegisterRequest(@NotBlank @Email @Size(max = 254) String email,
                                  @NotBlank @Size(min = 10, max = 72) String password) {
    }

    public record LoginRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank String password) {
    }

    public record AccountResponse(long id, String email) {
    }

    static class InvalidLoginException extends RuntimeException {
    }
}
