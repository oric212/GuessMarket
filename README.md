# GuessMarket

GuessMarket is a multi-user prediction-market platform built around two different market mechanisms: **LMSR automated market making** and a **two-sided limit order book**.

The project contains a shared Java domain engine, a Tomcat REST server, a JavaFX desktop client, and a browser client built with Vite and vanilla JavaScript. Users can join the market, fund accounts, trade event outcomes, act as market makers, monitor prices and holdings, and settle events when an outcome is known.

## Highlights

- Multi-user prediction markets with independent balances and portfolios
- Two trading mechanisms:
  - **LMSR** automated market maker
  - **Order Book** with BUY/SELL price-time priority
- Event lifecycle: `NOT_STARTED -> ACTIVE -> CLOSED`
- Market Maker ownership and authorization
- Per-user holdings, reservations, balances, transaction history, and participation data
- Event settlement and winner payouts
- Commission support
- REST API backed by Tomcat 11
- JavaFX desktop client
- Browser client with live polling and trading actions
- XML market import with XSD and semantic validation
- Immutable query/DTO layer between the domain and presentation layers
- Concurrency protection around shared server state

---

## How GuessMarket Works

A GuessMarket event represents a question with a set of possible outcomes. Users trade shares in those outcomes, and the market price changes as participants express their expectations through trading.

Each event has a designated **Market Maker (MM)**. The Market Maker owns the event lifecycle and is responsible for starting and eventually closing the event.

When an event closes, a winning option is selected and the market is settled.

### Event Lifecycle

1. **Not Started** — the event exists but cannot yet be traded.
2. **Active** — users may trade according to the event's market mechanism.
3. **Closed** — a winning outcome has been selected and settlement has been performed.

Only the event's Market Maker may start or close it.

---

# Trading Mechanisms

## LMSR

GuessMarket implements a **Logarithmic Market Scoring Rule (LMSR)** automated market maker.

Unlike a traditional order book, an LMSR market does not require another trader on the opposite side of a transaction. The market maker continuously provides liquidity and calculates prices from the outstanding quantities of each option.

The LMSR implementation includes:

- Dynamic option prices
- Quantity-based purchase cost calculation
- Configurable liquidity parameter `b`
- Market subsidy calculation
- Market Maker funding at event start
- Per-user purchase history
- Holdings tracking
- Commission handling
- Event settlement
- Remaining subsidy return to the Market Maker

As users buy shares, the relative prices of the outcomes change automatically.

## Order Book

Order Book events use a traditional two-sided market.

Each option maintains BUY and SELL liquidity with:

- Price-time priority
- Partial fills
- Multi-order matching
- Separate bid and ask sides
- SELL-side share reservation
- Trade accounting
- Commission handling
- `LAST`, `BID`, `ASK`, `MID`, and `SPREAD` market statistics
- Optional complementary-share minting for supported two-option markets

An incoming order may execute against several resting orders until it is completely filled or no compatible liquidity remains.

Ordinary crossing executes at the resting order's price.

---

# Users and Accounts

Every user has an independent account and market state.

GuessMarket tracks:

- Current cash balance
- Market Maker assignments
- Holdings per event and option
- Reserved shares for pending SELL orders
- LMSR purchases
- Order Book activity
- Event participation
- Cash paid and received
- Transaction history
- Settlement results

Account mutations are centralized on the server so that top-ups, market funding, purchases, trades, commissions, payouts, and subsidy transfers all produce consistent accounting records.

Runtime users are created by logging in with a username. Usernames are unique case-insensitively for the lifetime of the running server.

---

# Market Makers

A user may act as the Market Maker for multiple events.

Market Makers can:

- Own imported events
- Start their events
- Supply required market funding
- Monitor market activity
- Close events
- Select winning outcomes
- Receive remaining LMSR subsidy where applicable

Lifecycle permissions are enforced by the server rather than relying only on client-side controls.

---

# Web Client

The browser client lives in `guessmarket-web` and is built with **Vite + vanilla JavaScript**.

It provides an authenticated interface with two primary workspaces.

## Events

The Events screen provides:

- Event summary table
- Filtering by trading method, state, and commission
- Event selection
- Detailed market inspection
- LMSR prices and quantities
- LMSR market history
- Order Book configuration
- `LAST`, `BID`, `ASK`, `MID`, and `SPREAD` statistics
- Pending Order Book state
- Closed-event winner information
- Empty, loading, error, retry, and recovery states

## User

The User screen provides:

- Public user list
- Signed-in account information
- Account top-up
- Transaction history
- Market Maker assignments
- Event participations
- Holdings
- LMSR share purchases
- Order Book BUY and SELL submission
- Event start controls for owned markets
- Event close controls with winner selection

The authenticated screens poll the server approximately every **850 ms** while active.

Refreshes are coalesced to avoid overlapping update requests, and user selections and unsent form values remain stable while fresh server snapshots are applied.

Session data is stored in `sessionStorage`.

Authenticated requests use the server-issued token through:

```text
X-GuessMarket-Session
```

---

# JavaFX Client

GuessMarket also includes a JavaFX desktop client.

Its responsibilities include:

- Login and server communication
- Event monitoring
- User and account views
- Market Maker operations
- Trading
- XML upload
- Background network requests
- Structured error handling

The JavaFX application acts as an HTTP client of the Tomcat server rather than maintaining a separate authoritative market engine.

This keeps the server as the single source of truth for market state.

---

# REST Server

`guessmarket-server` is a Jakarta Servlet application designed for **Tomcat 11**.

The default deployment context is:

```text
/GuessMarket
```

The REST API is exposed under:

```text
/GuessMarket/api/
```

