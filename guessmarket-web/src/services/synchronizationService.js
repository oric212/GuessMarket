export class SynchronizationService {
  constructor({ operation, onData, onError, onRecovered = () => {}, intervalMs = 850 }) {
    if (typeof operation !== 'function' || typeof onData !== 'function' || typeof onError !== 'function') throw new TypeError('Synchronization callbacks are required');
    if (!Number.isFinite(intervalMs) || intervalMs <= 0) throw new TypeError('Polling interval must be positive');
    Object.assign(this, { operation, onData, onError, onRecovered, intervalMs });
    this.running = false; this.inFlight = false; this.failed = false; this.refreshRequested = false; this.timer = null; this.generation = 0;
  }
  start() { if (!this.running) { this.running = true; this.generation += 1; this.#cycle(this.generation); } }
  stop() { this.running = false; this.refreshRequested = false; this.generation += 1; if (this.timer !== null) clearTimeout(this.timer); this.timer = null; }
  refreshNow() {
    if (!this.running) return this.start();
    if (this.inFlight) { this.refreshRequested = true; return; }
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
      if (this.running && generation === this.generation && !this.failed) { this.failed = true; this.onError(error); }
    } finally {
      this.inFlight = false;
      if (this.running && generation === this.generation) {
        const delay = this.refreshRequested ? 0 : this.intervalMs;
        this.refreshRequested = false;
        this.timer = setTimeout(() => this.#cycle(generation), delay);
      }
    }
  }
}
