import express from 'express';
import helmet from 'helmet';
import { rateLimit } from 'express-rate-limit';
import admin from 'firebase-admin';
import { Resend } from 'resend';
import PDFDocument from 'pdfkit';
import { extractFreeCredits, normaliseSearchResults, buildProviderSummaries, maximumFreeCreditsNeeded } from './rewalto.js';

const app = express();
app.use(helmet());
app.use(express.json({ limit: '256kb' }));
app.use('/v1/', rateLimit({ windowMs: 60_000, limit: 60, standardHeaders: 'draft-7', legacyHeaders: false }));

if (!admin.apps.length) admin.initializeApp({ credential: admin.credential.applicationDefault() });
const firestore = admin.firestore();
const resend = process.env.RESEND_API_KEY ? new Resend(process.env.RESEND_API_KEY) : null;

function requireEnv(name) {
  const value = process.env[name];
  if (!value) throw new Error(`Missing server configuration: ${name}`);
  return value;
}

async function requireUser(req, res, next) {
  try {
    const header = req.headers.authorization ?? '';
    if (!header.startsWith('Bearer ')) return res.status(401).json({ error: 'Missing Firebase ID token' });
    req.user = await admin.auth().verifyIdToken(header.slice(7));
    next();
  } catch {
    return res.status(401).json({ error: 'Invalid authentication token' });
  }
}


app.get('/health', (_req, res) => res.json({ ok: true, service: 'karnan-kudumbam-backend' }));

async function rewaltoPublicGet(path) {
  const response = await fetch(`https://api.rewalto.com${path}`, {
    headers: { accept: 'application/json' }, cache: 'no-store', signal: AbortSignal.timeout(15000)
  });
  const text = await response.text();
  let payload;
  try { payload = text ? JSON.parse(text) : {}; } catch { throw new Error('Rewalto public sample returned invalid JSON'); }
  if (!response.ok) throw new Error(`Rewalto public sample returned HTTP ${response.status}`);
  return payload;
}

async function rewaltoGet(path, params = {}) {
  const key = requireEnv('REWALTO_API_KEY');
  const url = new URL(`https://api.rewalto.com${path}`);
  for (const [name, value] of Object.entries(params)) url.searchParams.set(name, String(value));
  const response = await fetch(url, {
    headers: { authorization: `Bearer ${key}`, accept: 'application/json' },
    cache: 'no-store',
    signal: AbortSignal.timeout(20000)
  });
  const text = await response.text();
  let payload;
  try { payload = text ? JSON.parse(text) : {}; } catch { payload = { message: text.slice(0, 300) }; }
  if (!response.ok) {
    const error = new Error(payload?.error?.message ?? payload?.message ?? `Rewalto returned HTTP ${response.status}`);
    error.status = response.status;
    error.providerCode = payload?.error?.code ?? payload?.code ?? null;
    throw error;
  }
  return payload;
}

// Public version and catalogue endpoints are free and do not consume API credits.
app.get('/v1/rewalto/sample', requireUser, async (_req, res) => {
  try {
    const [version, catalogue] = await Promise.all([
      rewaltoPublicGet('/v1/version'),
      rewaltoPublicGet('/v1/catalogue/products?status=live')
    ]);
    const datasets = Array.isArray(catalogue) ? catalogue :
      (catalogue.products ?? catalogue.datasets ?? catalogue.items ?? catalogue.data ?? []);
    res.set('Cache-Control', 'no-store');
    return res.json({
      provider: 'Rewalto', sampleFree: true, creditsConsumed: 0,
      version: version.version ?? version.release ?? version,
      liveCatalogueAvailable: Array.isArray(datasets),
      liveDatasetCount: Array.isArray(datasets) ? datasets.length : null,
      note: 'Public version/catalogue test only. No price search was run and no API credits were consumed.'
    });
  } catch (error) {
    return res.status(503).json({ error: 'Rewalto free sample endpoint is unavailable.', detail: error.message });
  }
});

