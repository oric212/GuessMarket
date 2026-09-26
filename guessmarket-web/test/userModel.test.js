import test from 'node:test';
import assert from 'node:assert/strict';
import { actionAvailability, retainValue, shouldApplySnapshot, validateOrderPrice, validatePositiveAmount, validatePositiveInteger } from '../src/views/userModel.js';

const user = { username: 'Alice', blocked: false };
test('action availability follows state, method, MM identity, and blocked status', () => {
  assert.deepEqual(actionAvailability(user, { eventState: 'NOT_STARTED', tradingMethod: 'LMSR', marketMakerUsername: 'alice' }), { start: true, close: false, lmsrPurchase: false, orderSubmission: false });
  assert.deepEqual(actionAvailability(user, { eventState: 'ACTIVE', tradingMethod: 'LMSR', marketMakerUsername: 'Bob' }), { start: false, close: false, lmsrPurchase: true, orderSubmission: false });
  assert.deepEqual(actionAvailability(user, { eventState: 'ACTIVE', tradingMethod: 'ORDER_BOOK', marketMakerUsername: 'Alice' }), { start: false, close: true, lmsrPurchase: false, orderSubmission: true });
  assert.deepEqual(actionAvailability({ ...user, blocked: true }, { eventState: 'ACTIVE', tradingMethod: 'LMSR', marketMakerUsername: 'Alice' }), { start: false, close: false, lmsrPurchase: false, orderSubmission: false });
});
test('top-up, quantity, and price validation catches only malformed values', () => {
  assert.equal(validatePositiveAmount('25.5'), null); assert.match(validatePositiveAmount('0'), /positive/); assert.match(validatePositiveAmount('nope'), /positive/);
  assert.equal(validatePositiveInteger('3'), null); assert.match(validatePositiveInteger('2.5'), /whole/); assert.match(validatePositiveInteger('-1'), /whole/);
  assert.equal(validateOrderPrice('3.25'), null); assert.match(validateOrderPrice('3.257'), /2 decimal/); assert.match(validateOrderPrice('0'), /positive/);
});
test('select values survive synchronization while still available', () => {
  assert.equal(retainValue(['Yes', 'No'], 'No'), 'No'); assert.equal(retainValue(['Yes', 'No'], 'Gone'), 'Yes');
});
test('snapshots older than a completed mutation are rejected', () => {
  assert.equal(shouldApplySnapshot(4, 4), true);
  assert.equal(shouldApplySnapshot(3, 4), false);
});
