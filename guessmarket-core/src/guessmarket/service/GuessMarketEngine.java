package guessmarket.service;

import guessmarket.api.Engine;
import guessmarket.dto.*;
import guessmarket.domain.*;
import guessmarket.xml.*;


import java.io.*;
import java.util.*;


public class GuessMarketEngine implements Engine, Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Map<Integer, Event> eventsById = new LinkedHashMap<>();

    @Override
    public void saveState(String filePath) {
        String actualPath = getSaveFilePath(filePath);

        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(actualPath))) {

            out.writeObject(this);

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to save system state: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public void loadState(String filePath) {
        filePath = getSaveFilePath(filePath);

        File saveFile = new File(filePath);

        if (!saveFile.exists()) {
            throw new IllegalArgumentException(
                    "Save file does not exist: " + filePath
            );
        }

        try (ObjectInputStream in =
                     new ObjectInputStream(new FileInputStream(filePath))) {

            GuessMarketEngine loadedEngine =
                    (GuessMarketEngine) in.readObject();

            eventsById.clear();
            eventsById.putAll(loadedEngine.eventsById);

        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Failed to load system state: " + e.getMessage(),
                    e
            );
        }
    }

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
        for (Event e:eventsById.values()) {

            List<String> optionNames = convertOptionsToStrings(e.getOptions());
                    EventDTO eventDTO = new EventDTO(
                    e.getId(),
                    e.getName(),
                    e.getDescription(),
                    e.getCommissionPercentage(),
                    e.getCommissionMethod().name(),
                    optionNames,
                    e.getState().name()
            );

            eventSummaries.add(eventDTO);
        }

        return eventSummaries;
    }

    @Override
    public EventStateDTO getEventState(int eventId) {
        Event requestedEvent = eventsById.get(eventId);

        validateEvent(eventId, requestedEvent);

        List<OptionStateDTO> optionStateDTOs = createOptionStateDtoList(requestedEvent);
        List<TradeDTO> tradeDTOs = createTradeDTOList(requestedEvent);

        return new EventStateDTO(
                requestedEvent.getId(),
                requestedEvent.getName(),
                requestedEvent.getAccountBalance(),
                requestedEvent.getTotalCommissionCollected(),
                optionStateDTOs,
                tradeDTOs,
                requestedEvent.getState().name(),
                requestedEvent
                .getWinningOption()
                .map(Option::getName)
                .orElse(null)
        );
    }

    private static List<TradeDTO> createTradeDTOList(Event requestedEvent) {
        List<TradeDTO> tradeDTOs = new ArrayList<>();

        for (Trade trade : requestedEvent.getTradeHistory().reversed()) {
            TradeDTO tradeDTO = new TradeDTO(
                    trade.getOption().getName(),
                    trade.getQuantity(),
                    trade.getPurchaseCost()
            );

            tradeDTOs.add(tradeDTO);
        }
        return tradeDTOs;
    }

    private List<OptionStateDTO> createOptionStateDtoList(Event requestedEvent) {
        List<OptionStateDTO> optionStateDTOs = new ArrayList<>();

        for (Option option : requestedEvent.getOptions()) {
            OptionStateDTO optionStateDTO = new OptionStateDTO(
                    option.getName(),
                    requestedEvent.getOptionPrice(option),
                    requestedEvent.getQuantityBought(option)
            );

            optionStateDTOs.add(optionStateDTO);
        }

        return optionStateDTOs;
    }

    private void validateEvent(int eventId, Event requestedEvent) {
        if (requestedEvent == null) {
            throw new IllegalArgumentException("No event exists with id: " + eventId);
        }
    }

    private String getSaveFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException(
                    "Save file path cannot be empty."
            );
        }

        return filePath + ".sav";
    }

    @Override
    public PurchaseResultDTO purchaseShares(int eventId, int optionChoice, int quantity) {
        Event requestedEvent = eventsById.get(eventId);
        validateEvent(eventId, requestedEvent);

        Option option = requestedEvent.getOptionByChoice(optionChoice);
        Trade trade = requestedEvent.purchase(option,quantity);

        return new PurchaseResultDTO(
                requestedEvent.getId(),
                requestedEvent.getName(),
                trade.getPurchaseCost() + trade.getCommissionPaid(),
                trade.getPurchaseCost(),
                trade.getCommissionPaid(),
                requestedEvent.getState().name()
                );
    }


    @Override
    public EventStateDTO closeEvent(int eventId, int winningOptionChoice) {
        Event requestedEvent = eventsById.get(eventId);
        validateEvent(eventId, requestedEvent);

        Option winningOption = requestedEvent.getOptionByChoice(winningOptionChoice);
        requestedEvent.close(winningOption);

        return getEventState(eventId);
    }

    private Map<Integer, Event> createEvents(List<EventXmlData> eventXmlData){

        Map<Integer, Event> events = new LinkedHashMap<>();

        for (EventXmlData e:eventXmlData) {

            int id = e.id();
            int commissionPercentage = e.commission();
            CommissionMethod commissionMethod = createCommissionMethod(e.commissionMethod());
            List<Option> options = createOptions(e.options());
            LMSR tradingMethod = createTradingMethod(e.tradingMethod(),options);
            double initialSubsidy = tradingMethod.calculateInitialSubsidy();
            Account account = new Account(initialSubsidy);

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

    private CommissionMethod createCommissionMethod(String commissionMethod) {
        if ("on-purchase".equals(commissionMethod)) {
            return CommissionMethod.ON_PURCHASE;
        }

        if ("on-close".equals(commissionMethod)) {
            return CommissionMethod.ON_CLOSE;
        }

        throw new IllegalArgumentException(
                "Unsupported commission method: " + commissionMethod
        );
    }
}


