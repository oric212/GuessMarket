package guessmarket.service;

import guessmarket.dto.UserDTO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Lightweight regression test runnable with plain Java assertions/checks. */
public final class Exercise02FoundationTest {
    public static void main(String[] args) throws Exception {
        emptyQueriesAndUnknownUser();
        validUsersAssignmentsAndLookup();
        validationAndAtomicReplacement();
        persistenceRestoresUsersAndAssignments();
        System.out.println("Exercise02FoundationTest: all checks passed");
    }

    private static void emptyQueriesAndUnknownUser() {
        GuessMarketEngine engine = new GuessMarketEngine();
        check(engine.getUsers().isEmpty(), "A new engine should have no users");
        expectFailure(() -> engine.getUser("missing"), "No user exists with username: missing");
    }

    private static void validUsersAssignmentsAndLookup() throws Exception {
        GuessMarketEngine engine = new GuessMarketEngine();
        engine.loadMarketFromXml(writeXml(validMarket("Alice", 100, true, 1)).toString());

        check(engine.getUsers().size() == 2, "Both MM and normal users should load");
        UserDTO alice = engine.getUser("  aLIce ");
        check(alice.accountBalance() == 100.0, "Initial cash should become the account balance");
        check(alice.marketMakerEventIds().equals(List.of(1, 2)), "MM event IDs should be derived from events");
        check(engine.getUser("Trader").marketMakerEventIds().isEmpty(), "A normal user may have no assignments");
        expectUnsupported(() -> alice.marketMakerEventIds().add(2));
        check(engine.getEventState(2).optionStateDTOList().isEmpty(),
                "An Order Book event should be queryable before its trading implementation exists");
    }

    private static void validationAndAtomicReplacement() throws Exception {
        GuessMarketEngine engine = new GuessMarketEngine();
        engine.loadMarketFromXml(writeXml(validMarket("First", 100, false, 1)).toString());

        expectLoadFailure(engine, validMarket("Case", 100, false, 1)
                .replace("<GM-user name=\"Trader\">", "<GM-user name=\" case \">"), "Duplicate username");
        assertPreviousMarket(engine);

        expectLoadFailure(engine, validMarket("Cash", 0, false, 1), "Initial cash must be greater than 0");
        assertPreviousMarket(engine);

        expectLoadFailure(engine, validMarket("Missing", 100, false, 99), "non-existing event ID");
        assertPreviousMarket(engine);

        String noMm = validMarket("Nobody", 100, false, 1)
                .replace("<GM-market-maker><event id=\"1\"/><event id=\"2\"/></GM-market-maker>", "");
        expectLoadFailure(engine, noMm, "does not have a Market Maker");
        assertPreviousMarket(engine);

        String multipleMm = validMarket("Many", 100, false, 1)
                .replace("<GM-user name=\"Trader\"><initial-cash>50</initial-cash></GM-user>",
                        "<GM-user name=\"Trader\"><initial-cash>50</initial-cash>" +
                                "<GM-market-maker><event id=\"1\"/></GM-market-maker></GM-user>");
        expectLoadFailure(engine, multipleMm, "more than one Market Maker");
        assertPreviousMarket(engine);

        engine.loadMarketFromXml(writeXml(validMarket("Replacement", 250, false, 1)).toString());
        check(engine.getUser("Replacement").accountBalance() == 250.0, "Valid load should replace the market");
        expectFailure(() -> engine.getUser("First"), "No user exists");
    }

    private static void persistenceRestoresUsersAndAssignments() throws Exception {
        GuessMarketEngine source = new GuessMarketEngine();
        source.loadMarketFromXml(writeXml(validMarket("Saved", 300, false, 1)).toString());
        Path saveBase = Files.createTempFile("guessmarket-state-", "");
        Files.delete(saveBase);
        source.saveState(saveBase.toString());

        GuessMarketEngine restored = new GuessMarketEngine();
        restored.loadState(saveBase.toString());
        check(restored.getEventSummaries().size() == 2, "Events should be restored");
        check(restored.getUser("saved").marketMakerEventIds().equals(List.of(1, 2)),
                "Event-to-user MM identity should survive serialization");
        Files.deleteIfExists(Path.of(saveBase + ".sav"));
    }

    private static void expectLoadFailure(GuessMarketEngine engine, String xml, String message) throws Exception {
        Path path = writeXml(xml);
        expectFailure(() -> engine.loadMarketFromXml(path.toString()), message);
    }

    private static void assertPreviousMarket(GuessMarketEngine engine) {
        check(engine.getUser("first").username().equals("First"), "Invalid load changed the previous market");
        check(engine.getEventSummaries().size() == 2, "Invalid load changed previous events");
    }

    private static Path writeXml(String xml) throws Exception {
        Path path = Files.createTempFile("guessmarket-ex02-", ".xml");
        Files.writeString(path, xml);
        path.toFile().deleteOnExit();
        return path;
    }

    private static String validMarket(String owner, int cash, boolean allowMint, int assignedEvent) {
        return """
                <Guess-Market>
                  <GM-events>
                    <GM-event name="LMSR event"><id>1</id><description>One</description>
                      <commission type="on-purchase">5</commission>
                      <GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>
                      <GM-method><GM-LMSR><b>100</b></GM-LMSR></GM-method>
                    </GM-event>
                    <GM-event name="Order Book event"><id>2</id><description>Two</description>
                      <commission type="on-close">10</commission>
                      <GM-options><GM-option>Up</GM-option><GM-option>Down</GM-option></GM-options>
                      <GM-method><GM-order-book allow-mint="%s" initial="100" d="1"/></GM-method>
                    </GM-event>
                  </GM-events>
                  <GM-users>
                    <GM-user name="%s"><initial-cash>%d</initial-cash>
                      <GM-market-maker><event id="%d"/><event id="2"/></GM-market-maker>
                    </GM-user>
                    <GM-user name="Trader"><initial-cash>50</initial-cash></GM-user>
                  </GM-users>
                </Guess-Market>
                """.formatted(allowMint, owner, cash, assignedEvent);
    }

    private static void expectFailure(ThrowingRunnable action, String expectedMessage) {
        try {
            action.run();
            throw new AssertionError("Expected failure containing: " + expectedMessage);
        } catch (RuntimeException expected) {
            check(expected.getMessage() != null && expected.getMessage().contains(expectedMessage),
                    "Unexpected error: " + expected.getMessage());
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected immutable collection");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
