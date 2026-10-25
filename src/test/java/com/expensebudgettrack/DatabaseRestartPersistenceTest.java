package com.expensebudgettrack;

import com.expensebudgettrack.repository.UserRepository;
import com.expensebudgettrack.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseRestartPersistenceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void registeredAccountAndPasswordHashSurviveApplicationRestart() {
        String databaseUrl = "jdbc:sqlite:" + temporaryDirectory.resolve("persistent-test.db").toAbsolutePath();
        String email = "restart-" + System.nanoTime() + "@example.com";
        String password = "A-strong-test-password-2026";

        try (ConfigurableApplicationContext firstRun = startApplication(databaseUrl)) {
            firstRun.getBean(AccountService.class).register(email, password);
        }

        try (ConfigurableApplicationContext secondRun = startApplication(databaseUrl)) {
            UserRepository userRepository = secondRun.getBean(UserRepository.class);
            PasswordEncoder passwordEncoder = secondRun.getBean(PasswordEncoder.class);
            var account = userRepository.findByEmail(email).orElseThrow();

            assertThat(account.passwordHash()).doesNotContain(password);
            assertThat(passwordEncoder.matches(password, account.passwordHash())).isTrue();
        }
    }

    private ConfigurableApplicationContext startApplication(String databaseUrl) {
        SpringApplication application = new SpringApplication(ExpenseBudgetTrackerApplication.class);
        return application.run(
                "--spring.datasource.url=" + databaseUrl,
                "--spring.sql.init.mode=always",
                "--server.port=0",
                "--spring.main.banner-mode=off");
    }
}
