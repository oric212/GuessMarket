package guessmarket.UI;

import guessmarket.engine.domain.Engine;
import guessmarket.engine.domain.GuessMarketEngine;

import java.io.InputStream;
import java.util.Scanner;

public class UIConsole implements UI{

    private UiState currentState = UiState.StartMainMenu;
    private Scanner userInput;
    private Engine engine;
    public UIConsole() {
        this.engine = new GuessMarketEngine();
        this.userInput = new Scanner(System.in);
    }


    @Override
    public void run() {
        while (currentState != null && currentState != currentState.EXIT) {
            try {

                this.currentState = currentState.run(this.userInput.nextLine().trim());

            } catch (Exception error) {
                UiState last_state = this.currentState;
                this.currentState = currentState.run(error.toString());
                // Waiting for input 1 to exit error screen
                String input = this.userInput.next().trim();
                while (!input.equals("1")) {
                    input = this.userInput.next().trim();
                }
                this.currentState = last_state;

            }
        }
    }
}
