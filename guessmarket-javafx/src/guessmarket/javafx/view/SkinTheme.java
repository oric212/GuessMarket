package guessmarket.javafx.view;

public enum SkinTheme {
    DEFAULT("Default", null),
    OCEAN("Ocean", "/guessmarket/javafx/view/ocean.css"),
    DUSK("Dusk", "/guessmarket/javafx/view/dusk.css");

    private final String displayName;
    private final String stylesheet;

    SkinTheme(String displayName, String stylesheet) {
        this.displayName = displayName;
        this.stylesheet = stylesheet;
    }

    public String stylesheet() {
        return stylesheet;
    }

    public static SkinTheme effectiveTheme(boolean skinsEnabled, SkinTheme selectedTheme) {
        return skinsEnabled && selectedTheme != null ? selectedTheme : DEFAULT;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
