package guessmarket.service;

import guessmarket.dto.AccountTransactionDTO;
import guessmarket.dto.UserDTO;

import java.util.List;

public final class RuntimeUserAccountTest {
    public static void main(String[] args) {
        registrationAndNormalization();
        topUpsAndHistory();
        invalidTopUpsAreAtomic();
        System.out.println("RuntimeUserAccountTest: all checks passed");
    }

    private static void registrationAndNormalization() {
        GuessMarketEngine engine = new GuessMarketEngine();
        UserDTO user = engine.registerUser("  Alice  ");
        check(user.username().equals("Alice"), "Runtime username was not trimmed");
        check(user.accountBalance() == 0.0, "Runtime user did not start at zero");
        check(!user.marketMaker(), "New runtime user unexpectedly became a Market Maker");
        expectFailure(() -> engine.registerUser(" "), "blank");
        expectFailure(() -> engine.registerUser("aLiCe"), "already in use");
        check(engine.getUsers().size() == 1, "Rejected registration changed user state");
    }

    private static void topUpsAndHistory() {
        GuessMarketEngine engine = new GuessMarketEngine();
        engine.registerUser("Alice");
        engine.topUpAccount("alice", 10.25);
        UserDTO user = engine.topUpAccount(" ALICE ", 4.75);
        check(user.accountBalance() == 15.0, "Multiple top-ups produced the wrong balance");
        List<AccountTransactionDTO> history = user.accountTransactions();
        check(history.size() == 3, "Account creation/top-ups were not recorded");
        check(history.get(0).type().equals("ACCOUNT_CREATED")
                && history.get(0).resultingBalance() == 0.0, "Creation transaction is wrong");
        check(history.get(1).type().equals("TOP_UP") && history.get(1).amountChange() == 10.25
                && history.get(1).resultingBalance() == 10.25, "First top-up transaction is wrong");
        check(history.get(2).sequence() == 3 && history.get(2).resultingBalance() == 15.0,
                "Transaction sequence or resulting balance is wrong");
    }

    private static void invalidTopUpsAreAtomic() {
        GuessMarketEngine engine = new GuessMarketEngine();
        engine.registerUser("Alice");
        for (double amount : List.of(0.0, -1.0, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            expectFailure(() -> engine.topUpAccount("Alice", amount), "finite and greater than zero");
        }
        UserDTO user = engine.getUser("Alice");
        check(user.accountBalance() == 0.0 && user.accountTransactions().size() == 1,
                "Rejected top-up changed account state");
    }

    private static void expectFailure(Runnable action, String text) {
        try {
            action.run();
            throw new AssertionError("Expected failure containing: " + text);
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().toLowerCase().contains(text.toLowerCase()),
                    "Unexpected error: " + expected.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
