# GuessMarket — Exercise 02

GuessMarket is a Java 25 / JavaFX 25 prediction-market application. Exercise 02 adds multiple independent user accounts, Market Maker assignments, event lifecycle management, a two-sided Order Book, and a graphical interface while retaining LMSR.


## Requirements and launch

The submitted package targets 64-bit Windows and requires Java 25. It bundles JavaFX 25 and JAXB runtime dependencies; IntelliJ is not required.

1. Extract the complete ZIP. Keep `lib` beside the JARs and `run.bat`.
2. From Windows Command Prompt, run `"<extracted-directory>\run.bat"`. Paths containing spaces work.
3. The launcher uses `%JAVA_HOME%\bin\java.exe` when `JAVA_HOME` is set, otherwise `java` from `PATH`, and checks for Java 25.

The application loads Exercise 02 XML exclusively through **Load File**. A valid load replaces the current market; an invalid load reports its cause and preserves the previous market.

To reproduce the package, set `JAVAFX_SDK` to a JavaFX 25 SDK and `JAXB_HOME` to a JAXB RI directory containing `mod`, then run `build-submission.bat`. It recreates only `submission-build` and `submission`.

## Architecture

- `guessmarket-core`: passive, JavaFX-independent `Engine`, domain, XML/JAXB validation, immutable query DTOs, and result DTOs.
- `guessmarket-javafx`: active UI. `GuessMarketApplication` starts JavaFX; `MainController` handles loading/refresh; `EventsController` monitors markets; `UsersController` exposes selected-user actions and participation details.
- `guessmarket-console`: retained Exercise 01 client, not the Exercise 02 launch target.

The JavaFX layer pulls immutable snapshots and invokes typed Engine operations. Core has no JavaFX properties, listeners, tasks, or callbacks. XML loading uses a JavaFX `Task`, visible progress, and a short simulated delay.

## Users and Market Makers

Every user has an independent balance and may be Market Maker for several events. Only the assigned MM starts or closes an event. A negative-causing transaction completes, then blocks that user from initiating operations; passive settlement credits remain possible. There is no top-up.

The Users screen shows accounts, MM assignments, active/closed participations, personal LMSR history, Order Book holdings/accounting, and every event available for a first action. Successful actions refresh both screens.

## Trading methods

LMSR startup transfers its calculated subsidy from the MM. Purchases use the LMSR cost function. Closing pays one unit per winning share, applies configured commission, and returns remaining subsidy to the MM.

Order Book events have independent option books with BUY/SELL price-time priority, partial/multi-order fills, SELL reservations, optional complementary minting, commissions, and backed settlement. Closing pays `d` per winning share, zero for losing shares, and drains the event account.

## Skin bonus

Skin switching starts disabled, preserving the original appearance. In the global header, select **Enable skins**, then choose **Default**, **Ocean**, or **Dusk** from the Skin selector. Clearing **Enable skins** immediately restores the original default skin on both Events and Users screens.

## Animation bonus

Animations also start disabled. Select **Enable animations** in the global header to enable a 250 ms screen fade when switching tabs, a 350 ms scale confirmation after a successful XML load, and a 300 ms fade confirmation after a successful user action. Clearing the checkbox bypasses all three effects; application actions and refreshes are never delayed by them.

## Create Event bonus

Select a user on the Users screen and complete the **Create Event** section to create an LMSR or Order Book event. The selected user becomes its Market Maker, and the new `NOT_STARTED` event immediately appears in the normal event and MM views so it can be started through the existing workflow.

## Implementation choices

- Events, LMSR state, holdings, DTOs, and independent Order Book option books support two or more ordered options.
- Event lifecycle and trading APIs accept either the legacy internal integer ID or the normalized unique event name.
- EX03 does not define N-outcome MINT semantics; auto-mint therefore remains available only for complementary two-option Order Book events.
- Ordinary crossing executes at the resting order's price.
- Auto-mint keeps the resting leg's offered price; the incoming leg is `d - resting price`.
- OB holding value uses MID, otherwise LAST, otherwise `N/A`.
- Non-divisible `initial / d` is rejected instead of truncating shares.
- Cumulative gross purchase amount is historical spend, not remaining-position cost basis.
- Closed OB P/L is `total cash received - total cash paid`; before closure it is unavailable.
- Bonuses 1 (skin switching), 2 (animations), and 4 (Create Event) are implemented.

## Main components

- `Engine` / `GuessMarketEngine`: API, atomic replacement, DTO projection, persistence.
- `XMLLoader`: JAXB conversion and Exercise 01/02 semantic validation.
- `Event`: lifecycle, MM authorization, funding, trading coordination, commission, settlement.
- `LMSR`: costs, prices, subsidy, quantities.
- `OrderBook`: books, matching, partial fills, statistics, and mint planning.
- `User` / `UserParticipation`: balance/block state, holdings, reservations, history, cash totals.
- DTO packages: immutable presentation-safe snapshots with no mutable domain leakage.
- `MainController`: FileChooser, Task/progress/error state, cross-screen refresh.
- `EventsController`: composed filters and method-specific monitoring.
- `UsersController`: selected-user workspace, MM actions, purchases/orders, and notifications.

Framework-free regression programs live under the two test directories. The package ships production classes and CSS only and does not depend on IDE output or source directories.

## EX03 server foundation

`guessmarket-server` is the Tomcat web module. Run `build-server.bat` with Java 25 to produce the single deployable artifact at `server-dist/GuessMarket.war`. The build downloads pinned compile/runtime dependencies into the ignored `.deps` cache, compiles core and server sources, and packages Gson plus JAXB runtime dependencies under `WEB-INF/lib`. The Servlet API is compile-only because Tomcat provides it.

Deploy `GuessMarket.war` to Tomcat 11's `webapps` directory. Its stable context path is `/GuessMarket`, with these initial JSON endpoints:

- `GET /GuessMarket/api/health` returns deployment status.
- `GET /GuessMarket/api/events` returns immutable event summaries.
- `GET /GuessMarket/api/events/{url-encoded-event-name}` returns details using EX03's case-insensitive name identity.
- `POST /GuessMarket/api/login` accepts `{"username":"Alice"}`, atomically registers a runtime user, and returns a UUID session token.
- `GET /GuessMarket/api/users` returns only public username, balance, and Market Maker status.
- `GET /GuessMarket/api/user/me` returns the token owner's private user/account state.
- `POST /GuessMarket/api/user/account/topup` accepts `{"amount":25.0}` and returns the updated private state.

Private endpoints identify the caller with the `X-GuessMarket-Session` header. Usernames are trimmed and unique case-insensitively for the lifetime of the server. EX03 does not specify a nonzero opening grant, so runtime users start at `0.00` and add funds through top-up; EX02 XML users retain their configured initial cash. Logout is not yet required, so registrations and tokens remain active until the in-memory server state is restarted.

Each user account stores real immutable transaction entries rather than reconstructing history. Entries have a deterministic sequence, transaction type, signed balance change, resulting balance, optional event name, and description. Creation, top-up, event funding, purchases/trades, commissions, settlement, and subsidy return all use the centralized account mutation path. Other users never receive this private history.

Errors use HTTP status codes and a JSON body shaped as `{"success":false,"code":"...","message":"..."}`. State lives once per deployed web application in the servlet context and intentionally disappears at restart. `ServerState` applies a fair read/write lock: DTO queries may run concurrently, while login, top-up, and future state-changing engine calls use the exclusive write path to preserve identity and accounting atomicity. XML upload, trading endpoints, polling, and chat remain deferred to later EX03 work.
