package com.expensebudgettrack.service;

import com.expensebudgettrack.model.UserAccount;
import com.expensebudgettrack.repository.UserRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AccountService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserAccount register(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        try {
            return userRepository.create(normalizedEmail, passwordEncoder.encode(password));
        } catch (DataAccessException exception) {
            if (userRepository.findByEmail(normalizedEmail).isPresent()) {
                throw new EmailAlreadyRegisteredException();
            }
            throw exception;
        }
    }

    public UserAccount findByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow();
    }

    public static class EmailAlreadyRegisteredException extends RuntimeException {
    }
}
