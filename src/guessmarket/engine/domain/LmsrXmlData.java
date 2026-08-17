package guessmarket.engine.domain;

import java.io.Serializable;

record LmsrXmlData(
        int liquidityParameter
) implements TradingMethodXmlData, Serializable {
}
