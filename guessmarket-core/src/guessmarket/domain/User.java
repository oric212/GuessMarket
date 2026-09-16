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

        if (!Double.isFinite(initialCash) || initialCash <= 0) {
            throw new IllegalArgumentException(
                    "Initial cash must be greater than 0"
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

    void deposit(double amount) {
        userAccount.deposit(amount);
    }

    void withdraw(double amount) {
        userAccount.withdraw(amount);

        if (userAccount.getBalance() < 0) {
            blocked = true;
        }
    }

    void validateCanPerformActions() {
        if (blocked) {
            throw new IllegalStateException(
                    "User " + username + " is blocked"
            );
        }
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

    void applyBalanceChange(double change) {
        if (change > 0.0) deposit(change);
        else if (change < 0.0) withdraw(-change);
    }
}
