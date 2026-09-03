package guessmarket.domain;

import java.util.List;

public final class OrderBook implements TradingMethod {
    private final boolean allowMint;
    private final int initial;
    private final int d;
    private final List<Option> options;

    public OrderBook(boolean allowMint, int initial, int d, List<Option> options) {
        if (initial < 0) {
            throw new IllegalArgumentException("Order Book initial amount cannot be negative");
        }
        if (d <= 0) {
            throw new IllegalArgumentException("Order Book d must be greater than zero");
        }
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("Order Book options cannot be null or empty");
        }

        this.allowMint = allowMint;
        this.initial = initial;
        this.d = d;
        this.options = List.copyOf(options);
    }

    public boolean isMintAllowed() { return allowMint; }
    public int getInitial() { return initial; }
    public int getD() { return d; }
    public List<Option> getOptions() { return options; }

    @Override
    public TradingMethodType getType() {
        return TradingMethodType.ORDER_BOOK;
    }
}
