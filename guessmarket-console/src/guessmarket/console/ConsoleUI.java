package guessmarket.console;

import guessmarket.dto.EventDTO;
import guessmarket.dto.EventStateDTO;
import guessmarket.dto.OptionStateDTO;
import guessmarket.dto.PurchaseResultDTO;
import guessmarket.dto.TradeDTO;
import guessmarket.api.Engine;
import guessmarket.service.GuessMarketEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ConsoleUI implements UserInterface {

    private ConsoleState currentState = ConsoleState.START_MAIN_MENU;
    private ConsoleState previousState;
    private String errorMessage;

    private final Scanner scanner;
    private final Engine engine;

    public ConsoleUI() {
        this.engine = new GuessMarketEngine();
        this.scanner = new Scanner(System.in);
    }

    @Override
    public void run() {
        while (currentState != ConsoleState.EXIT) {
            switch (currentState) {
                case START_MAIN_MENU -> handleStartMenu();
                case LOADED_MAIN_MENU -> handleLoadedMainMenu();
                case MARKET_ACTIONS -> handleMarketActions();
                case ERROR_SCREEN -> handleErrorScreen();
            }
        }
    }

    private void handleStartMenu() {
        printStartMenu();

        String userInput = scanner.nextLine().trim();

        switch (userInput) {
            case "1" -> handleLoadXmlFile();
            case "2" -> handleLoadState();
            case "3" -> currentState = ConsoleState.EXIT;
            default -> moveToErrorScreen("Invalid menu option.");
        }
    }

    private void handleLoadXmlFile() {
        System.out.println("Enter the full XML file path:");
        String filePath = scanner.nextLine().trim();

        try {
            engine.loadMarketFromXml(filePath);

            System.out.println("XML file loaded successfully.");

            if (currentState == ConsoleState.START_MAIN_MENU) {
                currentState = ConsoleState.LOADED_MAIN_MENU;
            }

        } catch (IllegalArgumentException | IllegalStateException e) {
            moveToErrorScreen(e.getMessage());
        }
    }

    private void handleLoadedMainMenu() {
        printLoadedMainMenu();

        String userInput = scanner.nextLine().trim();

        switch (userInput) {
            case "1" -> currentState = ConsoleState.MARKET_ACTIONS;
            case "2" -> handleLoadXmlFile();
            case "3" -> handleSaveState();
            case "4" -> handleLoadState();
            case "5" -> currentState = ConsoleState.EXIT;
            default -> moveToErrorScreen("Invalid menu option.");
        }
    }

    private void handleSaveState() {
        System.out.println(
                "Enter the full path for saving the system state, without extension:"
        );
        String filePath = scanner.nextLine().trim();

        try {
            engine.saveState(filePath);
            System.out.println("System state saved successfully.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            moveToErrorScreen(e.getMessage());
        }
    }

    private void handleLoadState() {
        System.out.println(
                "Enter the full path of the saved system state, without extension:"
        );
        String filePath = scanner.nextLine().trim();

        try {
            engine.loadState(filePath);
            System.out.println("System state loaded successfully.");
            currentState = ConsoleState.LOADED_MAIN_MENU;
        } catch (IllegalArgumentException | IllegalStateException e) {
            moveToErrorScreen(e.getMessage());
        }
    }



    private void handleMarketActions() {
        printMarketActionsMenu();

        String userInput = scanner.nextLine().trim();

        switch (userInput) {
            case "1" -> currentState = ConsoleState.LOADED_MAIN_MENU;
            case "2" -> showEventStateAction();
            case "3" -> showEventSummaries();
            case "4" -> participateInEvent();
            case "5" -> closeEvent();
            case "6" -> handleLoadXmlFile();
            case "7" -> currentState = ConsoleState.EXIT;
            default -> moveToErrorScreen("Invalid menu option.");
        }
    }

    private List<EventDTO> showEventSummaries() {
        printHeader("EVENTS");

        List<EventDTO> events = engine.getEventSummaries();

        for (int i = 0; i < events.size(); i++) {
            System.out.println("Event Number: " + (i + 1));
            printEventDTO(events.get(i));

            if (i < events.size() - 1) {
                System.out.println();
            }
        }

        System.out.println();

        return events;
    }

    private List<EventDTO> showEventSummaries(String stateFilter) {
        printHeader(stateFilter.toUpperCase() + " EVENTS");

        List<EventDTO> events = engine.getEventSummaries();
        List<EventDTO> matchingEvents = new ArrayList<>();

        for (EventDTO event : events) {
            if (event.eventState().equalsIgnoreCase(stateFilter)) {
                matchingEvents.add(event);

                System.out.println();
                System.out.println("Event Number: " + matchingEvents.size());
                printEventDTO(event);
            }
        }

        if (matchingEvents.isEmpty()) {
            System.out.println("No " + stateFilter.toLowerCase() + " events found.");
        }

        System.out.println();

        return matchingEvents;
    }

    private void showEventStateAction() {
        try {
            printHeader("SHOW EVENT STATE");

            List<EventDTO> events = showEventSummaries();

            System.out.println("Please choose an event number:");

            int choice = readEventId();

            if (choice < 1 || choice > events.size()) {
                throw new IllegalArgumentException("Invalid event choice.");
            }

            int eventId = events.get(choice - 1).id();

            showEventState(eventId);

        } catch (IllegalArgumentException | IllegalStateException e) {
            moveToErrorScreen(e.getMessage());
        }
    }

    private void showEventState(int eventId) {
        EventStateDTO dto = engine.getEventState(eventId);
        printEventStateDTO(dto);
    }

    private int readEventId() {
        String input = scanner.nextLine().trim();

        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Event number must be a valid integer."
            );
        }
    }

    private int readOptionChoice() {
        String input = scanner.nextLine().trim();

        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Option choice must be a valid integer."
            );
        }
    }

    private int readQuantity() {
        String input = scanner.nextLine().trim();

        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Quantity must be a valid integer."
            );
        }
    }

    private void participateInEvent() {
        try {
            printHeader("PARTICIPATE IN EVENT");

            int eventId = getEventChoiceFromUser();

            showEventState(eventId);

            System.out.println("Please enter an option number:");
            int optionChoice = readOptionChoice();

            System.out.println("Please enter a desired quantity:");
            int quantity = readQuantity();

            PurchaseResultDTO purchaseResult =
                    engine.purchaseShares(
                            eventId, optionChoice, quantity);

            printPurchaseResultDTO(purchaseResult);

            showEventState(eventId);

        } catch (IllegalArgumentException | IllegalStateException e) {
            moveToErrorScreen(e.getMessage());
        }
    }

    private int getEventChoiceFromUser() {
        List<EventDTO> activeEvents = showEventSummaries("ACTIVE");

        System.out.println("Please choose an event number:");
        int choice = readEventId();

        if (choice < 1 || choice > activeEvents.size()) {
            throw new IllegalArgumentException("Invalid event choice.");
        }

        return activeEvents.get(choice - 1).id();
    }

    private void closeEvent() {
        try {
            printHeader("CLOSE EVENT");

            int eventId = getEventChoiceFromUser();
            showEventState(eventId);

            System.out.println("Please enter the winning option number:");
            int optionChoice = readOptionChoice();

            EventStateDTO closedEvent =
                    engine.closeEvent(eventId, optionChoice);

            printEventStateDTO(closedEvent);

        } catch (IllegalArgumentException | IllegalStateException e) {
            moveToErrorScreen(e.getMessage());
        }
    }

    private void moveToErrorScreen(String message) {
        previousState = currentState;
        errorMessage = message;
        currentState = ConsoleState.ERROR_SCREEN;
    }

    private void handleErrorScreen() {
        printErrorScreen();

        String input = scanner.nextLine().trim();

        switch (input) {
            case "1" -> {
                currentState = previousState;
                previousState = null;
                errorMessage = null;
            }

            default -> {
                // Stay in ERROR_SCREEN.
            }
        }
    }



    private void printStartMenu() {
        printHeader("GUESS MARKET");
        System.out.println("1. Load XML File");
        System.out.println("2. Load Saved State");
        System.out.println("3. Exit");
    }

    private void printLoadedMainMenu() {
        printHeader("MAIN MENU");
        System.out.println("1. Enter Market Actions");
        System.out.println("2. Load Another XML File");
        System.out.println("3. Save System State");
        System.out.println("4. Load Saved State");
        System.out.println("5. Exit");
    }

    private void printMarketActionsMenu() {
        printHeader("MARKET ACTIONS");
        System.out.println("1. Return to Main Menu");
        System.out.println("2. Show Event State");
        System.out.println("3. Show Events");
        System.out.println("4. Participate in Event");
        System.out.println("5. Close Event");
        System.out.println("6. Load Another XML File");
        System.out.println("7. Exit");
    }

    private void printErrorScreen() {
        printHeader("ERROR");
        System.out.println(errorMessage);
        System.out.println();
        System.out.println("1. Return");
    }

    private void printEventDTO(EventDTO event) {
        if (event == null) {
            return;
        }

        System.out.println("Event ID: " + event.id());
        System.out.println("Event Name: " + event.eventName());
        System.out.println("Description: " + event.description());

        System.out.println(
                "Commission: "
                        + event.commissionPercentage()
                        + "% ("
                        + event.commissionMethod()
                        + ")"
        );

        System.out.println("Status: " + event.eventState());

        System.out.println("Options:");

        for (int i = 0; i < event.options().size(); i++) {
            System.out.println(
                    (i + 1) + ". " + event.options().get(i)
            );
        }
    }

    private void printEventStateDTO(EventStateDTO eventState) {
        if (eventState == null) {
            return;
        }

        printHeader("EVENT STATE");

        System.out.println("Event ID: " + eventState.id());
        System.out.println("Event Name: " + eventState.eventName());
        System.out.println("Status: " + eventState.eventState());

        System.out.printf(
                "Account Balance: %.2f%n",
                eventState.currentEventAccountBalance()
        );

        System.out.printf(
                "Commission Collected: %.2f%n",
                eventState.totalCommissionCollected()
        );

        if (eventState.winningOption() != null) {
            System.out.println(
                    "Winning Option: " + eventState.winningOption()
            );
        }

        System.out.println();
        System.out.println("Options:");

        for (int i = 0; i < eventState.optionStateDTOList().size(); i++) {
            OptionStateDTO option = eventState.optionStateDTOList().get(i);
            System.out.printf(
                    "%d. %s | Value: %.2f | Shares Bought: %d%n",
                    i + 1,
                    option.optionName(),
                    option.currentOptionValue(),
                    option.quantityBought()
            );
        }

        System.out.println();
        System.out.println("Trade History:");

        if (eventState.trades().isEmpty()) {
            System.out.println("No trades have been made for this event.");
        } else {
            for (TradeDTO trade : eventState.trades()) {
                System.out.printf(
                        "%s | Shares: %d | Cost: %.2f%n",
                        trade.boughtOptionName(),
                        trade.quantity(),
                        trade.purchaseCost()
                );
            }
        }
        System.out.println();
    }

    private void printPurchaseResultDTO(
            PurchaseResultDTO purchaseResult
    ) {
        if (purchaseResult == null) {
            return;
        }

        printHeader("PURCHASE RESULT");
        System.out.println(
                "Event Name: " + purchaseResult.eventName()
        );

        System.out.printf(
                "Purchase Cost: %.2f%n",
                purchaseResult.purchaseCost()
        );

        System.out.printf(
                "Commission: %.2f%n",
                purchaseResult.commission()
        );

        System.out.printf(
                "Total Paid: %.2f%n",
                purchaseResult.totalPricePaid()
        );
        System.out.println();
    }

    private void printHeader(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}