package guessmarket.javafx.controller;

import guessmarket.api.Engine;
import guessmarket.dto.*;
import guessmarket.javafx.view.LmsrValueFormatter;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.function.Function;

public final class EventsController {
    private static final String ALL = "All";
    private static final DecimalFormat NUMBER = new DecimalFormat(
            "0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private final Engine engine;
    private final SplitPane root = new SplitPane();
    private final ObservableList<EventDTO> events = FXCollections.observableArrayList();
    private final FilteredList<EventDTO> filteredEvents = new FilteredList<>(events);
    private final TableView<EventDTO> eventTable = new TableView<>(filteredEvents);
    private final ComboBox<String> methodFilter = new ComboBox<>();
    private final ComboBox<String> stateFilter = new ComboBox<>();
    private final ComboBox<String> commissionFilter = new ComboBox<>();
    private final Label emptyDetails = new Label("Load a market, then select an event to monitor it.");
    private final VBox detailsContent = new VBox(12);
    private final Label idValue = new Label();
    private final Label nameValue = new Label();
    private final Label descriptionValue = new Label();
    private final Label stateValue = new Label();
    private final Label methodValue = new Label();
    private final Label marketMakerValue = new Label();
    private final Label commissionValue = new Label();
    private final Label balanceValue = new Label();
    private final Label collectedValue = new Label();
    private final Label optionsValue = new Label();
    private final Label winnerValue = new Label();
    private final VBox methodDetails = new VBox();
    private final VBox participantsArea = new VBox();
    private EventDTO selectedEvent;

    public EventsController(Engine engine) {
        this.engine = engine;
        configureFilters();
        configureEventTable();
        root.getItems().addAll(buildEventBrowser(), buildDetails());
        root.setDividerPositions(0.43);
    }

    public Parent getView() { return root; }

    public void refreshEvents() {
        Integer selectedId = selectedEvent == null ? null : selectedEvent.id();
        events.setAll(engine.getEventSummaries());
        updateFilterPredicate();
        restoreSelection(selectedId);
    }

    private void configureFilters() {
        methodFilter.setItems(FXCollections.observableArrayList(ALL, "LMSR", "ORDER_BOOK"));
        stateFilter.setItems(FXCollections.observableArrayList(ALL, "NOT_STARTED", "ACTIVE", "CLOSED"));
        commissionFilter.setItems(FXCollections.observableArrayList(ALL, "ON_PURCHASE", "ON_CLOSE"));
        methodFilter.getSelectionModel().selectFirst();
        stateFilter.getSelectionModel().selectFirst();
        commissionFilter.getSelectionModel().selectFirst();
        methodFilter.setOnAction(event -> applyFiltersAndSelection());
        stateFilter.setOnAction(event -> applyFiltersAndSelection());
        commissionFilter.setOnAction(event -> applyFiltersAndSelection());
    }

    private Parent buildEventBrowser() {
        FlowPane filters = new FlowPane(8, 6,
                filterControl("Trading method", methodFilter), filterControl("State", stateFilter),
                filterControl("Commission", commissionFilter));
        filters.setPrefWrapLength(440);
        VBox left = new VBox(10, heading("Events overview"), filters, eventTable);
        left.setPadding(new Insets(12));
        left.setMinWidth(0);
        VBox.setVgrow(eventTable, Priority.ALWAYS);
        return left;
    }

    private void configureEventTable() {
        eventTable.setPlaceholder(new Label("Load an XML file to display events."));
        eventTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        eventTable.getColumns().add(column("ID", EventDTO::id, 50));
        eventTable.getColumns().add(column("Name", EventDTO::eventName, 140));
        eventTable.getColumns().add(column("State", EventDTO::eventState, 100));
        eventTable.getColumns().add(column("Method", EventDTO::tradingMethod, 105));
        eventTable.getColumns().add(column("Commission",
                dto -> dto.commissionMethod() + " " + dto.commissionPercentage() + "%", 150));
        eventTable.getColumns().add(column("Account", dto -> format(dto.currentEventAccountBalance()), 90));
        eventTable.getColumns().add(column("Market Maker", EventDTO::marketMakerUsername, 110));
        eventTable.getColumns().add(column("Options", dto -> String.join(" / ", dto.options()), 150));
        eventTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, value) -> selectEvent(value));
    }

    private Parent buildDetails() {
        detailsContent.getChildren().addAll(buildCommonDetails(), methodDetails, participantsArea);
        detailsContent.setVisible(false);
        detailsContent.setManaged(false);
        emptyDetails.setWrapText(true);
        VBox body = new VBox(10, heading("Event monitoring"), emptyDetails, detailsContent);
        body.setPadding(new Insets(12));
        body.setMinWidth(0);
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        return scroll;
    }

    private Parent buildCommonDetails() {
        descriptionValue.setWrapText(true);
        optionsValue.setWrapText(true);
        GridPane grid = infoGrid();
        addInfoRow(grid, 0, "Event ID", idValue);
        addInfoRow(grid, 1, "Name", nameValue);
        addInfoRow(grid, 2, "Description", descriptionValue);
        addInfoRow(grid, 3, "State", stateValue);
        addInfoRow(grid, 4, "Trading method", methodValue);
        addInfoRow(grid, 5, "Market Maker", marketMakerValue);
        addInfoRow(grid, 6, "Commission", commissionValue);
        addInfoRow(grid, 7, "Event account balance", balanceValue);
        addInfoRow(grid, 8, "Commission collected", collectedValue);
        addInfoRow(grid, 9, "Options", optionsValue);
        addInfoRow(grid, 10, "Winning option", winnerValue);
        return titled("Common event details", grid);
    }

    private Parent buildLmsrDetails(LmsrDetailsDTO details) {
        TableView<OptionStateDTO> options = new TableView<>();
        configureTable(options, "No LMSR option state is available.");
        options.getColumns().add(column("Option", OptionStateDTO::optionName, 150));
        options.getColumns().add(column(
                "Current value", dto -> LmsrValueFormatter.format(dto.currentOptionValue()), 120));
        options.getColumns().add(column("Total purchased", OptionStateDTO::quantityBought, 125));
        options.getItems().setAll(details.options());
        options.setPrefHeight(150);
        TableView<TradeDTO> trades = new TableView<>();
        configureTable(trades, "No LMSR trades have been made.");
        trades.getColumns().add(column("Option", TradeDTO::boughtOptionName, 150));
        trades.getColumns().add(column("Quantity", TradeDTO::quantity, 100));
        trades.getColumns().add(column("Purchase cost", dto -> format(dto.purchaseCost()), 125));
        trades.getItems().setAll(details.trades());
        trades.setPrefHeight(190);
        return titled("LMSR market details", new VBox(10,
                labeled("Options", options), labeled("Global trade history — newest first", trades)));
    }

    private Parent buildOrderBookDetails(OrderBookDetailsDTO details) {
        GridPane configuration = infoGrid();
        addInfoRow(configuration, 0, "d", new Label(Integer.toString(details.d())));
        addInfoRow(configuration, 1, "Initial", new Label(Integer.toString(details.initial())));
        addInfoRow(configuration, 2, "Allow mint", new Label(details.allowMint() ? "Yes" : "No"));
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        for (OrderBookOptionDTO option : details.optionBooks()) {
            tabs.getTabs().add(new Tab(option.optionName(), buildOptionBook(option)));
        }
        tabs.setPrefHeight(500);
        return titled("Order Book details", new VBox(12, labeled("Configuration", configuration), tabs));
    }

    private Parent buildOptionBook(OrderBookOptionDTO option) {
        GridPane statistics = infoGrid();
        addInfoRow(statistics, 0, "LAST", new Label(formatNullable(option.last())));
        addInfoRow(statistics, 1, "BID", new Label(formatNullable(option.bid())));
        addInfoRow(statistics, 2, "ASK", new Label(formatNullable(option.ask())));
        addInfoRow(statistics, 3, "MID", new Label(formatNullable(option.mid())));
        addInfoRow(statistics, 4, "SPREAD", new Label(formatNullable(option.spread())));
        TableView<PendingOrderDTO> buys = pendingOrdersTable("No pending BUY orders.");
        buys.getItems().setAll(option.pendingBuyOrders());
        TableView<PendingOrderDTO> sells = pendingOrdersTable("No pending SELL orders.");
        sells.getItems().setAll(option.pendingSellOrders());
        Label buyTitle = heading("Pending BUY orders");
        buyTitle.getStyleClass().add("buy-side");
        Label sellTitle = heading("Pending SELL orders");
        sellTitle.getStyleClass().add("sell-side");
        VBox content = new VBox(10, labeled("Statistics", statistics), buyTitle, buys, sellTitle, sells);
        content.setPadding(new Insets(10));
        buys.setPrefHeight(155);
        sells.setPrefHeight(155);
        return content;
    }

    private TableView<PendingOrderDTO> pendingOrdersTable(String placeholder) {
        TableView<PendingOrderDTO> table = new TableView<>();
        configureTable(table, placeholder);
        table.getColumns().add(column("Username", PendingOrderDTO::username, 150));
        table.getColumns().add(column("Remaining quantity", PendingOrderDTO::remainingQuantity, 145));
        table.getColumns().add(column("Price/share", dto -> format(dto.pricePerShare()), 115));
        return table;
    }

    private Parent buildParticipants(EventStateDTO state) {
        String first = state.options().get(0);
        String second = state.options().get(1);
        TableView<EventParticipantDTO> table = new TableView<>();
        configureTable(table, "No participants yet.");
        table.getColumns().add(column("Username", EventParticipantDTO::username, 130));
        table.getColumns().add(column(first + " quantity", dto -> dto.holdingsByOption().get(first), 125));
        table.getColumns().add(column(first + " value",
                dto -> formatNullable(dto.currentHoldingValueByOption().get(first)), 115));
        table.getColumns().add(column(second + " quantity", dto -> dto.holdingsByOption().get(second), 125));
        table.getColumns().add(column(second + " value",
                dto -> formatNullable(dto.currentHoldingValueByOption().get(second)), 115));
        table.getColumns().add(column("Reserved / available",
                dto -> compactQuantities(dto, first, second), 210));
        table.getColumns().add(column("Cash summary", EventsController::cashSummary, 215));
        table.getItems().setAll(state.participants());
        table.setPrefHeight(220);
        return titled("Event participants", table);
    }

    private static <T> void configureTable(TableView<T> table, String placeholder) {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label(placeholder));
    }

    private void selectEvent(EventDTO event) {
        if (event == null) return;
        selectedEvent = event;
        refreshSelectedDetails();
    }

    private void refreshSelectedDetails() {
        if (selectedEvent == null) return;
        try {
            EventStateDTO state = engine.getEventState(selectedEvent.id());
            idValue.setText(Integer.toString(state.id()));
            nameValue.setText(state.eventName());
            descriptionValue.setText(state.description());
            stateValue.setText(state.eventState());
            methodValue.setText(state.tradingMethod());
            marketMakerValue.setText(state.marketMakerUsername());
            commissionValue.setText(state.commissionMethod() + " — " + state.commissionPercentage() + "%");
            balanceValue.setText(format(state.currentEventAccountBalance()));
            collectedValue.setText(format(state.totalCommissionCollected()));
            optionsValue.setText(String.join(" / ", state.options()));
            winnerValue.setText(state.winningOption() == null ? "Not closed yet" : state.winningOption());
            methodDetails.getChildren().clear();
            if ("LMSR".equals(state.tradingMethod()) && state.lmsrDetails() != null) {
                methodDetails.getChildren().add(buildLmsrDetails(state.lmsrDetails()));
            } else if ("ORDER_BOOK".equals(state.tradingMethod()) && state.orderBookDetails() != null) {
                methodDetails.getChildren().add(buildOrderBookDetails(state.orderBookDetails()));
            } else {
                methodDetails.getChildren().add(new Label("Method-specific details are unavailable."));
            }
            participantsArea.getChildren().setAll(buildParticipants(state));
            emptyDetails.setVisible(false);
            emptyDetails.setManaged(false);
            detailsContent.setVisible(true);
            detailsContent.setManaged(true);
        } catch (RuntimeException error) {
            showDetailsError(error);
        }
    }

    private void applyFiltersAndSelection() {
        Integer selectedId = selectedEvent == null ? null : selectedEvent.id();
        updateFilterPredicate();
        restoreSelection(selectedId);
    }

    private void updateFilterPredicate() {
        filteredEvents.setPredicate(event -> matchesFilters(
                event, methodFilter.getValue(), stateFilter.getValue(), commissionFilter.getValue()));
        eventTable.setPlaceholder(new Label(events.isEmpty()
                ? "Load an XML file to display events." : "No events match the selected filters."));
    }

    static boolean matchesFilters(EventDTO event, String method, String state, String commission) {
        return matches(method, event.tradingMethod()) && matches(state, event.eventState())
                && matches(commission, event.commissionMethod());
    }

    private static boolean matches(String selected, String actual) {
        return selected == null || ALL.equals(selected) || selected.equals(actual);
    }

    private void restoreSelection(Integer preferredId) {
        EventDTO preferred = findPreferred(filteredEvents, preferredId);
        if (preferred != null) {
            selectedEvent = preferred;
            eventTable.getSelectionModel().select(preferred);
            refreshSelectedDetails();
        } else if (!filteredEvents.isEmpty()) {
            eventTable.getSelectionModel().selectFirst();
        } else {
            clearDetails();
        }
    }

    static EventDTO findPreferred(Iterable<EventDTO> candidates, Integer preferredId) {
        if (preferredId == null) return null;
        for (EventDTO candidate : candidates) {
            if (candidate.id() == preferredId) return candidate;
        }
        return null;
    }

    private void clearDetails() {
        selectedEvent = null;
        methodDetails.getChildren().clear();
        participantsArea.getChildren().clear();
        detailsContent.setVisible(false);
        detailsContent.setManaged(false);
        emptyDetails.setText(filteredEvents.isEmpty() && !events.isEmpty()
                ? "No events match the selected filters."
                : "Load a market, then select an event to monitor it.");
        emptyDetails.setVisible(true);
        emptyDetails.setManaged(true);
    }

    private void showDetailsError(RuntimeException error) {
        detailsContent.setVisible(false);
        detailsContent.setManaged(false);
        emptyDetails.setText("Event details could not be loaded: " + messageOf(error));
        emptyDetails.setVisible(true);
        emptyDetails.setManaged(true);
    }

    private static String compactQuantities(EventParticipantDTO dto, String first, String second) {
        return first + ": " + dto.reservedSellByOption().get(first) + " / "
                + dto.availableToSellByOption().get(first) + "  |  " + second + ": "
                + dto.reservedSellByOption().get(second) + " / " + dto.availableToSellByOption().get(second);
    }

    private static String cashSummary(EventParticipantDTO dto) {
        return "Paid " + format(dto.totalCashPaid()) + " | Received " + format(dto.totalCashReceived())
                + " | Commission " + format(dto.totalCommissionPaid());
    }

    private static GridPane infoGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(130);
        ColumnConstraints values = new ColumnConstraints();
        values.setMinWidth(0);
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labels, values);
        return grid;
    }

    private static void addInfoRow(GridPane grid, int row, String label, Label value) {
        grid.add(new Label(label + ":"), 0, row);
        value.setMaxWidth(Double.MAX_VALUE);
        value.setMinWidth(0);
        grid.add(value, 1, row);
    }

    private static VBox filterControl(String label, ComboBox<String> control) {
        control.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(3, new Label(label), control);
        box.setPrefWidth(145);
        return box;
    }

    private static VBox labeled(String title, Node content) {
        Label label = new Label(title);
        label.getStyleClass().add("detail-subtitle");
        VBox box = new VBox(6, label, content);
        VBox.setVgrow(content, Priority.ALWAYS);
        return box;
    }

    private static Label heading(String title) {
        Label label = new Label(title);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static <T> TableColumn<T, Object> column(
            String title, Function<T, Object> value, double width) {
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

    private static String format(double value) { return NUMBER.format(value); }
    private static String formatNullable(Double value) { return value == null ? "N/A" : format(value); }
    private static String messageOf(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? "The operation could not be completed." : error.getMessage();
    }
}
