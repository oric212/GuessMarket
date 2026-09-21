export class SynchronizationService {
  constructor({ operation, onData, onError, onRecovered = () => {}, intervalMs = 850 }) {
    if (typeof operation !== 'function' || typeof onData !== 'function' || typeof onError !== 'function') throw new TypeError('Synchronization callbacks are required');
    if (!Number.isFinite(intervalMs) || intervalMs <= 0) throw new TypeError('Polling interval must be positive');
    Object.assign(this, { operation, onData, onError, onRecovered, intervalMs });
    this.running = false; this.inFlight = false; this.failed = false; this.timer = null; this.generation = 0;
  }
  start() { if (!this.running) { this.running = true; this.generation += 1; this.#cycle(this.generation); } }
  stop() { this.running = false; this.generation += 1; if (this.timer !== null) clearTimeout(this.timer); this.timer = null; }
  refreshNow() {
    if (!this.running) return this.start();
    if (this.timer !== null) clearTimeout(this.timer);
    this.timer = null; this.#cycle(this.generation);
  }
  async #cycle(generation) {
    if (!this.running || generation !== this.generation || this.inFlight) return;
    this.inFlight = true;
    try {
      const data = await this.operation();
      if (!this.running || generation !== this.generation) return;
      this.onData(data);
      if (this.failed) this.onRecovered();
      this.failed = false;
    } catch (error) {
      if (this.running && generation === this.generation) { this.failed = true; this.onError(error); }
    } finally {
      this.inFlight = false;
      if (this.running && generation === this.generation) this.timer = setTimeout(() => this.#cycle(generation), this.intervalMs);
    }
  }
}
