# PaisaPlan — Expense & Budget Tracker

A personal finance website built with Java 21, Spring Boot, Maven, SQLite, HTML, CSS, JavaScript, and Chart.js. It displays amounts in Indian Rupees (₹ INR), keeps every account's records separate, and stores data in a local SQLite database.

## What you can do

- Sign up with a separate email address for each person, sign in, and sign out to switch accounts.
- Record, view, edit, and delete income and expenses.
- Use Food, Rent, Travel, Shopping, Education, Healthcare, Bills, Salary, and Other categories.
- Set an individual budget for each month.
- View monthly income, expenses, budget, remaining budget, savings, and category totals.
- View a category spending chart and a monthly activity list.
- Use the dashboard on desktop, tablet, or phone screens.

## Requirements

- Java 21
- Maven 3.9 or later
- Internet access the first time Maven downloads dependencies

Node.js, npm, and the SQLite command-line application are not required. Maven downloads the SQLite JDBC driver. Chart.js and the optional web fonts are loaded from CDNs, so an internet connection is needed to display the doughnut chart and those fonts. The dashboard and financial records still work if a CDN is unavailable.

## 1. Open the project

Open the `Expensebudgettrack` folder in VS Code. In the VS Code terminal, check that Java and Maven are available:

```powershell
java -version
mvn -version
```

Both commands should report Java 21 and Maven 3.9 or later.

## 2. Run the application

From the folder containing `pom.xml`, run:

```powershell
mvn spring-boot:run
```

Wait until the terminal says that Tomcat started, then open:

```text
http://localhost:8080
```

Create an account with an email address and a password of at least 10 characters. The app signs you in after registration. Then add transactions and set a monthly budget.

To stop the local server, press **Ctrl+C** in the terminal where Maven is running.

## 3. Run tests and build a JAR

Run the automated tests:

```powershell
mvn test
```

The tests start the Spring application and verify sign-up, password hashing, CSRF protection, account isolation (including switching accounts in the same browser session), transaction ownership, budget calculations, and public/private routes.

Build a runnable JAR:

```powershell
mvn clean package
```

Start the packaged application:

```powershell
java -jar target\expense-budget-tracker-1.0.0.jar
```

Then visit `http://localhost:8080`.

## Database and privacy — important

By default, SQLite stores data in `expense-tracker.db` in the directory where the application is started. The database file is created automatically and persists when the application stops. Back it up while the app is stopped by copying that file to a safe location.

Each account owns its own transactions and budgets. Every data query is restricted to the signed-in account. A different account cannot view or edit another account's records, even if it guesses a transaction ID. Passwords are stored as BCrypt hashes, not as plain text. The website uses an authenticated server session and protects state-changing requests against CSRF.

To use a different person's account in the same browser, choose **Sign out** beside the current account, then sign in with that person's email and password. Choose **Sign up** on the sign-in screen to create a new, separate account; registering does not merge or copy another account's records. On a shared computer, always sign out before handing the browser to someone else.

**A local SQLite file does not synchronize between phones or computers.** If Phone 1 and Phone 2 connect to separate local installations, each installation has its own database. To share data across devices, deploy one backend to a server that both devices can reach and configure that backend to use one shared, persistent database. Then sign in to the same account on both devices. Different accounts on that server will still have separate data. For an internet-facing or multi-instance deployment, use HTTPS, secure cookies, database backups, and a production database/service appropriate for the deployment; do not expose a development server directly to the public internet.

### Choose a different database file

Set `DATABASE_URL` before starting Maven. Use a path that already exists or a folder you have permission to write to:

```powershell
$env:DATABASE_URL = 'jdbc:sqlite:C:\Users\YourName\Documents\expense-tracker.db'
mvn spring-boot:run
```

The setting applies to that PowerShell window. To return to the default database path:

```powershell
$env:DATABASE_URL = $null
```

Keep the database file private and do not upload it to a public repository.

## How the project is organized

```text
pom.xml
src\
  main\
    java\com\expensebudgettrack\
      controller\       REST API endpoints and API error responses
      model\            Account, transaction, and category types
      repository\       SQLite database access
      security\         Session-based Spring Security configuration
      service\          Account, transaction, and calculation logic
    resources\
      application.properties
      schema.sql
      static\
        index.html      Dashboard and sign-in/register screens
        styles.css      Responsive layout and visual design
        app.js          API requests, forms, INR formatting, and chart
  test\
    java\com\expensebudgettrack\
      AccountIsolationIntegrationTest.java
      ExpenseBudgetTrackerApplicationTest.java
```

Amounts are received as decimal rupees and stored in SQLite as integer paise to avoid floating-point rounding errors. The browser formats numbers using the Indian currency locale.

## REST API overview

All API routes use the same origin as the website. Sign in first for private endpoints.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/auth/csrf` | Get a CSRF token before a state-changing request |
| `POST` | `/api/auth/register` | Create an account |
| `POST` | `/api/auth/login` | Sign in |
| `POST` | `/api/auth/logout` | Sign out |
| `GET` | `/api/auth/me` | Get the signed-in account |
| `GET` | `/api/dashboard?month=YYYY-MM` | Get monthly totals and category spending |
| `GET` | `/api/reports/monthly?month=YYYY-MM` | Get the selected month's report |
| `GET` / `PUT` | `/api/budgets?month=YYYY-MM` | Read or save a monthly budget |
| `GET` | `/api/transactions?from=YYYY-MM-DD&to=YYYY-MM-DD` | List the account's transactions |
| `POST` | `/api/transactions` | Add a transaction |
| `PUT` / `DELETE` | `/api/transactions/{id}` | Edit or delete an owned transaction |

The application validates emails, passwords, transaction categories, amounts, descriptions, dates, and month budgets on the server as well as in the browser.

## Troubleshooting

### `java` or `mvn` is not recognized

Install a JDK 21 distribution and Maven, then close and reopen VS Code so the terminal reloads PATH. Verify with `java -version` and `mvn -version`.

### Maven cannot download dependencies

Check your internet connection and try `mvn test` again. A company proxy or firewall may need to allow Maven Central.

### Port 8080 is already in use

Stop the other application using port 8080, or start this app on another port:

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

Open `http://localhost:8081`.

### The database cannot be opened or is locked

Stop other running copies of the app that use the same SQLite file. Check that the database folder exists and that your Windows account can write to it. If needed, set `DATABASE_URL` to a new writable file path and restart the app. Do not delete a database file that contains records you need.

### Sign-in, CSRF, or session errors

Use the same browser origin and port for the website and API. Refresh the page and sign in again if the session expired. Do not disable CSRF protection to work around a rejected request; the website fetches its CSRF token automatically.

### The chart is unavailable

The chart library is downloaded from a CDN. Check internet access or any browser extension/network filter blocking the CDN. Totals and transactions continue to work without the chart.

### An email address is already registered

Use the sign-in form for an existing account, or register with a different email address. The app does not provide a password-reset email service.

## Security and deployment notes

- Passwords use BCrypt hashing.
- Server-side authentication uses an HTTP session; private endpoints require authentication.
- Repositories filter transactions and budgets by the authenticated user's database ID.
- CSRF protection is enabled for create, update, and delete operations.
- Use HTTPS and secure session-cookie settings when deploying over a network. Keep SQLite or the selected production database on persistent storage and back it up.
- The local development configuration is intended for a single-user machine, not as a public production hosting configuration.