app.get('/v1/rewalto/balance', requireUser, async (_req, res) => {
  try {
    const balance = await rewaltoGet('/v1/credits/balance');
    const freeCredits = extractFreeCredits(balance);
    if (freeCredits === null) return res.status(502).json({ error: 'Could not safely read Rewalto free-credit balance. Price checks are blocked to protect the zero-spend rule.' });
    res.set('Cache-Control', 'no-store');
    return res.json({ provider: 'Rewalto', freeCredits, paidUsageAllowed: false, policy: 'free-credits-only' });
  } catch (error) {
    return res.status(error.status ?? 503).json({ error: 'Rewalto balance is unavailable; no price search was started.', providerCode: error.providerCode });
  }
});

/**
 * Manual-only price check. One bounded search (max 10 rows) per basket line.
 * We preflight the worst-case free-credit cost and fail closed if the free
 * allowance cannot cover it. No paid plan, top-up, or payment method is used.
 */
app.post('/v1/rewalto/basket/compare', requireUser, async (req, res) => {
  try {
    const { pincode, lines } = req.body ?? {};
    if (!/^([1-9][0-9]{5})$/.test(String(pincode ?? '')) || !Array.isArray(lines) || lines.length < 1 || lines.length > 20) {
      return res.status(400).json({ error: 'Provide a six-digit PIN and 1–20 basket items.' });
    }
    const cleanLines = lines.map((line, index) => ({
      id: String(line.id ?? index + 1).slice(0, 80),
      name: String(line.name ?? '').trim().slice(0, 120),
      quantity: String(line.quantity ?? '').trim().slice(0, 60),
      quantityCount: Math.max(1, Math.min(50, Number(line.quantityCount ?? 1)))
    }));
    if (cleanLines.some((line) => !line.name || !line.quantity)) return res.status(400).json({ error: 'Every basket item needs a product name and pack size.' });

    // Rewalto spends this month's free allowance before paid credits. Require
    // enough free credits for the worst case before issuing any charged reads.
    const before = await rewaltoGet('/v1/credits/balance');
    const freeBefore = extractFreeCredits(before);
    const maximumCredits = maximumFreeCreditsNeeded(cleanLines.length);
    if (freeBefore === null) return res.status(503).json({ error: 'Free-credit balance could not be verified. Blocked to guarantee zero paid usage.' });
    if (freeBefore < maximumCredits) return res.status(402).json({
      error: 'Not enough free credits for this check. No product searches were made.',
      freeCredits: freeBefore, maximumCredits, paidUsageAllowed: false
    });

    const observations = [];
    let remainingFree = freeBefore;
    for (const line of cleanLines) {
      // Re-check the free allowance before every charged operation. The search
      // is capped at 10 returned listings (worst case 1 credit).
      const currentBalance = await rewaltoGet('/v1/credits/balance');
      remainingFree = extractFreeCredits(currentBalance);
      if (remainingFree === null || remainingFree < 1) break;
      const payload = await rewaltoGet('/v1/data/listings/search', {
        q: `${line.name} ${line.quantity}`,
        pincode,
        limit: 10,
        include: 'latest'
      });
      observations.push(...normaliseSearchResults(payload, { ...line, pincode }));
    }
    const providers = buildProviderSummaries(cleanLines, observations);
    const after = await rewaltoGet('/v1/credits/balance');
    const freeAfter = extractFreeCredits(after);
    const recommendation = providers.filter((item) => item.complete)
      .sort((a, b) => (a.productSubtotal ?? Number.POSITIVE_INFINITY) - (b.productSubtotal ?? Number.POSITIVE_INFINITY))[0] ?? null;

    res.set('Cache-Control', 'no-store');
    return res.json({
      provider: 'Rewalto', pincode, fetchedAt: new Date().toISOString(),
      sourceNote: 'Latest stored public-page observations, not guaranteed real-time checkout quotes. Check each reading timestamp and confirm price/stock in the retailer app.',
      freeCreditsBefore: freeBefore, freeCreditsAfter: freeAfter,
      maximumCreditsReservedByPolicy: maximumCredits, paidUsageAllowed: false,
      partialCheck: cleanLines.some((line) => !observations.some((item) => item.requestLineId === line.id)),
      recommendation, providers, observations
    });
  } catch (error) {
    const status = error.status === 402 ? 402 : error.status === 429 ? 429 : 503;
    return res.status(status).json({
      error: error.status === 402 ? 'Rewalto free credits are exhausted. No paid usage is permitted.' : 'Price data could not be checked. No paid fallback will be attempted.',
      providerCode: error.providerCode
    });
  }
});

