import { prisma } from './prisma';

/**
 * Generates "FP/YY-YY/NNNNN" where YY-YY is the Indian financial year
 * (Apr–Mar) and NNNNN is a monotonic sequence that resets each FY.
 */
export async function nextInvoiceNumber(now: Date = new Date()): Promise<string> {
  const y = now.getFullYear();
  const m = now.getMonth(); // 0-based
  const fyStart = m >= 3 ? y : y - 1;
  const fyEnd = fyStart + 1;
  const prefix = `FP/${String(fyStart).slice(-2)}-${String(fyEnd).slice(-2)}/`;

  // Find the highest sequence used in this FY and increment.
  const last = await prisma.invoice.findFirst({
    where: { number: { startsWith: prefix } },
    orderBy: { number: 'desc' },
    select: { number: true },
  });

  const nextSeq = last ? Number(last.number.slice(prefix.length)) + 1 : 1;
  return `${prefix}${String(nextSeq).padStart(5, '0')}`;
}
