package guessmarket.javafx.view;

public final class SkinThemeTest {
    public static void main(String[] args) {
        check(SkinTheme.effectiveTheme(false, SkinTheme.OCEAN) == SkinTheme.DEFAULT,
                "Disabled skins must always use Default");
        check(SkinTheme.effectiveTheme(true, SkinTheme.OCEAN) == SkinTheme.OCEAN,
                "Ocean selection was not retained when enabled");
        check(SkinTheme.effectiveTheme(true, SkinTheme.DUSK) == SkinTheme.DUSK,
                "Dusk selection was not retained when enabled");
        check(SkinTheme.effectiveTheme(true, SkinTheme.DEFAULT) == SkinTheme.DEFAULT,
                "Switching back to Default failed");
        check(SkinTheme.values().length >= 3, "At least three total themes are required");
        check(SkinTheme.class.getResource(SkinTheme.OCEAN.stylesheet()) != null,
                "Ocean stylesheet is missing");
        check(SkinTheme.class.getResource(SkinTheme.DUSK.stylesheet()) != null,
                "Dusk stylesheet is missing");
        System.out.println("SkinThemeTest: all checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
