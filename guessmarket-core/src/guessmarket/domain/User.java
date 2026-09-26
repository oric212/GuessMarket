package guessmarket.domain;

import java.io.Serializable;

public final class User implements Serializable {
    private final String username;
    private final Account userAccount;

    private boolean blocked;

    public User(String username, double initialCash) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }

        if (!Double.isFinite(initialCash) || initialCash < 0) {
            throw new IllegalArgumentException(
                    "Initial cash must be non-negative"
            );
        }

        this.username = username.trim();
        this.userAccount = new Account(initialCash);
        this.blocked = false;
    }

    public String getUsername() {
        return username;
    }

    public double getAccountBalance() {
        return userAccount.getBalance();
    }

    public boolean isBlocked() {
        return blocked;
    }

    void deposit(
            double amount, AccountTransactionType type, String eventName, String description) {
        userAccount.deposit(amount, type, eventName, description);
    }

    void validateCanPerformActions() {
        if (blocked) {
            throw new IllegalStateException(
                    "User " + username + " is blocked"
            );
        }
    }

    void withdraw(
            double amount, AccountTransactionType type, String eventName, String description) {
        userAccount.withdraw(amount, type, eventName, description);
        if (userAccount.getBalance() < 0) blocked = true;
    }

    public void topUp(double amount) {
        userAccount.deposit(amount, AccountTransactionType.TOP_UP, null, "Account top-up");
    }

    public java.util.List<AccountTransaction> getAccountTransactions() {
        return userAccount.getTransactions();
    }

    boolean canAfford(double amount) {
        return userAccount.canAfford(amount);
    }

    boolean canReceive(double amount) {
        return userAccount.canDeposit(amount);
    }

    boolean canApplyBalanceChange(double change) {
        return Double.isFinite(change)
                && Double.isFinite(userAccount.getBalance() + change);
    }

    void applyBalanceChange(double change, String eventName, String description) {
        applyBalanceChange(change, AccountTransactionType.ORDER_BOOK_TRADE, eventName, description);
    }

    void applyBalanceChange(
            double change, AccountTransactionType type, String eventName, String description) {
        if (change > 0.0) deposit(change, type, eventName, description);
        else if (change < 0.0) withdraw(-change, type, eventName, description);
    }
}
