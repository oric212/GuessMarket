package guessmarket.engine.domain;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XMLLoader {
    private final DocumentBuilderFactory factory;

    public XMLLoader(){
        this.factory = DocumentBuilderFactory.newInstance();
    }

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

    private Document parseXmlFromPath(String path){
        Document doc;

        try(InputStream xmlFileInputStream = new FileInputStream(path)){
            doc = parseXml(xmlFileInputStream);

        } catch (FileNotFoundException e) {
            throw new UncheckedIOException("XML file was not found",e);
        } catch (IOException e) {
            throw new UncheckedIOException("An I/O error occurred while loading XML", e);
        }

        return doc;
    }

    private Document parseXml(InputStream xmlFileInputStream) {
        Document doc;

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            doc = builder.parse(xmlFileInputStream);
            doc.getDocumentElement().normalize();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Failed to create XML parser", e);
        } catch (SAXException e) {
            throw new RuntimeException("XML is malformed or could not be parsed", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed while reading XML input", e);
        }

        return doc;
    }

    private List<EventXmlData> extractEventsFromXml(Document doc) {
        Element root = doc.getDocumentElement();
        NodeList events = root.getElementsByTagName("GM-event");
        List<EventXmlData> extractedEvents = new ArrayList<>();

        for (int i = 0; i < events.getLength(); i++) {
            Element eventElement = (Element) events.item(i);
            extractedEvents.add(extractEventXmlData(eventElement));
        }

        return extractedEvents;
    }

    private EventXmlData extractEventXmlData(Element eventElement) {
        Element commission = (Element) eventElement.getElementsByTagName("commission").item(0);
        NodeList optionNodes = eventElement.getElementsByTagName("GM-option");
        List<String> options = new ArrayList<>();

        for (int optionIndex = 0; optionIndex < optionNodes.getLength(); optionIndex++) {
            options.add(optionNodes.item(optionIndex).getTextContent());
        }

        return new EventXmlData(
                eventElement.getElementsByTagName("id").item(0).getTextContent(),
                eventElement.getAttribute("name"),
                eventElement.getElementsByTagName("description").item(0).getTextContent(),
                commission.getTextContent(),
                commission.getAttribute("type"),
                options,
                extractTradingMethodXmlData(eventElement)
        );
    }

    private TradingMethodXmlData extractTradingMethodXmlData(Element eventElement) {
        Element methodContainer = (Element) eventElement.getElementsByTagName("GM-method").item(0);
        Element methodElement = findFirstChildElement(methodContainer);

        switch (methodElement.getTagName()) {
            case "GM-LMSR":
                LmsrXmlData lmsrXmlData = extractLmsrXmlData(methodElement);
                return lmsrXmlData;
            default:
                throw new IllegalArgumentException(
                        "Unsupported trading method: " + methodElement.getTagName()
                );
        }
    }

    private Element findFirstChildElement(Element parentElement) {
        NodeList childNodes = parentElement.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node childNode = childNodes.item(i);

            if (childNode instanceof Element childElement) {
                return childElement;
            }
        }

        throw new IllegalArgumentException("GM-method does not contain a trading method");
    }

    private LmsrXmlData extractLmsrXmlData(Element lmsrElement) {
        String liquidityParameter = lmsrElement.getElementsByTagName("b")
                .item(0)
                .getTextContent();

        return new LmsrXmlData(liquidityParameter);
    }

    private void validateXmlFile(List<EventXmlData> events){
        Set<String> idSet = new HashSet<>();

        for (EventXmlData event : events) {
            String id = event.id();

            if (idSet.contains(id)) {
                throw new IllegalArgumentException("XML file is not valid application-wise, each event must have a unique id");
            }

            String commissionText = event.commission();

            int commission = Integer.parseInt(commissionText);

            if (commission < 0 || commission > 90){
                throw new IllegalArgumentException("XML file is not valid application-wise, Each event must fulfill 0 <= commission <= 90");
            }

            idSet.add(id);
        }

    }

    List<EventXmlData> loadEventsFromXml(String path){
        validatePath(path);
        Document doc = parseXmlFromPath(path);
        List<EventXmlData> events = extractEventsFromXml(doc);
        validateXmlFile(events);

        return events;
    }


}
