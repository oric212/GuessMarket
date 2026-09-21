export function actionAvailability(user, event) {
  const none = { start: false, close: false, lmsrPurchase: false, orderSubmission: false };
  if (!user || !event || user.blocked) return none;
  const marketMaker = event.marketMakerUsername?.toLowerCase() === user.username?.toLowerCase();
  const active = event.eventState === 'ACTIVE';
  return {
    start: marketMaker && event.eventState === 'NOT_STARTED',
    close: marketMaker && active,
    lmsrPurchase: active && event.tradingMethod === 'LMSR',
    orderSubmission: active && event.tradingMethod === 'ORDER_BOOK',
  };
}

export function validatePositiveAmount(value, label = 'Amount') {
  if (String(value).trim() === '') return `${label} is required.`;
  const number = Number(value);
  return Number.isFinite(number) && number > 0 ? null : `${label} must be a positive number.`;
}

export function validatePositiveInteger(value, label = 'Quantity') {
  if (!/^\d+$/.test(String(value).trim()) || Number(value) <= 0) return `${label} must be a positive whole number.`;
  return null;
}

export function validateOrderPrice(value) {
  const text = String(value).trim();
  if (!/^\d+(\.\d{1,2})?$/.test(text) || Number(text) <= 0) return 'Price must be positive and use at most 2 decimal digits.';
  return null;
}

export function retainValue(availableValues, currentValue) {
  return availableValues.includes(currentValue) ? currentValue : (availableValues[0] ?? '');
}

export function shouldApplySnapshot(snapshotRevision, currentRevision) {
  return snapshotRevision === currentRevision;
}
