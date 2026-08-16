package guessmarket.UI;

import guessmarket.dto.*;
import guessmarket.engine.domain.Engine;
import guessmarket.engine.domain.GuessMarketEngine;

import java.util.Scanner;

public class UIConsole implements UI{

    private UiState currentState = UiState.StartMainMenu;
    private Scanner scanner;
    private Engine engine;
    private  UiState previousState;
    public UIConsole() {
        this.engine = new GuessMarketEngine();
        this.scanner = new Scanner(System.in);
    }


    @Override
    public void run() {
        while (currentState != null && currentState != currentState.EXIT) {
           // display
            try {
                switch (currentState) {
                    case LoadedMainMenu,StartMainMenu -> handleMainMenu();
                    case MarketActions,ShowEventState,ParticipateInEvent,PreTransactionScreen,PostTransactionScreen,CloseEvent -> handleMarketActions();
                    case ErrorScreen -> handleError();
                    case EXIT -> handleExit();

                }
            } catch (Exception error) {
                UiState last_state = this.currentState;
                this.currentState = UiState.ErrorScreen;
                handleError(last_state);

                this.currentState = last_state;

            }
        }
    }
    private void handleMainMenu() {
        switch (currentState) {
            case StartMainMenu -> handleStartMenu();
            case LoadedMainMenu -> handleLoadedMainMenu();
        }
    }
    private void handleStartMenu() {
          String userInput = scanner.nextLine().trim();
           try {
               engine.loadMarketFromXml(userInput);
               currentState = UiState.LoadedMainMenu;
           }
           catch (Exception e) {
               previousState = currentState;
               currentState = UiState.ErrorScreen;
           }
    }
    private void handleLoadedMainMenu() {
        String userInput = scanner.next();
        try {
            switch (userInput) {
                case "0" -> currentState = UiState.EXIT;
                case "1" -> currentState = UiState.MarketActions;
                case "2" -> currentState = UiState.StartMainMenu;
            }
        }
        catch (Exception e) {
            previousState = currentState;
            currentState = UiState.ErrorScreen;
        }
    }
    private void handleMarketActions() {
        switch (currentState) {
            case MarketActions -> handleMarketAction();
            case ShowEventState -> handleShowEventState();
            case ParticipateInEvent -> handleParticipateInEvent();
            case PreTransactionScreen -> handlePreTransactionScreen();
            case PostTransactionScreen -> handlePostTransactionScreen();
            case CloseEvent -> handleCloseEvent();
        }
    }
    private void handleMarketAction() {
        String userInput = scanner.next();
        this.currentState = switch (userInput) {
            case "1" -> UiState.LoadedMainMenu;
            case "2" -> UiState.ShowEventState;
            case "3" -> UiState.ShowEventSummaries;
            default -> UiState.MarketActions;
        };
    }

    private void handleShowEventState() {
        String userInput = scanner.nextLine().trim();
        try {
            EventStateDTO dto = engine.getEventState(Integer.parseInt(userInput));

            currentState = UiState.ShowEventDetails;
        }
        catch (Exception e) {
            previousState = currentState;
            currentState = UiState.ErrorScreen;
        }

    }

    private void handleEnterEventId() {
        this.currentState = UiState.ShowEventState;
    }

    private void handleParticipateInEvent() {
        this.currentState = switch (this.scanner.nextLine().trim()) {
            case "1" -> UiState.MarketActions;
            case "4" -> UiState.PreTransactionScreen;
            default -> UiState.ParticipateInEvent;
        };
    }

    private void handlePreTransactionScreen() {
        this.currentState = switch (this.scanner.nextLine().trim()) {
            case "1" -> UiState.PostTransactionScreen;
            case "2" -> UiState.MarketActions;
            default -> UiState.PreTransactionScreen;
        };
    }

    private void handlePostTransactionScreen() {
        this.currentState = switch (this.scanner.nextLine().trim()) {
            case "1" -> UiState.MarketActions;
            default -> UiState.PostTransactionScreen;
        };
    }

    private void handleCloseEvent() {
        this.currentState = UiState.ShowEventState;
    }

    private void handleError() {

    }
    private void handleError(UiState last_state) {

    }
    private void handleExit() {

    }

    private static final int BOX_WIDTH = 64;

