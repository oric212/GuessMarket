package guessmarket.javafx.controller;

import guessmarket.api.Engine;
import guessmarket.dto.*;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Locale;
import java.util.function.Function;

public final class EventsController {
    private final Engine engine;
    private final SplitPane root = new SplitPane();
    private final ObservableList<EventDTO> events = FXCollections.observableArrayList();
    private final FilteredList<EventDTO> filteredEvents = new FilteredList<>(events);
    private final TableView<EventDTO> eventTable = new TableView<>(filteredEvents);
    private final ComboBox<String> statusFilter = new ComboBox<>();
    private final ComboBox<String> commissionFilter = new ComboBox<>();

    private final Label emptyDetails = new Label("Select an event to view details and trading controls.");
    private final VBox detailsContent = new VBox(12);
    private final Label idValue = new Label();
    private final Label nameValue = new Label();
    private final Label descriptionValue = new Label();
    private final Label statusValue = new Label();
    private final Label commissionValue = new Label();
    private final Label winnerValue = new Label();
    private final Label balanceValue = new Label();
    private final Label collectedValue = new Label();
    private final TableView<OptionStateDTO> optionsTable = new TableView<>();
    private final TableView<TradeDTO> tradesTable = new TableView<>();
    private final ComboBox<String> purchaseOption = new ComboBox<>();
    private final TextField quantityField = new TextField();
    private final Label purchaseResult = new Label();
    private final ComboBox<String> winningOption = new ComboBox<>();
    private EventDTO selectedEvent;

    public EventsController(Engine engine) {
        this.engine = engine;
        configureEventTable();
        configureDetailTables();
        root.getItems().addAll(buildEventBrowser(), buildDetails());
        root.setDividerPositions(0.38);
    }

    public Parent getView() {
        return root;
    }

    public void refreshEvents() {
        Integer selectedId = selectedEvent == null ? null : selectedEvent.id();
        events.setAll(engine.getEventSummaries());
        updateFilterPredicate();
        restoreSelection(selectedId);
    }

    private Parent buildEventBrowser() {
        Label title = new Label("Events");
        title.getStyleClass().add("section-title");

        ComboBox<String> methodFilter = new ComboBox<>(FXCollections.observableArrayList("LMSR"));
        methodFilter.getSelectionModel().selectFirst();
        methodFilter.setDisable(true);
        methodFilter.setTooltip(new Tooltip("LMSR is the only method available in Exercise 01."));
        statusFilter.setItems(FXCollections.observableArrayList("All statuses", "NOT_STARTED", "ACTIVE", "CLOSED"));
        commissionFilter.setItems(FXCollections.observableArrayList("All commission methods", "ON_PURCHASE", "ON_CLOSE"));
        statusFilter.getSelectionModel().selectFirst();
        commissionFilter.getSelectionModel().selectFirst();
        statusFilter.setOnAction(event -> applyFiltersAndSelection());
        commissionFilter.setOnAction(event -> applyFiltersAndSelection());

        FlowPane filters = new FlowPane(8, 6,
                filterControl("Method", methodFilter),
                filterControl("Status", statusFilter),
                filterControl("Commission", commissionFilter));
        filters.setPrefWrapLength(420);

        VBox left = new VBox(10, title, filters, eventTable);
        left.setPadding(new Insets(12));
        VBox.setVgrow(eventTable, Priority.ALWAYS);
        return left;
    }

