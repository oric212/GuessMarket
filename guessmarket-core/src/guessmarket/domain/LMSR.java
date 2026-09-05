package guessmarket.domain;

import java.io.Serializable;
import java.util.HashMap;
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

        if(options.isEmpty()){
            throw new IllegalArgumentException("options is Empty");
        }

        this.liquidityParameter = liquidityParameter;
        this.options = List.copyOf(options);
        this.quantitiesByOption = new HashMap<>();

        for (Option option : this.options) {
            quantitiesByOption.put(option, 0);
        }
    }

    public List<Option> getOptions() {
        return options;
    }

    public double calculateCurrentValue(Option option) {

        if (!options.contains(option)) {
            throw new IllegalArgumentException("Option does not belong to this LMSR market");
        }

        double numerator = calculateExponent(option);
        double denominator = 0;

        for (Option currOption : options) {
            denominator += calculateExponent(currOption);
        }

        return numerator / denominator;
    }

    public double calculatePurchaseCost(Option option, int quantity) {

        validateOption(option);
        validateQuantity(quantity);

        double beforeCost = 0;
        double afterCost = 0;

        for (Option currOption : options) {
            beforeCost += calculateExponent(currOption);
        }

        beforeCost = (double) liquidityParameter * Math.log(beforeCost);

        for (Option currOption : options) {

            if (currOption == option) {
                afterCost += Math.exp((quantitiesByOption.get(currOption) + quantity) /(double) liquidityParameter);
            }else {
                afterCost += calculateExponent(currOption);
            }
        }

        afterCost = (double) liquidityParameter * Math.log(afterCost);

        return afterCost - beforeCost;
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
        quantitiesByOption.put(option, currentQuantity + quantity);
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

    private double calculateExponent(Option option) {
        return Math.exp(quantitiesByOption.get(option) / (double) liquidityParameter);
    }

    public int getQuantityBought(Option option) {
        return quantitiesByOption.get(option);
    }

    @Override
    public TradingMethodType getType() {
        return TradingMethodType.LMSR;
    }
}
