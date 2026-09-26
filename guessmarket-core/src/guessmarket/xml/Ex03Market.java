package guessmarket.xml;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlValue;

import java.util.ArrayList;
import java.util.List;

/** JAXB binding model dedicated to GM-EX3-Schema.xsd. */
@XmlRootElement(name = "Guess-Market")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(propOrder = {"events"})
public final class Ex03Market {
    @XmlElement(name = "GM-events", required = true)
    private Events events;

    public Events events() {
        return events;
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Events {
        @XmlElement(name = "GM-event", required = true)
        private List<MarketEvent> events = new ArrayList<>();

        public List<MarketEvent> events() {
            return events;
        }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"description", "commission", "options", "method"})
    public static final class MarketEvent {
        @XmlElement(required = true)
        private String description;
        @XmlElement(required = true)
        private Commission commission;
        @XmlElement(name = "GM-options", required = true)
        private Options options;
        @XmlElement(name = "GM-method", required = true)
        private Method method;
        @XmlAttribute(required = true)
        private String name;

        public String name() { return name; }
        public String description() { return description; }
        public Commission commission() { return commission; }
        public Options options() { return options; }
        public Method method() { return method; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Commission {
        @XmlValue
        private int value;
        @XmlAttribute(required = true)
        private String type;

        public int value() { return value; }
        public String type() { return type; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class Options {
        @XmlElement(name = "GM-option", required = true)
        private List<String> options = new ArrayList<>();

        public List<String> options() { return options; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"lmsr", "orderBook"})
    public static final class Method {
        @XmlElement(name = "GM-LMSR")
        private Lmsr lmsr;
        @XmlElement(name = "GM-order-book")
        private OrderBook orderBook;

        public Lmsr lmsr() { return lmsr; }
        public OrderBook orderBook() { return orderBook; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    @XmlType(propOrder = {"b"})
    public static final class Lmsr {
        @XmlElement(required = true)
        private int b;

        public int b() { return b; }
    }

    @XmlAccessorType(XmlAccessType.FIELD)
    public static final class OrderBook {
        @XmlAttribute(required = true)
        private int initial;
        @XmlAttribute(required = true)
        private int d;
        @XmlAttribute(name = "allow-mint", required = true)
        private String allowMint;

        public int initial() { return initial; }
        public int d() { return d; }
        public boolean allowMint() { return Boolean.parseBoolean(allowMint); }
    }
}
