package guessmarket.javafx.controller;

import guessmarket.api.Engine;
import guessmarket.javafx.view.SkinTheme;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;

public final class MainController {
    private final Engine engine;
    private final Stage owner;
    private final BorderPane root = new BorderPane();
    private final Label pathLabel = new Label("No market file loaded");
    private final Label statusLabel = new Label("Choose an XML market file to begin.");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final Button loadButton = new Button("Load File");
    private final EventsController eventsController;
    private final UsersController usersController;
    private final TabPane navigation = new TabPane();
    private final CheckBox skinsEnabled = new CheckBox("Enable skins");
    private final ComboBox<SkinTheme> skinSelector = new ComboBox<>();

    public MainController(Engine engine, Stage owner) {
        this.engine = engine;
        this.owner = owner;
        this.eventsController = new EventsController(engine);
        this.usersController = new UsersController(engine, this::refreshApplication);
        buildView();
    }

    public Parent getView() {
        return root;
    }

    private void buildView() {
        root.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        root.setTop(buildHeader());

        Tab eventsTab = new Tab("Events", eventsController.getView());
        Tab usersTab = new Tab("Users", usersController.getView());
        eventsTab.setClosable(false);
        usersTab.setClosable(false);
        navigation.getTabs().setAll(eventsTab, usersTab);
        navigation.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        root.setCenter(navigation);
    }

    private Parent buildHeader() {
        Label title = new Label("Guess Market");
        title.getStyleClass().add("app-title");
        pathLabel.setMaxWidth(Double.MAX_VALUE);
        pathLabel.setMinWidth(0);
        pathLabel.setEllipsisString("...");
        pathLabel.setTooltip(new Tooltip(pathLabel.getText()));
        HBox.setHgrow(pathLabel, Priority.ALWAYS);

        loadButton.setOnAction(event -> chooseAndLoadXml());
        progress.setPrefSize(26, 26);
        progress.setVisible(false);
        progress.setManaged(false);
        statusLabel.setWrapText(true);

        skinSelector.getItems().setAll(SkinTheme.values());
        skinSelector.setValue(SkinTheme.DEFAULT);
        skinSelector.setDisable(true);
        skinSelector.setId("skin-selector");
        skinsEnabled.setId("skins-enabled");
        skinsEnabled.setOnAction(event -> updateSkin());
        skinSelector.setOnAction(event -> updateSkin());

        Label skinLabel = new Label("Skin:");
        HBox skinControls = new HBox(8, skinsEnabled, skinLabel, skinSelector);
        skinControls.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        skinControls.getStyleClass().add("skin-controls");

        HBox fileRow = new HBox(10, loadButton, progress, new Label("Current file:"), pathLabel);
        fileRow.setFillHeight(true);
        VBox header = new VBox(8, title, fileRow, statusLabel, skinControls);
        header.setPadding(new Insets(14, 18, 10, 18));
        header.getStyleClass().add("app-header");
        return header;
    }

    private void updateSkin() {
        skinSelector.setDisable(!skinsEnabled.isSelected());
        root.getStylesheets().removeIf(MainController::isSkinStylesheet);
        SkinTheme theme = SkinTheme.effectiveTheme(skinsEnabled.isSelected(), skinSelector.getValue());
        if (theme.stylesheet() != null) {
            root.getStylesheets().add(MainController.class.getResource(theme.stylesheet()).toExternalForm());
        }
    }

    private static boolean isSkinStylesheet(String stylesheet) {
        return stylesheet.endsWith("/ocean.css") || stylesheet.endsWith("/dusk.css");
    }

    private void chooseAndLoadXml() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Guess Market XML");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("XML files", "*.xml"));
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }

        Task<Void> loadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Loading and validating market...");
                updateProgress(-1, 1);
                Thread.sleep(1_300);
                engine.loadMarketFromXml(file.getAbsolutePath());
                return null;
            }
        };

        loadButton.setDisable(true);
        navigation.setDisable(true);
        progress.setManaged(true);
        progress.setVisible(true);
        statusLabel.textProperty().bind(loadTask.messageProperty());

        loadTask.setOnSucceeded(event -> {
            finishLoading();
            pathLabel.setText(file.getAbsolutePath());
            pathLabel.getTooltip().setText(file.getAbsolutePath());
            statusLabel.setText("Market loaded successfully.");
            refreshApplication();
        });
        loadTask.setOnFailed(event -> {
            finishLoading();
            statusLabel.setText("The market could not be loaded. The previous market is unchanged.");
            showError("Could not load market", messageOf(loadTask.getException()));
        });

        Thread worker = new Thread(loadTask, "guessmarket-xml-loader");
        worker.setDaemon(true);
        worker.start();
    }

    private void refreshApplication() {
        usersController.refreshUsers();
        eventsController.refreshEvents();
    }

    private void finishLoading() {
        statusLabel.textProperty().unbind();
        loadButton.setDisable(false);
        navigation.setDisable(false);
        progress.setVisible(false);
        progress.setManaged(false);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.showAndWait();
    }

    private static String messageOf(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return "An unexpected error occurred.";
        }
        return error.getMessage();
    }
}
