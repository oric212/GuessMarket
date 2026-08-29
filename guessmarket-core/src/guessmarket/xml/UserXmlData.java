package guessmarket.xml;

import java.util.List;

public record UserXmlData(
        String username,
        double initialCash,
        List<Integer> marketMakerEventIds) {
}
