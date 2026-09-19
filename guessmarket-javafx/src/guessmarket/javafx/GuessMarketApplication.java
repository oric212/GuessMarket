package guessmarket.javafx;

import guessmarket.javafx.client.GuessMarketApiClient;
import guessmarket.javafx.controller.MainController;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public final class GuessMarketApplication extends Application {
    @Override
    public void start(Stage stage) {
        stage.setTitle("Guess Market Login");
        stage.setMinWidth(420);
        stage.setMinHeight(280);
        stage.setScene(loginScene(stage));
        stage.show();
    }

    private Scene loginScene(Stage stage) {
        GuessMarketApiClient client = new GuessMarketApiClient();
        Label title = new Label("Guess Market");
        title.getStyleClass().add("app-title");
        TextField username = new TextField();
        username.setPromptText("Username");
        Button login = new Button("Login");
        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(28, 28);
        progress.setVisible(false);
        Label error = new Label();
        error.getStyleClass().add("error-message");
        error.setWrapText(true);
        Runnable submit = () -> {
            if (username.getText() == null || username.getText().isBlank()) {
                error.setText("Username is required.");
                return;
            }
            Task<Void> task = new Task<>() {
                @Override protected Void call() { client.login(username.getText()); return null; }
            };
            login.setDisable(true); username.setDisable(true); progress.setVisible(true); error.setText("");
            task.setOnSucceeded(event -> showMain(stage, client));
            task.setOnFailed(event -> {
                login.setDisable(false); username.setDisable(false); progress.setVisible(false);
                error.setText(messageOf(task.getException()));
            });
            Thread worker = new Thread(task, "guessmarket-login");
            worker.setDaemon(true); worker.start();
        };
        login.setOnAction(event -> submit.run());
        username.setOnAction(event -> submit.run());
        VBox root = new VBox(12, title, new Label("Username"), username, login, progress, error);
        root.setAlignment(Pos.CENTER); root.setPadding(new Insets(28)); root.setMaxWidth(Double.MAX_VALUE);
        Scene scene = new Scene(root, 480, 340);
        addStylesheet(scene);
        return scene;
    }

    private void showMain(Stage stage, GuessMarketApiClient client) {
        MainController controller = new MainController(client, stage, client.username());
        Scene scene = new Scene(controller.getView(), 1280, 800);
        addStylesheet(scene);
        Font.getDefault();
        stage.setTitle("Guess Market — " + client.username());
        stage.setMinWidth(560);
        stage.setMinHeight(400);
        stage.setScene(scene);
    }

    private static void addStylesheet(Scene scene) {
        scene.getStylesheets().add(
                GuessMarketApplication.class.getResource("/guessmarket/javafx/view/application.css").toExternalForm());
    }

    private static String messageOf(Throwable error) {
        return error == null || error.getMessage() == null ? "Login failed." : error.getMessage();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
