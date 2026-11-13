const RETAILERS = [
  { id: 'zepto', label: 'Zepto', aliases: ['zepto'] },
  { id: 'blinkit', label: 'Blinkit', aliases: ['blinkit'] },
  { id: 'swiggy_instamart', label: 'Swiggy Instamart', aliases: ['swiggy_instamart', 'instamart', 'swiggy instamart'] },
  { id: 'flipkart_minutes', label: 'Flipkart Minutes', aliases: ['flipkart_minutes', 'flipkart minutes', 'minutes'] },
  { id: 'bigbasket', label: 'BigBasket', aliases: ['bigbasket', 'big basket', 'bbnow'] }
];

export function extractFreeCredits(balance) {
  const candidates = [
    balance?.freeCredits,
    balance?.free_credits,
    balance?.freeCreditsAvailable,
    balance?.free_credits_available,
    balance?.freeBalance,
    balance?.free_balance,
    balance?.freeRemaining,
    balance?.free?.available,
    balance?.free?.remaining,
    balance?.free?.credits,
    balance?.free?.balance,
    balance?.balances?.free?.available,
    balance?.balances?.free?.remaining,
    balance?.credits?.free?.available,
    balance?.credits?.free,
    balance?.available?.free
  ];
  for (const value of candidates) {
    const number = typeof value === 'object' && value !== null ? Number(value.available ?? value.remaining ?? value.credits ?? value.balance) : Number(value);
    if (Number.isFinite(number) && number >= 0) return Math.floor(number);
  }
  const buckets = balance?.buckets ?? balance?.creditBuckets ?? balance?.balances;
  if (Array.isArray(buckets)) {
    const freeBucket = buckets.find((bucket) => String(bucket.kind ?? bucket.type ?? bucket.name ?? '').toLowerCase() === 'free');
    const number = Number(freeBucket?.available ?? freeBucket?.remaining ?? freeBucket?.credits ?? freeBucket?.balance);
    if (Number.isFinite(number) && number >= 0) return Math.floor(number);
  }
  return null; // fail closed: never infer a free balance from a total balance
}

export function normaliseRetailer(value) {
  const raw = typeof value === 'object' && value !== null
    ? (value.key ?? value.id ?? value.slug ?? value.name ?? '') : String(value ?? '');
  const key = raw.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '');
  return RETAILERS.find((retailer) => retailer.aliases.some((alias) =>
    alias.replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '') === key
  )) ?? null;
}

function recordsFrom(payload) {
  for (const key of ['listings', 'results', 'items', 'rows', 'data']) {
    if (Array.isArray(payload?.[key])) return payload[key];
  }
  return [];
}

function firstDefined(...values) { return values.find((value) => value !== undefined && value !== null && value !== ''); }
function toRupees(value, explicitRupees = false) {
  const number = Number(value);
  if (!Number.isFinite(number) || number < 0) return null;
  return explicitRupees ? number : number / 100; // Rewalto documents price/MRP in paise.
}
function stockState(value) {
  const state = String(value ?? '').toLowerCase();
  if (['in_stock', 'instock', 'available', 'in stock', 'true'].includes(state)) return 'in_stock';
  if (['out_of_stock', 'outofstock', 'unavailable', 'out of stock', 'false'].includes(state)) return 'out_of_stock';
  return 'unknown';
}
function normalisePack(value) {
  return String(value ?? '').toLowerCase().replace(/\s+/g, '').replace(/×/g, 'x').replace(/[^a-z0-9.]/g, '');
}
function packMatches(requested, listing) {
  const wanted = normalisePack(requested);
  if (!wanted || wanted === '1pack' || wanted === '1') return false;
  const observed = normalisePack(`${listing.packSize ?? ''} ${listing.title ?? ''}`);
  return observed.includes(wanted);
}
function titleMatches(requestedName, title) {
  const words = String(requestedName).toLowerCase().split(/[^a-z0-9]+/).filter((w) => w.length > 2);
  const haystack = String(title ?? '').toLowerCase();
  return words.length > 0 && words.every((word) => haystack.includes(word));
}

