package guessmarket.xml;

import java.io.Serializable;

public record LmsrXmlData(
        int liquidityParameter
) implements TradingMethodXmlData, Serializable {
}
