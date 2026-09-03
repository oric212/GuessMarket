package guessmarket.domain;

import java.io.Serializable;

public final class Order implements Serializable {
    private final User user;
    private final Option option;
    private final OrderSide side;
    private final int originalQuantity;
    private final double pricePerShare;
    private final long submissionSequence;
    private int remainingQuantity;

    Order(User user, Option option, OrderSide side, int quantity,
          double pricePerShare, long submissionSequence) {
        this.user = user;
        this.option = option;
        this.side = side;
        this.originalQuantity = quantity;
        this.remainingQuantity = quantity;
        this.pricePerShare = pricePerShare;
        this.submissionSequence = submissionSequence;
    }

    public User getUser() { return user; }
    public Option getOption() { return option; }
    public OrderSide getSide() { return side; }
    public int getOriginalQuantity() { return originalQuantity; }
    public int getRemainingQuantity() { return remainingQuantity; }
    public double getPricePerShare() { return pricePerShare; }
    public long getSubmissionSequence() { return submissionSequence; }
    public boolean isFilled() { return remainingQuantity == 0; }

    void fill(int quantity) {
        if (quantity <= 0 || quantity > remainingQuantity) {
            throw new IllegalArgumentException("Fill quantity must be positive and cannot exceed remaining quantity");
        }
        remainingQuantity -= quantity;
    }
}