## Main API Endpoints

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/api/health` | Check server health |
| `POST` | `/api/login` | Login or register a runtime user |
| `GET` | `/api/events` | Retrieve event summaries |
| `GET` | `/api/events/{id}` | Retrieve detailed event state |
| `GET` | `/api/users` | Retrieve public user information |
| `GET` | `/api/user/me` | Retrieve the authenticated user's private state |
| `POST` | `/api/user/account/topup` | Add funds to the current account |
| `POST` | `/api/events/upload` | Import events from XML |
| `POST` | `/api/events/{id}/start` | Start an owned event |
| `POST` | `/api/events/{id}/purchases` | Purchase LMSR shares |
| `POST` | `/api/events/{id}/orders` | Submit an Order Book order |
| `POST` | `/api/events/{id}/close` | Close an owned event |

Authenticated requests include:

```text
X-GuessMarket-Session: <session-token>
```

Server errors use HTTP status codes together with structured JSON:

```json
{
  "success": false,
  "code": "ERROR_CODE",
  "message": "Human-readable explanation"
}
```

---

# XML Market Import

Markets can be imported from XML and fully validated before they become visible to users.

The import pipeline includes:

- XSD validation
- Semantic validation
- Duplicate-name detection
- Trading-method configuration validation
- Event-option validation
- Atomic import behavior

The server reads uploaded XML directly from the request stream rather than saving the raw upload to disk.

An import is prepared completely before being committed to server state. If any part fails validation, no partial market state is added.

Imported events initially enter the `NOT_STARTED` state.

Market funding is deferred until the assigned Market Maker explicitly starts the event.

---

# Architecture

GuessMarket separates domain logic, networking, and presentation into independent modules.

```text
GuessMarket/
├── guessmarket-core/      Domain model and trading engine
├── guessmarket-server/    Tomcat/Jakarta REST server
├── guessmarket-javafx/    JavaFX desktop client
├── guessmarket-web/       Vite browser client
├── guessmarket-console/   Console interface
├── schema/                XML schemas
└── Docs/                  Supporting documentation
```

## Core

`guessmarket-core` contains the main business rules and market implementation.

Important components include:

- `Engine`
- `GuessMarketEngine`
- `Event`
- `LMSR`
- `OrderBook`
- `User`
- `UserParticipation`
- XML loading and validation
- Immutable result and query DTOs

The core module is independent of JavaFX.

## Server

The server owns the authoritative application state.

Shared state exists once per deployed web application.

A fair read/write lock allows multiple concurrent read operations while serializing state-changing operations such as:

- Login
- Top-up
- XML import
- Event start
- Event close
- LMSR purchases
- Order Book submissions
- Settlement

## Client Boundary

Clients consume immutable snapshots rather than receiving references to mutable domain objects.

This allows the same backend to support multiple presentation layers without coupling UI logic to the market engine.

---

# Technology Stack

## Backend

- Java 25
- Jakarta Servlet API
- Tomcat 11
- Gson
- JAXB
- XML Schema / XSD validation

## Desktop

- JavaFX 25

## Web

- Vite
- Vanilla JavaScript
- HTML
- CSS
- Fetch API

---

# Running GuessMarket

## Requirements

- Java 25
- Tomcat 11
- Node.js
- npm
- Windows for the provided helper scripts

## Build the Server

From the repository root:

```bat
build-server.bat
```

The server build produces:

```text
server-dist/GuessMarket.war
```

Deploy the WAR into Tomcat's `webapps` directory and start Tomcat.

The API should then be available at:

```text
http://localhost:8080/GuessMarket/api/
```

To verify the deployment:

```text
http://localhost:8080/GuessMarket/api/health
```

---

## Run the Web Client

From the repository root:

```bash
cd guessmarket-web
npm install
npm run dev
```

Then open:

```text
http://localhost:5173
```

During development, Vite proxies:

```text
/api/*
```

to:

```text
http://localhost:8080/GuessMarket/api/*
```

### Production Build

```bash
npm run build
```

The generated bundle is written to:

```text
guessmarket-web/dist
```

To preview the production build locally:

```bash
npm run preview
```

---

# Testing

The repository contains regression testing for the Java layers and API-oriented tests for the web client.

Useful commands include:

```bat
test-server.bat
```

```bat
test-javafx.bat
```

From `guessmarket-web`:

```bash
npm test
```

The tests cover behavior including:

- Market operations
- Trading rules
- Account mutations
- Server contracts
- Error handling
- Web API behavior
- Regression-sensitive workflows

---

# Important Implementation Details

Several implementation decisions are important to GuessMarket's behavior:

- Market operations are validated server-side even when clients perform their own validation.
- Order Book matching uses price-time priority.
- Crossing orders execute at the resting order's price.
- SELL orders reserve shares to prevent the same holdings from being sold multiple times.
- Complementary minting is restricted to supported two-option Order Book markets.
- Order Book position value prefers `MID`, then `LAST`, otherwise remains unavailable.
- Closed Order Book profit/loss is calculated from total cash received minus total cash paid.
- Invalid non-divisible funding configurations are rejected instead of silently truncating shares.
- Transaction history records actual account mutations instead of reconstructing them afterward.
- Server DTOs are immutable presentation-safe snapshots.
- The authoritative market state exists on the server.

---

# Persistence Model

GuessMarket currently keeps runtime state in memory.

This includes:

- Users
- Sessions
- Account balances
- Events
- Holdings
- Orders
- Trades
- Transaction history

Restarting the deployed server application resets the runtime state.

This keeps the current implementation focused on prediction-market mechanics, accounting, concurrency, client/server architecture, and trading behavior.

A natural future extension would be adding persistent database storage while preserving the existing domain and REST API boundaries.
