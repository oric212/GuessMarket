package guessmarket.javafx.controller;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;

public final class UsersController {
    private final SplitPane root = new SplitPane();

    public UsersController() {
        VBox usersTable = placeholder("Users Table",
                "Users will appear here after Exercise 02 user support is implemented.");
        VBox userDetails = new VBox(12,
                heading("Single User Details"),
                futureSection("Account Balance"),
                futureSection("Events Participation / Owner"),
                futureSection("Single Event Details and Trade"),
                wrapped("User management will be available after Exercise 02 user support is implemented."));
        userDetails.setPadding(new Insets(20));
        root.getItems().addAll(scrolling(usersTable), scrolling(userDetails));
        root.setDividerPositions(0.36);
    }

    public Parent getView() {
        return root;
    }

    private static VBox placeholder(String title, String message) {
        VBox box = new VBox(12, heading(title), wrapped(message));
        box.setPadding(new Insets(20));
        return box;
    }

    private static Label heading(String title) {
        Label heading = new Label(title);
        heading.getStyleClass().add("section-title");
        return heading;
    }

    private static Label wrapped(String message) {
        Label text = new Label(message);
        text.setWrapText(true);
        return text;
    }

    private static TitledPane futureSection(String title) {
        TitledPane pane = new TitledPane(title, wrapped("No user data is available in the current engine."));
        pane.setCollapsible(false);
        pane.setMaxWidth(Double.MAX_VALUE);
        return pane;
    }

    private static ScrollPane scrolling(VBox content) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPannable(true);
        return scroll;
    }
}
