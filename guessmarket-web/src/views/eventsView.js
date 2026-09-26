import { GuessMarketApiError } from '../api/guessMarketApi.js';
import { SynchronizationService } from '../services/synchronizationService.js';
import { ALL, displayEnum, filterEvents, formatNumber, retainedSelection } from './eventModel.js';

const escapeHtml = (value) => String(value ?? '').replace(/[&<>'"]/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character]);
const cell = (value, className = '') => `<td class="${className}">${escapeHtml(value)}</td>`;
const info = (label, value) => `<div class="info-row"><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`;
function table(headers, rows, emptyMessage) {
  if (!rows.length) return `<p class="empty-inline">${escapeHtml(emptyMessage)}</p>`;
  return `<div class="table-scroll"><table class="detail-table"><thead><tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join('')}</tr></thead><tbody>${rows.join('')}</tbody></table></div>`;
}
function renderLmsr(details) {
  const options = table(['Option', 'Current value', 'Total purchased'], details.options.map((option) => `<tr>${cell(option.optionName)}${cell(formatNumber(option.currentOptionValue), 'numeric')}${cell(option.quantityBought, 'numeric')}</tr>`), 'No LMSR option state is available.');
  const trades = table(['Option', 'Quantity', 'Purchase cost'], details.trades.map((trade) => `<tr>${cell(trade.boughtOptionName)}${cell(trade.quantity, 'numeric')}${cell(formatNumber(trade.purchaseCost), 'numeric')}</tr>`), 'No LMSR trades have been made.');
  return `<section class="detail-section"><h3>LMSR market details</h3><p><strong>Liquidity parameter (b):</strong> ${escapeHtml(details.liquidityParameter)}</p><h4>Options</h4>${options}<h4>Global trade history — newest first</h4>${trades}</section>`;
}
function renderOrderBook(details) {
  const books = details.optionBooks.map((option) => {
    const orders = (items, message) => table(['Username', 'Remaining quantity', 'Price/share'], items.map((order) => `<tr>${cell(order.username)}${cell(order.remainingQuantity, 'numeric')}${cell(formatNumber(order.pricePerShare), 'numeric')}</tr>`), message);
    return `<section class="option-book"><h4>${escapeHtml(option.optionName)}</h4><dl class="stats-grid">${info('LAST', formatNumber(option.last))}${info('BID', formatNumber(option.bid))}${info('ASK', formatNumber(option.ask))}${info('MID', formatNumber(option.mid))}${info('SPREAD', formatNumber(option.spread))}</dl><h5 class="buy">Pending BUY orders</h5>${orders(option.pendingBuyOrders, 'No pending BUY orders.')}<h5 class="sell">Pending SELL orders</h5>${orders(option.pendingSellOrders, 'No pending SELL orders.')}</section>`;
  }).join('');
  return `<section class="detail-section"><h3>Order Book details</h3><dl class="stats-grid">${info('Denominator (d)', details.d)}${info('Initial investment', details.initial)}${info('Allow mint', details.allowMint ? 'Yes' : 'No')}</dl><div class="option-books">${books || '<p class="empty-inline">No option books are available.</p>'}</div></section>`;
}
function renderParticipants(state) {
  const headers = state.options.flatMap((option) => [`${option} held`, `${option} value`]);
  const rows = state.participants.map((participant) => {
    const options = state.options.map((option) => `${cell(participant.holdingsByOption?.[option] ?? 0, 'numeric')}${cell(formatNumber(participant.currentHoldingValueByOption?.[option]), 'numeric')}`).join('');
    const availability = state.options.map((option) => `${option}: ${participant.reservedSellByOption?.[option] ?? 0} / ${participant.availableToSellByOption?.[option] ?? 0}`).join(' | ');
    const cash = `Paid ${formatNumber(participant.totalCashPaid)} | Received ${formatNumber(participant.totalCashReceived)} | Commission ${formatNumber(participant.totalCommissionPaid)}`;
    return `<tr>${cell(participant.username)}${options}${cell(availability)}${cell(cash)}</tr>`;
  });
  return `<section class="detail-section"><h3>Event participants</h3>${table(['Username', ...headers, 'Reserved / available', 'Cash summary'], rows, 'No participants yet.')}</section>`;
}
function renderDetails(state) {
  const common = `<section class="detail-section"><h3>Common event details</h3><dl class="info-grid">${info('Event ID', state.id)}${info('Name', state.eventName)}${info('Description', state.description)}${info('State', displayEnum(state.eventState))}${info('Trading method', displayEnum(state.tradingMethod))}${info('Market Maker', state.marketMakerUsername)}${info('Commission', `${displayEnum(state.commissionMethod)} — ${formatNumber(state.commissionPercentage)}%`)}${info('Event account balance', formatNumber(state.currentEventAccountBalance))}${info('Commission collected', formatNumber(state.totalCommissionCollected))}${info('Options', state.options.join(' / '))}${info('Winning option', state.winningOption ?? 'Not closed yet')}</dl></section>`;
  let method = '<section class="detail-section"><p class="empty-inline">Method-specific details are unavailable.</p></section>';
  if (state.tradingMethod === 'LMSR' && state.lmsrDetails) method = renderLmsr(state.lmsrDetails);
  if (state.tradingMethod === 'ORDER_BOOK' && state.orderBookDetails) method = renderOrderBook(state.orderBookDetails);
  return common + method + renderParticipants(state);
}

export class EventsView {
  constructor({ container, api }) {
    this.container = container; this.api = api;
    this.events = []; this.selectedId = null; this.details = null;
    this.filters = { method: ALL, state: ALL, commission: ALL };
    this.sync = new SynchronizationService({ intervalMs: 850, operation: () => this.#fetchSnapshot(), onData: (snapshot) => this.#applySnapshot(snapshot), onError: (error) => this.#showError(error), onRecovered: () => this.#showStatus('Connection restored. Events are up to date.', false) });
  }
  mount() {
    this.container.innerHTML = `<section class="events-view" aria-labelledby="events-heading"><div class="events-toolbar"><div><p class="eyebrow">Live markets</p><h1 id="events-heading">Events</h1></div><div id="events-status" class="sync-status" role="status" aria-live="polite">Loading events…</div></div><div class="events-grid"><section class="events-browser" aria-labelledby="event-list-heading"><div class="panel-heading"><div><h2 id="event-list-heading">Events overview</h2><p id="event-count"></p></div><button id="events-retry" class="quiet-button" type="button">Refresh now</button></div><div class="filters">${this.#select('method-filter', 'Trading method', [['ALL','All'],['LMSR','LMSR'],['ORDER_BOOK','Order Book']])}${this.#select('state-filter', 'Event state', [['ALL','All'],['NOT_STARTED','Not Started'],['ACTIVE','Active'],['CLOSED','Closed']])}${this.#select('commission-filter', 'Commission', [['ALL','All'],['ON_PURCHASE','On Purchase'],['ON_CLOSE','On Close']])}</div><div class="event-table-wrap"><table class="events-table"><thead><tr><th>ID</th><th>Name</th><th>State</th><th>Method</th><th>Commission</th><th>Account</th><th>Market Maker</th><th>Options</th></tr></thead><tbody id="events-body"></tbody></table><div id="events-empty" class="empty-state" hidden></div></div></section><aside class="event-details" aria-labelledby="details-heading"><h2 id="details-heading">Event monitoring</h2><div id="details-body" class="details-body"><div class="empty-state">Select an event to inspect its market details.</div></div></aside></div></section>`;
    this.status = this.container.querySelector('#events-status'); this.body = this.container.querySelector('#events-body'); this.empty = this.container.querySelector('#events-empty'); this.count = this.container.querySelector('#event-count'); this.detailsBody = this.container.querySelector('#details-body');
    this.container.querySelector('#events-retry').addEventListener('click', () => { this.#showStatus('Refreshing events…', false); this.sync.refreshNow(); });
    for (const [key, id] of [['method', '#method-filter'], ['state', '#state-filter'], ['commission', '#commission-filter']]) this.container.querySelector(id).addEventListener('change', (event) => { this.filters[key] = event.target.value; this.#renderList(); });
    this.body.addEventListener('click', (event) => { const row = event.target.closest('tr[data-event-id]'); if (row) this.#selectEvent(Number(row.dataset.eventId)); });
    this.sync.start();
  }
  unmount() { this.sync.stop(); }
  async #fetchSnapshot() {
    await this.api.getCurrentUser();
    const events = await this.api.getEventSummaries();
    const requestedId = retainedSelection(events, this.selectedId);
    const details = requestedId === null ? null : await this.api.getEventDetails(requestedId);
    return { events, requestedId, details };
  }
  #applySnapshot({ events, requestedId, details }) {
    this.events = events;
    this.selectedId = retainedSelection(events, this.selectedId);
    if (this.selectedId === requestedId) this.details = details;
    else if (this.details?.id !== this.selectedId) this.details = null;
    this.#renderList(); this.#renderDetails(); this.#showStatus('Live synchronization active', false);
  }
  #renderList() {
    const filtered = filterEvents(this.events, this.filters);
    this.count.textContent = `${filtered.length} of ${this.events.length} event${this.events.length === 1 ? '' : 's'}`;
    this.body.innerHTML = filtered.map((event) => `<tr data-event-id="${event.id}" tabindex="0" class="${event.id === this.selectedId ? 'selected' : ''}" aria-selected="${event.id === this.selectedId}">${cell(event.id, 'numeric')}${cell(event.eventName)}${cell(displayEnum(event.eventState))}${cell(displayEnum(event.tradingMethod))}${cell(`${displayEnum(event.commissionMethod)} ${formatNumber(event.commissionPercentage)}%`)}${cell(formatNumber(event.currentEventAccountBalance), 'numeric')}${cell(event.marketMakerUsername)}${cell(event.options.join(' / '))}</tr>`).join('');
    this.empty.hidden = filtered.length !== 0; this.empty.textContent = this.events.length === 0 ? 'No events are currently available on the server.' : 'No events match the selected filters.';
    this.body.querySelectorAll('tr').forEach((row) => row.addEventListener('keydown', (event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); this.#selectEvent(Number(row.dataset.eventId)); } }));
  }
  async #selectEvent(eventId) {
    if (eventId === this.selectedId && this.details) return;
    this.selectedId = eventId; this.details = null; this.#renderList(); this.detailsBody.innerHTML = '<div class="empty-state">Loading event details…</div>';
    try { const details = await this.api.getEventDetails(eventId); if (this.selectedId === eventId) { this.details = details; this.#renderDetails(); } }
    catch (error) { if (!(error instanceof GuessMarketApiError && error.invalidSession) && this.selectedId === eventId) this.detailsBody.innerHTML = `<div class="error-state">Event details could not be loaded: ${escapeHtml(error.message)}</div>`; }
  }
  #renderDetails() {
    if (this.selectedId === null) { this.detailsBody.innerHTML = `<div class="empty-state">${this.events.length ? 'Select an event to inspect its market details.' : 'Event details will appear here when events become available.'}</div>`; return; }
    if (!this.details || this.details.id !== this.selectedId) { this.detailsBody.innerHTML = '<div class="empty-state">Loading event details…</div>'; return; }
    const scrollTop = this.detailsBody.scrollTop; this.detailsBody.innerHTML = renderDetails(this.details); this.detailsBody.scrollTop = scrollTop;
  }
  #showError(error) {
    if (error instanceof GuessMarketApiError && error.invalidSession) { this.sync.stop(); return; }
    this.#showStatus(`${error.message || 'Server temporarily unavailable.'} Retrying automatically…`, true);
    if (!this.events.length) { this.empty.hidden = false; this.empty.textContent = 'Events could not be loaded. The application will retry automatically.'; }
  }
  #showStatus(message, error) { this.status.textContent = message; this.status.className = `sync-status${error ? ' error' : ''}`; }
  #select(id, label, options) { return `<label>${escapeHtml(label)}<select id="${id}">${options.map(([value, text]) => `<option value="${value}">${text}</option>`).join('')}</select></label>`; }
}
