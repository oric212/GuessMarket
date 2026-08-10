package guessmarket.engine.domain;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.HashSet;
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

    private void validateXmlFile(Document doc){
        Element root = doc.getDocumentElement();

        NodeList events = root.getElementsByTagName("GM-event");
        Set<String> idSet = new HashSet<>();

        for (int i = 0; i < events.getLength(); i++) {
            Element event = (Element) events.item(i);

            String id = event.getElementsByTagName("id")
                    .item(0)
                    .getTextContent();

            if (idSet.contains(id)) {
                throw new IllegalArgumentException("XML file is not valid application-wise, each event must have a unique id");
            }

            String commissionText = event.getElementsByTagName("commission")
                    .item(0)
                    .getTextContent();

            int commission = Integer.parseInt(commissionText);

            if (commission < 0 || commission > 90){
                throw new IllegalArgumentException("XML file is not valid application-wise, Each event must fulfill 0 <= commission <= 90");
            }

            idSet.add(id);
        }

    }

    public Document validateXmlFileFromPath(String path){
        validatePath(path);
        Document doc = parseXmlFromPath(path);
        validateXmlFile(doc);

        return doc;
    }


}
