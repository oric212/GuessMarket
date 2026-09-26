package guessmarket.service;

import guessmarket.domain.CommissionMethod;
import guessmarket.domain.OrderSide;
import guessmarket.dto.CreateEventRequest;
import guessmarket.dto.EventStateDTO;
import guessmarket.dto.OptionStateDTO;
import guessmarket.dto.UserParticipationDTO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class MultiOptionCoreTest {
    private static final double EPSILON = 1.0e-9;

    public static void main(String[] args) throws Exception {
        threeOptionLmsrLifecycleAndProjection();
        threeOptionOrderBookAndHoldings();
        multiOptionMintIsExplicitlyRejected();
        System.out.println("MultiOptionCoreTest: all checks passed");
    }

    private static void threeOptionLmsrLifecycleAndProjection() throws Exception {
        GuessMarketEngine engine = engine();
        EventStateDTO created = engine.createEvent(new CreateEventRequest(
                "Creator", "Three-way election", "A three-outcome LMSR event",
                List.of("Red", "Green", "Blue"), CommissionMethod.ON_PURCHASE, 0,
                new CreateEventRequest.LmsrConfiguration(10)));

        check(created.options().equals(List.of("Red", "Green", "Blue")),
                "Option order was not preserved");
        check(created.optionStateDTOList().size() == 3, "DTO omitted an LMSR option");
        for (OptionStateDTO option : created.optionStateDTOList()) {
            close(option.currentOptionValue(), 1.0 / 3.0, "Initial probability is not uniform");
            check(option.quantityBought() == 0, "Initial LMSR quantity is not zero");
        }
        check(engine.getEventState(" three-WAY ELECTION ").id() == created.id(),
                "Name-based event lookup is not normalized");

        engine.startEvent("Creator", "Three-way election");
        engine.purchaseShares("Buyer", "Three-way election", 3, 5);
        EventStateDTO active = engine.getEventState(created.id());
        double probabilitySum = active.optionStateDTOList().stream()
                .mapToDouble(OptionStateDTO::currentOptionValue).sum();
        close(probabilitySum, 1.0, "LMSR probabilities do not sum to one");
        check(active.optionStateDTOList().get(2).currentOptionValue() > 1.0 / 3.0,
                "Purchased option probability did not rise");
        check(active.optionStateDTOList().get(2).quantityBought() == 5,
                "Purchase did not update only the selected quantity");
        check(active.optionStateDTOList().get(0).quantityBought() == 0
                        && active.optionStateDTOList().get(1).quantityBought() == 0,
                "Purchase changed an unselected option quantity");
        expectFailure(() -> engine.purchaseShares("Buyer", created.id(), 4, 1), "between 1 and 3");

        UserParticipationDTO participation = engine.getUser("Buyer").participations().stream()
                .filter(item -> item.eventId() == created.id()).findFirst().orElseThrow();
        check(participation.holdingsByOption().keySet().stream().toList()
                        .equals(List.of("Red", "Green", "Blue")),
                "Participation projection omitted or reordered options");
        check(participation.holdingsByOption().get("Blue") == 5,
                "Selected option holdings are wrong");

        EventStateDTO closed = engine.closeEvent("Creator", "Three-way election", 3);
        check(closed.eventState().equals("CLOSED") && closed.winningOption().equals("Blue"),
                "A third option could not be selected as winner");
        close(closed.currentEventAccountBalance(), 0.0, "LMSR event account was not settled");
    }

    private static void threeOptionOrderBookAndHoldings() throws Exception {
        GuessMarketEngine engine = engine();
        EventStateDTO created = engine.createEvent(new CreateEventRequest(
                "Creator", "Three-way order book", "Independent option books",
                List.of("Home", "Draw", "Away"), CommissionMethod.ON_CLOSE, 0,
                new CreateEventRequest.OrderBookConfiguration(10, 100, false)));
        engine.startEvent("Creator", created.id());
        engine.submitOrder("Creator", "Three-way order book", 3, OrderSide.SELL, 2, 4.0);
        engine.submitOrder("Buyer", "Three-way order book", 3, OrderSide.BUY, 2, 4.0);

        EventStateDTO active = engine.getEventState(created.id());
        check(active.orderBookDetails().optionBooks().size() == 3,
                "Order Book DTO omitted an option book");
        check(active.orderBookDetails().optionBooks().get(2).last() == 4.0,
                "Third option LAST statistic is wrong");
        check(active.participants().stream()
                        .filter(item -> item.username().equals("Buyer"))
                        .findFirst().orElseThrow().holdingsByOption().get("Away") == 2,
                "Third-option Order Book holdings are wrong");

        EventStateDTO closed = engine.closeEvent("Creator", created.id(), 3);
        check(closed.winningOption().equals("Away"), "Order Book could not settle a third option");
        close(closed.currentEventAccountBalance(), 0.0, "Order Book event account was not settled");
    }

    private static void multiOptionMintIsExplicitlyRejected() throws Exception {
        GuessMarketEngine engine = engine();
        expectFailure(() -> engine.createEvent(new CreateEventRequest(
                "Creator", "Undefined MINT", "MINT remains complementary and binary",
                List.of("One", "Two", "Three"), CommissionMethod.ON_PURCHASE, 0,
                new CreateEventRequest.OrderBookConfiguration(10, 100, true))),
                "only defined for two-option");
    }

    private static GuessMarketEngine engine() throws Exception {
        String xml = "<Guess-Market><GM-events><GM-event name=\"Existing\"><id>10</id>"
                + "<description>Existing event</description><commission type=\"on-purchase\">0</commission>"
                + "<GM-options><GM-option>Yes</GM-option><GM-option>No</GM-option></GM-options>"
                + "<GM-method><GM-LMSR><b>10</b></GM-LMSR></GM-method></GM-event></GM-events>"
                + "<GM-users><GM-user name=\"Creator\"><initial-cash>1000</initial-cash>"
                + "<GM-market-maker><event id=\"10\"/></GM-market-maker></GM-user>"
                + "<GM-user name=\"Buyer\"><initial-cash>1000</initial-cash></GM-user>"
                + "</GM-users></Guess-Market>";
        Path file = Files.createTempFile("guessmarket-multi-option-", ".xml");
        Files.writeString(file, xml);
        file.toFile().deleteOnExit();
        GuessMarketEngine engine = new GuessMarketEngine();
        engine.loadMarketFromXml(file.toString());
        return engine;
    }

    private static void close(double actual, double expected, String message) {
        check(Math.abs(actual - expected) <= EPSILON, message + ": " + actual);
    }

    private static void expectFailure(Runnable action, String expectedText) {
        try {
            action.run();
            throw new AssertionError("Expected failure containing: " + expectedText);
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().toLowerCase().contains(expectedText.toLowerCase()),
                    "Unexpected error: " + expected.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
