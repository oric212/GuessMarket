# GuessMarket JavaFX client

This module is the first JavaFX stage of Exercise 02. It depends on
`guessmarket-core`; the core module has no dependency on JavaFX.

## JavaFX setup

JavaFX is distributed separately from JDK 25. Install a JavaFX 25 SDK for your
platform, then create an IntelliJ project library named `JavaFX 25` containing
the SDK's `lib` directory. No SDK or machine-specific path is stored in this
repository.

Run `guessmarket.javafx.GuessMarketApplication` with these VM options, replacing
the variable with the location of your own SDK:

```text
--module-path "<JAVAFX_SDK>/lib" --add-modules javafx.controls --enable-native-access=javafx.graphics
```

The application uses JavaFX controls only; it does not require FXML.