    private void configureEventTable() {
        eventTable.setPlaceholder(new Label("Load an XML file to display events."));
        eventTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        eventTable.getColumns().add(column("ID", dto -> dto.id(), 55));
        eventTable.getColumns().add(column("Name", EventDTO::eventName, 160));
        eventTable.getColumns().add(column("Description", EventDTO::description, 210));
        eventTable.getColumns().add(column("Status", EventDTO::eventState, 100));
        eventTable.getColumns().add(column("Commission", dto -> dto.commissionPercentage() + "% " + dto.commissionMethod(), 145));
        eventTable.getColumns().add(column("Options", dto -> String.join(", ", dto.options()), 180));
        eventTable.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, value) -> selectEvent(value));
    }

    private Parent buildDetails() {
        Label title = new Label("Event details and trade");
        title.getStyleClass().add("section-title");
        detailsContent.getChildren().addAll(
                buildGeneralDetails(), buildCurrentState(), buildActions(), buildTradeHistory(), buildFutureAreas());
        detailsContent.setPadding(new Insets(4));
        detailsContent.setVisible(false);
        detailsContent.setManaged(false);
        emptyDetails.setWrapText(true);

        VBox body = new VBox(10, title, emptyDetails, detailsContent);
        body.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        return scroll;
    }

    private Parent buildGeneralDetails() {
        descriptionValue.setWrapText(true);
        winnerValue.setWrapText(true);
        GridPane grid = infoGrid();
        addInfoRow(grid, 0, "Event ID", idValue);
        addInfoRow(grid, 1, "Name", nameValue);
        addInfoRow(grid, 2, "Description", descriptionValue);
        addInfoRow(grid, 3, "Status", statusValue);
        addInfoRow(grid, 4, "Commission", commissionValue);
        addInfoRow(grid, 5, "Winning option", winnerValue);
        return titled("General", grid);
    }

    private Parent buildCurrentState() {
        GridPane accounts = infoGrid();
        addInfoRow(accounts, 0, "Event account balance", balanceValue);
        addInfoRow(accounts, 1, "Total commission collected", collectedValue);
        VBox box = new VBox(8, accounts, optionsTable);
        optionsTable.setPrefHeight(190);
        VBox.setVgrow(optionsTable, Priority.ALWAYS);
        return titled("Current LMSR state and account", box);
    }

    private Parent buildActions() {
        quantityField.setPromptText("Positive whole number");
        Button purchaseButton = new Button("Purchase shares");
        purchaseButton.setOnAction(event -> purchase());
        purchaseResult.setWrapText(true);
        purchaseResult.getStyleClass().add("result-message");
        GridPane purchase = new GridPane();
        purchase.setHgap(8);
        purchase.setVgap(8);
        purchase.addRow(0, new Label("Option"), purchaseOption);
        purchase.addRow(1, new Label("Quantity"), quantityField);
        purchase.add(purchaseButton, 1, 2);
        purchase.add(purchaseResult, 0, 3, 2, 1);

        Button closeButton = new Button("Close event");
        closeButton.setOnAction(event -> closeEvent());
        GridPane close = new GridPane();
        close.setHgap(8);
        close.setVgap(8);
        close.addRow(0, new Label("Winning option"), winningOption);
        close.add(closeButton, 1, 1);

        TitledPane purchasePane = titled("Purchase shares", purchase);
        TitledPane closePane = titled("Close event", close);
        VBox actions = new VBox(12, purchasePane, closePane);
        VBox.setVgrow(purchasePane, Priority.ALWAYS);
        VBox.setVgrow(closePane, Priority.ALWAYS);
        purchasePane.setMaxWidth(Double.MAX_VALUE);
        closePane.setMaxWidth(Double.MAX_VALUE);
        return actions;
    }

    private Parent buildTradeHistory() {
        tradesTable.setPrefHeight(190);
        return titled("Trade history", tradesTable);
    }

    private Parent buildFutureAreas() {
        TabPane future = new TabPane(
                futureTab("Option order books", "Order Books will be added with Exercise 02 trading support."),
                futureTab("Participations", "Participation and ownership information will be added with user support."));
        future.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        future.setPrefHeight(120);
        return titled("Exercise 02 extension areas", future);
    }

    private void configureDetailTables() {
        optionsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        optionsTable.setPlaceholder(new Label("No options available."));
        optionsTable.getColumns().add(column("Option", OptionStateDTO::optionName, 170));
        optionsTable.getColumns().add(column("Current value", dto -> format(dto.currentOptionValue()), 110));
        optionsTable.getColumns().add(column("Total purchased", OptionStateDTO::quantityBought, 120));

        tradesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tradesTable.setPlaceholder(new Label("No trades have been made for this event."));
        tradesTable.getColumns().add(column("Option", TradeDTO::boughtOptionName, 170));
        tradesTable.getColumns().add(column("Quantity", TradeDTO::quantity, 100));
        tradesTable.getColumns().add(column("Purchase cost", dto -> format(dto.purchaseCost()), 120));
    }

    private void selectEvent(EventDTO event) {
        if (event == null) {
            clearDetails();
            return;
        }
        selectedEvent = event;
        refreshSelectedDetails();
    }

    private void refreshSelectedDetails() {
        if (selectedEvent == null) return;
        try {
            EventDTO summary = events.stream().filter(item -> item.id() == selectedEvent.id()).findFirst().orElse(selectedEvent);
            EventStateDTO state = engine.getEventState(summary.id());
            idValue.setText(Integer.toString(summary.id()));
            nameValue.setText(summary.eventName());
            descriptionValue.setText(summary.description());
            statusValue.setText(state.eventState());
            commissionValue.setText(summary.commissionPercentage() + "% (" + summary.commissionMethod() + ")");
            winnerValue.setText(state.winningOption() == null ? "Not available" : state.winningOption());
            balanceValue.setText(format(state.currentEventAccountBalance()));
            collectedValue.setText(format(state.totalCommissionCollected()));
            optionsTable.getItems().setAll(state.optionStateDTOList());
            tradesTable.getItems().setAll(state.trades());
            purchaseOption.getItems().setAll(summary.options());
            winningOption.getItems().setAll(summary.options());
            purchaseOption.getSelectionModel().selectFirst();
            winningOption.getSelectionModel().selectFirst();
            emptyDetails.setText("Select an event to view details and trading controls.");
            emptyDetails.setVisible(false);
            emptyDetails.setManaged(false);
            detailsContent.setVisible(true);
            detailsContent.setManaged(true);
        } catch (RuntimeException error) {
            showDetailsError(error);
        }
    }

    private void purchase() {
        if (selectedEvent == null) {
            setResultError("Select an event first.");
            return;
        }
        int optionIndex = purchaseOption.getSelectionModel().getSelectedIndex();
        if (optionIndex < 0) {
            setResultError("Select an option.");
            return;
        }
        try {
            int quantity = Integer.parseInt(quantityField.getText().trim());
            if (quantity <= 0) throw new IllegalArgumentException("Quantity must be a positive whole number.");
            PurchaseResultDTO result = engine.purchaseShares(selectedEvent.id(), optionIndex + 1, quantity);
            purchaseResult.getStyleClass().remove("error-message");
            purchaseResult.setText("Purchase cost: " + format(result.purchaseCost())
                    + " | Commission: " + format(result.commission())
                    + " | Total paid: " + format(result.totalPricePaid()));
            refreshEvents();
        } catch (NumberFormatException error) {
            setResultError("Quantity must be a valid whole number.");
        } catch (RuntimeException error) {
            setResultError(messageOf(error));
        }
    }

    private void closeEvent() {
        if (selectedEvent == null) {
            setResultError("Select an event first.");
            return;
        }
        int optionIndex = winningOption.getSelectionModel().getSelectedIndex();
        if (optionIndex < 0) {
            setResultError("Select a winning option.");
            return;
        }
        try {
            engine.closeEvent(selectedEvent.id(), optionIndex + 1);
            purchaseResult.getStyleClass().remove("error-message");
            purchaseResult.setText("Event closed successfully.");
            refreshEvents();
        } catch (RuntimeException error) {
            setResultError(messageOf(error));
        }
    }

    private void applyFiltersAndSelection() {
        Integer selectedId = selectedEvent == null ? null : selectedEvent.id();
        updateFilterPredicate();
        restoreSelection(selectedId);
    }

    private void updateFilterPredicate() {
        String status = statusFilter.getValue();
        String commission = commissionFilter.getValue();
        filteredEvents.setPredicate(event ->
                (status == null || status.startsWith("All") || status.equals(event.eventState()))
                        && (commission == null || commission.startsWith("All") || commission.equals(event.commissionMethod())));
    }

    private void restoreSelection(Integer preferredId) {
        EventDTO preferred = preferredId == null ? null : filteredEvents.stream()
                .filter(item -> item.id() == preferredId)
                .findFirst()
                .orElse(null);
        if (preferred != null) {
            eventTable.getSelectionModel().select(preferred);
        } else if (!filteredEvents.isEmpty()) {
            eventTable.getSelectionModel().selectFirst();
        } else {
            clearDetails();
        }
    }

    private void clearDetails() {
        selectedEvent = null;
        eventTable.getSelectionModel().clearSelection();
        emptyDetails.setText(filteredEvents.isEmpty() && !events.isEmpty()
                ? "No events match the selected filters."
                : "Select an event to view details and trading controls.");
        detailsContent.setVisible(false);
        detailsContent.setManaged(false);
        emptyDetails.setVisible(true);
        emptyDetails.setManaged(true);
    }

    private void showDetailsError(RuntimeException error) {
        selectedEvent = null;
        detailsContent.setVisible(false);
        detailsContent.setManaged(false);
        emptyDetails.setText("Event details could not be loaded: " + messageOf(error));
        emptyDetails.setVisible(true);
        emptyDetails.setManaged(true);
    }

    private void setResultError(String message) {
        if (!purchaseResult.getStyleClass().contains("error-message")) {
            purchaseResult.getStyleClass().add("error-message");
        }
        purchaseResult.setText(message);
    }

    private static GridPane infoGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(145);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values);
        return grid;
    }

    private static void addInfoRow(GridPane grid, int row, String label, Label value) {
        grid.add(new Label(label + ":"), 0, row);
        value.setMaxWidth(Double.MAX_VALUE);
        grid.add(value, 1, row);
    }

    private static VBox filterControl(String label, ComboBox<String> control) {
        control.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(3, new Label(label), control);
        box.setPrefWidth(135);
        return box;
    }

    private static <T> TableColumn<T, Object> column(String title, Function<T, Object> value, double width) {
        TableColumn<T, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static TitledPane titled(String title, Parent content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setCollapsible(false);
        pane.setMaxWidth(Double.MAX_VALUE);
        return pane;
    }

    private static Tab futureTab(String title, String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setPadding(new Insets(14));
        return new Tab(title, label);
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static String messageOf(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? "The operation could not be completed." : error.getMessage();
    }
}
