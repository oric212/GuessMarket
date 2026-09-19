package guessmarket.javafx.controller;

import guessmarket.dto.ChatMessageDTO;
import guessmarket.javafx.client.GuessMarketApiClient;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ChatController {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final GuessMarketApiClient api;
    private final BorderPane root = new BorderPane();
    private final ListView<ChatMessageDTO> history = new ListView<>();
    private final TextField input = new TextField();
    private final Button send = new Button("Send");
    private final Label status = new Label();
    private final Set<Long> deliveredSequences = new HashSet<>();

    public ChatController(GuessMarketApiClient api) {
        this.api = api;
        history.setPlaceholder(new Label("No chat messages yet."));
        history.setCellFactory(ignored -> new ListCell<>() {
            @Override protected void updateItem(ChatMessageDTO message, boolean empty) {
                super.updateItem(message, empty);
                setText(empty || message == null ? null : TIME.format(Instant.ofEpochMilli(message.timestampMillis()))
                        + "  " + message.senderUsername() + ": " + message.message());
                setWrapText(true);
            }
        });
        input.setPromptText("Message (maximum 500 characters)");
        input.setOnAction(event -> send());
        send.setOnAction(event -> send());
        HBox composer = new HBox(8, input, send);
        HBox.setHgrow(input, Priority.ALWAYS);
        BorderPane.setMargin(history, new Insets(12));
        BorderPane.setMargin(composer, new Insets(0, 12, 6, 12));
        BorderPane.setMargin(status, new Insets(0, 12, 12, 12));
        root.setCenter(history);
        root.setBottom(new javafx.scene.layout.VBox(6, composer, status));
    }

    public Parent getView() { return root; }

    public void appendMessages(List<ChatMessageDTO> messages) {
        boolean followTail = history.getSelectionModel().isEmpty()
                || history.getSelectionModel().getSelectedIndex() >= history.getItems().size() - 2;
        for (ChatMessageDTO message : messages) {
            if (deliveredSequences.add(message.sequence())) history.getItems().add(message);
        }
        if (followTail && !history.getItems().isEmpty()) history.scrollTo(history.getItems().size() - 1);
    }

    private void send() {
        String text = input.getText();
        if (text == null || text.isBlank()) { status.setText("Message cannot be blank."); return; }
        if (text.trim().length() > 500) { status.setText("Message cannot exceed 500 characters."); return; }
        Task<ChatMessageDTO> task = new Task<>() {
            @Override protected ChatMessageDTO call() { return api.sendChatMessage(text); }
        };
        send.setDisable(true); input.setDisable(true); status.setText("Sending...");
        task.setOnSucceeded(event -> {
            appendMessages(List.of(task.getValue()));
            input.clear(); input.setDisable(false); send.setDisable(false); input.requestFocus(); status.setText("");
        });
        task.setOnFailed(event -> {
            input.setDisable(false); send.setDisable(false);
            status.setText(task.getException().getMessage());
        });
        Thread worker = new Thread(task, "guessmarket-chat-send");
        worker.setDaemon(true); worker.start();
    }
}
