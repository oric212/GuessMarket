import { GuessMarketApiError } from '../api/guessMarketApi.js';
import { SynchronizationService } from '../services/synchronizationService.js';
import { displayEnum, formatNumber } from './eventModel.js';
import { actionAvailability, retainValue, shouldApplySnapshot, validateOrderPrice, validatePositiveAmount, validatePositiveInteger } from './userModel.js';

const escapeHtml = (value) => String(value ?? '').replace(/[&<>'"]/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[character]);
const cell = (value, className = '') => `<td class="${className}">${escapeHtml(value)}</td>`;
const info = (label, value) => `<div class="info-row"><dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd></div>`;
function rowsOrEmpty(headers, rows, message) {
  return rows.length ? `<div class="table-scroll"><table class="detail-table"><thead><tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join('')}</tr></thead><tbody>${rows.join('')}</tbody></table></div>` : `<p class="empty-inline">${escapeHtml(message)}</p>`;
}
function optionsHtml(values, selected) {
  return values.map((value, index) => `<option value="${index + 1}" ${value === selected ? 'selected' : ''}>${escapeHtml(value)}</option>`).join('');
}

export class UserView {
  constructor({ container, api, onInvalidSession }) {
    this.container = container; this.api = api; this.onInvalidSession = onInvalidSession;
    this.publicUsers = []; this.user = null; this.events = []; this.selectedEventId = null; this.eventDetails = null; this.selectedParticipationId = null; this.revision = 0;
    this.sync = new SynchronizationService({ intervalMs: 850, operation: () => this.#fetchSnapshot(), onData: (snapshot) => this.#applySnapshot(snapshot), onError: (error) => this.#pollError(error), onRecovered: () => this.#status('Connection restored. Account is up to date.', false) });
  }
  mount() {
    this.container.innerHTML = `<section class="user-view" aria-labelledby="user-heading"><div class="events-toolbar"><div><p class="eyebrow">Account workspace</p><h1 id="user-heading">User</h1></div><div id="user-status" class="sync-status" role="status" aria-live="polite">Loading account…</div></div><div class="user-grid"><section class="user-sidebar panel"><h2>Public users</h2><p class="secondary-text">Only public server fields are shown for other users.</p><div class="table-scroll"><table class="detail-table"><thead><tr><th>Username</th><th>Balance</th><th>Market Maker</th></tr></thead><tbody id="public-users-body"></tbody></table></div></section><div class="user-workspace"><section class="panel"><h2>My Account</h2><dl id="account-summary" class="info-grid"></dl><form id="topup-form" class="inline-form" novalidate><label>Top-up amount<input id="topup-amount" type="number" min="0" step="0.01" placeholder="Positive amount"></label><button type="submit">Top up</button><span id="topup-result" class="action-message" role="status"></span></form></section><section class="panel"><h2>My Transactions</h2><div id="transactions"></div></section><section class="panel"><h2>My Market Maker Assignments</h2><div id="mm-assignments"></div></section><section class="panel"><h2>My Participations / Holdings</h2><label class="select-control">Participation<select id="participation-select"></select></label><div id="participation-details"></div></section><section class="panel"><h2>Actions / Trading</h2><label class="select-control">Event<select id="action-event-select"></select></label><div id="action-context" class="action-context"></div><div id="no-actions" class="empty-inline">Select an event to see available actions.</div><form id="start-form" class="action-form" hidden><h3>Market Maker start</h3><button type="submit">Start event</button></form><form id="close-form" class="action-form" hidden><h3>Market Maker close</h3><label>Winning option<select id="close-winner"></select></label><button type="submit">Close event</button></form><form id="lmsr-form" class="action-form" hidden><h3>LMSR purchase</h3><label>Option<select id="lmsr-option"></select></label><label>Quantity<input id="lmsr-quantity" inputmode="numeric" placeholder="Positive whole number"></label><button type="submit">Purchase shares</button></form><form id="order-form" class="action-form" hidden><h3>Order Book submission</h3><label>Side<select id="order-side"><option>BUY</option><option>SELL</option></select></label><label>Option<select id="order-option"></select></label><label>Quantity<input id="order-quantity" inputmode="numeric" placeholder="Positive whole number"></label><label>Price/share<input id="order-price" inputmode="decimal" placeholder="Max 2 decimals"></label><button type="submit">Submit order</button></form><p id="action-result" class="action-message" role="status" aria-live="polite"></p></section></div></div></section>`;
    const query = (selector) => this.container.querySelector(selector);
    this.dom = { status: query('#user-status'), publicUsers: query('#public-users-body'), account: query('#account-summary'), transactions: query('#transactions'), assignments: query('#mm-assignments'), participationSelect: query('#participation-select'), participationDetails: query('#participation-details'), actionEventSelect: query('#action-event-select'), actionContext: query('#action-context'), noActions: query('#no-actions'), startForm: query('#start-form'), closeForm: query('#close-form'), lmsrForm: query('#lmsr-form'), orderForm: query('#order-form'), closeWinner: query('#close-winner'), lmsrOption: query('#lmsr-option'), orderOption: query('#order-option'), actionResult: query('#action-result'), topupResult: query('#topup-result') };
    query('#topup-form').addEventListener('submit', (event) => this.#topUp(event));
    this.dom.participationSelect.addEventListener('change', () => { this.selectedParticipationId = Number(this.dom.participationSelect.value) || null; this.#renderParticipation(); });
    this.dom.actionEventSelect.addEventListener('change', () => this.#selectActionEvent(Number(this.dom.actionEventSelect.value) || null));
    this.dom.startForm.addEventListener('submit', (event) => this.#start(event)); this.dom.closeForm.addEventListener('submit', (event) => this.#close(event)); this.dom.lmsrForm.addEventListener('submit', (event) => this.#purchase(event)); this.dom.orderForm.addEventListener('submit', (event) => this.#order(event));
    this.sync.start();
  }
  unmount() { this.sync.stop(); }
  async #fetchSnapshot() {
    const revision = this.revision;
    const [publicUsers, user, events] = await Promise.all([this.api.getPublicUsers(), this.api.getCurrentUser(), this.api.getEventSummaries()]);
    const requestedId = events.some((event) => event.id === this.selectedEventId) ? this.selectedEventId : null;
    const details = requestedId === null ? null : await this.api.getEventDetails(requestedId);
    return { revision, publicUsers, user, events, requestedId, details };
  }
  #applySnapshot(snapshot) {
    if (!shouldApplySnapshot(snapshot.revision, this.revision)) return;
    Object.assign(this, { publicUsers: snapshot.publicUsers, user: snapshot.user, events: snapshot.events });
    this.selectedEventId = this.events.some((event) => event.id === this.selectedEventId) ? this.selectedEventId : null;
    if (this.selectedEventId === snapshot.requestedId) this.eventDetails = snapshot.details;
    this.selectedParticipationId = this.user.participations.some((item) => item.eventId === this.selectedParticipationId) ? this.selectedParticipationId : (this.user.participations[0]?.eventId ?? null);
    this.#renderReadOnly(); this.#renderActionSelector(); this.#renderActions(); this.#status('Live synchronization active', false);
  }
  #renderReadOnly() {
    this.dom.publicUsers.innerHTML = this.publicUsers.map((user) => `<tr>${cell(user.username)}${cell(formatNumber(user.accountBalance), 'numeric')}${cell(user.marketMaker ? 'Yes' : 'No')}</tr>`).join('') || `<tr><td colspan="3">No public users available.</td></tr>`;
    this.dom.account.innerHTML = info('Username', this.user.username) + info('Account balance', formatNumber(this.user.accountBalance)) + info('Status', this.user.blocked ? 'Blocked' : 'Active') + info('Market Maker', this.user.marketMaker ? 'Yes' : 'No');
    const transactions = this.user.accountTransactions.map((tx) => `<tr>${cell(tx.sequence, 'numeric')}${cell(displayEnum(tx.type))}${cell(formatNumber(tx.amountChange), 'numeric')}${cell(formatNumber(tx.resultingBalance), 'numeric')}${cell(tx.eventName ?? '—')}${cell(tx.description)}</tr>`);
    this.dom.transactions.innerHTML = rowsOrEmpty(['#', 'Type', 'Change', 'Balance', 'Event', 'Description'], transactions, 'No account transactions available.');
    const byId = new Map(this.events.map((event) => [event.id, event]));
    const assignments = this.user.marketMakerEventIds.map((id) => byId.get(id)).filter(Boolean).map((event) => `<tr>${cell(event.id, 'numeric')}${cell(event.eventName)}${cell(displayEnum(event.tradingMethod))}${cell(displayEnum(event.eventState))}</tr>`);
    this.dom.assignments.innerHTML = rowsOrEmpty(['ID', 'Event', 'Method', 'State'], assignments, 'You are not Market Maker for any events.');
    this.#updateSelect(this.dom.participationSelect, this.user.participations.map((item) => [item.eventId, `${item.eventName} — ${displayEnum(item.eventState)}`]), this.selectedParticipationId, 'No participations');
    this.#renderParticipation();
  }
  #renderParticipation() {
    const item = this.user?.participations.find((participation) => participation.eventId === this.selectedParticipationId);
    if (!item) { this.dom.participationDetails.innerHTML = '<p class="empty-inline">No participations yet.</p>'; return; }
    const optionRows = Object.keys(item.holdingsByOption).map((option) => `<tr>${cell(option)}${cell(item.holdingsByOption[option], 'numeric')}${cell(item.reservedSellByOption[option], 'numeric')}${cell(item.availableToSellByOption[option], 'numeric')}${cell(formatNumber(item.cumulativePurchaseAmountByOption[option]), 'numeric')}${cell(formatNumber(item.currentHoldingValueByOption[option]), 'numeric')}</tr>`);
    const trades = item.trades.map((trade) => `<tr>${cell(trade.boughtOptionName)}${cell(trade.quantity, 'numeric')}${cell(formatNumber(trade.purchaseCost), 'numeric')}</tr>`);
    this.dom.participationDetails.innerHTML = `<dl class="stats-grid participation-summary">${info('Event', item.eventName)}${info('Method', displayEnum(item.tradingMethod))}${info('State', displayEnum(item.eventState))}${info('Winner', item.winningOption ?? 'Not closed yet')}${info('Commission paid', formatNumber(item.totalCommissionPaid))}${info('Cash paid', formatNumber(item.totalCashPaid))}${info('Cash received', formatNumber(item.totalCashReceived))}${info('Profit / loss', formatNumber(item.profitLoss))}</dl>${rowsOrEmpty(['Option', 'Held', 'Reserved SELL', 'Available to sell', 'Gross paid', 'Current value'], optionRows, 'No option holdings.')}${item.tradingMethod === 'LMSR' ? `<h4>Personal trade history</h4>${rowsOrEmpty(['Option', 'Quantity', 'Purchase cost'], trades, 'No personal trades yet.')}` : ''}`;
  }
  #renderActionSelector() {
    this.#updateSelect(this.dom.actionEventSelect, this.events.map((event) => [event.id, `${event.eventName} — ${displayEnum(event.tradingMethod)} / ${displayEnum(event.eventState)}`]), this.selectedEventId, 'Select an event');
  }
  #renderActions() {
    const event = this.events.find((candidate) => candidate.id === this.selectedEventId);
    if (!event || !this.eventDetails) { this.dom.actionContext.innerHTML = ''; this.#toggleActions({}); return; }
    const state = this.eventDetails;
    let market = '';
    if (state.lmsrDetails) market = `Current prices: ${state.lmsrDetails.options.map((option) => `${option.optionName} ${formatNumber(option.currentOptionValue)}`).join(' | ')}`;
    if (state.orderBookDetails) market = state.orderBookDetails.optionBooks.map((option) => `${option.optionName}: LAST ${formatNumber(option.last)}, BID ${formatNumber(option.bid)}, ASK ${formatNumber(option.ask)}`).join(' | ');
    this.dom.actionContext.innerHTML = `<dl class="stats-grid">${info('Event', state.eventName)}${info('Method / state', `${displayEnum(state.tradingMethod)} / ${displayEnum(state.eventState)}`)}${info('Market Maker', state.marketMakerUsername)}${info('Commission', `${displayEnum(state.commissionMethod)} ${formatNumber(state.commissionPercentage)}%`)}</dl><p>${escapeHtml(market)}</p>`;
    const options = state.options;
    this.#updateOptionSelect(this.dom.closeWinner, options); this.#updateOptionSelect(this.dom.lmsrOption, options); this.#updateOptionSelect(this.dom.orderOption, options);
    this.#toggleActions(actionAvailability(this.user, event));
  }
  #toggleActions(availability) {
    for (const [name, form] of [['start', this.dom.startForm], ['close', this.dom.closeForm], ['lmsrPurchase', this.dom.lmsrForm], ['orderSubmission', this.dom.orderForm]]) form.hidden = !availability[name];
    const any = Object.values(availability).some(Boolean); this.dom.noActions.hidden = any;
    this.dom.noActions.textContent = this.user?.blocked ? 'Actions are disabled because your account is blocked.' : 'No action is available for this event in its current state.';
  }
  async #selectActionEvent(eventId) {
    this.selectedEventId = eventId; this.eventDetails = null; this.dom.actionResult.textContent = ''; this.#renderActions();
    if (eventId === null) return;
    try { const details = await this.api.getEventDetails(eventId); if (this.selectedEventId === eventId) { this.eventDetails = details; this.#renderActions(); } }
    catch (error) { if (!(error instanceof GuessMarketApiError && error.invalidSession)) this.#actionMessage(error.message, true); }
  }
  #topUp(event) {
    event.preventDefault(); const input = this.container.querySelector('#topup-amount'); const message = validatePositiveAmount(input.value, 'Top-up amount');
    if (message) { this.#topupMessage(message, true); return; }
    const button = event.currentTarget.querySelector('button');
    this.#mutate(button, () => this.api.topUpAccount(Number(input.value)), (result) => `Balance topped up successfully. New balance: ${formatNumber(result.accountBalance)}.`, () => { input.value = ''; }, this.#topupMessage.bind(this));
  }
  #start(event) { event.preventDefault(); this.#mutate(event.currentTarget.querySelector('button'), () => this.api.startEvent(this.selectedEventId), () => 'Event started successfully.'); }
  #close(event) { event.preventDefault(); const winner = Number(this.dom.closeWinner.value); if (!winner) return this.#actionMessage('Select a winning option.', true); this.#mutate(event.currentTarget.querySelector('button'), () => this.api.closeEvent(this.selectedEventId, winner), () => 'Event closed successfully.'); }
  #purchase(event) {
    event.preventDefault(); const quantity = this.container.querySelector('#lmsr-quantity'); const validation = validatePositiveInteger(quantity.value);
    if (validation) return this.#actionMessage(validation, true);
    const option = Number(this.dom.lmsrOption.value); this.#mutate(event.currentTarget.querySelector('button'), () => this.api.purchaseShares(this.selectedEventId, option, Number(quantity.value)), (result) => `Purchase completed — cost ${formatNumber(result.purchaseCost)}, commission ${formatNumber(result.commission)}, total paid ${formatNumber(result.totalPricePaid)}.`, () => { quantity.value = ''; });
  }
  #order(event) {
    event.preventDefault(); const quantity = this.container.querySelector('#order-quantity'); const price = this.container.querySelector('#order-price'); const quantityError = validatePositiveInteger(quantity.value); const priceError = validateOrderPrice(price.value);
    if (quantityError || priceError) return this.#actionMessage(quantityError || priceError, true);
    const side = this.container.querySelector('#order-side').value; const option = Number(this.dom.orderOption.value);
    this.#mutate(event.currentTarget.querySelector('button'), () => this.api.submitOrder(this.selectedEventId, option, side, Number(quantity.value), Number(price.value)), (result) => `Submitted ${result.side} ${result.optionName}: quantity ${result.originalQuantity}, resting ${result.remainingQuantity}, limit ${formatNumber(result.limitPrice)}; executions ${result.executions.length}, mints ${result.mintExecutions.length}.`, () => { quantity.value = ''; price.value = ''; });
  }
  async #mutate(button, operation, successText, clear = () => {}, reporter = this.#actionMessage.bind(this)) {
    if (button.disabled) return; button.disabled = true; reporter('Submitting…', false);
    try { const result = await operation(); this.revision += 1; clear(); reporter(successText(result), false); this.sync.refreshNow(); }
    catch (error) { reporter(error.message || 'The action could not be completed.', true); }
    finally { button.disabled = false; }
  }
  #updateSelect(select, items, selected, placeholder) {
    const current = String(selected ?? select.value ?? ''); select.innerHTML = `<option value="">${escapeHtml(placeholder)}</option>` + items.map(([value, label]) => `<option value="${value}">${escapeHtml(label)}</option>`).join('');
    select.value = items.some(([value]) => String(value) === current) ? current : '';
  }
  #updateOptionSelect(select, options) { const currentText = select.selectedOptions[0]?.textContent; const retained = retainValue(options, currentText); select.innerHTML = optionsHtml(options, retained); }
  #pollError(error) { if (error instanceof GuessMarketApiError && error.invalidSession) { this.sync.stop(); this.onInvalidSession(); return; } this.#status(`${error.message || 'Server temporarily unavailable.'} Retrying automatically…`, true); }
  #status(message, error) { this.dom.status.textContent = message; this.dom.status.className = `sync-status${error ? ' error' : ''}`; }
  #actionMessage(message, error) { this.dom.actionResult.textContent = message; this.dom.actionResult.className = `action-message${error ? ' error' : ' success'}`; }
  #topupMessage(message, error) { this.dom.topupResult.textContent = message; this.dom.topupResult.className = `action-message${error ? ' error' : ' success'}`; }
}
