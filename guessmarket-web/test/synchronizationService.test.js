import test from 'node:test';
import assert from 'node:assert/strict';
import { SynchronizationService } from '../src/services/synchronizationService.js';
const wait = (milliseconds) => new Promise((resolve) => setTimeout(resolve, milliseconds));
test('polling is single-flight and start is idempotent', async () => {
  let calls = 0; let concurrent = 0; let maximumConcurrent = 0;
  const service = new SynchronizationService({ intervalMs: 5, operation: async () => {
    calls += 1; concurrent += 1; maximumConcurrent = Math.max(maximumConcurrent, concurrent); await wait(12); concurrent -= 1; return calls;
  }, onData: () => {}, onError: assert.fail });
  service.start(); service.start(); await wait(45); service.stop();
  assert.ok(calls >= 2); assert.equal(maximumConcurrent, 1);
});
test('temporary failures are reported and followed by recovery', async () => {
  let attempts = 0; let failures = 0; let recoveries = 0;
  const service = new SynchronizationService({ intervalMs: 5, operation: async () => {
    attempts += 1; if (attempts === 1) throw new Error('offline'); return 'online';
  }, onData: () => {}, onError: () => { failures += 1; }, onRecovered: () => { recoveries += 1; } });
  service.start(); await wait(25); service.stop();
  assert.equal(failures, 1); assert.equal(recoveries, 1);
});