export function normaliseSearchResults(payload, requestLine) {
  return recordsFrom(payload).map((row) => {
    const latest = row.latest ?? row.latestObservation ?? row.observation ?? {};
    const retailer = normaliseRetailer(firstDefined(row.retailer, row.retailerId, row.source, row.app));
    const title = String(firstDefined(row.title, row.name, row.productName, row.displayName, '')).trim();
    const packSize = String(firstDefined(row.packSize, row.pack_size, row.size, row.variant, '')).trim();
    const rawPrice = firstDefined(latest.pricePaise, latest.sellingPricePaise, latest.price, row.pricePaise, row.price, row.sellingPricePaise);
    const explicitRupees = latest.priceRupees !== undefined || row.priceRupees !== undefined;
    const price = toRupees(firstDefined(latest.priceRupees, row.priceRupees, rawPrice), explicitRupees);
    const stock = stockState(firstDefined(latest.stockState, latest.stock, latest.availability, latest.inStock, row.stockState, row.stock));
    const checkedAt = firstDefined(latest.readAt, latest.observedAt, latest.checkedAt, latest.collectedAt, row.readAt, row.updatedAt, null);
    const pin = String(firstDefined(latest.pincode, latest.pinCode, row.pincode, row.pinCode, '')).trim();
    return {
      requestLineId: requestLine.id,
      providerId: retailer?.id ?? 'unknown',
      providerName: retailer?.label ?? String(firstDefined(row.retailerName, row.retailer, row.app, 'Unknown retailer')),
      listingId: String(firstDefined(row.id, row.listingId, row.listing_id, row.retailerSku, '')),
      productId: String(firstDefined(row.productId, row.product_id, '')),
      title,
      brand: String(row.brand ?? ''),
      packSize,
      price,
      stock,
      checkedAt,
      pincode: pin,
      locationSpecific: retailer?.id === 'bigbasket' ? false : pin === String(requestLine.pincode ?? ''),
      sourceUrl: String(firstDefined(row.url, row.sourceUrl, row.pageUrl, '')),
      exactNameMatch: titleMatches(requestLine.name, title),
      exactPackMatch: packMatches(requestLine.quantity, { ...row, title, packSize })
    };
  }).filter((row) => row.providerId !== 'unknown' && row.title && row.price !== null);
}

export function buildProviderSummaries(lines, observations) {
  return RETAILERS.map((provider) => {
    const selected = lines.map((line) => {
      const candidates = observations.filter((item) => item.requestLineId === line.id && item.providerId === provider.id &&
        item.exactNameMatch && item.exactPackMatch && item.stock === 'in_stock' && item.price !== null &&
        item.locationSpecific);
      return candidates.sort((a, b) => new Date(b.checkedAt ?? 0) - new Date(a.checkedAt ?? 0))[0] ?? null;
    });
    const missingItems = lines.filter((_line, index) => !selected[index]).map((line) => line.name);
    const complete = lines.length > 0 && missingItems.length === 0;
    const subtotal = complete ? selected.reduce((sum, item, index) => sum + item.price * Number(lines[index].quantityCount ?? 1), 0) : null;
    return {
      providerId: provider.id,
      providerName: provider.label,
      complete,
      matchedItems: selected.filter(Boolean).length,
      requiredItems: lines.length,
      missingItems,
      productSubtotal: subtotal === null ? null : Math.round(subtotal * 100) / 100,
      finalPayable: null,
      checkoutFeesIncluded: false,
      locationCaveat: provider.id === 'bigbasket' ? 'Rewalto labels BigBasket readings as national, not PIN-specific; verify in the app.' : null,
      items: selected.filter(Boolean)
    };
  });
}

export function maximumFreeCreditsNeeded(itemCount) {
  // One search per basket line, at most 10 records per search: max 1 credit per line.
  return Math.max(0, Math.min(20, Math.floor(itemCount)));
}
