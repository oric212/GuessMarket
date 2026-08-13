package guessmarket.engine.domain;

import java.util.List;

record EventXmlData(
        String id,
        String name,
        String description,
        String commission,
        String commissionMethod,
        List<String> options,
        TradingMethodXmlData tradingMethod
) {
}
