package guessmarket.xml;

import guessmarket.jaxb.*;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;


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

    private Ex03Market unmarshalEx03(InputStream input) {
        if (input == null) throw new InvalidMarketXmlException("Uploaded XML stream cannot be null");
        try (InputStream schemaInput = XMLLoader.class.getResourceAsStream("GM-EX3-Schema.xsd")) {
            if (schemaInput == null) throw new IllegalStateException("EX03 XML schema resource is missing");
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Schema schema = factory.newSchema(new StreamSource(schemaInput));
            JAXBContext context = JAXBContext.newInstance(Ex03Market.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            unmarshaller.setSchema(schema);
            SAXParserFactory parserFactory = SAXParserFactory.newInstance();
            parserFactory.setNamespaceAware(true);
            parserFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            parserFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            parserFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            XMLReader reader = parserFactory.newSAXParser().getXMLReader();
            return (Ex03Market) unmarshaller.unmarshal(
                    new SAXSource(reader, new InputSource(input)));
        } catch (InvalidMarketXmlException error) {
            throw error;
        } catch (Exception error) {
            Throwable detail = error;
            while (detail.getCause() != null && detail.getCause() != detail) detail = detail.getCause();
            String message = detail.getMessage();
            throw new InvalidMarketXmlException(
                    "XML is malformed or does not match the EX03 schema"
                            + (message == null ? "" : ": " + message), error);
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
        Set<String> eventNames = new HashSet<>();

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

            if (event.options().size() < 2) {
                throw new IllegalArgumentException(
                        "Event ID " + event.id()
                                + " must contain at least 2 options"
                );
            }

            if (event.name() == null || event.name().isBlank()
                    || !eventNames.add(event.name().trim().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Event names must be non-blank and unique");
            }

            Set<String> optionNames = new HashSet<>();
            for (String option : event.options()) {
                if (option == null || option.isBlank()
                        || !optionNames.add(option.trim().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException(
                            "Event ID " + event.id()
                                    + " must contain distinct, non-blank option names");
                }
            }

            if (event.tradingMethod() instanceof OrderBookXmlData orderBook
                    && orderBook.allowMint() && event.options().size() != 2) {
                throw new IllegalArgumentException(
                        "Event ID " + event.id() + " may enable MINT only with exactly 2 options");
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

    public List<EventXmlData> loadEventsFromEx03Xml(InputStream input) {
        Ex03Market market = unmarshalEx03(input);
        if (market.events() == null) {
            throw new InvalidMarketXmlException("XML must contain GM-events");
        }
        List<EventXmlData> events = convertEx03Events(market);
        validateEx03Events(events);
        return events;
    }

    private List<EventXmlData> convertEx03Events(Ex03Market market) {
        List<EventXmlData> events = new ArrayList<>();
        for (Ex03Market.MarketEvent event : market.events().events()) {
            TradingMethodXmlData method;
            if (event.method().lmsr() != null) {
                method = new LmsrXmlData(event.method().lmsr().b());
            } else if (event.method().orderBook() != null) {
                Ex03Market.OrderBook book = event.method().orderBook();
                method = new OrderBookXmlData(book.allowMint(), book.initial(), book.d());
            } else {
                throw new InvalidMarketXmlException("Event must define a trading method");
            }
            events.add(new EventXmlData(0, event.name(), event.description(),
                    event.commission().value(), event.commission().type(),
                    event.options().options(), method));
        }
        return events;
    }

    private void validateEx03Events(List<EventXmlData> events) {
        Set<String> eventNames = new HashSet<>();
        if (events.isEmpty()) throw new InvalidMarketXmlException("Upload must contain at least one event");
        for (EventXmlData event : events) {
            if (event.name() == null || event.name().isBlank()) {
                throw new InvalidMarketXmlException("Event name cannot be blank");
            }
            String normalized = event.name().trim().toLowerCase(Locale.ROOT);
            if (!eventNames.add(normalized)) {
                throw new InvalidMarketXmlException("Duplicate event name in upload: " + event.name().trim());
            }
            if (event.description() == null || event.description().isBlank()) {
                throw new InvalidMarketXmlException("Event description cannot be blank: " + event.name().trim());
            }
            if (event.commission() < 0 || event.commission() > 90) {
                throw new InvalidMarketXmlException("Commission must be between 0 and 90: " + event.name().trim());
            }
            Set<String> options = new HashSet<>();
            if (event.options().size() < 2) {
                throw new InvalidMarketXmlException("Event must contain at least two options: " + event.name().trim());
            }
            for (String option : event.options()) {
                if (option == null || option.isBlank()
                        || !options.add(option.trim().toLowerCase(Locale.ROOT))) {
                    throw new InvalidMarketXmlException("Options must be distinct and non-blank: " + event.name().trim());
                }
            }
            if (event.tradingMethod() instanceof LmsrXmlData lmsr && lmsr.liquidityParameter() <= 0) {
                throw new InvalidMarketXmlException("LMSR b must be greater than zero: " + event.name().trim());
            }
            if (event.tradingMethod() instanceof OrderBookXmlData book) {
                if (book.d() <= 0 || book.initial() < 0 || book.initial() % book.d() != 0) {
                    throw new InvalidMarketXmlException("Invalid Order Book d or initial amount: " + event.name().trim());
                }
                if (book.allowMint() && event.options().size() != 2) {
                    throw new InvalidMarketXmlException("MINT requires exactly two options: " + event.name().trim());
                }
            }
        }
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
