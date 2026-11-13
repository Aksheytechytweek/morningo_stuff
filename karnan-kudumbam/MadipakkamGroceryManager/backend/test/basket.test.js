import test from 'node:test';
import assert from 'node:assert/strict';
import { rankCompleteBaskets } from '../src/basket.js';

test('never recommends a provider missing one basket item', () => {
  const lines = [{ id: '1', name: 'Rice', quantity: 1 }, { id: '2', name: 'Milk', quantity: 1 }];
  const quotes = [
    { providerId: 'a', providerName: 'App A', serviceable: true, checkoutQuoteAvailable: true, items: [{ requestLineId: '1', available: true, lineTotal: 100 }], deliveryFee: 0 },
    { providerId: 'b', providerName: 'App B', serviceable: true, checkoutQuoteAvailable: true, items: [{ requestLineId: '1', available: true, lineTotal: 90 }, { requestLineId: '2', available: true, lineTotal: 30 }], deliveryFee: 10 }
  ];
  const result = rankCompleteBaskets(lines, quotes);
  assert.equal(result.recommendation.providerId, 'b');
  assert.equal(result.candidates.find((x) => x.providerId === 'a').eligible, false);
  assert.equal(result.recommendation.finalPayable, 130);
});

test('returns no recommendation if no single app can fulfil every line', () => {
  const lines = [{ id: '1', name: 'Rice', quantity: 1 }, { id: '2', name: 'Milk', quantity: 1 }];
  const quotes = [{ providerId: 'a', serviceable: true, checkoutQuoteAvailable: true, items: [{ requestLineId: '1', available: true, lineTotal: 100 }] }];
  assert.equal(rankCompleteBaskets(lines, quotes).recommendation, null);
});
