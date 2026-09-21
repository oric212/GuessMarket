import test from 'node:test';
import assert from 'node:assert/strict';
import { GuessMarketApi, GuessMarketApiError, SESSION_HEADER } from '../src/api/guessMarketApi.js';

test('login uses the existing EX03 request contract', async (context) => {
  context.after(() => { delete globalThis.fetch; });
  globalThis.fetch = async (url, options) => {
    assert.equal(url.href, 'http://test/api/login');
    assert.equal(options.method, 'POST');
    assert.deepEqual(JSON.parse(options.body), { username: 'Alice' });
    return new Response(JSON.stringify({ success: true, sessionToken: 'token', user: { username: 'Alice' } }), { status: 201 });
  };
  const result = await new GuessMarketApi({ baseUrl: 'http://test/api/' }).login('Alice');
  assert.equal(result.sessionToken, 'token');
});

test('authenticated operations send the session header and exact payload', async (context) => {
  context.after(() => { delete globalThis.fetch; });
  globalThis.fetch = async (url, options) => {
    assert.equal(url.href, 'http://test/api/events/7/orders');
    assert.equal(options.headers[SESSION_HEADER], 'session-7');
    assert.deepEqual(JSON.parse(options.body), {
      side: 'BUY', optionIndex: 2, quantity: 4, price: 3.5,
    });
    return new Response('{}', { status: 200 });
  };
  const api = new GuessMarketApi({
    baseUrl: 'http://test/api/', getSession: () => ({ sessionToken: 'session-7' }),
  });
  await api.submitOrder(7, 2, 'BUY', 4, 3.5);
});

test('server errors are exposed and invalid sessions trigger cleanup hook', async (context) => {
  context.after(() => { delete globalThis.fetch; });
  let invalidated = false;
  globalThis.fetch = async () => new Response(JSON.stringify({
    success: false, code: 'INVALID_SESSION', message: 'The session token is missing or invalid',
  }), { status: 401 });
  const api = new GuessMarketApi({
    baseUrl: 'http://test/api/',
    getSession: () => ({ sessionToken: 'expired' }),
    onInvalidSession: () => { invalidated = true; },
  });
  await assert.rejects(api.getCurrentUser(), (error) => {
    assert.ok(error instanceof GuessMarketApiError);
    assert.equal(error.status, 401);
    assert.equal(error.code, 'INVALID_SESSION');
    return true;
  });
  assert.equal(invalidated, true);
});
