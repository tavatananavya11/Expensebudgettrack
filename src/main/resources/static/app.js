const API = "/api";
const CATEGORY_LABELS = {
    FOOD: "Food",
    RENT: "Rent",
    TRAVEL: "Travel",
    SHOPPING: "Shopping",
    EDUCATION: "Education",
    HEALTHCARE: "Healthcare",
    BILLS: "Bills",
    SALARY: "Salary",
    OTHER: "Other"
};
const CATEGORY_COLORS = ["#4263eb", "#22a782", "#f2a64a", "#e8756b", "#856fe5", "#43a9c6", "#d78cba", "#7393cf", "#a4adba"];
const INR = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 2 });
const state = { account: null, accountVersion: 0, month: currentMonth(), transactions: [], chart: null, editingId: null, csrfToken: null };

const $ = (id) => document.getElementById(id);

function currentMonth() {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function monthBounds(month) {
    const [year, monthNumber] = month.split("-").map(Number);
    const lastDay = new Date(year, monthNumber, 0).getDate();
    return { from: `${month}-${"01"}`, to: `${month}-${String(lastDay).padStart(2, "0")}` };
}

function money(value) {
    return INR.format(Number(value || 0));
}

function prettyMonth(month) {
    const [year, monthNumber] = month.split("-").map(Number);
    return new Intl.DateTimeFormat("en-IN", { month: "long", year: "numeric" }).format(new Date(year, monthNumber - 1, 1));
}

function showNotice(message, isError = false) {
    const notice = $("notice");
    notice.textContent = message;
    notice.classList.toggle("error", isError);
    notice.classList.remove("hidden");
    window.clearTimeout(showNotice.timer);
    showNotice.timer = window.setTimeout(() => notice.classList.add("hidden"), 4500);
}

async function responseMessage(response) {
    try {
        const body = await response.json();
        return body.message || body.detail || `Request failed (${response.status})`;
    } catch {
        return `Request failed (${response.status})`;
    }
}

async function fetchCsrf() {
    const response = await fetch(`${API}/auth/csrf`, { credentials: "same-origin" });
    if (!response.ok) throw new Error(await responseMessage(response));
    const body = await response.json();
    state.csrfToken = body.token;
}

async function api(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (options.body !== undefined) headers.set("Content-Type", "application/json");
    if (["POST", "PUT", "DELETE", "PATCH"].includes((options.method || "GET").toUpperCase())) {
        if (!state.csrfToken) await fetchCsrf();
        headers.set("X-XSRF-TOKEN", state.csrfToken);
    }
    const response = await fetch(`${API}${path}`, {
        ...options,
        headers,
        credentials: "same-origin"
    });
    if (response.status === 401) {
        const wasSignedIn = Boolean(state.account);
        if (wasSignedIn) {
            clearAccountState();
            showAuth(false);
            $("auth-error").textContent = "Your session ended. Please sign in again.";
            $("auth-error").classList.remove("hidden");
        }
        const message = wasSignedIn ? "Your session ended. Please sign in again." : await responseMessage(response);
        const error = new Error(message);
        error.status = 401;
        throw error;
    }
    if (!response.ok) throw new Error(await responseMessage(response));
    if (response.status === 204) return null;
    const contentType = response.headers.get("content-type") || "";
    return contentType.includes("application/json") ? response.json() : null;
}

function showAuth(register = false) {
    $("app-view").classList.add("hidden");
    $("auth-view").classList.remove("hidden");
    $("auth-title").textContent = register ? "Create your account" : "Welcome back";
    $("auth-subtitle").textContent = register
        ? "Start building a clearer picture of your finances."
        : "Sign in to see your monthly snapshot.";
    $("auth-password").autocomplete = register ? "new-password" : "current-password";
    $("auth-password").minLength = register ? 10 : 1;
    $("auth-submit").textContent = register ? "Create account" : "Sign in";
    $("auth-switch-copy").textContent = register ? "Already have an account?" : "New to PaisaPlan?";
    $("auth-switch").textContent = register ? "Sign in" : "Sign up";
    $("auth-error").classList.add("hidden");
    $("auth-form").dataset.mode = register ? "register" : "login";
}

function showDashboard(account) {
    state.accountVersion += 1;
    state.account = account;
    $("auth-view").classList.add("hidden");
    $("app-view").classList.remove("hidden");
    $("account-email").textContent = account.email;
    $("account-avatar").textContent = (account.email || "U").slice(0, 1).toUpperCase();
    $("month-picker").value = state.month;
    loadDashboard();
}

function clearAccountState() {
    state.accountVersion += 1;
    state.account = null;
    state.transactions = [];
    if (state.chart) {
        state.chart.destroy();
        state.chart = null;
    }
    $("account-email").textContent = "";
    $("account-avatar").textContent = "";
    $("transaction-rows").replaceChildren();
    $("category-legend").replaceChildren();
}

async function loadDashboard() {
    const requestVersion = state.accountVersion;
    $("loading").classList.remove("hidden");
    $("dashboard-content").classList.add("hidden");
    try {
        const bounds = monthBounds(state.month);
        const [summary, transactions] = await Promise.all([
            api(`/dashboard?month=${encodeURIComponent(state.month)}`),
            api(`/transactions?from=${bounds.from}&to=${bounds.to}`)
        ]);
        if (requestVersion !== state.accountVersion || !state.account) return;
        state.transactions = transactions;
        renderDashboard(summary, transactions);
        $("loading").classList.add("hidden");
        $("dashboard-content").classList.remove("hidden");
    } catch (error) {
        if (requestVersion !== state.accountVersion) return;
        $("loading").classList.add("hidden");
        if (!state.account) return;
        showNotice(error.message, true);
    }
}

function renderDashboard(summary, transactions) {
    $("report-month-label").textContent = prettyMonth(state.month);
    $("welcome-heading").textContent = `${prettyMonth(state.month)} at a glance`;
    $("total-income").textContent = money(summary.totalIncome);
    $("total-expenses").textContent = money(summary.totalExpenses);
    $("monthly-budget").textContent = money(summary.monthlyBudget);
    $("remaining-budget").textContent = money(summary.remainingBudget);
    $("monthly-savings").textContent = money(summary.savings);
    $("remaining-caption").textContent = Number(summary.remainingBudget) < 0 ? "Over your planned budget" : "Available for this month";
    $("budget-spent").textContent = money(summary.totalExpenses);
    $("budget-total").textContent = money(summary.monthlyBudget);
    renderBudgetProgress(summary.totalExpenses, summary.monthlyBudget);
    renderChart(summary.categorySpending || []);
    renderTransactions(transactions);
}

function renderBudgetProgress(spentValue, budgetValue) {
    const spent = Number(spentValue || 0);
    const budget = Number(budgetValue || 0);
    const percentage = budget > 0 ? (spent / budget) * 100 : 0;
    const bar = $("budget-progress");
    bar.style.width = `${Math.min(percentage, 100)}%`;
    bar.classList.toggle("over-budget", percentage > 100);
    if (budget === 0) {
        $("budget-progress-label").textContent = "Set a monthly budget to track your progress.";
    } else if (percentage > 100) {
        $("budget-progress-label").textContent = `You are ${Math.round(percentage - 100)}% over this month's budget.`;
    } else {
        $("budget-progress-label").textContent = `${Math.round(percentage)}% of your monthly budget used.`;
    }
    $("budget-input").value = budget > 0 ? budget.toFixed(2) : "";
}

function renderChart(categorySpending) {
    const hasItems = categorySpending.length > 0;
    $("chart-empty").classList.toggle("hidden", hasItems);
    $("chart-unavailable").classList.add("hidden");
    $("category-legend").replaceChildren();
    if (state.chart) {
        state.chart.destroy();
        state.chart = null;
    }
    if (!hasItems) return;
    if (typeof Chart === "undefined") {
        $("chart-unavailable").classList.remove("hidden");
        return;
    }
    const labels = categorySpending.map((item) => CATEGORY_LABELS[item.category] || item.category);
    const values = categorySpending.map((item) => Number(item.amount));
    const colors = categorySpending.map((_, index) => CATEGORY_COLORS[index % CATEGORY_COLORS.length]);
    const canvas = $("category-chart");
    state.chart = new Chart(canvas, {
        type: "doughnut",
        data: { labels, datasets: [{ data: values, backgroundColor: colors, borderWidth: 0, hoverOffset: 5 }] },
        options: {
            responsive: true,
            maintainAspectRatio: true,
            cutout: "68%",
            plugins: {
                legend: { display: false },
                tooltip: { callbacks: { label: (context) => ` ${context.label}: ${money(context.raw)}` } }
            }
        }
    });
    categorySpending.forEach((item, index) => {
        const legendItem = document.createElement("span");
        legendItem.className = "legend-item";
        const dot = document.createElement("span");
        dot.className = "legend-dot";
        dot.style.backgroundColor = colors[index];
        const label = document.createElement("span");
        label.textContent = CATEGORY_LABELS[item.category] || item.category;
        legendItem.append(dot, label);
        $("category-legend").append(legendItem);
    });
}

function renderTransactions(transactions) {
    const tbody = $("transaction-rows");
    tbody.replaceChildren();
    $("transactions-empty").classList.toggle("hidden", transactions.length > 0);
    $("transaction-rows").closest("table").classList.toggle("hidden", transactions.length === 0);
    transactions.forEach((transaction) => {
        const row = document.createElement("tr");
        const nameCell = document.createElement("td");
        const name = document.createElement("span");
        name.className = "transaction-name";
        const symbol = document.createElement("span");
        symbol.className = "transaction-symbol";
        symbol.textContent = transaction.type === "INCOME" ? "↗" : "↘";
        const description = document.createElement("span");
        description.textContent = transaction.description;
        name.append(symbol, description);
        nameCell.append(name);
        const categoryCell = document.createElement("td");
        const category = document.createElement("span");
        category.className = "category-pill";
        category.textContent = CATEGORY_LABELS[transaction.category] || transaction.category;
        categoryCell.append(category);
        const dateCell = document.createElement("td");
        dateCell.textContent = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" })
            .format(new Date(`${transaction.occurredOn}T00:00:00`));
        const amountCell = document.createElement("td");
        amountCell.className = transaction.type === "INCOME" ? "amount-income" : "amount-expense";
        amountCell.textContent = `${transaction.type === "INCOME" ? "+" : "−"}${money(transaction.amount)}`;
        const actionsCell = document.createElement("td");
        const actions = document.createElement("div");
        actions.className = "row-actions";
        const edit = document.createElement("button");
        edit.type = "button";
        edit.className = "row-action";
        edit.textContent = "Edit";
        edit.setAttribute("aria-label", `Edit ${transaction.description}`);
        edit.addEventListener("click", () => openTransactionDialog(transaction));
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "row-action delete";
        remove.textContent = "Delete";
        remove.setAttribute("aria-label", `Delete ${transaction.description}`);
        remove.addEventListener("click", () => deleteTransaction(transaction));
        actions.append(edit, remove);
        actionsCell.append(actions);
        row.append(nameCell, categoryCell, dateCell, amountCell, actionsCell);
        tbody.append(row);
    });
}

function openTransactionDialog(transaction = null) {
    state.editingId = transaction ? transaction.id : null;
    $("dialog-title").textContent = transaction ? "Edit transaction" : "Add transaction";
    $("save-transaction").textContent = transaction ? "Save changes" : "Save transaction";
    $("transaction-type").value = transaction?.type || "EXPENSE";
    $("transaction-amount").value = transaction ? Number(transaction.amount).toFixed(2) : "";
    $("transaction-category").value = transaction?.category || "FOOD";
    $("transaction-description").value = transaction?.description || "";
    $("transaction-date").value = transaction?.occurredOn || new Date().toISOString().slice(0, 10);
    $("transaction-error").classList.add("hidden");
    $("transaction-dialog").showModal();
}

async function deleteTransaction(transaction) {
    if (!window.confirm(`Delete "${transaction.description}"? This cannot be undone.`)) return;
    try {
        await api(`/transactions/${transaction.id}`, { method: "DELETE" });
        showNotice("Transaction deleted.");
        await loadDashboard();
    } catch (error) {
        showNotice(error.message, true);
    }
}

async function submitAuth(event) {
    event.preventDefault();
    const error = $("auth-error");
    error.classList.add("hidden");
    const mode = $("auth-form").dataset.mode;
    const credentials = { email: $("auth-email").value.trim(), password: $("auth-password").value };
    try {
        await fetchCsrf();
        if (mode === "register") {
            await api("/auth/register", { method: "POST", body: JSON.stringify(credentials) });
            await fetchCsrf();
            const account = await api("/auth/login", { method: "POST", body: JSON.stringify(credentials) });
            await fetchCsrf();
            showDashboard(account);
            showNotice("Your account is ready. You are signed in.");
        } else {
            const account = await api("/auth/login", { method: "POST", body: JSON.stringify(credentials) });
            await fetchCsrf();
            showDashboard(account);
        }
    } catch (requestError) {
        error.textContent = requestError.message;
        error.classList.remove("hidden");
    }
}

async function saveTransaction(event) {
    event.preventDefault();
    const error = $("transaction-error");
    error.classList.add("hidden");
    const transaction = {
        type: $("transaction-type").value,
        amount: Number($("transaction-amount").value),
        category: $("transaction-category").value,
        description: $("transaction-description").value.trim(),
        occurredOn: $("transaction-date").value
    };
    try {
        const path = state.editingId === null ? "/transactions" : `/transactions/${state.editingId}`;
        await api(path, { method: state.editingId === null ? "POST" : "PUT", body: JSON.stringify(transaction) });
        $("transaction-dialog").close();
        showNotice(state.editingId === null ? "Transaction added." : "Transaction updated.");
        await loadDashboard();
    } catch (requestError) {
        error.textContent = requestError.message;
        error.classList.remove("hidden");
    }
}

async function saveBudget(event) {
    event.preventDefault();
    try {
        await api("/budgets", {
            method: "PUT",
            body: JSON.stringify({ month: state.month, amount: Number($("budget-input").value) })
        });
        showNotice("Monthly budget saved.");
        await loadDashboard();
    } catch (error) {
        showNotice(error.message, true);
    }
}

async function logout() {
    try {
        await api("/auth/logout", { method: "POST" });
        clearAccountState();
        await fetchCsrf();
        showAuth(false);
    } catch (error) {
        showNotice(error.message, true);
    }
}

async function start() {
    $("month-picker").value = state.month;
    $("auth-form").addEventListener("submit", submitAuth);
    $("auth-switch").addEventListener("click", () => {
        showAuth($("auth-form").dataset.mode !== "register");
    });
    $("month-picker").addEventListener("change", (event) => {
        if (!event.target.value) return;
        state.month = event.target.value;
        loadDashboard();
    });
    $("add-transaction").addEventListener("click", () => openTransactionDialog());
    $("add-transaction-secondary").addEventListener("click", () => openTransactionDialog());
    $("empty-add-transaction").addEventListener("click", () => openTransactionDialog());
    $("transaction-form").addEventListener("submit", saveTransaction);
    $("budget-form").addEventListener("submit", saveBudget);
    $("logout-button").addEventListener("click", logout);
    $("close-dialog").addEventListener("click", () => $("transaction-dialog").close());
    $("cancel-dialog").addEventListener("click", () => $("transaction-dialog").close());

    try {
        await fetchCsrf();
        const account = await api("/auth/me");
        showDashboard(account);
    } catch (error) {
        showAuth(false);
        if (error.status !== 401) {
            $("auth-error").textContent = error.message || "Could not reach the application server.";
            $("auth-error").classList.remove("hidden");
        }
    }
}

document.addEventListener("DOMContentLoaded", start);
