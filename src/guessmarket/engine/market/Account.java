package guessmarket.engine.market;

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

        this.balance += amount;
    }

    void withdraw(double amount) {
        if (!Double.isFinite(amount) || amount < 0.0) {
            throw new IllegalArgumentException(
                    "Withdrawal amount must be finite and non-negative"
            );
        }

        this.balance -= amount;
    }
}
