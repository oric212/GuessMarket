package guessmarket.xml;

import guessmarket.jaxb.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import java.io.File;
import java.util.*;


public class XMLLoader {

    private void validatePath(String path){
        if (path == null){
            throw new IllegalArgumentException("Path cannot be null");

        }

        if (path.isEmpty()) {
            throw new IllegalArgumentException("Path cannot be empty");
        }

        if (path.isBlank()) {
            throw new IllegalArgumentException("Path cannot be blank");
        }

        if (!path.toLowerCase().endsWith(".xml")) {
            throw new IllegalArgumentException("Path must point to an XML file");
        }
    }

    private GuessMarket unmarshalWithJaxb(String path){
        try{
            JAXBContext context = JAXBContext.newInstance(GuessMarket.class);

            Unmarshaller unmarshaller = context.createUnmarshaller();

            return (GuessMarket) unmarshaller.unmarshal(new File(path));

        } catch (JAXBException e) {
            throw new IllegalArgumentException("XML file is malformed or does not match the required format.");
        }
    }

    private List<EventXmlData> convertJaxbEvents(GuessMarket market){
        List<EventXmlData> events = new ArrayList<>();

        for (GMEvent event : market.getGMEvents().getGMEvent()){
            events.add(convertEvent(event));
        }

        return events;
    }

    private EventXmlData convertEvent(GMEvent event){
        List<String> options = new ArrayList<>();

        for (String option : event.getGMOptions().getGMOption()) {
            options.add(option);
        }

        TradingMethodXmlData tradingMethod =
                convertTradingMethod(event);

        return new EventXmlData(
                event.getId(),
                String.join(" ", event.getName()),
                event.getDescription(),
                event.getCommission().getValue(),
                event.getCommission().getType(),
                options,
                tradingMethod
        );
    }

    private TradingMethodXmlData convertTradingMethod(GMEvent event) {
        if (event.getGMMethod() == null) {
            throw new IllegalArgumentException("Event must define a trading method");
        }

        if (event.getGMMethod().getGMLMSR() != null) {
            return convertLmsr(event.getGMMethod().getGMLMSR());
        }

        if (event.getGMMethod().getGMOrderBook() != null) {
            GMOrderBook orderBook = event.getGMMethod().getGMOrderBook();
            return new OrderBookXmlData(
                    Boolean.parseBoolean(orderBook.getAllowMint()),
                    orderBook.getInitial(),
                    orderBook.getD()
            );
        }

        throw new IllegalArgumentException("Unsupported trading method");
    }

    private LmsrXmlData convertLmsr(GMLMSR lmsr) {
        return new LmsrXmlData(lmsr.getB());
    }




    private void validateXmlFile(List<EventXmlData> events, List<UserXmlData> users) {

        validateEvents(events);
        validateUsers(users);
        validateMarketMakerAssignments(events, users);
    }

    private void validateEvents(List<EventXmlData> events) {
        Set<Integer> eventIds = new HashSet<>();

        for (EventXmlData event : events) {
            if (!eventIds.add(event.id())) {
                throw new IllegalArgumentException(
                        "Duplicate event ID: " + event.id()
                );
            }

            if (event.commission() < 0
                    || event.commission() > 90) {
                throw new IllegalArgumentException(
                        "Commission percentage must be between 0 and 90 for event ID: "
                                + event.id()
                );
            }

            if (event.options().size() != 2) {
                throw new IllegalArgumentException(
                        "Event ID " + event.id()
                                + " must contain exactly 2 options"
                );
            }
        }
    }

    private void validateUsers(List<UserXmlData> users) {
        Set<String> usernames = new HashSet<>();

        for (UserXmlData user : users) {
            if (user.username() == null || user.username().isBlank()) {
                throw new IllegalArgumentException("Username cannot be blank"
                );
            }

            String normalizedName =
                    user.username().trim().toLowerCase(Locale.ROOT);

            if (!usernames.add(normalizedName)) {
                throw new IllegalArgumentException(
                        "Duplicate username: " + user.username()
                );
            }

            if (!Double.isFinite(user.initialCash()) || user.initialCash() <= 0) {
                throw new IllegalArgumentException(
                        "Initial cash must be greater than 0 for user: "
                                + user.username()
                );
            }
        }
    }

    private void validateMarketMakerAssignments(
            List<EventXmlData> events,
            List<UserXmlData> users) {

        Set<Integer> eventIds = new HashSet<>();

        for (EventXmlData event : events) {
            eventIds.add(event.id());
        }

        Set<Integer> eventIdsWithMarketMaker = new HashSet<>();

        for (UserXmlData user : users) {
            for (Integer eventId : user.marketMakerEventIds()) {

                if (!eventIds.contains(eventId)) {
                    throw new IllegalArgumentException(
                            "Market Maker references non-existing event ID: "
                                    + eventId
                    );
                }

                if (!eventIdsWithMarketMaker.add(eventId)) {
                    throw new IllegalArgumentException(
                            "Event " + eventId
                                    + " has more than one Market Maker"
                    );
                }
            }
        }

        for (Integer eventId : eventIds) {
            if (!eventIdsWithMarketMaker.contains(eventId)) {
                throw new IllegalArgumentException(
                        "Event " + eventId
                                + " does not have a Market Maker"
                );
            }
        }
    }



    public MarketXmlData loadMarketFromXml(String path) {
        validatePath(path);

        GuessMarket guessMarket = unmarshalWithJaxb(path);

        List<EventXmlData> events = convertJaxbEvents(guessMarket);
        List<UserXmlData> users = convertJaxbUsers(guessMarket);

        validateXmlFile(events, users);

        return new MarketXmlData(events, users);
    }

    private List<UserXmlData> convertJaxbUsers(GuessMarket guessMarket) {
        List<UserXmlData> users = new ArrayList<>();

        if (guessMarket.getGMUsers() == null) {
            throw new IllegalArgumentException("XML must contain GM-users");
        }

        for (GMUser user : guessMarket.getGMUsers().getGMUser()){
            users.add(convertUser(user));
        }

        return users;
    }

    private UserXmlData convertUser(GMUser user){
        List<Integer> marketMakerEventIds = new ArrayList<>();

        if (user.getGMMarketMaker() != null) {
            for (Event event : user.getGMMarketMaker().getEvent()) {
                marketMakerEventIds.add(event.getId());
            }
        }

        String username = user.getName();
        double initialCash = user.getInitialCash();

        return new UserXmlData(
                username,
                initialCash,
                marketMakerEventIds
                );

    }


}
