package guessmarket.dto;

public record ChatMessageDTO(long sequence, String senderUsername, String message, long timestampMillis) {}
