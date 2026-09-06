package guessmarket.javafx.view;

public final class AnimationSettingsTest {
    public static void main(String[] args) {
        AnimationSettings settings = new AnimationSettings();
        check(!settings.isEnabled(), "Animations must start disabled");

        settings.setEnabled(true);
        check(settings.isEnabled(), "Animations could not be enabled");
        settings.setEnabled(false);
        check(!settings.isEnabled(), "Animations could not be bypassed");

        checkDuration(AnimationSettings.TAB_FADE_MILLIS, "tab fade");
        checkDuration(AnimationSettings.XML_SUCCESS_SCALE_MILLIS, "XML success scale");
        checkDuration(AnimationSettings.ACTION_SUCCESS_FADE_MILLIS, "action success fade");
        System.out.println("AnimationSettingsTest: all checks passed");
    }

    private static void checkDuration(int milliseconds, String animation) {
        check(milliseconds > 0 && milliseconds <= 2_000,
                animation + " must last between 1 and 2000 milliseconds");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
