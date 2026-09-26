package guessmarket.server.api;

public record OrderRequest(String side, int optionIndex, int quantity, double price) {}
