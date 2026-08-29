package guessmarket.domain;

import java.util.List;

public final class OrderBook implements TradingMethod {
    public OrderBook(boolean b, int initial, int d, List<Option> options) {
    }

    @Override
    public TradingMethodType getType() {
        return TradingMethodType.ORDER_BOOK;
    }
}
