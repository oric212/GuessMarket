package guessmarket.xml;

public final class InvalidMarketXmlException extends IllegalArgumentException {
    public InvalidMarketXmlException(String message) { super(message); }
    public InvalidMarketXmlException(String message, Throwable cause) { super(message, cause); }
}
