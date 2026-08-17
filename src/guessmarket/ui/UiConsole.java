package guessmarket.ui;

import guessmarket.dto.EventDTO;
import guessmarket.dto.EventStateDTO;
import guessmarket.dto.OptionStateDTO;
import guessmarket.dto.PurchaseResultDTO;
import guessmarket.dto.TradeDTO;
import guessmarket.engine.domain.Engine;
import guessmarket.engine.domain.GuessMarketEngine;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class UiConsole implements Ui {

    private UiState currentState = UiState.START_MAIN_MENU;
    private UiState previousState;
    private String errorMessage;

    private final Scanner scanner;
    private Engine engine;

    public UiConsole() {
        this.engine = new GuessMarketEngine();
        this.scanner = new Scanner(System.in);
    }

    @Override
    public void run() {
        while (currentState != UiState.EXIT) {
            switch (currentState) {
                case START_MAIN_MENU -> handleStartMenu();
                case LOADED_MAIN_MENU -> handleLoadedMainMenu();
                case MARKET_ACTIONS -> handleMarketActions();
                case LOAD_FILE -> handleLoadState();
                case SAVE_FILE -> handleSaveState();
                case ERROR_SCREEN -> handleErrorScreen();
                case EXIT -> {
                    // Loop ends.
                }
            }
        }
    }

    private void handleSaveState() {
        System.out.print("Enter file path to save: ");
        String filePath = scanner.nextLine().trim();
        try {
            engine.saveState(filePath);
            currentState = UiState.LOADED_MAIN_MENU;
        } catch (IOException e) {
            moveToErrorScreen(e.getMessage());
        }
    }

    private void handleLoadState() {
        System.out.print("Enter file path to load: ");
        String filePath = scanner.nextLine().trim();
        try {
            this.engine = Engine.loadState(filePath);
            currentState = UiState.LOADED_MAIN_MENU;
        } catch (IOException | ClassNotFoundException e) {
            moveToErrorScreen(e.getMessage());
        }
    }

    private void handleStartMenu() {
        printStartMenu();

        String userInput = scanner.next().trim();
        switch (userInput) {
            case "1" -> {
                printXMLLoad();
                try {
                    engine.loadMarketFromXml(userInput);
                    currentState = UiState.LOADED_MAIN_MENU;
                } catch (IllegalArgumentException | IllegalStateException e) {
                    moveToErrorScreen(e.getMessage());
                }
            }
            case "2" -> currentState = UiState.LOAD_FILE;
            default -> moveToErrorScreen("Invalid menu option.");
        }

    }

    private void handleLoadedMainMenu() {
        printLoadedMainMenu();

        String userInput = scanner.nextLine().trim();

        switch (userInput) {
            case "0" -> currentState = UiState.EXIT;
            case "1" -> currentState = UiState.MARKET_ACTIONS;
            case "2" -> currentState = UiState.START_MAIN_MENU;
            case "3" -> currentState = UiState.LOAD_FILE;
            case "4" -> currentState = UiState.SAVE_FILE;
            default -> moveToErrorScreen("Invalid menu option.");
        }
    }

    private void handleMarketActions() {
        printMarketActionsMenu();

        String userInput = scanner.nextLine().trim();

        switch (userInput) {
            case "1" -> currentState = UiState.LOADED_MAIN_MENU;
            case "2" -> showEventStateAction();
            case "3" -> showEventSummaries();
            case "4" -> participateInEvent();
            case "5" -> closeEvent();
            default -> moveToErrorScreen("Invalid menu option.");
        }
    }

    private void showEventSummaries() {
        List<EventDTO> events = engine.getEventSummaries();

        for (EventDTO event : events) {
            printEventDTO(event);
        }

        System.out.println("\n");
    }

    private void showEventSummaries(String stateFilter) {
        List<EventDTO> events = engine.getEventSummaries();
        boolean found = false;

        for (EventDTO event : events) {
            if (event.eventState().equalsIgnoreCase(stateFilter)) {
                printEventDTO(event);
                found = true;
            }
        }

        if (!found) {
            System.out.println("No matching events found.");
        }

        System.out.println("\n");
    }

    private void showEventStateAction() {
        try {
            System.out.println("Printing all system events:");
            showEventSummaries();

            System.out.println("Please enter an event ID:");

            int eventId = readEventId();

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
                    "Event ID must be a valid integer."
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
            System.out.println();
            System.out.println("=== MARKET ACTIONS ===");
            System.out.println("Participate in an event");
            System.out.println("Active events:");

            showEventSummaries("ACTIVE");

            System.out.println("Please enter an event ID:");
            int eventId = readEventId();

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

    private void closeEvent() {
        try {
            System.out.println();
            System.out.println("=== MARKET ACTIONS ===");
            System.out.println("Close an event");
            System.out.println("Active events:");

            showEventSummaries("ACTIVE");

            System.out.println("Please enter an event ID:");
            int eventId = readEventId();

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
        currentState = UiState.ERROR_SCREEN;
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
        System.out.println();
        System.out.println("=== GUESS MARKET ===");
        System.out.println("1. Enter the full XML file path:");
        System.out.println("2. Load State from File");

    }
    private void printXMLLoad() {
        System.out.println();
        System.out.println("=== GUESS MARKET ===");
        System.out.println("Enter the full XML file path:");
    }

    private void printLoadedMainMenu() {
        System.out.println();
        System.out.println("=== MAIN MENU ===");
        System.out.println("1. Enter Market Actions");
        System.out.println("2. Load Another XML File");
        System.out.println("3. Load State from File");
        System.out.println("4. Save State to File");
        System.out.println("0. Exit");
    }

    private void printMarketActionsMenu() {
        System.out.println();
        System.out.println("=== MARKET ACTIONS ===");
        System.out.println("1. Return to Main Menu");
        System.out.println("2. Show Event State");
        System.out.println("3. Show Events");
        System.out.println("4. Participate in Event");
        System.out.println("5. Close Event");
    }

    private void printErrorScreen() {
        System.out.println();
        System.out.println("=== ERROR ===");
        System.out.println(errorMessage);
        System.out.println();
        System.out.println("1. Return");
    }

    private void printEventDTO(EventDTO event) {
        if (event == null) {
            return;
        }

        System.out.println();
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

        System.out.println();
        System.out.println(
                "=== EVENT STATE [ID: " + eventState.id() + "] ==="
        );

        System.out.println("Event Name: " + eventState.eventName());
        System.out.println("Status: " + eventState.eventState());

        System.out.printf(
                "Current Account Balance: %.2f%n",
                eventState.currentEventAccountBalance()
        );

        System.out.printf(
                "Total Commission Collected: %.2f%n",
                eventState.totalCommissionCollected()
        );

        if (eventState.winningOption() != null) {
            System.out.println(
                    "Winning Option: " + eventState.winningOption()
            );
        }

        System.out.println();
        System.out.println("Option States:");

        for (OptionStateDTO option : eventState.optionStateDTOList()) {
            System.out.printf(
                    "%s | Value: %.2f | Quantity: %d%n",
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
                        "%s | Quantity: %d | Cost: %.2f%n",
                        trade.boughtOptionName(),
                        trade.quantity(),
                        trade.purchaseCost()
                );
            }
        }
        System.out.println("\n");
    }

    private void printPurchaseResultDTO(
            PurchaseResultDTO purchaseResult
    ) {
        if (purchaseResult == null) {
            return;
        }

        System.out.println();
        System.out.println("=== PURCHASE RESULT ===");

        System.out.println(
                "Event: " + purchaseResult.eventName()
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
        System.out.println("\n");
    }
}