import test from 'node:test';
import assert from 'node:assert/strict';
import { createExpenseReportPdf, istDateTime, bytesToBase64 } from '../src/reports.js';

test('daily expense report creates a PDF attachment with household totals', () => {
  const pdf = createExpenseReportPdf({
    date: '2026-11-17', email: 'family@example.com', budget: 15000,
    expenses: [{ date: '2026-11-17', category: 'Vegetables', amount: 120, note: 'Tomato and onion' }]
  });
  const text = new TextDecoder().decode(pdf);
  assert.ok(text.startsWith('%PDF-1.4'));
  assert.ok(text.includes('Recorded expenses: INR 120.00'));
  assert.ok(text.includes('family@example.com'));
  assert.ok(text.endsWith('%%EOF'));
  assert.ok(bytesToBase64(pdf).length > 100);
});

test('report scheduler formats time in India Standard Time', () => {
  assert.deepEqual(istDateTime(new Date('2026-11-17T14:30:00.000Z')), { date: '2026-11-17', time: '20:00' });
});
