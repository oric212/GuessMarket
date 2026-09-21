import './styles/application.css';
import { GuessMarketApi, GuessMarketApiError } from './api/guessMarketApi.js';
import { sessionService } from './services/sessionService.js';
import { EventsView } from './views/eventsView.js';
import { UserView } from './views/userView.js';

const root = document.querySelector('#app');
let activeView = null;
const stopActiveView = () => { activeView?.unmount?.(); activeView = null; };
function returnToLogin(message) { stopActiveView(); sessionService.clear(); renderLogin(message); }
const api = new GuessMarketApi({ getSession: () => sessionService.get(), onInvalidSession: () => returnToLogin('Your session is no longer valid. Please log in again.') });
const escapeHtml = (value) => String(value).replace(/[&<>'"]/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character]);

function renderLogin(message = '') {
  stopActiveView();
  root.innerHTML = `<main class="login-layout"><section class="login-card" aria-labelledby="app-title"><p class="eyebrow">Prediction market</p><h1 id="app-title">Guess Market</h1><p class="intro">Sign in with a username to connect to the market server.</p><form id="login-form" novalidate><label for="username">Username</label><input id="username" name="username" type="text" autocomplete="username" maxlength="80" autofocus required><button type="submit">Log in</button></form><p id="status" class="status ${message ? 'error' : ''}" role="status" aria-live="polite">${escapeHtml(message)}</p></section></main>`;
  const form = root.querySelector('#login-form'); const input = root.querySelector('#username'); const button = form.querySelector('button'); const status = root.querySelector('#status');
  form.addEventListener('submit', async (event) => {
    event.preventDefault(); const username = input.value.trim();
    if (!username) { status.textContent = 'Username cannot be blank.'; status.className = 'status error'; input.focus(); return; }
    input.disabled = true; button.disabled = true; button.textContent = 'Logging in…'; status.textContent = 'Connecting to server…'; status.className = 'status';
    try {
      const response = await api.login(username);
      if (!response?.sessionToken || !response?.user?.username) throw new GuessMarketApiError('Login response is missing session data', { code: 'MALFORMED_RESPONSE' });
      sessionService.save(response.user.username, response.sessionToken); renderApplication(response.user.username);
    } catch (error) {
      status.textContent = error instanceof GuessMarketApiError ? error.message : 'Login failed unexpectedly.'; status.className = 'status error'; input.disabled = false; button.disabled = false; button.textContent = 'Log in'; input.focus();
    }
  });
}

function renderApplication(username) {
  stopActiveView();
  root.innerHTML = `<header class="app-header"><button class="brand" type="button" data-route="events">Guess Market</button><nav aria-label="Main navigation"><button type="button" data-route="events">Events</button><button type="button" data-route="user">User</button></nav><div class="account"><span>Signed in as <strong>${escapeHtml(username)}</strong></span><button id="clear-session" class="quiet-button" type="button">Clear session</button></div></header><main id="view-root" class="content"></main>`;
  root.querySelector('#clear-session').addEventListener('click', () => returnToLogin('Session cleared.'));
  root.querySelectorAll('[data-route]').forEach((button) => button.addEventListener('click', () => navigate(button.dataset.route)));
  navigate('events');
}

function navigate(route) {
  stopActiveView();
  root.querySelectorAll('nav [data-route]').forEach((button) => { if (button.dataset.route === route) button.setAttribute('aria-current', 'page'); else button.removeAttribute('aria-current'); });
  const container = root.querySelector('#view-root');
  const View = route === 'user' ? UserView : EventsView;
  activeView = new View({ container, api, onInvalidSession: () => returnToLogin('Your session is no longer valid. Please log in again.') }); activeView.mount();
}

const session = sessionService.get();
if (!session) renderLogin();
else api.getCurrentUser().then((user) => renderApplication(user.username)).catch((error) => { if (!(error instanceof GuessMarketApiError && error.invalidSession)) renderLogin(error.message); });
