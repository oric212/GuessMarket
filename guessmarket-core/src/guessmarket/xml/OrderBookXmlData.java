package guessmarket.xml;

public record OrderBookXmlData(
        boolean allowMint,
        int initial,
        int d
) implements TradingMethodXmlData {
}