package guessmarket.domain;

import java.io.Serializable;

public final class Account implements Serializable {
    private double balance;

    public Account(double initialBalance) {
        if (!Double.isFinite(initialBalance) || initialBalance < 0.0) {
            throw new IllegalArgumentException(
                    "Initial balance must be finite and non-negative"
            );
        }

        this.balance = initialBalance;
    }

    public double getBalance() {
        return balance;
    }

    void deposit(double amount) {
        if (!Double.isFinite(amount) || amount <= 0.0) {
            throw new IllegalArgumentException(
                    "Deposit amount must be finite and greater than zero"
            );
        }

        double updatedBalance = this.balance + amount;
        if (!Double.isFinite(updatedBalance)) {
            throw new IllegalStateException("Account balance would become non-finite");
        }
        this.balance = updatedBalance;
    }

    void withdraw(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException(
                    "Withdrawal amount must be finite and non-negative"
            );
        }

        double updatedBalance = this.balance - amount;
        if (!Double.isFinite(updatedBalance)) {
            throw new IllegalStateException("Account balance would become non-finite");
        }
        this.balance = updatedBalance;
    }

    boolean canAfford(double amount) {
        return Double.isFinite(amount) && amount >= 0.0 && balance >= amount;
    }

    boolean canDeposit(double amount) {
        return Double.isFinite(amount) && amount >= 0.0 && Double.isFinite(balance + amount);
    }

    double drain() {
        double amount = balance;
        balance = 0.0;
        return amount;
    }
}
