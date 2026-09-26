package guessmarket.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public final class Account implements Serializable {
    private double balance;
    private final List<AccountTransaction> transactions = new ArrayList<>();
    private long nextTransactionSequence = 1;

    public Account(double initialBalance) {
        if (!Double.isFinite(initialBalance) || initialBalance < 0.0) {
            throw new IllegalArgumentException(
                    "Initial balance must be finite and non-negative"
            );
        }

        this.balance = initialBalance;
        record(AccountTransactionType.ACCOUNT_CREATED, initialBalance, null,
                "Account created");
    }

    public double getBalance() {
        return balance;
    }

    void deposit(double amount) {
        deposit(amount, AccountTransactionType.ADJUSTMENT, null, "Account credit");
    }

    void deposit(double amount, AccountTransactionType type, String eventName, String description) {
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
        record(type, amount, eventName, description);
    }

    void withdraw(double amount) {
        withdraw(amount, AccountTransactionType.ADJUSTMENT, null, "Account debit");
    }

    void withdraw(double amount, AccountTransactionType type, String eventName, String description) {
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
        if (amount > 0.0) record(type, -amount, eventName, description);
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

    List<AccountTransaction> getTransactions() {
        return List.copyOf(transactions);
    }

    private void record(
            AccountTransactionType type, double amountChange, String eventName, String description) {
        if (type == null) throw new IllegalArgumentException("Transaction type cannot be null");
        transactions.add(new AccountTransaction(
                nextTransactionSequence++, type, amountChange, balance, eventName, description));
    }
}
