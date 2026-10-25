package com.expensebudgettrack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:sqlite:file:expense-budget-test?mode=memory&cache=shared")
@AutoConfigureMockMvc
class AccountIsolationIntegrationTest {
    private static final String PASSWORD = "A-strong-test-password-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void accountsCanOnlyReadAndChangeTheirOwnFinancialData() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String firstEmail = "first-" + suffix + "@example.com";
        String secondEmail = "second-" + suffix + "@example.com";
        YearMonth month = YearMonth.now();
        LocalDate date = month.atDay(10);

        register(firstEmail);
        MockHttpSession firstSession = login(firstEmail);
        long expenseId = createTransaction(firstSession, "EXPENSE", "3000.00", "FOOD", "Groceries", date);
        createTransaction(firstSession, "INCOME", "10000.00", "SALARY", "Monthly pay", date);
        saveBudget(firstSession, month, "5000.00");

        mockMvc.perform(get("/api/dashboard").param("month", month.toString()).session(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(10000.00))
                .andExpect(jsonPath("$.totalExpenses").value(3000.00))
                .andExpect(jsonPath("$.monthlyBudget").value(5000.00))
                .andExpect(jsonPath("$.remainingBudget").value(2000.00))
                .andExpect(jsonPath("$.savings").value(7000.00))
                .andExpect(jsonPath("$.categorySpending[0].category").value("FOOD"));

        register(secondEmail);
        MockHttpSession secondSession = login(secondEmail);
        mockMvc.perform(get("/api/transactions").session(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/dashboard").param("month", month.toString()).session(secondSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIncome").value(0))
                .andExpect(jsonPath("$.totalExpenses").value(0))
                .andExpect(jsonPath("$.monthlyBudget").value(0));

        updateTransaction(secondSession, expenseId, "EXPENSE", "3250.00", "FOOD", "Not mine", date)
                .andExpect(status().isNotFound());
        deleteTransaction(secondSession, expenseId).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/transactions")
                        .session(secondSession)
                        .contentType("application/json")
                        .content(transactionJson("EXPENSE", "-1.00", "FOOD", "Invalid amount", date)))
                .andExpect(status().isForbidden());

