package guessmarket.javafx.controller;

import guessmarket.dto.EventDTO;
import java.util.List;

public final class EventsControllerFilterTest {
    public static void main(String[] args) {
        EventDTO lmsr = event("LMSR", "ACTIVE", "ON_PURCHASE");
        EventDTO orderBook = event("ORDER_BOOK", "CLOSED", "ON_CLOSE");
        check(EventsController.matchesFilters(lmsr, "All", "All", "All"), "All filter failed");
        check(EventsController.matchesFilters(lmsr, "LMSR", "ACTIVE", "ON_PURCHASE"),
                "Exact LMSR filters failed");
        check(!EventsController.matchesFilters(lmsr, "ORDER_BOOK", "ACTIVE", "ON_PURCHASE"),
                "Method filter failed");
        check(EventsController.matchesFilters(orderBook, "ORDER_BOOK", "CLOSED", "ON_CLOSE"),
                "Composed Order Book filters failed");
        check(!EventsController.matchesFilters(orderBook, "All", "ACTIVE", "ON_CLOSE"),
                "State filter failed");
        check(!EventsController.matchesFilters(orderBook, "All", "CLOSED", "ON_PURCHASE"),
                "Commission filter failed");
        check(EventsController.findPreferred(List.of(lmsr, orderBook), orderBook.id()) == orderBook,
                "Preferred selection was not retained");
        check(EventsController.findPreferred(List.of(lmsr), orderBook.id()) == null,
                "Hidden selection was incorrectly retained");
        System.out.println("EventsControllerFilterTest: all checks passed");
    }

    private static EventDTO event(String method, String state, String commission) {
        int id = "LMSR".equals(method) ? 1 : 2;
        return new EventDTO(id, "Event", "Description", 5, commission,
                List.of("Yes", "No"), state, method, 10.25, "MM");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
