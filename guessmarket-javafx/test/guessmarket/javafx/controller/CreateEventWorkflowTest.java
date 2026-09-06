package guessmarket.javafx.controller;

import guessmarket.domain.CommissionMethod;
import guessmarket.domain.TradingMethodType;
import guessmarket.dto.CreateEventRequest;
import guessmarket.dto.EventDTO;
import guessmarket.service.GuessMarketEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class CreateEventWorkflowTest {
    public static void main(String[] args) throws Exception {
        requestConstructionUsesSelectedCreatorAndMethodFields();
        methodSpecificValidationIgnoresHiddenFields();
        successfulCreationRefreshesVisibleData();
        failureProducesCleanMessageWithoutRefresh();
        System.out.println("CreateEventWorkflowTest: all checks passed");
    }

    private static void requestConstructionUsesSelectedCreatorAndMethodFields() {
        CreateEventRequest lmsr = request(TradingMethodType.LMSR, "12", "unused", "unused", false);
        check(lmsr.creatorUsername().equals("SelectedUser"), "Selected user was not used as creator");
        check(lmsr.eventName().equals("New event") && lmsr.options().equals(List.of("Yes", "No")),
                "Common fields were not constructed correctly");
        check(lmsr.tradingConfiguration() instanceof CreateEventRequest.LmsrConfiguration config
                        && config.liquidityParameter() == 12,
                "LMSR configuration was not constructed correctly");

        CreateEventRequest orderBook = request(TradingMethodType.ORDER_BOOK, "unused", "10", "100", true);
        check(orderBook.tradingConfiguration() instanceof CreateEventRequest.OrderBookConfiguration config
                        && config.d() == 10 && config.initial() == 100 && config.allowMint(),
                "Order Book configuration was not constructed correctly");
    }

    private static void methodSpecificValidationIgnoresHiddenFields() {
        request(TradingMethodType.LMSR, "10", "not-a-number", "not-a-number", false);
        request(TradingMethodType.ORDER_BOOK, "not-a-number", "10", "100", false);
        expectFailure(() -> request(TradingMethodType.LMSR, "", "10", "100", false),
                "Liquidity parameter (b) is required");
        expectFailure(() -> request(TradingMethodType.ORDER_BOOK, "10", "x", "100", false),
                "Order Book d must be a whole number");
        expectFailure(() -> UsersController.buildCreateEventRequest(
                "SelectedUser", " ", "Description", "Yes", "No", TradingMethodType.LMSR,
                CommissionMethod.ON_PURCHASE, "5", "10", "", "", false),
                "Event name is required");
    }

    private static void successfulCreationRefreshesVisibleData() throws Exception {
        GuessMarketEngine engine = engine();
        AtomicInteger refreshCount = new AtomicInteger();
        AtomicReference<List<EventDTO>> visibleEvents = new AtomicReference<>();
        CreateEventRequest request = request(TradingMethodType.LMSR, "10", "", "", false);

        int id = UsersController.createAndRefresh(engine, () -> {
            refreshCount.incrementAndGet();
            visibleEvents.set(engine.getEventSummaries());
        }, request).id();

        check(refreshCount.get() == 1, "Successful creation did not refresh the application once");
        check(visibleEvents.get().stream().anyMatch(event -> event.id() == id),
                "Created event was not visible after refresh");
        check(engine.getUser("SelectedUser").marketMakerEventIds().contains(id),
                "Created event was not visible in the selected user's MM assignments");
    }

    private static void failureProducesCleanMessageWithoutRefresh() throws Exception {
        GuessMarketEngine engine = engine();
        AtomicInteger refreshCount = new AtomicInteger();
        CreateEventRequest duplicateOptions = new CreateEventRequest(
                "SelectedUser", "Bad event", "Description", List.of("Same", " same "),
                CommissionMethod.ON_PURCHASE, 5, new CreateEventRequest.LmsrConfiguration(10));
        try {
            UsersController.createAndRefresh(engine, refreshCount::incrementAndGet, duplicateOptions);
            throw new AssertionError("Expected duplicate options to fail");
        } catch (RuntimeException expected) {
            String message = UsersController.messageOf(expected);
            check(message.contains("distinct") && !message.contains("Exception"),
                    "Engine failure was not converted to a clean UI message");
        }
        check(refreshCount.get() == 0, "Failed creation triggered a refresh");
    }

    private static CreateEventRequest request(
            TradingMethodType method, String b, String d, String initial, boolean allowMint) {
        return UsersController.buildCreateEventRequest(
                "SelectedUser", " New event ", " Description ", " Yes ", " No ", method,
                CommissionMethod.ON_PURCHASE, "5", b, d, initial, allowMint);
    }

    private static GuessMarketEngine engine() throws Exception {
        String xml = "<Guess-Market><GM-events><GM-event name=\"Existing\"><id>7</id>" +
                "<description>Existing</description><commission type=\"on-purchase\">0</commission>" +
                "<GM-options><GM-option>Up</GM-option><GM-option>Down</GM-option></GM-options>" +
                "<GM-method><GM-LMSR><b>10</b></GM-LMSR></GM-method></GM-event></GM-events>" +
                "<GM-users><GM-user name=\"SelectedUser\"><initial-cash>1000</initial-cash>" +
                "<GM-market-maker><event id=\"7\"/></GM-market-maker></GM-user>" +
                "</GM-users></Guess-Market>";
        Path file = Files.createTempFile("guessmarket-create-ui-", ".xml");
        Files.writeString(file, xml);
        file.toFile().deleteOnExit();
        GuessMarketEngine engine = new GuessMarketEngine();
        engine.loadMarketFromXml(file.toString());
        return engine;
    }

    private static void expectFailure(Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError("Expected failure containing: " + message);
        } catch (RuntimeException expected) {
            check(expected.getMessage() != null && expected.getMessage().contains(message),
                    "Unexpected error: " + expected.getMessage());
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
