package guessmarket.server.api;

import guessmarket.domain.OrderSide;
import guessmarket.dto.EventStateDTO;
import guessmarket.dto.OrderSubmissionResultDTO;
import guessmarket.dto.PurchaseResultDTO;
import guessmarket.server.ServerState;

import java.util.Locale;

public final class TradingApiService {
    private final ServerState state;

    public TradingApiService(ServerState state) { this.state = state; }

    public EventStateDTO start(String token, int eventId) {
        return invoke(() -> state.startEvent(token, eventId));
    }

    public PurchaseResultDTO purchase(String token, int eventId, PurchaseRequest request) {
        if (request.quantity() <= 0) throw bad("INVALID_QUANTITY", "Quantity must be positive");
        if (request.optionIndex() <= 0) throw bad("INVALID_OPTION", "Option index must be positive");
        return invoke(() -> state.purchaseShares(
                token, eventId, request.optionIndex(), request.quantity()));
    }

    public OrderSubmissionResultDTO order(String token, int eventId, OrderRequest request) {
        OrderSide side;
        try {
            side = OrderSide.valueOf(request.side() == null ? "" : request.side().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw bad("INVALID_ORDER_SIDE", "Order side must be BUY or SELL");
        }
        if (request.optionIndex() <= 0) throw bad("INVALID_OPTION", "Option index must be positive");
        if (request.quantity() <= 0) throw bad("INVALID_QUANTITY", "Quantity must be positive");
        if (!Double.isFinite(request.price()) || request.price() <= 0.0) {
            throw bad("INVALID_PRICE", "Price must be finite and positive");
        }
        OrderSide requestedSide = side;
        return invoke(() -> state.submitOrder(token, eventId, request.optionIndex(),
                requestedSide, request.quantity(), request.price()));
    }

    public EventStateDTO close(String token, int eventId, CloseEventRequest request) {
        if (request.winningOptionIndex() <= 0) {
            throw bad("INVALID_WINNER", "Winning option index must be positive");
        }
        return invoke(() -> state.closeEvent(token, eventId, request.winningOptionIndex()));
    }

    private <T> T invoke(Action<T> action) {
        try {
            return action.run();
        } catch (ServerState.UnknownSessionException error) {
            throw new ApiException(401, "INVALID_SESSION", error.getMessage());
        } catch (IllegalArgumentException error) {
            throw bad("INVALID_OPERATION", error.getMessage());
        } catch (IllegalStateException error) {
            throw new ApiException(409, "OPERATION_REJECTED", error.getMessage());
        }
    }

    private static ApiException bad(String code, String message) {
        return new ApiException(400, code, message);
    }

    @FunctionalInterface private interface Action<T> { T run(); }
}
