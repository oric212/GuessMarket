package guessmarket.engine.market;

public final class Option {
    private final String name;

    public Option(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Option name cannot be null or blank"
            );
        }

        this.name = name;
    }

    public String getName() {
        return name;
    }
}
