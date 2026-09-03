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
    private final Map<Integer, Event> eventsById = new LinkedHashMap<>();
    private final Map<String, User> usersByName = new LinkedHashMap<>();

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

            Object loaded = in.readObject();
            if (!(loaded instanceof GuessMarketEngine loadedEngine)) {
                throw new IllegalArgumentException("Save file does not contain a GuessMarket state");
            }

            Map<String, User> loadedUsers = copyAndValidateUsers(loadedEngine.usersByName);
            Map<Integer, Event> loadedEvents = copyAndValidateEvents(
                    loadedEngine.eventsById, loadedUsers.values());

            usersByName.clear();
            usersByName.putAll(loadedUsers);
            eventsById.clear();
            eventsById.putAll(loadedEvents);

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
        MarketXmlData marketXmlData  = xmlLoader.loadMarketFromXml(xmlFilePath);

        Map<String, User> newUsers = createUsers(marketXmlData.users());
        Map<Integer, User> marketMakerById = createMarketMakerAssignments(marketXmlData.users(),newUsers);
        Map<Integer, Event> newEvents = createEvents(marketXmlData.events(),marketMakerById);

        usersByName.clear();
        usersByName.putAll(newUsers);

        eventsById.clear();
        eventsById.putAll(newEvents);
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
    public List<UserDTO> getUsers() {
        List<UserDTO> users = new ArrayList<>();

        for (User user : usersByName.values()) {
            users.add(createUserDTO(user));
        }

        return List.copyOf(users);
    }

    @Override
    public UserDTO getUser(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be blank");
        }

        User user = usersByName.get(normalizeUsername(username));
        if (user == null) {
            throw new IllegalArgumentException("No user exists with username: " + username.trim());
        }

        return createUserDTO(user);
    }

    private UserDTO createUserDTO(User user) {
        List<Integer> marketMakerEventIds = eventsById.values().stream()
                .filter(event -> event.hasMarketMaker(user))
                .map(Event::getId)
                .toList();

        return new UserDTO(
                user.getUsername(),
                user.getAccountBalance(),
                user.isBlocked(),
                marketMakerEventIds
        );
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

        if (requestedEvent.getTradingMethodType() != TradingMethodType.LMSR) {
            return List.of();
        }

        for (Option option : requestedEvent.getOptions()) {
            OptionStateDTO optionStateDTO = new OptionStateDTO(
                    option.getName(),
                    requestedEvent.getLmsrOptionPrice(option),
                    requestedEvent.getLmsrQuantityBought(option)
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
        Trade trade = requestedEvent.purchaseLmsrShares(option,quantity);

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

    private Map<Integer, Event> createEvents(
            List<EventXmlData> eventsData,
            Map<Integer, User> marketMakersByEventId) {

        Map<Integer, Event> events = new LinkedHashMap<>();

        for (EventXmlData eventData : eventsData) {
            int id = eventData.id();

            CommissionMethod commissionMethod =
                    createCommissionMethod(eventData.commissionMethod());

            List<Option> options =
                    createOptions(eventData.options());

            TradingMethod tradingMethod =
                    createTradingMethod(
                            eventData.tradingMethod(),
                            options
                    );

            User marketMaker =
                    marketMakersByEventId.get(id);

            if (marketMaker == null) {
                throw new IllegalStateException(
                        "Event " + id + " does not have a Market Maker"
                );
            }

            Account eventAccount = new Account(0);

            Event event = new Event(
                    id,
                    eventData.name(),
                    eventData.description(),
                    eventData.commission(),
                    commissionMethod,
                    options,
                    tradingMethod,
                    eventAccount,
                    marketMaker
            );

            events.put(id, event);
        }

        return events;
    }


    private Map<String, User> createUsers(List<UserXmlData> usersData) {
        Map<String, User> users = new LinkedHashMap<>();

        for (UserXmlData userData : usersData) {
            String username = userData.username();
            String key = normalizeUsername(username);

            if (users.containsKey(key)) {
                throw new IllegalArgumentException(
                        "Duplicate username: " + username
                );
            }

            User user = new User(username, userData.initialCash());
            users.put(key, user);
        }

        return users;
    }

    private String normalizeUsername(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, User> copyAndValidateUsers(Map<String, User> loadedUsers) {
        if (loadedUsers == null) {
            throw new IllegalArgumentException("Save file does not contain users");
        }

        Map<String, User> users = new LinkedHashMap<>();
        for (User user : loadedUsers.values()) {
            if (user == null) {
                throw new IllegalArgumentException("Save file contains an invalid user");
            }
            String key = normalizeUsername(user.getUsername());
            if (users.putIfAbsent(key, user) != null) {
                throw new IllegalArgumentException("Save file contains duplicate username: " + user.getUsername());
            }
        }
        return users;
    }

    private Map<Integer, Event> copyAndValidateEvents(
            Map<Integer, Event> loadedEvents, Collection<User> loadedUsers) {
        if (loadedEvents == null) {
            throw new IllegalArgumentException("Save file does not contain events");
        }

        Map<Integer, Event> events = new LinkedHashMap<>();
        for (Event event : loadedEvents.values()) {
            if (event == null || events.putIfAbsent(event.getId(), event) != null) {
                throw new IllegalArgumentException("Save file contains invalid or duplicate events");
            }
            boolean hasKnownMarketMaker = loadedUsers.stream().anyMatch(event::hasMarketMaker);
            if (!hasKnownMarketMaker) {
                throw new IllegalArgumentException(
                        "Event " + event.getId() + " has no Market Maker in the saved users");
            }
        }
        return events;
    }



    private TradingMethod createTradingMethod(
            TradingMethodXmlData methodData,
            List<Option> options) {

        return switch (methodData) {
            case LmsrXmlData lmsr ->
                    new LMSR(lmsr.liquidityParameter(), options);

            case OrderBookXmlData orderBook ->
                    new OrderBook(
                            orderBook.allowMint(),
                            orderBook.initial(),
                            orderBook.d(),
                            options
                    );
        };
    }

    private Map<Integer, User> createMarketMakerAssignments(
            List<UserXmlData> usersData,
            Map<String, User> usersByName) {

        Map<Integer, User> marketMakersByEventId = new HashMap<>();

        for (UserXmlData userData : usersData) {
            User user = usersByName.get(
                    normalizeUsername(userData.username())
            );

            for (Integer eventId : userData.marketMakerEventIds()) {
                marketMakersByEventId.put(eventId, user);
            }
        }

        return marketMakersByEventId;
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


