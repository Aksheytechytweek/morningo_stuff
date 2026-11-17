function pdfText(value) {
  return String(value ?? '').normalize('NFKD').replace(/[^\x20-\x7E]/g, '?').replace(/\\/g, '\\\\').replace(/\(/g, '\\(').replace(/\)/g, '\\)');
}

export function createExpenseReportPdf({ date, email, budget, expenses }) {
  const rows = (expenses ?? []).slice(0, 34);
  const total = rows.reduce((sum, item) => sum + Number(item.amount ?? 0), 0);
  const lines = [
    'Karnan Kudumbam - Daily Household Report',
    'Date: ' + date,
    'Account: ' + email,
    'Monthly budget: INR ' + Number(budget ?? 15000).toFixed(2),
    'Recorded expenses: INR ' + total.toFixed(2),
    'Entries: ' + rows.length,
    '',
    'DATE          CATEGORY              AMOUNT (INR)  NOTE',
    '--------------------------------------------------------------',
    ...rows.map((item) => [
      String(item.date ?? '').slice(0,10).padEnd(14),
      String(item.category ?? 'Other').slice(0,19).padEnd(21),
      Number(item.amount ?? 0).toFixed(2).padStart(10),
      String(item.note ?? '').slice(0,32)
    ].join('  ')),
    '',
    'This report contains expenses recorded in Karnan Kudumbam.',
    'Retailer prices are observations, not final checkout totals.'
  ];
  const commands = ['BT', '/F1 9 Tf', '48 790 Td', '12 TL'];
  for (let i = 0; i < lines.length; i++) {
    if (i > 0) commands.push('T*');
    commands.push('(' + pdfText(lines[i]) + ') Tj');
  }
  commands.push('ET');
  const stream = commands.join('\n');
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    '<< /Length ' + new TextEncoder().encode(stream).length + ' >>\nstream\n' + stream + '\nendstream'
  ];
  let pdf = '%PDF-1.4\n';
  const offsets = [0];
  for (let i = 0; i < objects.length; i++) {
    offsets.push(new TextEncoder().encode(pdf).length);
    pdf += (i + 1) + ' 0 obj\n' + objects[i] + '\nendobj\n';
  }
  const xref = new TextEncoder().encode(pdf).length;
  pdf += 'xref\n0 ' + (objects.length + 1) + '\n0000000000 65535 f \n';
  for (let i = 1; i < offsets.length; i++) pdf += String(offsets[i]).padStart(10, '0') + ' 00000 n \n';
  pdf += 'trailer\n<< /Size ' + (objects.length + 1) + ' /Root 1 0 R >>\nstartxref\n' + xref + '\n%%EOF';
  return new TextEncoder().encode(pdf);
}

export function istDateTime(date = new Date()) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Kolkata', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
  }).formatToParts(date);
  const get = (key) => parts.find((part) => part.type === key)?.value ?? '';
  return { date: get('year') + '-' + get('month') + '-' + get('day'), time: get('hour') + ':' + get('minute') };
}

export function bytesToBase64(bytes) {
  let binary = '';
  for (let i = 0; i < bytes.length; i += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
  }
  return btoa(binary);
}