    private void printBox(java.util.List<String> lines, String title) {
        clearScreen();
        int innerWidth = BOX_WIDTH - 2;

        // Top Border
        System.out.println("┌" + "─".repeat(innerWidth) + "┐");

        // Title Section
        if (title != null && !title.isEmpty()) {
            System.out.println("│" + centerText(title, innerWidth) + "│");
            System.out.println("├" + "─".repeat(innerWidth) + "┤");
        }

        // Content Lines
        for (String line : lines) {
            if ("---".equals(line)) {
                System.out.println("├" + "─".repeat(innerWidth) + "┤");
            } else {
                System.out.println("│" + centerText(line, innerWidth) + "│");
            }
        }

        // Bottom Border
        System.out.println("└" + "─".repeat(innerWidth) + "┘");
    }

    private String centerText(String text, int width) {
        if (text == null) text = "";
        if (text.length() >= width) {
            return text.substring(0, width);
        }
        int leftPadding = (width - text.length()) / 2;
        int rightPadding = width - text.length() - leftPadding;

        return " ".repeat(leftPadding) + text + " ".repeat(rightPadding);
    }


    private void printEventDTO(EventDTO event) {
        if (event == null) return;

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("Event Name: " + event.eventName());
        lines.add("Description: " + event.description());
        lines.add("Commission: " + event.commissionPercentage() + "% (" + event.commissionMethod() + ")");
        lines.add("Status: " + event.eventState());
        lines.add("---");
        lines.add("[ Available Options ]");

        if (event.options() != null && !event.options().isEmpty()) {
            for (int i = 0; i < event.options().size(); i++) {
                lines.add((i + 1) + ". " + event.options().get(i));
            }
        } else {
            lines.add("No options available.");
        }

        printBox(lines, "EVENT DETAILS [ID: " + event.id() + "]");
    }

    private void printEventStateDTO(EventStateDTO eventState) {
        if (eventState == null) return;

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("Event Name: " + eventState.eventName());
        lines.add("Status: " + eventState.eventState());
        lines.add(String.format("Current Account Balance: $%.2f", eventState.currentEventAccountBalance()));
        lines.add(String.format("Total Commission Collected: $%.2f", eventState.totalCommissionCollected()));

        if (eventState.winningOption() != null && !eventState.winningOption().isEmpty()) {
            lines.add("Winning Option: " + eventState.winningOption());
        }

        lines.add("---");
        lines.add("[ Option States ]");
        if (eventState.optionStateDTOList() != null && !eventState.optionStateDTOList().isEmpty()) {
            for (OptionStateDTO option : eventState.optionStateDTOList()) {
                lines.add(String.format("%s | Val: $%.2f | Qty: %d",
                        option.optionName(), option.currentOptionValue(), option.quantityBought()));
            }
        } else {
            lines.add("No option data available.");
        }

        lines.add("---");
        lines.add("[ Trade History ]");
        if (eventState.trades() != null && !eventState.trades().isEmpty()) {
            for (TradeDTO trade : eventState.trades()) {
                lines.add(String.format("Bought: %s | Qty: %d | Cost: $%.2f",
                        trade.boughtOptionName(), trade.quantity(), trade.purchaseCost()));
            }
        } else {
            lines.add("No recorded trades.");
        }

        printBox(lines, "EVENT STATE [ID: " + eventState.id() + "]");
    }

    private void printOptionStateDTO(OptionStateDTO optionState) {
        if (optionState == null) return;

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("Option Name: " + optionState.optionName());
        lines.add(String.format("Current Value: $%.2f", optionState.currentOptionValue()));
        lines.add("Quantity Bought: " + optionState.quantityBought());

        printBox(lines, "OPTION STATE");
    }

    private void printPurchaseResultDTO(PurchaseResultDTO purchaseResult) {
        if (purchaseResult == null) return;

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("Event Name: " + purchaseResult.eventName());
        lines.add(String.format("Net Purchase Cost: $%.2f", purchaseResult.purchaseCost()));
        lines.add(String.format("Commission Fee: $%.2f", purchaseResult.commission()));
        lines.add(String.format("Total Paid: $%.2f", purchaseResult.totalPricePaid()));
        lines.add("Event Status: " + purchaseResult.eventState());

        printBox(lines, "PURCHASE RESULT [EVENT ID: " + purchaseResult.id() + "]");
    }

    private void printTradeDTO(TradeDTO trade) {
        if (trade == null) return;

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("Option Bought: " + trade.boughtOptionName());
        lines.add("Quantity: " + trade.quantity());
        lines.add(String.format("Purchase Cost: $%.2f", trade.purchaseCost()));

        printBox(lines, "TRADE DETAILS");
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }
}
