# Guess Market Ex01 - Codex Instructions

## Scope

This is a Java 25 university OOP project called Guess Market.

We are implementing Exercise 01 only.

Authoritative specification:
`docs/Guess Market - v1.docx`

Additional lecturer guidance:
`docs/LECTURER_GUIDUIDANCE.md`

When a task depends on assignment requirements, inspect the relevant section of
the assignment before answering.

You may consider Ex02/Ex03 when reviewing architecture if it helps avoid an
obvious future redesign, but do not implement future requirements prematurely.

If this file conflicts with the official assignment, point out the conflict.

## Learning Mode

This is a learning project.

For implementation tasks:

1. Ask me for my proposed design, pseudocode, or code first.
2. Review my attempt before replacing it.
3. Explain problems and trade-offs before showing code.
4. Prefer hints and focused snippets.
5. Give complete implementations only when I explicitly request them.

Do not silently rewrite large parts of the project.

When reviewing code, consider OOP, encapsulation, SOLID, Java idioms,
maintainability, complexity where relevant, and future extensibility.

## Architecture

Current intended modules:

- engine
- dto
- console-ui

Dependency direction:

- console-ui -> engine API
- console-ui -> dto
- engine -> dto

The engine must never depend on console-ui.

The UI accesses the engine through an `Engine` interface.

The UI owns:
- user input
- menus
- console formatting
- presentation

The engine owns:
- business logic
- mutable domain state
- validation
- trading behavior

Do not expose mutable engine/domain objects directly to the UI.

Use DTOs for structured cross-layer data when appropriate.

Prefer explicit engine operations over generic String commands or universal
Interaction/Action abstractions.

## Domain Direction

Current domain concepts include:

- Event
- Option
- Account
- Trade
- EventStatus
- CommissionMethod

Use `TradingMethod` as a behavioral abstraction.

For Ex01:
`LMSR implements TradingMethod`

Do not implement Order Book yet.

Do not model event options with booleans.

Do not model event lifecycle with a boolean.

Keep `Account` focused on monetary balance rather than user identity.

## XML and Errors

XML loading belongs outside the UI.

A failed load must not corrupt or replace the last valid system state.

Do not make `XMLLoader` a Singleton without a concrete reason.

Use meaningful exceptions for validation/domain failures.

The engine describes the failure.
The UI decides how to display it.

## Console UI

Only console-ui should normally handle:

- `Scanner`
- `System.out`
- menus
- console formatting
- console-specific input handling

ConsoleUI must not perform LMSR calculations or mutate domain state directly.

## Code Quality

Use Java 25.

Prefer:

- private fields
- final where appropriate
- encapsulation
- composition
- meaningful interfaces
- enums for finite states
- small cohesive methods
- immutable DTOs where practical

Avoid:

- unnecessary setters
- global mutable state
- unnecessary Singletons
- God classes
- vague manager classes
- speculative patterns
- premature optimization

## Codex Workflow

Before substantial changes:

1. Read this file.
2. Inspect the relevant source code.
3. Inspect the relevant assignment section if needed.
4. Ask for my proposed approach first unless I explicitly request a solution.

Do not generate the entire project unless I explicitly ask.