package guessmarket.javafx;

import guessmarket.api.Engine;
import guessmarket.javafx.controller.MainController;
import guessmarket.service.GuessMarketEngine;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public final class GuessMarketApplication extends Application {
    @Override
    public void start(Stage stage) {
        Engine engine = new GuessMarketEngine();
        MainController controller = new MainController(engine, stage);
        Scene scene = new Scene(controller.getView(), 1280, 800);
        scene.getStylesheets().add(
                GuessMarketApplication.class.getResource("/guessmarket/javafx/view/application.css").toExternalForm());
        Font.getDefault();
        stage.setTitle("Guess Market");
        stage.setMinWidth(760);
        stage.setMinHeight(520);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
