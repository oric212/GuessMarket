const STORAGE_KEY = 'guessmarket.session';

function validSession(value) {
  return value
    && typeof value.username === 'string'
    && value.username.trim() !== ''
    && typeof value.sessionToken === 'string'
    && value.sessionToken.trim() !== '';
}

export const sessionService = {
  get() {
    try {
      const value = JSON.parse(sessionStorage.getItem(STORAGE_KEY));
      if (validSession(value)) {
        return { username: value.username, sessionToken: value.sessionToken };
      }
    } catch {
      // Invalid browser state is equivalent to no session.
    }
    this.clear();
    return null;
  },

  save(username, sessionToken) {
    const session = { username: username.trim(), sessionToken };
    if (!validSession(session)) {
      throw new TypeError('A username and session token are required');
    }
    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    return session;
  },

  clear() {
    sessionStorage.removeItem(STORAGE_KEY);
  },
};
