package guessmarket.javafx.controller;

import guessmarket.api.Engine;
import guessmarket.domain.OrderSide;
import guessmarket.dto.*;
import guessmarket.javafx.view.AnimationSettings;
import javafx.animation.FadeTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class UsersController {
    private static final DecimalFormat NUMBER = new DecimalFormat(
            "0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private final Engine engine;
    private final Runnable refreshApplication;
    private final BooleanSupplier animationsEnabled;
    private final SplitPane root = new SplitPane();
    private final ObservableList<UserDTO> users = FXCollections.observableArrayList();
    private final TableView<UserDTO> userTable = new TableView<>(users);
    private final Label emptyDetails = new Label("Select a user to open their workspace.");
    private final VBox detailsContent = new VBox(12);
    private final Label usernameValue = new Label();
    private final Label balanceValue = new Label();
    private final Label statusValue = new Label();
    private final Label blockedWarning = new Label();
    private final TableView<EventDTO> marketMakerEvents = new TableView<>();
    private final TableView<UserParticipationDTO> participations = new TableView<>();
    private final VBox participationDetails = new VBox();
    private final TableView<EventDTO> actionEvents = new TableView<>();
    private final VBox eventContext = new VBox();
    private final VBox actionArea = new VBox();
    private final Label actionResult = new Label();
    private final Button startButton = new Button("Start Event");
    private final Button closeButton = new Button("Close Event");
    private final ComboBox<String> closeWinner = new ComboBox<>();
    private final ComboBox<String> lmsrOption = new ComboBox<>();
    private final TextField lmsrQuantity = new TextField();
    private final Button purchaseButton = new Button("Purchase shares");
    private final ComboBox<OrderSide> orderSide = new ComboBox<>();
    private final ComboBox<String> orderOption = new ComboBox<>();
    private final TextField orderQuantity = new TextField();
    private final TextField orderPrice = new TextField();
    private final Button submitOrderButton = new Button("Submit order");
    private final VBox startPane = new VBox(6);
    private final VBox closePane = new VBox(6);
    private final VBox lmsrPane = new VBox(6);
    private final VBox orderBookPane = new VBox(6);
    private UserDTO selectedUser;
    private EventDTO selectedActionEvent;
    private EventStateDTO selectedActionState;
    private Integer selectedParticipationEventId;

    public UsersController(Engine engine, Runnable refreshApplication, BooleanSupplier animationsEnabled) {
        this.engine = Objects.requireNonNull(engine);
        this.refreshApplication = Objects.requireNonNull(refreshApplication);
        this.animationsEnabled = Objects.requireNonNull(animationsEnabled);
        configureUserTable();
        configureMarketMakerTable();
        configureParticipationTable();
        configureActionEventTable();
        configureActions();
        root.getItems().addAll(buildUserBrowser(), buildWorkspace());
        root.setDividerPositions(0.32);
    }

    public Parent getView() { return root; }

    public void refreshUsers() {
        String username = selectedUser == null ? null : selectedUser.username();
        Integer eventId = selectedActionEvent == null ? null : selectedActionEvent.id();
        Integer participationId = selectedParticipationEventId;
        users.setAll(engine.getUsers());
        UserDTO retained = findUser(users, username);
        if (retained != null) {
            selectedActionEvent = eventId == null ? null : selectedActionEvent;
            selectedParticipationEventId = participationId;
            userTable.getSelectionModel().select(retained);
            selectUser(retained);
        } else if (!users.isEmpty()) {
            userTable.getSelectionModel().selectFirst();
        } else {
            clearWorkspace();
        }
    }

    private Parent buildUserBrowser() {
        VBox left = new VBox(10, heading("Users"), userTable);
        left.setPadding(new Insets(12));
        left.setMinWidth(0);
        VBox.setVgrow(userTable, Priority.ALWAYS);
        return left;
    }

    private Parent buildWorkspace() {
        GridPane account = infoGrid();
        addInfoRow(account, 0, "Username", usernameValue);
        addInfoRow(account, 1, "Account balance", balanceValue);
        addInfoRow(account, 2, "Status", statusValue);
        blockedWarning.getStyleClass().add("blocked-warning");
        blockedWarning.setWrapText(true);
        blockedWarning.setVisible(false);
        blockedWarning.setManaged(false);

        participationDetails.getChildren().add(new Label("Select a participation to inspect it."));
        eventContext.getChildren().add(new Label("Select an event to view available actions."));
        actionResult.setWrapText(true);
        actionArea.getChildren().addAll(startPane, closePane, lmsrPane, orderBookPane, actionResult);

        detailsContent.getChildren().addAll(
                titled("Account", new VBox(8, account, blockedWarning)),
                titled("Market Maker assignments", marketMakerEvents),
                titled("Participations", new VBox(8, participations, participationDetails)),
                titled("Select an event / Actions", new VBox(10, actionEvents, eventContext, actionArea)));
        detailsContent.setVisible(false);
        detailsContent.setManaged(false);
        emptyDetails.setWrapText(true);
        VBox body = new VBox(10, heading("User workspace"), emptyDetails, detailsContent);
        body.setPadding(new Insets(12));
        body.setMinWidth(0);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        return scroll;
    }

    private void configureUserTable() {
        configureTable(userTable, "Load an XML file to display users.");
        userTable.getColumns().add(column("Username", UserDTO::username, 160));
        userTable.getColumns().add(column("Balance", dto -> format(dto.accountBalance()), 105));
        userTable.getColumns().add(column("Status", UsersController::statusOf, 90));
        userTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, value) -> { if (value != null) selectUser(value); });
    }

    private void configureMarketMakerTable() {
        configureTable(marketMakerEvents, "This user is not Market Maker for any events.");
        marketMakerEvents.getColumns().add(column("ID", EventDTO::id, 60));
        marketMakerEvents.getColumns().add(column("Event", EventDTO::eventName, 180));
        marketMakerEvents.getColumns().add(column("Method", EventDTO::tradingMethod, 115));
        marketMakerEvents.getColumns().add(column("State", EventDTO::eventState, 110));
        marketMakerEvents.setPrefHeight(150);
    }

    private void configureParticipationTable() {
        configureTable(participations, "No participations yet.");
        participations.getColumns().add(column("Event", UserParticipationDTO::eventName, 170));
        participations.getColumns().add(column("Method", UserParticipationDTO::tradingMethod, 110));
        participations.getColumns().add(column("State", UserParticipationDTO::eventState, 105));
        participations.getColumns().add(column("Holdings", UsersController::holdingsSummary, 210));
        participations.getColumns().add(column("Commission",
                dto -> format(dto.totalCommissionPaid()), 105));
        participations.setPrefHeight(175);
        participations.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, value) -> showParticipation(value));
    }

    private void configureActionEventTable() {
        configureTable(actionEvents, "Load a market to select an event.");
        actionEvents.getColumns().add(column("Event", EventDTO::eventName, 170));
        actionEvents.getColumns().add(column("Method", EventDTO::tradingMethod, 110));
        actionEvents.getColumns().add(column("State", EventDTO::eventState, 105));
        actionEvents.getColumns().add(column("Role", dto -> isSelectedUserMarketMaker(dto) ? "MM" : "User", 90));
        actionEvents.getColumns().add(column("Participates", dto -> participatesIn(dto.id()) ? "Yes" : "No", 100));
        actionEvents.setPrefHeight(185);
        actionEvents.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, value) -> { if (value != null) selectActionEvent(value); });
    }

    private void configureActions() {
        startButton.setOnAction(event -> mutate(() -> engine.startEvent(username(), eventId()),
                "Event started successfully."));
        closeWinner.setPromptText("Winning option");
        closeButton.setOnAction(event -> closeEvent());
        closePane.getChildren().setAll(sectionLabel("Market Maker close"),
                flow(new Label("Winner"), closeWinner, closeButton));
        startPane.getChildren().setAll(sectionLabel("Market Maker start"), startButton);

        lmsrQuantity.setPromptText("Positive whole number");
        purchaseButton.setOnAction(event -> purchaseLmsr());
        lmsrPane.getChildren().setAll(sectionLabel("LMSR purchase"),
                flow(new Label("Option"), lmsrOption, new Label("Quantity"), lmsrQuantity, purchaseButton));

        orderSide.setItems(FXCollections.observableArrayList(OrderSide.BUY, OrderSide.SELL));
        orderSide.getSelectionModel().selectFirst();
        orderQuantity.setPromptText("Positive whole number");
        orderPrice.setPromptText("Price, max 2 decimals");
        submitOrderButton.setOnAction(event -> submitOrder());
        orderBookPane.getChildren().setAll(sectionLabel("Order Book submission"),
                flow(new Label("Side"), orderSide, new Label("Option"), orderOption,
                        new Label("Quantity"), orderQuantity, new Label("Price/share"), orderPrice,
                        submitOrderButton));
        actionResult.getStyleClass().add("result-message");
    }

    private void selectUser(UserDTO summary) {
        try {
            String previousUsername = selectedUser == null ? null : selectedUser.username();
            Integer eventId = selectedActionEvent == null ? null : selectedActionEvent.id();
            Integer participationId = selectedParticipationEventId;
            selectedUser = engine.getUser(summary.username());
            if (previousUsername == null || !previousUsername.equalsIgnoreCase(selectedUser.username())) {
                actionResult.setText("");
            }
            usernameValue.setText(selectedUser.username());
            balanceValue.setText(format(selectedUser.accountBalance()));
            statusValue.setText(statusOf(selectedUser));
            blockedWarning.setText("This user is BLOCKED and can no longer initiate operations. "
                    + "Participation history and settlement credits remain visible.");
            blockedWarning.setVisible(selectedUser.blocked());
            blockedWarning.setManaged(selectedUser.blocked());

            List<EventDTO> allEvents = engine.getEventSummaries();
            Map<Integer, EventDTO> byId = allEvents.stream().collect(Collectors.toMap(EventDTO::id, Function.identity()));
            marketMakerEvents.getItems().setAll(selectedUser.marketMakerEventIds().stream()
                    .map(byId::get).filter(Objects::nonNull).toList());
            participations.getItems().setAll(selectedUser.participations());
            actionEvents.getItems().setAll(allEvents);
            restoreParticipation(participationId);
            restoreActionEvent(eventId);
            emptyDetails.setVisible(false);
            emptyDetails.setManaged(false);
            detailsContent.setVisible(true);
            detailsContent.setManaged(true);
        } catch (RuntimeException error) {
            showWorkspaceError("User details could not be loaded: " + messageOf(error));
        }
    }

    private void restoreParticipation(Integer eventId) {
        UserParticipationDTO retained = eventId == null ? null : participations.getItems().stream()
                .filter(dto -> dto.eventId() == eventId).findFirst().orElse(null);
        if (retained != null) participations.getSelectionModel().select(retained);
        else if (!participations.getItems().isEmpty()) participations.getSelectionModel().selectFirst();
        else showParticipation(null);
    }

    private void restoreActionEvent(Integer eventId) {
        EventDTO retained = findEvent(actionEvents.getItems(), eventId);
        if (retained != null) actionEvents.getSelectionModel().select(retained);
        else if (!actionEvents.getItems().isEmpty()) actionEvents.getSelectionModel().selectFirst();
        else clearActionEvent();
    }

    private void showParticipation(UserParticipationDTO participation) {
        participationDetails.getChildren().clear();
        if (participation == null) {
            selectedParticipationEventId = null;
            participationDetails.getChildren().add(new Label("No participations yet."));
            return;
        }
        selectedParticipationEventId = participation.eventId();
        participationDetails.getChildren().add("LMSR".equals(participation.tradingMethod())
                ? buildLmsrParticipation(participation) : buildOrderBookParticipation(participation));
    }

    private Parent buildLmsrParticipation(UserParticipationDTO dto) {
        GridPane summary = participationSummary(dto);
        addInfoRow(summary, 4, "Holdings", new Label(holdingsSummary(dto)));
        addInfoRow(summary, 5, "Commission paid", new Label(format(dto.totalCommissionPaid())));
        TableView<TradeDTO> trades = new TableView<>();
        configureTable(trades, "No personal trades yet.");
        trades.getColumns().add(column("Option", TradeDTO::boughtOptionName, 150));
        trades.getColumns().add(column("Quantity", TradeDTO::quantity, 100));
        trades.getColumns().add(column("Purchase cost", trade -> format(trade.purchaseCost()), 120));
        trades.getItems().setAll(dto.trades());
        trades.setPrefHeight(160);
        return new VBox(8, summary, sectionLabel("Personal trade history — newest first"), trades);
    }

    private Parent buildOrderBookParticipation(UserParticipationDTO dto) {
        GridPane summary = participationSummary(dto);
        addInfoRow(summary, 4, "Commission paid", new Label(format(dto.totalCommissionPaid())));
        addInfoRow(summary, 5, "Total cash paid", new Label(format(dto.totalCashPaid())));
        addInfoRow(summary, 6, "Total cash received", new Label(format(dto.totalCashReceived())));
        addInfoRow(summary, 7, "Final profit/loss", new Label(formatProfitLoss(dto.profitLoss())));
        TableView<String> options = new TableView<>();
        configureTable(options, "No option holdings.");
        options.getColumns().add(column("Option", name -> name, 140));
        options.getColumns().add(column("Held", name -> dto.holdingsByOption().get(name), 85));
        options.getColumns().add(column("Cumulative gross paid",
                name -> format(dto.cumulativePurchaseAmountByOption().get(name)), 155));
        options.getColumns().add(column("Reserved SELL", name -> dto.reservedSellByOption().get(name), 115));
        options.getColumns().add(column("Available to sell", name -> dto.availableToSellByOption().get(name), 125));
        options.getItems().setAll(dto.holdingsByOption().keySet());
        options.setPrefHeight(130);
        Label note = new Label("Cumulative gross paid is purchase history, not remaining-position cost basis.");
        note.setWrapText(true);
        note.getStyleClass().add("secondary-text");
        return new VBox(8, summary, options, note);
    }

    private GridPane participationSummary(UserParticipationDTO dto) {
        GridPane summary = infoGrid();
        addInfoRow(summary, 0, "Event", new Label(dto.eventName()));
        addInfoRow(summary, 1, "Method", new Label(dto.tradingMethod()));
        addInfoRow(summary, 2, "State", new Label(dto.eventState()));
        addInfoRow(summary, 3, "Winning option", new Label(
                dto.winningOption() == null ? "Not closed yet" : dto.winningOption()));
        return summary;
    }

    private void selectActionEvent(EventDTO event) {
        if (selectedActionEvent == null || selectedActionEvent.id() != event.id()) actionResult.setText("");
        selectedActionEvent = event;
        try {
            selectedActionState = engine.getEventState(event.id());
            showEventContext(selectedActionState);
            updateActionAvailability();
        } catch (RuntimeException error) {
            selectedActionState = null;
            eventContext.getChildren().setAll(new Label("Event details could not be loaded: " + messageOf(error)));
            hideActions();
        }
    }

    private void showEventContext(EventStateDTO state) {
        GridPane common = infoGrid();
        addInfoRow(common, 0, "Event", new Label(state.eventName()));
        addInfoRow(common, 1, "Method / state", new Label(state.tradingMethod() + " / " + state.eventState()));
        addInfoRow(common, 2, "Market Maker", new Label(state.marketMakerUsername()));
        addInfoRow(common, 3, "Commission", new Label(
                state.commissionMethod() + " " + state.commissionPercentage() + "%"));
        addInfoRow(common, 4, "Options", new Label(String.join(" / ", state.options())));
        VBox context = new VBox(8, common);
        if (state.lmsrDetails() != null) {
            context.getChildren().add(new Label(state.lmsrDetails().options().stream()
                    .map(option -> option.optionName() + " " + format(option.currentOptionValue()))
                    .collect(Collectors.joining(" | ", "Current prices: ", ""))));
        } else if (state.orderBookDetails() != null) {
            context.getChildren().add(new Label("d: " + state.orderBookDetails().d()
                    + " | Allow mint: " + (state.orderBookDetails().allowMint() ? "Yes" : "No")));
            for (OrderBookOptionDTO option : state.orderBookDetails().optionBooks()) {
                context.getChildren().add(new Label(option.optionName() + " — LAST " + formatMarketValue(option.last())
                        + " | BID " + formatMarketValue(option.bid()) + " | ASK " + formatMarketValue(option.ask())
                        + selectedUserPosition(option.optionName())));
            }
        }
        eventContext.getChildren().setAll(context);
        closeWinner.getItems().setAll(state.options());
        lmsrOption.getItems().setAll(state.options());
        orderOption.getItems().setAll(state.options());
        closeWinner.getSelectionModel().selectFirst();
        lmsrOption.getSelectionModel().selectFirst();
        orderOption.getSelectionModel().selectFirst();
    }

    private String selectedUserPosition(String option) {
        UserParticipationDTO participation = participationFor(eventId());
        if (participation == null) return " | Your position: none";
        return " | Yours held/reserved/available: " + participation.holdingsByOption().get(option) + "/"
                + participation.reservedSellByOption().get(option) + "/"
                + participation.availableToSellByOption().get(option);
    }

    private void updateActionAvailability() {
        ActionAvailability availability = actionAvailability(selectedUser, selectedActionEvent);
        setShown(startPane, availability.start());
        setShown(closePane, availability.close());
        setShown(lmsrPane, availability.lmsrPurchase());
        setShown(orderBookPane, availability.orderSubmission());
        startButton.setDisable(!availability.start());
        closeButton.setDisable(!availability.close());
        purchaseButton.setDisable(!availability.lmsrPurchase());
        submitOrderButton.setDisable(!availability.orderSubmission());
        if (!availability.any()) {
            actionArea.getChildren().removeIf(node -> node.getStyleClass().contains("no-actions"));
            Label none = new Label(selectedUser.blocked()
                    ? "Actions are disabled because this user is blocked."
                    : "No mutation is available for this user in the event's current state.");
            none.getStyleClass().add("no-actions");
            actionArea.getChildren().addFirst(none);
        } else {
            actionArea.getChildren().removeIf(node -> node.getStyleClass().contains("no-actions"));
        }
    }

    static ActionAvailability actionAvailability(UserDTO user, EventDTO event) {
        if (user == null || event == null || user.blocked()) return new ActionAvailability(false, false, false, false);
        boolean mm = event.marketMakerUsername().equalsIgnoreCase(user.username());
        boolean notStarted = "NOT_STARTED".equals(event.eventState());
        boolean active = "ACTIVE".equals(event.eventState());
        return new ActionAvailability(mm && notStarted, mm && active,
                active && "LMSR".equals(event.tradingMethod()),
                active && "ORDER_BOOK".equals(event.tradingMethod()));
    }

    record ActionAvailability(boolean start, boolean close, boolean lmsrPurchase, boolean orderSubmission) {
        boolean any() { return start || close || lmsrPurchase || orderSubmission; }
    }

    private void closeEvent() {
        int choice = closeWinner.getSelectionModel().getSelectedIndex();
        if (choice < 0) { showActionError("Select a winning option."); return; }
        mutate(() -> engine.closeEvent(username(), eventId(), choice + 1), "Event closed successfully.");
    }

    private void purchaseLmsr() {
        int choice = lmsrOption.getSelectionModel().getSelectedIndex();
        if (choice < 0) { showActionError("Select an option."); return; }
        Integer quantity = positiveInteger(lmsrQuantity.getText(), "Quantity");
        if (quantity == null) return;
        mutate(() -> engine.purchaseShares(username(), eventId(), choice + 1, quantity), result -> {
            PurchaseResultDTO purchase = (PurchaseResultDTO) result;
            return "Purchase completed — cost " + format(purchase.purchaseCost()) + ", commission "
                    + format(purchase.commission()) + ", total paid " + format(purchase.totalPricePaid()) + ".";
        });
    }

    private void submitOrder() {
        OrderSide side = orderSide.getValue();
        int choice = orderOption.getSelectionModel().getSelectedIndex();
        if (side == null) { showActionError("Select BUY or SELL."); return; }
        if (choice < 0) { showActionError("Select an option."); return; }
        Integer quantity = positiveInteger(orderQuantity.getText(), "Quantity");
        if (quantity == null) return;
        Double price = validPrice(orderPrice.getText());
        if (price == null) { showActionError("Price must be finite, greater than zero, and use at most 2 decimal digits."); return; }
        mutate(() -> engine.submitOrder(username(), eventId(), choice + 1, side, quantity, price), result ->
                orderResult((OrderSubmissionResultDTO) result));
    }

    private String orderResult(OrderSubmissionResultDTO result) {
        StringBuilder text = new StringBuilder("Submitted ").append(result.side()).append(' ')
                .append(result.optionName()).append(" — quantity ").append(result.originalQuantity())
                .append(", resting ").append(result.remainingQuantity()).append(", limit ")
                .append(format(result.limitPrice())).append(".\nOrdinary executions: ")
                .append(result.executions().size());
        for (OrderExecutionDTO execution : result.executions()) {
            text.append("\n• ").append(execution.buyerUsername()).append(" bought from ")
                    .append(execution.sellerUsername()).append(": ").append(execution.quantity())
                    .append(" @ ").append(format(execution.executionPrice()));
        }
        text.append("\nMint executions: ").append(result.mintExecutions().size());
        for (MintExecutionDTO mint : result.mintExecutions()) {
            text.append("\n• ").append(mint.quantity()).append(" pairs: ")
                    .append(mint.restingBuyerUsername()).append('/').append(mint.restingOptionName())
                    .append(" @ ").append(format(mint.restingExecutionPrice())).append(" + ")
                    .append(mint.incomingBuyerUsername()).append('/').append(mint.incomingOptionName())
                    .append(" @ ").append(format(mint.incomingExecutionPrice()));
        }
        return text.toString();
    }

    private void mutate(Action action, String success) { mutate(action, ignored -> success); }
    private void mutate(Action action, Function<Object, String> successText) {
        boolean wasBlocked = selectedUser.blocked();
        try {
            Object result = action.run();
            String message = successText.apply(result);
            refreshApplication.run();
            UserDTO refreshed = engine.getUser(username());
            selectedUser = refreshed;
            showActionSuccess(message);
            if (!wasBlocked && refreshed.blocked()) {
                blockedWarning.setText("Warning: this completed operation made the account negative. "
                        + "The user is now BLOCKED from future operations.");
                blockedWarning.setVisible(true);
                blockedWarning.setManaged(true);
                updateActionAvailability();
            }
        } catch (RuntimeException error) {
            showActionError(messageOf(error));
        }
    }

    private Integer positiveInteger(String text, String label) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException error) {
            showActionError(label + " must be a positive whole number.");
            return null;
        }
    }

    static Double validPrice(String text) {
        if (text == null || !text.trim().matches("\\d+(\\.\\d{1,2})?")) return null;
        try {
            double value = Double.parseDouble(text.trim());
            return Double.isFinite(value) && value > 0 ? value : null;
        } catch (NumberFormatException error) { return null; }
    }

    private void showActionSuccess(String message) {
        actionResult.getStyleClass().remove("error-message");
        actionResult.setText(message);
        actionResult.setOpacity(1.0);
        if (!animationsEnabled.getAsBoolean()) return;
        FadeTransition fade = new FadeTransition(
                Duration.millis(AnimationSettings.ACTION_SUCCESS_FADE_MILLIS), actionResult);
        fade.setFromValue(0.2);
        fade.setToValue(1.0);
        fade.play();
    }

    private void showActionError(String message) {
        if (!actionResult.getStyleClass().contains("error-message")) actionResult.getStyleClass().add("error-message");
        actionResult.setText(message);
    }

    private String username() { return selectedUser.username(); }
    private int eventId() { return selectedActionEvent.id(); }
    private UserParticipationDTO participationFor(int eventId) {
        return selectedUser.participations().stream().filter(dto -> dto.eventId() == eventId).findFirst().orElse(null);
    }
    private boolean participatesIn(int eventId) { return selectedUser != null && participationFor(eventId) != null; }
    private boolean isSelectedUserMarketMaker(EventDTO event) {
        return selectedUser != null && event.marketMakerUsername().equalsIgnoreCase(selectedUser.username());
    }

    private void clearActionEvent() {
        selectedActionEvent = null;
        selectedActionState = null;
        eventContext.getChildren().setAll(new Label("Select an event to view available actions."));
        hideActions();
    }

    private void hideActions() {
        setShown(startPane, false); setShown(closePane, false);
        setShown(lmsrPane, false); setShown(orderBookPane, false);
    }

    private void clearWorkspace() {
        selectedUser = null;
        selectedParticipationEventId = null;
        marketMakerEvents.getItems().clear();
        participations.getItems().clear();
        actionEvents.getItems().clear();
        detailsContent.setVisible(false);
        detailsContent.setManaged(false);
        emptyDetails.setText(users.isEmpty() ? "Load an XML file to display users." : "Select a user to open their workspace.");
        emptyDetails.setVisible(true);
        emptyDetails.setManaged(true);
    }

    private void showWorkspaceError(String message) {
        detailsContent.setVisible(false);
        detailsContent.setManaged(false);
        emptyDetails.setText(message);
        emptyDetails.setVisible(true);
        emptyDetails.setManaged(true);
    }

    static UserDTO findUser(Iterable<UserDTO> candidates, String username) {
        if (username == null) return null;
        for (UserDTO candidate : candidates) if (candidate.username().equalsIgnoreCase(username)) return candidate;
        return null;
    }
    static EventDTO findEvent(Iterable<EventDTO> candidates, Integer eventId) {
        if (eventId == null) return null;
        for (EventDTO candidate : candidates) if (candidate.id() == eventId) return candidate;
        return null;
    }

    private static String holdingsSummary(UserParticipationDTO dto) {
        return dto.holdingsByOption().entrySet().stream()
                .map(entry -> entry.getKey() + ": " + entry.getValue()).collect(Collectors.joining(" | "));
    }
    private static String statusOf(UserDTO user) { return user.blocked() ? "BLOCKED" : "ACTIVE"; }
    private static String format(double value) { return NUMBER.format(value); }
    static String formatMarketValue(Double value) { return value == null ? "N/A" : format(value); }
    static String formatProfitLoss(Double value) {
        return value == null ? "N/A — available after closure" : format(value);
    }
    private static void setShown(Node node, boolean shown) { node.setVisible(shown); node.setManaged(shown); }
    private static Label heading(String text) { Label label = new Label(text); label.getStyleClass().add("section-title"); return label; }
    private static Label sectionLabel(String text) { Label label = new Label(text); label.getStyleClass().add("detail-subtitle"); return label; }
    private static FlowPane flow(Node... nodes) { FlowPane pane = new FlowPane(8, 7, nodes); pane.setPrefWrapLength(620); return pane; }
    private static GridPane infoGrid() {
        GridPane grid = new GridPane(); grid.setHgap(12); grid.setVgap(6);
        ColumnConstraints labels = new ColumnConstraints(); labels.setMinWidth(130);
        ColumnConstraints values = new ColumnConstraints(); values.setMinWidth(0); values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values); return grid;
    }
    private static void addInfoRow(GridPane grid, int row, String name, Label value) {
        grid.add(new Label(name + ":"), 0, row); value.setWrapText(true); value.setMinWidth(0); grid.add(value, 1, row);
    }
    private static <T> void configureTable(TableView<T> table, String placeholder) {
        table.setPlaceholder(new Label(placeholder));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
    }
    private static <T> TableColumn<T, Object> column(String name, Function<T, Object> value, double width) {
        TableColumn<T, Object> column = new TableColumn<>(name);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setPrefWidth(width); return column;
    }
    private static TitledPane titled(String name, Parent content) {
        TitledPane pane = new TitledPane(name, content); pane.setCollapsible(false); pane.setMaxWidth(Double.MAX_VALUE); return pane;
    }
    private static String messageOf(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? "The operation could not be completed." : error.getMessage();
    }
    @FunctionalInterface private interface Action { Object run(); }
}
