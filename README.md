# GuessMarket

GuessMarket is a **Java prediction market system** developed as part of a four-assignment Object-Oriented Programming project.

This repository contains **Exercise 01**, the first iteration of the project. The system is designed to evolve across four assignments, with each iteration extending the functionality and architecture introduced in the previous stage.

## Current Features

The first iteration implements the core prediction-market engine, including:

* Loading market and event definitions from XML using **JAXB**
* Validation of loaded market data
* Event and option management
* Share purchasing and trade history
* Event lifecycle and settlement
* Purchase-time and settlement-time commission models
* DTO-based communication between the engine and presentation layer

## LMSR Market Maker

GuessMarket uses the **Logarithmic Market Scoring Rule (LMSR)** as its trading mechanism.

The implementation dynamically calculates option prices according to the number of shares purchased and uses the LMSR cost function to determine the cost of new purchases.

The market maker also calculates the initial subsidy required for an event based on its liquidity parameter and available options.

## Object-Oriented Design

The project is structured around separate responsibilities:

* **Engine** – coordinates application operations and exposes the market API
* **Domain Model** – represents events, options, trades, accounts, commissions, and trading behavior
* **DTOs** – transfer structured data without exposing mutable domain objects
* **XML Loader** – converts JAXB-generated XML objects into validated application entities

Trading behavior is represented through a `TradingMethod` abstraction, with **LMSR** providing the trading implementation for this first assignment.

## Technologies

* Java
* Object-Oriented Programming
* JAXB
* XML / XSD
* LMSR prediction-market algorithm

## Project Status

This repository represents **Iteration 1 of 4**.

Future assignments build on the architecture and functionality introduced here, while this version focuses on establishing the core market engine, domain model, XML loading, trading logic, and event lifecycle.
