package guessmarket.javafx.controller;

import guessmarket.api.Engine;
import guessmarket.dto.EventDTO;
import guessmarket.dto.UserDTO;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class UsersController {
    private final Engine engine;
    private final SplitPane root = new SplitPane();
    private final ObservableList<UserDTO> users = FXCollections.observableArrayList();
    private final TableView<UserDTO> userTable = new TableView<>(users);
    private final Label emptyDetails = new Label("Select a user to view details.");
    private final VBox detailsContent = new VBox(12);
    private final Label usernameValue = new Label();
    private final Label balanceValue = new Label();
    private final Label statusValue = new Label();
    private final TableView<EventDTO> marketMakerEvents = new TableView<>();
    private String selectedUsername;

    public UsersController(Engine engine) {
        this.engine = engine;
        configureUserTable();
        configureMarketMakerEventsTable();
        root.getItems().addAll(buildUserBrowser(), buildDetails());
        root.setDividerPositions(0.42);
    }

    public Parent getView() {
        return root;
    }

    public void refreshUsers() {
        String previousUsername = selectedUsername;
        users.setAll(engine.getUsers());

        UserDTO previous = previousUsername == null ? null : users.stream()
                .filter(user -> user.username().equalsIgnoreCase(previousUsername))
                .findFirst()
                .orElse(null);

        if (previous != null) {
            userTable.getSelectionModel().select(previous);
        } else if (!users.isEmpty()) {
            userTable.getSelectionModel().selectFirst();
        } else {
            clearDetails();
        }
    }

    private Parent buildUserBrowser() {
        VBox left = new VBox(10, heading("Users"), userTable);
        left.setPadding(new Insets(12));
        VBox.setVgrow(userTable, Priority.ALWAYS);
        return left;
    }

    private void configureUserTable() {
        userTable.setPlaceholder(new Label("Load an XML file to display users."));
        userTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        userTable.getColumns().add(column("Username", UserDTO::username, 170));
        userTable.getColumns().add(column("Balance", user -> format(user.accountBalance()), 110));
        userTable.getColumns().add(column("Status", UsersController::statusOf, 90));
        userTable.getSelectionModel().selectedItemProperty()
                .addListener((observable, oldValue, value) -> selectUser(value));
    }

    private Parent buildDetails() {
        GridPane accountDetails = infoGrid();
        addInfoRow(accountDetails, 0, "Username", usernameValue);
        addInfoRow(accountDetails, 1, "Account balance", balanceValue);
        addInfoRow(accountDetails, 2, "Status", statusValue);

        Label participation = new Label(
                "Trading participation and share holdings are not available yet.");
        participation.setWrapText(true);
        detailsContent.getChildren().addAll(
                titled("Account", accountDetails),
                titled("Market Maker events", marketMakerEvents),
                titled("Future trading participation", participation));
        detailsContent.setVisible(false);
        detailsContent.setManaged(false);
        marketMakerEvents.setPrefHeight(230);
        emptyDetails.setWrapText(true);

        VBox body = new VBox(10, heading("User details"), emptyDetails, detailsContent);
        body.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        return scroll;
    }

    private void configureMarketMakerEventsTable() {
        marketMakerEvents.setPlaceholder(
                new Label("This user is not Market Maker for any events."));
        marketMakerEvents.setColumnResizePolicy(
                TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        marketMakerEvents.getColumns().add(column("Event ID", EventDTO::id, 85));
        marketMakerEvents.getColumns().add(column("Event name", EventDTO::eventName, 210));
        marketMakerEvents.getColumns().add(column("State", EventDTO::eventState, 110));
    }

    private void selectUser(UserDTO summary) {
        if (summary == null) {
            clearDetails();
            return;
        }

        try {
            UserDTO user = engine.getUser(summary.username());
            selectedUsername = user.username();
            usernameValue.setText(user.username());
            balanceValue.setText(format(user.accountBalance()));
            statusValue.setText(statusOf(user));

            Map<Integer, EventDTO> eventsById = engine.getEventSummaries().stream()
                    .collect(Collectors.toMap(EventDTO::id, Function.identity()));
            List<EventDTO> assignments = user.marketMakerEventIds().stream()
                    .map(eventsById::get)
                    .filter(event -> event != null)
                    .toList();
            marketMakerEvents.getItems().setAll(assignments);

            emptyDetails.setVisible(false);
            emptyDetails.setManaged(false);
            detailsContent.setVisible(true);
            detailsContent.setManaged(true);
        } catch (RuntimeException error) {
            clearDetails();
            emptyDetails.setText("User details could not be loaded: " + messageOf(error));
        }
    }

    private void clearDetails() {
        selectedUsername = null;
        marketMakerEvents.getItems().clear();
        detailsContent.setVisible(false);
        detailsContent.setManaged(false);
        emptyDetails.setText(users.isEmpty()
                ? "Load an XML file to display user details."
                : "Select a user to view details.");
        emptyDetails.setVisible(true);
        emptyDetails.setManaged(true);
    }

    private static String statusOf(UserDTO user) {
        return user.blocked() ? "BLOCKED" : "ACTIVE";
    }

    private static String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static Label heading(String title) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        return heading;
    }

    private static GridPane infoGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(6);
        ColumnConstraints labels = new ColumnConstraints();
        labels.setMinWidth(130);
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

    private static TitledPane titled(String title, Parent content) {
        TitledPane pane = new TitledPane(title, content);
        pane.setCollapsible(false);
        pane.setMaxWidth(Double.MAX_VALUE);
        return pane;
    }

    private static <T> TableColumn<T, Object> column(
            String title, Function<T, Object> value, double width) {
        TableColumn<T, Object> column = new TableColumn<>(title);
        column.setCellValueFactory(
                cell -> new ReadOnlyObjectWrapper<>(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private static String messageOf(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? "The operation could not be completed." : error.getMessage();
    }
}
