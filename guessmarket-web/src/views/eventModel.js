export const ALL = 'ALL';
const matches = (selected, actual) => selected === ALL || selected === actual;
export const matchesEventFilters = (event, filters) => matches(filters.method, event.tradingMethod)
  && matches(filters.state, event.eventState) && matches(filters.commission, event.commissionMethod);
export const filterEvents = (events, filters) => events.filter((event) => matchesEventFilters(event, filters));
export const retainedSelection = (events, selectedId) => selectedId != null && events.some((event) => event.id === selectedId) ? selectedId : null;
export function formatNumber(value) {
  return Number.isFinite(value) ? new Intl.NumberFormat('en-US', { maximumFractionDigits: 2 }).format(value) : 'N/A';
}
export function displayEnum(value) {
  return value ? value.toLowerCase().split('_').map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join(' ') : 'N/A';
}
