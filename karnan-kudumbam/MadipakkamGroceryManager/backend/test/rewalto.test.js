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
    latest: { stockState: 'in_stock', readAt: '2026-11-17T10:00:00Z', pincode: '600091' }
  }] }, { id: '1', name: 'Aashirvaad Atta', quantity: '1 kg', pincode: '600091', now: '2026-11-17T10:30:00Z' });
  assert.equal(rows[0].price, 55);
  assert.equal(rows[0].exactPackMatch, true);
  assert.equal(rows[0].stock, 'in_stock');
});

test('normalises current Rewalto contract fields: priceMinor, packSizeRaw and inStock', () => {
  const rows = normaliseSearchResults({ data: [{
    id: '7c1e5f0a-2f55-4d2b-8c36-0f3f1d9c2b77',
    retailer: 'zepto',
    title: 'Sunflower Oil 1 L',
    packSizeRaw: '1 L',
    latest: {
      state: 'observed',
      priceMinor: 7999,
      mrpMinor: 8999,
      currency: 'INR',
      inStock: true,
      pincode: '600091',
      observedAt: '2026-11-19T09:30:00.000Z'
    }
  }] }, { id: '1', name: 'Sunflower Oil', quantity: '1 L', pincode: '600091', now: '2026-11-19T10:00:00.000Z' });
  assert.equal(rows.length, 1);
  assert.equal(rows[0].price, 79.99);
  assert.equal(rows[0].packSize, '1 L');
  assert.equal(rows[0].stock, 'in_stock');
  assert.equal(rows[0].checkedAt, '2026-11-19T09:30:00.000Z');
  assert.equal(rows[0].locationSpecific, true);
  assert.equal(rows[0].exactPackMatch, true);
  assert.equal(rows[0].exactNameMatch, true);
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

test('PIN-matched BigBasket observations can qualify for a complete basket', () => {
  const rows = normaliseSearchResults({ listings: [{
    retailer: 'bigbasket', title: 'Amul Milk 500 ml', packSize: '500 ml', price: 3200,
    latest: { stockState: 'in_stock', readAt: '2026-11-17T10:00:00Z', pincode: '600091' }
  }] }, { id: '1', name: 'Amul Milk', quantity: '500 ml', pincode: '600091', now: '2026-11-17T10:30:00Z' });
  assert.equal(rows[0].locationSpecific, true);
});

test('stale or undated stock is unknown and cannot qualify for a provider basket', () => {
  const rows = normaliseSearchResults({ listings: [{
    retailer: 'zepto', title: 'Amul Milk 500 ml', packSize: '500 ml', price: 3200,
    latest: { stockState: 'in_stock', readAt: '2026-11-14T10:00:00Z', pincode: '600091' }
  }] }, { id: '1', name: 'Amul Milk', quantity: '500 ml', pincode: '600091', now: '2026-11-17T10:30:00Z' });
  assert.equal(rows[0].stock, 'unknown');
});
