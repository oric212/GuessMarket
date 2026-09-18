package guessmarket.domain;

import java.io.Serializable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class LMSR implements TradingMethod {
    private final int liquidityParameter;
    private final List<Option> options;
    private final Map<Option, Integer> quantitiesByOption;

    public LMSR(int liquidityParameter, List<Option> options) {

        if(liquidityParameter <= 0){
            throw new IllegalArgumentException("liquidityParameter should be greater than zero");
        }

        if(options == null){
            throw new IllegalArgumentException("Options is null");
        }

        if(options.size() < 2){
            throw new IllegalArgumentException("LMSR requires at least two options");
        }

        this.liquidityParameter = liquidityParameter;
        this.options = List.copyOf(options);
        this.quantitiesByOption = new IdentityHashMap<>();

        for (Option option : this.options) {
            if (option == null || quantitiesByOption.put(option, 0) != null) {
                throw new IllegalArgumentException("LMSR requires distinct, non-null options");
            }
        }
    }

    public List<Option> getOptions() {
        return options;
    }

    public int getLiquidityParameter() {
        return liquidityParameter;
    }

    public double calculateCurrentValue(Option option) {

        if (!options.contains(option)) {
            throw new IllegalArgumentException("Option does not belong to this LMSR market");
        }

        double maximum = maxScaledQuantity(0, null);
        return Math.exp(scaledQuantity(option) - maximum)
                / scaledExponentSum(maximum, 0, null);
    }

    public double calculatePurchaseCost(Option option, int quantity) {

        validateOption(option);
        validateQuantity(quantity);

        Math.addExact(quantitiesByOption.get(option), quantity);
        return logSumExpCost(quantity, option) - logSumExpCost(0, null);
    }

    public double calculateInitialSubsidy() {

        double numOfOptions = options.size();

        return (double) liquidityParameter * Math.log(numOfOptions);
    }

    public int getPurchasedQuantity(Option option) {

        validateOption(option);

        return quantitiesByOption.get(option);
    }

    void recordPurchase(Option option, int quantity) {
        validateOption(option);
        validateQuantity(quantity);

        int currentQuantity = quantitiesByOption.get(option);
        quantitiesByOption.put(option, Math.addExact(currentQuantity, quantity));
    }

    private void validateOption(Option option) {
        if (!options.contains(option)) {
            throw new IllegalArgumentException(
                    "Option does not belong to this LMSR market"
            );
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "Quantity should be greater than zero"
            );
        }
    }

    private double scaledQuantity(Option option) {
        return quantitiesByOption.get(option) / (double) liquidityParameter;
    }

    private double maxScaledQuantity(int selectedIncrease, Option selectedOption) {
        double maximum = Double.NEGATIVE_INFINITY;
        for (Option option : options) {
            int quantity = quantitiesByOption.get(option)
                    + (option == selectedOption ? selectedIncrease : 0);
            maximum = Math.max(maximum, quantity / (double) liquidityParameter);
        }
        return maximum;
    }

    private double scaledExponentSum(double maximum, int selectedIncrease, Option selectedOption) {
        double sum = 0.0;
        for (Option option : options) {
            int quantity = quantitiesByOption.get(option)
                    + (option == selectedOption ? selectedIncrease : 0);
            sum += Math.exp(quantity / (double) liquidityParameter - maximum);
        }
        return sum;
    }

    private double logSumExpCost(int selectedIncrease, Option selectedOption) {
        double maximum = maxScaledQuantity(selectedIncrease, selectedOption);
        return liquidityParameter
                * (maximum + Math.log(scaledExponentSum(maximum, selectedIncrease, selectedOption)));
    }

    public int getQuantityBought(Option option) {
        return quantitiesByOption.get(option);
    }

    @Override
    public TradingMethodType getType() {
        return TradingMethodType.LMSR;
    }
}
