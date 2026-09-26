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
test('an immediate refresh requested in flight runs directly after that cycle', async () => {
  let calls = 0;
  let releaseFirst;
  const first = new Promise((resolve) => { releaseFirst = resolve; });
  const service = new SynchronizationService({
    intervalMs: 10_000,
    operation: async () => { calls += 1; if (calls === 1) await first; return calls; },
    onData: () => {},
    onError: assert.fail,
  });
  service.start();
  service.refreshNow();
  releaseFirst();
  await wait(15);
  service.stop();
  assert.equal(calls, 2);
});
test('one outage reports one error even across repeated failed cycles', async () => {
  let failures = 0;
  const service = new SynchronizationService({
    intervalMs: 2,
    operation: async () => { throw new Error('offline'); },
    onData: assert.fail,
    onError: () => { failures += 1; },
  });
  service.start();
  await wait(15);
  service.stop();
  assert.equal(failures, 1);
});
