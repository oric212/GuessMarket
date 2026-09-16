package guessmarket.javafx.controller;

import guessmarket.dto.EventDTO;
import guessmarket.dto.UserDTO;

import java.util.List;

public final class UsersControllerWorkflowTest {
    public static void main(String[] args) {
        UserDTO mm = user("MM", false);
        UserDTO trader = user("Trader", false);
        UserDTO blocked = user("Blocked", true);
        EventDTO notStarted = event(1, "LMSR", "NOT_STARTED", "MM");
        EventDTO activeLmsr = event(2, "LMSR", "ACTIVE", "MM");
        EventDTO activeBook = event(3, "ORDER_BOOK", "ACTIVE", "OtherMM");
        EventDTO closed = event(4, "ORDER_BOOK", "CLOSED", "MM");

        check(UsersController.actionAvailability(mm, notStarted).start(), "MM start should be enabled");
        check(!UsersController.actionAvailability(trader, notStarted).any(), "Non-MM can start an event");
        check(UsersController.actionAvailability(mm, activeLmsr).close(), "Active MM close should be enabled");
        check(UsersController.actionAvailability(trader, activeLmsr).lmsrPurchase(),
                "Active LMSR purchase should be enabled");
        check(UsersController.actionAvailability(trader, activeBook).orderSubmission(),
                "Active Order Book submission should be enabled");
        check(!UsersController.actionAvailability(mm, closed).any(), "Closed event has enabled actions");
        check(!UsersController.actionAvailability(blocked, activeBook).any(), "Blocked user has enabled actions");

        check(UsersController.validPrice("10") == 10.0, "Whole-number price rejected");
        check(UsersController.validPrice("10.25") == 10.25, "Two-decimal price rejected");
        check(UsersController.validPrice("10.250") == null, "Three-decimal price accepted");
        check(UsersController.validPrice("NaN") == null && UsersController.validPrice("0") == null,
                "Invalid price accepted");
        check(UsersController.formatMarketValue(null).equals("N/A"),
                "Unavailable market statistic has a misleading label");
        check(UsersController.formatProfitLoss(null).equals("N/A — available after closure"),
                "Active-event final P/L availability is unclear");

        check(UsersController.findUser(List.of(mm, trader), "trader") == trader,
                "User selection was not retained");
        check(UsersController.findEvent(List.of(notStarted, activeBook), 3) == activeBook,
                "All-events selection was not retained");
        check(UsersController.findEvent(List.of(notStarted, activeBook), 99) == null,
                "Missing event selection was fabricated");
        System.out.println("UsersControllerWorkflowTest: all checks passed");
    }

    private static UserDTO user(String username, boolean blocked) {
        return new UserDTO(username, 100, blocked, List.of(), List.of());
    }

    private static EventDTO event(int id, String method, String state, String mm) {
        return new EventDTO(id, "Event " + id, "Description", 5, "ON_PURCHASE",
                List.of("Yes", "No"), state, method, 10, mm);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
