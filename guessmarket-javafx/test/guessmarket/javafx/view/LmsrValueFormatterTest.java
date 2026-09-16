package guessmarket.javafx.view;

public final class LmsrValueFormatterTest {
    public static void main(String[] args) {
        check(0.0, "0");
        check(1.0, "1");
        check(0.5, "0.5");
        check(0.376, "0.38");
        check(0.000_001, "<0.01");
        check(0.9999, "> 0.99");
        System.out.println("LmsrValueFormatterTest: all checks passed");
    }

    private static void check(double value, String expected) {
        String actual = LmsrValueFormatter.format(value);
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + value + " to display as " + expected + ", got " + actual);
        }
    }
}
