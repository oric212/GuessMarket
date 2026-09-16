package guessmarket.xml;

import java.util.List;

public record MarketXmlData(List<EventXmlData> events, List<UserXmlData> users) {
    public MarketXmlData(List<EventXmlData> events, List<UserXmlData> users) {
        this.events = List.copyOf(events);
        this.users = List.copyOf(users);
    }
}