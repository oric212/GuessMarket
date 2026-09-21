# Guess Market web client

This directory contains the Exercise 04 browser client foundation. It uses Vite and
vanilla JavaScript and communicates with the unchanged Exercise 03 Tomcat server.

## Run locally

1. Start Tomcat with `GuessMarket.war` deployed at the `/GuessMarket` context.
2. In this directory, run `npm install`.
3. Run `npm run dev`.
4. Open <http://localhost:5173>.

The Vite development server proxies browser requests from `/api/*` to
`http://localhost:8080/GuessMarket/api/*`. This avoids browser CORS/preflight
configuration and leaves the server deployment unchanged.

Use `npm run build` to create the production bundle in `dist`, `npm run preview`
to inspect that bundle locally, and `npm test` to run the API client contract tests.

This foundation intentionally includes only login and an authenticated application
shell. The complete Events and Users screens belong to the next implementation stage.