app.put('/v1/report-subscription', requireUser, async (req, res) => {
  const { email, enabled = true, monthlyBudget = 15000, reportTime = '20:00' } = req.body ?? {};
  const verifiedEmail = String(req.user.email ?? '').toLowerCase();
  const requestedEmail = String(email ?? '').trim().toLowerCase();
  if (req.user.email_verified !== true || !verifiedEmail || requestedEmail !== verifiedEmail) {
    return res.status(403).json({ error: 'Sign in with the verified recipient email address to enable automatic reports.' });
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(requestedEmail) || !/^([01]\d|2[0-3]):[0-5]\d$/.test(reportTime) || !Number.isFinite(Number(monthlyBudget)) || Number(monthlyBudget) <= 0) {
    return res.status(400).json({ error: 'Invalid report email, time or monthly budget.' });
  }
  await firestore.collection('reportSubscriptions').doc(req.user.uid).set({
    email: requestedEmail, enabled: Boolean(enabled), monthlyBudget: Number(monthlyBudget),
    reportTime, timeZone: 'Asia/Kolkata', updatedAt: admin.firestore.FieldValue.serverTimestamp()
  }, { merge: true });
  res.json({ saved: true, email: requestedEmail, enabled: Boolean(enabled), reportTime, timeZone: 'Asia/Kolkata' });
});