        updateTransaction(firstSession, expenseId, "EXPENSE", "3250.00", "FOOD", "Groceries", date)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(3250.00));
        mockMvc.perform(get("/api/dashboard").param("month", month.toString()).session(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses").value(3250.00))
                .andExpect(jsonPath("$.remainingBudget").value(1750.00));

        deleteTransaction(firstSession, expenseId).andExpect(status().isOk());
        mockMvc.perform(get("/api/transactions").session(firstSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = ?", String.class, firstEmail);
        assertThat(passwordHash).isNotEqualTo(PASSWORD).startsWith("$2");
    }

    @Test
    void dashboardAndFrontendAreAccessibleWithoutSigningInButFinancialApiIsNot() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
        mockMvc.perform(get("/styles.css")).andExpect(status().isOk());
        mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
        mockMvc.perform(withCsrf(post("/api/auth/login"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new Registration("not-registered@example.com", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "Email or password is incorrect. New users can choose Sign up."));
    }

    @Test
    void signingIntoAnotherAccountInTheSameBrowserSessionShowsOnlyThatAccountsData() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String firstEmail = "switch-first-" + suffix + "@example.com";
        String secondEmail = "switch-second-" + suffix + "@example.com";
        YearMonth month = YearMonth.now();

        register(firstEmail);
        register(secondEmail);

        MockHttpSession browserSession = login(firstEmail);
        createTransaction(browserSession, "EXPENSE", "4200.00", "RENT", "First account rent", month.atDay(3));
        saveBudget(browserSession, month, "9000.00");

        MockHttpSession switchedSession = login(secondEmail, browserSession);
        assertThat(switchedSession).isSameAs(browserSession);
        mockMvc.perform(get("/api/auth/me").session(switchedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(secondEmail));
        mockMvc.perform(get("/api/transactions").session(switchedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/api/dashboard").param("month", month.toString()).session(switchedSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses").value(0))
                .andExpect(jsonPath("$.monthlyBudget").value(0));

        MockHttpSession switchedBackSession = login(firstEmail, switchedSession);
        mockMvc.perform(get("/api/dashboard").param("month", month.toString()).session(switchedBackSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses").value(4200.00))
                .andExpect(jsonPath("$.monthlyBudget").value(9000.00));
    }

    @Test
    void duplicateEmailRegistrationIsCaseInsensitiveAndLoginDoesNotCreateAccounts() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.com";
        register(email);

        mockMvc.perform(withCsrf(post("/api/auth/register"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new Registration(email.toUpperCase(Locale.ROOT), PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with this email already exists"));

        long accountsBeforeLogin = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        mockMvc.perform(withCsrf(post("/api/auth/login"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(
                                new Registration("missing-" + UUID.randomUUID() + "@example.com", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "Email or password is incorrect. New users can choose Sign up."));
        mockMvc.perform(withCsrf(post("/api/auth/login"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Registration(email, "Wrong-password-2026"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(
                        "Email or password is incorrect. New users can choose Sign up."));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class))
                .isEqualTo(accountsBeforeLogin);

        MockHttpSession session = login(email.toUpperCase(Locale.ROOT));
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void logoutThenLoginAgainRestoresTheOriginalAccountAndItsData() throws Exception {
        String email = "relogin-" + UUID.randomUUID() + "@example.com";
        YearMonth month = YearMonth.now();
        register(email);
        MockHttpSession session = login(email);
        createTransaction(session, "EXPENSE", "875.00", "FOOD", "Lunch", month.atDay(5));
        saveBudget(session, month, "2500.00");

        mockMvc.perform(withCsrf(post("/api/auth/logout"), session))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());

        MockHttpSession newSession = login(email);
        mockMvc.perform(get("/api/dashboard").param("month", month.toString()).session(newSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses").value(875.00))
                .andExpect(jsonPath("$.monthlyBudget").value(2500.00));
    }

    private void register(String email) throws Exception {
        mockMvc.perform(withCsrf(post("/api/auth/register"))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Registration(email, PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));
    }

    private MockHttpSession login(String email) throws Exception {
        return login(email, null);
    }

    private MockHttpSession login(String email, MockHttpSession existingSession) throws Exception {
        String normalizedEmail = email.toLowerCase(Locale.ROOT);
        MvcResult result = mockMvc.perform(withCsrf(post("/api/auth/login"), existingSession)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Registration(email, PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(normalizedEmail))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private long createTransaction(MockHttpSession session, String type, String amount, String category,
                                   String description, LocalDate date) throws Exception {
        MvcResult result = mockMvc.perform(withCsrf(post("/api/transactions"), session)
                        .contentType("application/json")
                        .content(transactionJson(type, amount, category, description, date)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions updateTransaction(
            MockHttpSession session, long id, String type, String amount, String category,
            String description, LocalDate date) throws Exception {
        return mockMvc.perform(withCsrf(put("/api/transactions/{id}", id), session)
                .contentType("application/json")
                .content(transactionJson(type, amount, category, description, date)));
    }

    private org.springframework.test.web.servlet.ResultActions deleteTransaction(MockHttpSession session, long id)
            throws Exception {
        return mockMvc.perform(withCsrf(delete("/api/transactions/{id}", id), session));
    }

    private void saveBudget(MockHttpSession session, YearMonth month, String amount) throws Exception {
        mockMvc.perform(withCsrf(put("/api/budgets"), session)
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new Budget(month, new BigDecimal(amount)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(new BigDecimal(amount).doubleValue()));
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request) throws Exception {
        return withCsrf(request, null);
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder request, MockHttpSession session)
            throws Exception {
        MockHttpServletRequestBuilder csrfRequest = get("/api/auth/csrf");
        if (session != null) {
            csrfRequest.session(session);
        }
        MvcResult result = mockMvc.perform(csrfRequest).andExpect(status().isOk()).andReturn();
        JsonNode tokenBody = objectMapper.readTree(result.getResponse().getContentAsString());
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        request.cookie(cookie).header("X-XSRF-TOKEN", tokenBody.get("token").asText());
        if (session != null) {
            request.session(session);
        }
        return request;
    }

    private String transactionJson(String type, String amount, String category, String description, LocalDate date)
            throws Exception {
        return objectMapper.writeValueAsString(new Transaction(type, new BigDecimal(amount), category, description, date));
    }

    private record Registration(String email, String password) {
    }

    private record Transaction(String type, BigDecimal amount, String category, String description, LocalDate occurredOn) {
    }

    private record Budget(YearMonth month, BigDecimal amount) {
    }
}
