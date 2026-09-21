export const SESSION_HEADER = 'X-GuessMarket-Session';

export class GuessMarketApiError extends Error {
  constructor(message, { status = 0, code = 'CLIENT_ERROR', cause } = {}) {
    super(message, { cause });
    this.name = 'GuessMarketApiError';
    this.status = status;
    this.code = code;
  }

  get invalidSession() {
    return this.status === 401 || this.code === 'INVALID_SESSION';
  }
}

export class GuessMarketApi {
  constructor({ baseUrl = '/api/', getSession = () => null, onInvalidSession = () => {} } = {}) {
    this.baseUrl = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
    this.getSession = getSession;
    this.onInvalidSession = onInvalidSession;
  }

  health() {
    return this.#request('health');
  }

  login(username) {
    return this.#request('login', { method: 'POST', body: { username } });
  }

  getEventSummaries() {
    return this.#request('events');
  }

  getEventDetails(eventId) {
    return this.#request(`events/${this.#positiveInteger(eventId, 'eventId')}`);
  }

  getPublicUsers() {
    return this.#request('users');
  }

  getCurrentUser() {
    return this.#request('user/me', { authenticated: true });
  }

  topUpAccount(amount) {
    return this.#request('user/account/topup', {
      method: 'POST', authenticated: true, body: { amount },
    });
  }

  startEvent(eventId) {
    return this.#eventAction(eventId, 'start');
  }

  purchaseShares(eventId, optionIndex, quantity) {
    return this.#eventAction(eventId, 'purchases', { optionIndex, quantity });
  }

  submitOrder(eventId, optionIndex, side, quantity, price) {
    return this.#eventAction(eventId, 'orders', { side, optionIndex, quantity, price });
  }

  closeEvent(eventId, winningOptionIndex) {
    return this.#eventAction(eventId, 'close', { winningOptionIndex });
  }

  #eventAction(eventId, action, body = {}) {
    return this.#request(`events/${this.#positiveInteger(eventId, 'eventId')}/${action}`, {
      method: 'POST', authenticated: true, body,
    });
  }

  #positiveInteger(value, name) {
    if (!Number.isInteger(value) || value <= 0) {
      throw new TypeError(`${name} must be a positive integer`);
    }
    return value;
  }

  async #request(path, { method = 'GET', body, authenticated = false } = {}) {
    const headers = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (authenticated) {
      const token = this.getSession()?.sessionToken;
      if (!token) {
        throw new GuessMarketApiError('Please log in first', {
          status: 401, code: 'NO_SESSION',
        });
      }
      headers[SESSION_HEADER] = token;
    }

    let response;
    try {
      response = await fetch(new URL(path, this.#absoluteBaseUrl()), {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (cause) {
      throw new GuessMarketApiError('Could not connect to GuessMarket server', {
        code: 'SERVER_UNREACHABLE', cause,
      });
    }

    const payload = await this.#readJson(response);
    if (!response.ok) {
      const error = new GuessMarketApiError(
        typeof payload?.message === 'string' ? payload.message : 'Server request failed',
        {
          status: response.status,
          code: typeof payload?.code === 'string' ? payload.code : 'HTTP_ERROR',
        },
      );
      if (authenticated && error.invalidSession) this.onInvalidSession(error);
      throw error;
    }
    return payload;
  }

  #absoluteBaseUrl() {
    return new URL(this.baseUrl, globalThis.location?.origin ?? 'http://localhost');
  }

  async #readJson(response) {
    const text = await response.text();
    if (!text.trim()) {
      if (response.ok) {
        throw new GuessMarketApiError('Server returned an empty response', {
          status: response.status, code: 'MALFORMED_RESPONSE',
        });
      }
      return null;
    }
    try {
      return JSON.parse(text);
    } catch (cause) {
      throw new GuessMarketApiError('Server returned malformed JSON', {
        status: response.status, code: 'MALFORMED_RESPONSE', cause,
      });
    }
  }
}
