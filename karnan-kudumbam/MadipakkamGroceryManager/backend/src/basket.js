/**
 * Single-store basket decision logic. This is deliberately fail-closed:
 * a store is eligible only if it can fulfil every requested line.
 */
export function rankCompleteBaskets(requestLines, quotes) {
  const required = requestLines.filter((line) => Number(line.quantity) > 0);
  if (required.length === 0) return { recommendation: null, candidates: [] };

  const candidates = quotes.map((quote) => {
    const byProduct = new Map((quote.items ?? []).map((item) => [item.requestLineId, item]));
    const missing = required.filter((line) => {
      const match = byProduct.get(line.id);
      return !match || match.available !== true || !Number.isFinite(Number(match.lineTotal)) || Number(match.lineTotal) < 0;
    });
    const productSubtotal = required.reduce((sum, line) => sum + Number(byProduct.get(line.id)?.lineTotal ?? 0), 0);
    const fees = [quote.deliveryFee, quote.handlingFee, quote.platformFee, quote.smallCartFee, quote.tax]
      .reduce((sum, value) => sum + (Number.isFinite(Number(value)) ? Number(value) : 0), 0);
    const discount = Number.isFinite(Number(quote.discount)) ? Number(quote.discount) : 0;
    const total = Math.max(0, productSubtotal + fees - discount);
    const feeFields = ['deliveryFee', 'handlingFee', 'platformFee', 'smallCartFee', 'tax', 'discount'];
    const validFees = feeFields.every((field) => Number.isFinite(Number(quote[field] ?? 0)) && Number(quote[field] ?? 0) >= 0);
    const eligible = missing.length === 0 && validFees && quote.serviceable === true && quote.checkoutQuoteAvailable === true;
    return {
      providerId: quote.providerId,
      providerName: quote.providerName,
      eligible,
      missingItems: missing.map((line) => line.name),
      productSubtotal: round2(productSubtotal),
      fees: round2(fees),
      discount: round2(discount),
      finalPayable: eligible ? round2(total) : null,
      currency: 'INR',
      checkedAt: quote.checkedAt ?? null,
      quoteExpiresAt: quote.quoteExpiresAt ?? null
    };
  });

  const eligible = candidates.filter((candidate) => candidate.eligible)
    .sort((a, b) => a.finalPayable - b.finalPayable);
  return { recommendation: eligible[0] ?? null, candidates };
}

function round2(value) { return Math.round((value + Number.EPSILON) * 100) / 100; }
