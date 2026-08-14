package guessmarket.engine.domain;

import guessmarket.dto.*;
import guessmarket.engine.market.*;

import java.util.*;


public class GuessMarketEngine implements Engine {

    private Map<Integer, Event> eventsById = new LinkedHashMap<>();;

    @Override
    public void loadMarketFromXml(String xmlFilePath) {
        XMLLoader xmlLoader = new XMLLoader();
        List<EventXmlData> eventXmlData = xmlLoader.loadEventsFromXml(xmlFilePath);
        Map<Integer, Event> newEvents = createEvents(eventXmlData);

        eventsById = newEvents;
    }

    @Override
    public List<EventDTO> getEventSummaries() {
        List<EventDTO> eventSummaries = new ArrayList<>();

        for(Event e:eventsById.values()){
            List<String> optionNames = convertOptionsToStrings(e.getOptions());
            EventDTO eventDTO =
                    new EventDTO(e.getId(),e.getName(),e.getDescription(),e.getCommissionPercentage(),e.getCommissionMethod().name(),optionNames);

            eventSummaries.add(eventDTO);
        }
        return eventSummaries;
    }

    @Override
    public EventStateDTO getEventState(int eventId) {
        Event requestedEvent = eventsById.get(eventId);

        if (requestedEvent == null) {
            throw new IllegalArgumentException(
                    "No event exists with id: " + eventId
            );
        }

        List<OptionStateDTO> optionStateDTOs = new ArrayList<>();

        for (Option option : requestedEvent.getOptions()) {
            OptionStateDTO optionStateDTO = new OptionStateDTO(
                    option.getName(),
                    requestedEvent.getOptionPrice(option),
                    requestedEvent.getQuantityBought(option)
            );

            optionStateDTOs.add(optionStateDTO);
        }

        List<TradeDTO> tradeDTOs = new ArrayList<>();

        for (Trade trade : requestedEvent.getTradeHistory()) {
            TradeDTO tradeDTO = new TradeDTO(
                    trade.getOption().getName(),
                    trade.getQuantity(),
                    trade.getPricePaid()
            );

            tradeDTOs.add(tradeDTO);
        }

        return new EventStateDTO(
                requestedEvent.getId(),
                requestedEvent.getName(),
                requestedEvent.getAccountBalance(),
                requestedEvent.getTotalCommissionCollected(),
                optionStateDTOs,
                tradeDTOs,
                requestedEvent.getStatus().name()
        );
    }

    @Override
    public PurchaseResultDTO purchaseShares(int eventId, int optionIndex, int quantity) {
        return null;
    }

    @Override
    public CloseResultDTO closeEvent(int eventId, int winningOptionIndex) {
        return null;
    }

    private Map<Integer, Event> createEvents(List<EventXmlData> eventXmlData){

        Map<Integer, Event> events = new LinkedHashMap<>();

        for (EventXmlData e:eventXmlData) {

            int id = e.id();
            int commissionPercentage = e.commission();
            CommissionMethod commissionMethod = createCommissionMethod(e.commissionMethod());
            List<Option> options = createOptions(e.options());
            LMSR tradingMethod = createTradingMethod(e.tradingMethod(),options);
            Account account = new Account(0.0);

            Event event = new Event(id,e.name(),e.description(),commissionPercentage,commissionMethod,tradingMethod,account);
            events.put(event.getId(),event);

        }

        return events;
    }



    private LMSR createTradingMethod(TradingMethodXmlData tradingMethodXmlData, List<Option> options) {
        switch (tradingMethodXmlData) {
            case LmsrXmlData(int liquidityParameter):
                return new LMSR(liquidityParameter, options);

            default:
                throw new IllegalArgumentException(
                        "Unsupported trading method XML data: " + tradingMethodXmlData.getClass().getSimpleName()
                );
        }
    }



    private List<Option> createOptions(List<String> optionNames) {
        List<Option> options = new ArrayList<>();

        for (String optionName : optionNames) {
            options.add(new Option(optionName));
        }

        return options;
    }

    private List<String> convertOptionsToStrings(List<Option> options) {
        List<String> optionNames = new ArrayList<>();

        for (Option option : options) {
            optionNames.add(option.getName());
        }

        return optionNames;
    }

    private CommissionMethod createCommissionMethod(String s) {
        if ("ON_PURCHASE".equals(s)) {
            return CommissionMethod.ON_PURCHASE;
        }

        if ("ON_CLOSE".equals(s)) {
            return CommissionMethod.ON_CLOSE;
        }

        throw new IllegalArgumentException(
                "Unsupported commission method: " + s
        );
    }
}


