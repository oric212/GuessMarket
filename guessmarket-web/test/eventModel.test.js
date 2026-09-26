import test from 'node:test';
import assert from 'node:assert/strict';
import { ALL, displayEnum, filterEvents, formatNumber, retainedSelection } from '../src/views/eventModel.js';
const events = [
  { id: 1, tradingMethod: 'LMSR', eventState: 'ACTIVE', commissionMethod: 'ON_PURCHASE' },
  { id: 2, tradingMethod: 'ORDER_BOOK', eventState: 'NOT_STARTED', commissionMethod: 'ON_CLOSE' },
  { id: 3, tradingMethod: 'ORDER_BOOK', eventState: 'CLOSED', commissionMethod: 'ON_PURCHASE' },
];
test('events are filtered by all three independent selectors', () => {
  assert.deepEqual(filterEvents(events, { method: 'ORDER_BOOK', state: ALL, commission: ALL }).map((event) => event.id), [2, 3]);
  assert.deepEqual(filterEvents(events, { method: ALL, state: 'ACTIVE', commission: 'ON_PURCHASE' }).map((event) => event.id), [1]);
  assert.deepEqual(filterEvents(events, { method: ALL, state: 'NOT_STARTED', commission: ALL }).map((event) => event.id), [2]);
  assert.deepEqual(filterEvents(events, { method: ALL, state: ALL, commission: 'ON_CLOSE' }).map((event) => event.id), [2]);
  assert.deepEqual(filterEvents(events, { method: ALL, state: 'CLOSED', commission: ALL }).map((event) => event.id), [3]);
  assert.deepEqual(filterEvents(events, { method: 'LMSR', state: 'CLOSED', commission: ALL }), []);
});
test('selection is retained only while the event remains in the snapshot', () => {
  assert.equal(retainedSelection(events, 2), 2); assert.equal(retainedSelection(events, 9), null); assert.equal(retainedSelection(events, null), null);
});
test('display mapping formats enums and decimals', () => {
  assert.equal(displayEnum('ORDER_BOOK'), 'Order Book'); assert.equal(displayEnum('NOT_STARTED'), 'Not Started');
  assert.equal(formatNumber(1234.567), '1,234.57'); assert.equal(formatNumber(null), 'N/A');
});
