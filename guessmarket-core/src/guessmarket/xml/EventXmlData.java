package guessmarket.xml;

import java.io.Serializable;
import java.util.List;

public record EventXmlData(
        int id,
        String name,
        String description,
        int commission,
        String commissionMethod,
        List<String> options,
        TradingMethodXmlData tradingMethod
) implements Serializable {
    public EventXmlData {
        options = List.copyOf(options);
    }
}