app.post('/v1/expenses/sync', requireUser, async (req, res) => {
  const { householdId, expenses } = req.body ?? {};
  if (!householdId || !Array.isArray(expenses) || expenses.length > 500) return res.status(400).json({ error: 'Invalid sync payload' });
  // A user may only write to their own household unless membership is separately granted.
  if (householdId !== req.user.uid) return res.status(403).json({ error: 'Household membership not configured for this account' });
  const batch = firestore.batch();
  for (const item of expenses) {
    if (!item.id || !/^[A-Za-z0-9_-]{1,160}$/.test(String(item.id)) || !/^\d{4}-\d{2}-\d{2}$/.test(String(item.date)) || !Number.isFinite(Number(item.amount)) || Number(item.amount) < 0 || Number(item.amount) > 10000000) continue;
    const ref = firestore.collection('households').doc(householdId).collection('expenses').doc(String(item.id));
    batch.set(ref, {
      date: String(item.date), amount: Number(item.amount), category: String(item.category ?? 'Other'),
      note: String(item.note ?? ''), payment: String(item.payment ?? 'Cash'), updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
  }
  await batch.commit();
  res.json({ synced: true });
});

app.get('/v1/expenses/sync', requireUser, async (req, res) => {
  const householdId = req.user.uid;
  const snapshot = await firestore.collection('households').doc(householdId).collection('expenses').limit(1000).get();
  res.json({ householdId, expenses: snapshot.docs.map(doc => ({ id: doc.id, ...doc.data(), updatedAt: undefined })) });
});

function createPdf(report) {
  return new Promise((resolve, reject) => {
    const doc = new PDFDocument({ size: 'A4', margin: 48 });
    const chunks = [];
    doc.on('data', (chunk) => chunks.push(chunk));
    doc.on('end', () => resolve(Buffer.concat(chunks)));
    doc.on('error', reject);
    doc.fontSize(20).fillColor('#16734A').text('Karnan Kudumbam', { continued: false });
    doc.fontSize(11).fillColor('#333333').text('Daily Household Expense Statement');
    doc.moveDown().fontSize(10).text(`Date: ${report.date}`);
    doc.text(`Monthly budget: Rs. ${report.monthlyBudget.toFixed(2)}`);
    doc.text(`Month-to-date spending: Rs. ${report.monthSpent.toFixed(2)}`);
    doc.text(`Remaining budget: Rs. ${(report.monthlyBudget - report.monthSpent).toFixed(2)}`);
    doc.moveDown().fontSize(13).fillColor('#16734A').text(`Today's total: Rs. ${report.dayTotal.toFixed(2)}`);
    doc.moveDown().fontSize(11).fillColor('#222222').text('Expenses');
    for (const expense of report.expenses) {
      doc.fontSize(9).text(`${expense.category} | ${expense.note || 'Household expense'} | ${expense.payment} | Rs. ${Number(expense.amount).toFixed(2)}`);
    }
    doc.moveDown().fontSize(10).fillColor('#555555').text('Generated automatically by Karnan Kudumbam.');
    doc.end();
  });
}

/** Called by a trusted scheduler at 20:00 Asia/Kolkata. */
app.post('/internal/jobs/daily-report', async (req, res) => {
  const expected = process.env.DAILY_JOB_SECRET;
  if (!expected || req.headers['x-job-secret'] !== expected) return res.status(401).json({ error: 'Unauthorized scheduler' });
  if (!resend) return res.status(503).json({ error: 'Email provider is not configured' });
  const dateParts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const dateMap = Object.fromEntries(dateParts.map((part) => [part.type, part.value]));
  const date = `${dateMap.year}-${dateMap.month}-${dateMap.day}`;
  const month = date.slice(0, 7);
  const recipients = await firestore.collection('reportSubscriptions').where('enabled', '==', true).get();
  const outcomes = [];
  for (const subscription of recipients.docs) {
    const settings = subscription.data();
    const householdId = subscription.id;
    const deliveryId = `${householdId}_${date}`;
    const deliveryRef = firestore.collection('reportDeliveries').doc(deliveryId);
    const claim = await firestore.runTransaction(async (transaction) => {
      const existing = await transaction.get(deliveryRef);
      if (existing.exists) {
        const state = existing.data();
        if (state.status === 'sent') return 'already-sent';
        if (state.status === 'sending' && state.updatedAt?.toDate && (Date.now() - state.updatedAt.toDate().getTime()) < 15 * 60 * 1000) return 'in-progress';
      }
      transaction.set(deliveryRef, {
        householdId, date, status: 'sending', recipient: settings.email,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
      return 'claimed';
    });
    if (claim !== 'claimed') { outcomes.push({ householdId, status: claim }); continue; }
    const expenseSnap = await firestore.collection('households').doc(householdId).collection('expenses')
      .where('date', '>=', `${date.slice(0, 7)}-01`).where('date', '<=', date).get();
    const expenses = expenseSnap.docs.map((doc) => ({ id: doc.id, ...doc.data() }));
    const todays = expenses.filter((item) => item.date === date);
    const monthSpent = expenses.reduce((sum, item) => sum + Number(item.amount ?? 0), 0);
    const dayTotal = todays.reduce((sum, item) => sum + Number(item.amount ?? 0), 0);
    const report = { date, monthlyBudget: Number(settings.monthlyBudget ?? 15000), monthSpent, dayTotal, expenses: todays };
    const pdf = await createPdf(report);
    try {
      const sent = await resend.emails.send({
        from: requireEnv('REPORT_FROM_EMAIL'), to: [settings.email],
        subject: `Karnan Kudumbam - Daily Expense Report - ${date}`,
        html: `<h2>Karnan Kudumbam daily report</h2><p>Today's spending: <b>₹${dayTotal.toFixed(2)}</b></p><p>Month-to-date: ₹${monthSpent.toFixed(2)} / ₹${report.monthlyBudget.toFixed(2)}</p><p>Remaining: ₹${(report.monthlyBudget - monthSpent).toFixed(2)}</p><p>See attached PDF for details.</p>`,
        attachments: [{ filename: `Karnan-Kudumbam-${date}.pdf`, content: pdf.toString('base64') }]
      });
      if (sent.error || !sent.data?.id) throw new Error(sent.error?.message ?? 'Email provider did not confirm acceptance');
      await deliveryRef.set({ status: 'sent', providerMessageId: sent.data?.id ?? null, sentAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
      outcomes.push({ householdId, status: 'sent' });
    } catch (error) {
      await deliveryRef.set({ status: 'failed', error: String(error.message).slice(0, 300), updatedAt: admin.firestore.FieldValue.serverTimestamp() }, { merge: true });
      outcomes.push({ householdId, status: 'failed' });
    }
  }
  res.json({ date, outcomes });
});

const port = Number(process.env.PORT ?? 8080);
app.listen(port, () => console.log(`Karnan Kudumbam backend listening on ${port}`));
