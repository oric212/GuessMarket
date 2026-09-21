import './styles/application.css';
import { GuessMarketApi, GuessMarketApiError } from './api/guessMarketApi.js';
import { sessionService } from './services/sessionService.js';

const root = document.querySelector('#app');
const api = new GuessMarketApi({
  getSession: () => sessionService.get(),
  onInvalidSession: () => {
    sessionService.clear();
    renderLogin('Your session is no longer valid. Please log in again.');
  },
});

function escapeHtml(value) {
  return String(value).replace(/[&<>'"]/g, (character) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;',
  })[character]);
}

function renderLogin(message = '') {
  root.innerHTML = `
    <main class="login-layout">
      <section class="login-card" aria-labelledby="app-title">
        <p class="eyebrow">Prediction market</p>
        <h1 id="app-title">Guess Market</h1>
        <p class="intro">Sign in with a username to connect to the market server.</p>
        <form id="login-form" novalidate>
          <label for="username">Username</label>
          <input id="username" name="username" type="text" autocomplete="username"
                 maxlength="80" autofocus required>
          <button type="submit">Log in</button>
        </form>
        <p id="status" class="status ${message ? 'error' : ''}" role="status" aria-live="polite">${escapeHtml(message)}</p>
      </section>
    </main>`;

  const form = document.querySelector('#login-form');
  const input = document.querySelector('#username');
  const button = form.querySelector('button');
  const status = document.querySelector('#status');
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    const username = input.value.trim();
    if (!username) {
      status.textContent = 'Username cannot be blank.';
      status.className = 'status error';
      input.focus();
      return;
    }

    input.disabled = true;
    button.disabled = true;
    button.textContent = 'Logging in…';
    status.textContent = 'Connecting to server…';
    status.className = 'status';
    try {
      const response = await api.login(username);
      if (!response?.sessionToken || !response?.user?.username) {
        throw new GuessMarketApiError('Login response is missing session data', {
          code: 'MALFORMED_RESPONSE',
        });
      }
      sessionService.save(response.user.username, response.sessionToken);
      renderApplication(response.user.username);
    } catch (error) {
      status.textContent = error instanceof GuessMarketApiError
        ? error.message : 'Login failed unexpectedly.';
      status.className = 'status error';
      input.disabled = false;
      button.disabled = false;
      button.textContent = 'Log in';
      input.focus();
    }
  });
}

function renderApplication(username) {
  root.innerHTML = `
    <header class="app-header">
      <a class="brand" href="#" aria-label="Guess Market home">Guess Market</a>
      <nav aria-label="Main navigation">
        <button type="button" aria-current="page">Events</button>
        <button type="button" disabled>Users</button>
      </nav>
      <div class="account">
        <span>Signed in as <strong>${escapeHtml(username)}</strong></span>
        <button id="clear-session" class="quiet-button" type="button">Clear session</button>
      </div>
    </header>
    <main class="content">
      <section class="placeholder" aria-labelledby="welcome-heading">
        <p class="eyebrow">EX04 foundation</p>
        <h1 id="welcome-heading">Connected application shell</h1>
        <p>The Events and Users screens will be added in the next implementation stage.</p>
        <p id="status" class="status" role="status" aria-live="polite">Session restored for this browser tab.</p>
      </section>
    </main>`;
  document.querySelector('#clear-session').addEventListener('click', () => {
    sessionService.clear();
    renderLogin();
  });
}

const session = sessionService.get();
if (session) {
  renderApplication(session.username);
  api.getCurrentUser()
    .then((user) => renderApplication(user.username))
    .catch((error) => {
      if (!(error instanceof GuessMarketApiError && error.invalidSession)) {
        document.querySelector('#status').textContent = error.message;
        document.querySelector('#status').className = 'status error';
      }
    });
} else {
  renderLogin();
}
