package guessmarket.javafx.view;

public final class AnimationSettings {
    public static final int TAB_FADE_MILLIS = 250;
    public static final int XML_SUCCESS_SCALE_MILLIS = 350;
    public static final int ACTION_SUCCESS_FADE_MILLIS = 300;

    private boolean enabled;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
