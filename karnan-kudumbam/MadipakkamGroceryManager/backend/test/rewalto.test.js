import test from 'node:test';
import assert from 'node:assert/strict';
import { extractFreeCredits, normaliseRetailer, normaliseSearchResults, buildProviderSummaries, maximumFreeCreditsNeeded } from '../src/rewalto.js';

test('free balance parser fails closed and never treats total balance as free', () => {
  assert.equal(extractFreeCredits({ total: 1000 }), null);
  assert.equal(extractFreeCredits({ free: { available: 75 }, paid: { available: 900 } }), 75);
  assert.equal(extractFreeCredits({ freeCredits: '12' }), 12);
});

test('retailer names are normalised to supported platforms', () => {
  assert.equal(normaliseRetailer('swiggy_instamart').id, 'swiggy_instamart');
  assert.equal(normaliseRetailer('Flipkart Minutes').id, 'flipkart_minutes');
  assert.equal(normaliseRetailer('unknown'), null);
});

test('normalises Rewalto paise, stock and exact pack evidence', () => {
  const rows = normaliseSearchResults({ listings: [{
    retailer: 'blinkit', title: 'Aashirvaad Atta 1 kg', packSize: '1 kg', price: 5500,
    latest: { stockState: 'in_stock', readAt: '2026-11-12T10:00:00Z', pincode: '600091' }
  }] }, { id: '1', name: 'Aashirvaad Atta', quantity: '1 kg', pincode: '600091' });
  assert.equal(rows[0].price, 55);
  assert.equal(rows[0].exactPackMatch, true);
  assert.equal(rows[0].stock, 'in_stock');
});

test('basket summary never marks an incomplete or unknown-stock provider complete', () => {
  const lines = [{ id: '1', name: 'Aashirvaad Atta', quantity: '1 kg' }, { id: '2', name: 'Amul Butter', quantity: '500 g' }];
  const observations = [{ requestLineId: '1', providerId: 'zepto', exactNameMatch: true, exactPackMatch: true, stock: 'in_stock', price: 55, locationSpecific: true }];
  const result = buildProviderSummaries(lines, observations).find((item) => item.providerId === 'zepto');
  assert.equal(result.complete, false);
  assert.deepEqual(result.missingItems, ['Amul Butter']);
  assert.equal(result.finalPayable, null);
});

test('worst-case free credit guard is one credit per requested line, capped at 20', () => {
  assert.equal(maximumFreeCreditsNeeded(10), 10);
  assert.equal(maximumFreeCreditsNeeded(100), 20);
});

test('national BigBasket readings cannot complete a PIN-specific basket', () => {
  const lines = [{ id: '1', name: 'Milk', quantity: '500 ml' }];
  const observations = [{ requestLineId: '1', providerId: 'bigbasket', exactNameMatch: true, exactPackMatch: true, stock: 'in_stock', price: 30, locationSpecific: false }];
  const result = buildProviderSummaries(lines, observations).find((item) => item.providerId === 'bigbasket');
  assert.equal(result.complete, false);
  assert.equal(result.finalPayable, null);
});
