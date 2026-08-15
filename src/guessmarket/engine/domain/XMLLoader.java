package guessmarket.engine.domain;

import guessmarket.jaxb.GMEvent;
import guessmarket.jaxb.GMLMSR;
import guessmarket.jaxb.GuessMarket;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


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

            throw new RuntimeException("Failed to load XML file", e);
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
                event.getComision().getValue(),
                event.getComision().getType(),
                options,
                tradingMethod
        );
    }

    private TradingMethodXmlData convertTradingMethod(GMEvent event) {
        if (event.getGMMethod().getGMLMSR() != null) {
            return convertLmsr(event.getGMMethod().getGMLMSR());
        }

        throw new IllegalArgumentException("Unsupported trading method");
    }

    private LmsrXmlData convertLmsr(GMLMSR lmsr) {
        return new LmsrXmlData(lmsr.getB());
    }




    private void validateXmlFile(List<EventXmlData> events){
        Set<Integer> idSet = new HashSet<>();

        for (EventXmlData event : events) {
            int id = event.id();

            if (idSet.contains(id)) {
                throw new IllegalArgumentException("XML file is not valid application-wise, each event must have a unique id");
            }

            int commission = event.commission();

            if (commission < 0 || commission > 90){
                throw new IllegalArgumentException("XML file is not valid application-wise, Each event must fulfill 0 <= commission <= 90");
            }

            idSet.add(id);
        }

    }

    List<EventXmlData> loadEventsFromXml(String path){
        validatePath(path);
        GuessMarket guessMarket = unmarshalWithJaxb(path);
        List<EventXmlData> events = convertJaxbEvents(guessMarket);
        validateXmlFile(events);

        return events;
    }


}
