package guessmarket.engine.domain;

import java.util.List;

record EventXmlData(
        int id,
        String name,
        String description,
        int commission,
        String commissionMethod,
        List<String> options,
        TradingMethodXmlData tradingMethod
) {
}
