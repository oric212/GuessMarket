package guessmarket.javafx.view;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class LmsrValueFormatter {
    private static final DecimalFormat TWO_DECIMALS = new DecimalFormat(
            "0.##", DecimalFormatSymbols.getInstance(Locale.US));

    private LmsrValueFormatter() {
    }

    public static String format(double value) {
        if (value == 0.0) return "0";
        if (value == 1.0) return "1";
        if (value > 0.0 && value < 0.01) return "<0.01";

        String formatted = TWO_DECIMALS.format(value);
        if (value < 1.0 && "1".equals(formatted)) return "> 0.99";
        return formatted;
    }
}
