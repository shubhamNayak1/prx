import { Router } from 'express';
import { z } from 'zod';
import ExcelJS from 'exceljs';
import { Role } from '@prisma/client';
import { prisma } from '../lib/prisma';
import { requireAuth } from '../middleware/auth';

const router = Router();
router.use(requireAuth);

const dateOnly = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, (m || 1) - 1, d || 1));
};
const addDays = (d: Date, n: number) =>
  new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate() + n));
const fmt = (d: Date) => {
  const dd = String(d.getUTCDate()).padStart(2, '0');
  const mm = String(d.getUTCMonth() + 1).padStart(2, '0');
  return `${dd}/${mm}/${d.getUTCFullYear()}`;
};

// Scope check: the signed-in user may export their own DCR; managers may
// export for any descendant in their subtree; admins may export for anyone
// in the same company.
async function canExportFor(authUserId: string, authRole: Role, companyId: string, targetId: string) {
  if (targetId === authUserId) return true;
  if (authRole === Role.FIELD_PERSON) return false;
  if (authRole === Role.ADMIN) {
    const u = await prisma.user.findFirst({ where: { id: targetId, companyId }, select: { id: true } });
    return !!u;
  }
  // Managers: walk descendants.
  const all = await prisma.user.findMany({
    where: { companyId, isActive: true },
    select: { id: true, managerId: true },
  });
  const children = new Map<string, string[]>();
  for (const u of all) {
    if (!u.managerId) continue;
    const arr = children.get(u.managerId) || [];
    arr.push(u.id);
    children.set(u.managerId, arr);
  }
  const seen = new Set<string>();
  const stack = [...(children.get(authUserId) || [])];
  while (stack.length) {
    const id = stack.pop()!;
    if (seen.has(id)) continue;
    seen.add(id);
    if (id === targetId) return true;
    (children.get(id) || []).forEach(c => stack.push(c));
  }
  return false;
}

// Map a doctor's speciality string to the DCR column (BAMS / BHMS / MBBS /
// Gyn / Pead) or returns 'OTHER' with a short label (e.g. "RMP", "MD").
function doctorColumn(sp: string | null): { col: 'BAMS' | 'BHMS' | 'MBBS' | 'Gyn' | 'Pead' | 'OTHER'; label: string } {
  const s = (sp || '').trim().toUpperCase();
  if (!s) return { col: 'OTHER', label: '' };
  if (s === 'BAMS') return { col: 'BAMS', label: 'Y' };
  if (s === 'BHMS') return { col: 'BHMS', label: 'Y' };
  if (s === 'MBBS') return { col: 'MBBS', label: 'Y' };
  if (s.startsWith('GYN') || s.includes('OBG')) return { col: 'Gyn', label: 'Y' };
  if (s.startsWith('PAED') || s.startsWith('PED')) return { col: 'Pead', label: 'Y' };
  // Everything else (RMP, MD, ORTHO, …) goes to OTHER with its label.
  return { col: 'OTHER', label: s };
}

const querySchema = z.object({
  userId: z.string().optional(),
  from: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  to: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
});

router.get('/dcr.xlsx', async (req, res) => {
  const parsed = querySchema.safeParse(req.query);
  if (!parsed.success) {
    return res.status(400).json({ error: 'Invalid input', details: parsed.error.flatten() });
  }
  const targetUserId = parsed.data.userId || req.auth!.userId;
  const from = dateOnly(parsed.data.from);
  const to = dateOnly(parsed.data.to);
  if (to < from) return res.status(400).json({ error: 'BAD_RANGE' });

  const allowed = await canExportFor(req.auth!.userId, req.auth!.role, req.auth!.companyId, targetUserId);
  if (!allowed) return res.status(403).json({ error: 'FORBIDDEN' });

  const user = await prisma.user.findFirst({
    where: { id: targetUserId, companyId: req.auth!.companyId },
    include: { company: { select: { name: true } } },
  });
  if (!user) return res.status(404).json({ error: 'User not found' });

  const toExclusive = addDays(to, 1);
  const [visits, dcrDays, expenses] = await Promise.all([
    prisma.visit.findMany({
      where: {
        userId: targetUserId,
        checkInAt: { gte: from, lt: toExclusive },
      },
      include: {
        client: {
          select: {
            id: true, name: true, type: true,
            speciality: true, address: true, city: true, phone: true,
          },
        },
      },
      orderBy: { checkInAt: 'asc' },
    }),
    prisma.dcrDay.findMany({
      where: { userId: targetUserId, date: { gte: from, lte: to } },
    }),
    prisma.expense.findMany({
      where: { userId: targetUserId, date: { gte: from, lte: to } },
      select: { date: true, distanceKm: true },
    }),
  ]);

  // Build the list of all days in range so empty days still get a sheet.
  const days: Date[] = [];
  for (let d = from; d <= to; d = addDays(d, 1)) days.push(d);

  const visitsByDate = new Map<string, typeof visits>();
  for (const v of visits) {
    const key = v.checkInAt.toISOString().slice(0, 10);
    const arr = visitsByDate.get(key) || [];
    arr.push(v);
    visitsByDate.set(key, arr);
  }
  const dcrByDate = new Map<string, typeof dcrDays[number]>();
  for (const d of dcrDays) dcrByDate.set(d.date.toISOString().slice(0, 10), d);
  const kmByDate = new Map<string, number>();
  for (const e of expenses) {
    const k = e.date.toISOString().slice(0, 10);
    if (e.distanceKm != null) kmByDate.set(k, (kmByDate.get(k) || 0) + e.distanceKm);
  }

  const wb = new ExcelJS.Workbook();
  wb.creator = 'Baserastech Field Pharma';
  wb.created = new Date();

  days.forEach((d, idx) => {
    const key = d.toISOString().slice(0, 10);
    const sheetName = `DCR${String(idx + 1).padStart(2, '0')}`;
    const ws = wb.addWorksheet(sheetName, {
      pageSetup: { orientation: 'landscape', paperSize: 9, fitToPage: true, fitToWidth: 1, fitToHeight: 1 },
      views: [{ state: 'frozen', ySplit: 0, xSplit: 0 }],
    });

    for (let c = 1; c <= 11; c++) ws.getColumn(c).width = c === 2 ? 28 : c === 3 ? 24 : 12;

    const dcr = dcrByDate.get(key);
    const dayVisits = visitsByDate.get(key) || [];
    const km = kmByDate.get(key) ?? dcr?.kmTravelled ?? null;

    writeHeader(ws, user, dcr, idx + 1, d, km);
    const doctorEnd = writeDoctorsTable(ws, dayVisits.filter(v => v.client.type === 'DOCTOR'), 9);
    const chemistEnd = writeChemistsTable(ws, dayVisits.filter(v => v.client.type === 'CHEMIST'), doctorEnd + 1);
    const stockistEnd = writeStockistsTable(ws, dayVisits.filter(v => v.client.type === 'STOCKIST'), chemistEnd + 1);
    writeSummary(ws, dayVisits, stockistEnd + 1);
  });

  const buf = await wb.xlsx.writeBuffer();
  const nameSlug = user.name.replace(/\s+/g, '_').replace(/[^A-Za-z0-9_]/g, '');
  const filename = `${nameSlug}_DCR_${parsed.data.from}_to_${parsed.data.to}.xlsx`;
  res.setHeader('Content-Type', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
  res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
  res.send(Buffer.from(buf));
});

function border(cell: ExcelJS.Cell) {
  cell.border = {
    top: { style: 'thin' }, bottom: { style: 'thin' },
    left: { style: 'thin' }, right: { style: 'thin' },
  };
}
function headerFill(cell: ExcelJS.Cell) {
  cell.fill = { type: 'pattern', pattern: 'solid', fgColor: { argb: 'FFE7F0FA' } };
  cell.font = { bold: true };
  cell.alignment = { vertical: 'middle', horizontal: 'center', wrapText: true };
  border(cell);
}

function writeHeader(
  ws: ExcelJS.Worksheet,
  user: { name: string; employeeCode: string | null; hq: string | null; company: { name: string } },
  dcr: { workTypeI: string | null; workTypeII: string | null; placeOfWork: string | null; baseTownOnOs: string | null; routeNoInSvl: string | null } | undefined,
  dfrNo: number,
  dateOfWorking: Date,
  km: number | null,
) {
  ws.mergeCells('A1:K1');
  const t1 = ws.getCell('A1');
  t1.value = `${user.company.name}`;
  t1.font = { bold: true, size: 12 };
  t1.alignment = { horizontal: 'center' };

  ws.mergeCells('A2:K2');
  const t2 = ws.getCell('A2');
  t2.value = 'DAILY FIELD REPORT';
  t2.font = { bold: true, size: 14 };
  t2.alignment = { horizontal: 'center' };

  // Row 3
  ws.getCell('A3').value = 'NAME OF ASR:';
  ws.getCell('B3').value = user.name;
  ws.getCell('E3').value = `I . NO- ${user.employeeCode || ''}`;
  ws.getCell('G3').value = 'DFR NO-';
  ws.getCell('H3').value = dfrNo;
  ws.getCell('I3').value = `DATE OF WRITING- ${fmt(new Date())}`;

  // Row 4
  ws.getCell('A4').value = 'HQ-';
  ws.getCell('B4').value = user.hq || '';
  ws.getCell('D4').value = `PLACE OF WORK- ${dcr?.placeOfWork || ''}`;
  ws.getCell('I4').value = `DATE OF WORKING- ${fmt(dateOfWorking)}`;

  // Row 5
  ws.getCell('A5').value = `TYPE OF WORK I-   ${dcr?.workTypeI || 'INDP/JOINT/MEETING'}`;
  ws.getCell('F5').value = 'WORK WITH -';

  // Row 6
  ws.getCell('A6').value = `TYPE OF WORK -II-  ${dcr?.workTypeII || 'LOCAL/EX/OS'}`;
  ws.getCell('F6').value = `KILOMETER TRAVELLED- ${km != null ? km : ''}`;

  // Row 7
  ws.getCell('A7').value = `NAME OF BASE TOWN ON OS WORK- ${dcr?.baseTownOnOs || ''}`;
  ws.getCell('F7').value = `ROUTE NO IN SVL- ${dcr?.routeNoInSvl || ''}`;

  for (let r = 3; r <= 7; r++) {
    for (let c = 1; c <= 11; c++) ws.getCell(r, c).font = { ...(ws.getCell(r, c).font || {}), size: 10 };
  }
}

function writeDoctorsTable(
  ws: ExcelJS.Worksheet,
  rows: Array<{
    client: { name: string; speciality: string | null; address: string | null; city: string | null; phone: string | null };
    pobValue: any;
  }>,
  startRow: number,
) {
  ws.mergeCells(startRow - 1, 4, startRow - 1, 11);
  const tHead = ws.getCell(startRow - 1, 4);
  tHead.value = 'TYPE OF CUSTOMER';
  headerFill(tHead);

  const h = ['NO', 'NAME OF THE CUSTOMER', 'PLACE', 'MOBILE NO', 'BAMS', 'BHMS', 'MBBS', 'Gyn', 'Pead', 'OTHER', 'POB VALUE'];
  h.forEach((val, i) => {
    const c = ws.getCell(startRow, i + 1);
    c.value = val;
    headerFill(c);
  });

  rows.forEach((v, i) => {
    const r = startRow + 1 + i;
    const col = doctorColumn(v.client.speciality);
    ws.getCell(r, 1).value = i + 1;
    ws.getCell(r, 2).value = v.client.name;
    ws.getCell(r, 3).value = v.client.city || v.client.address || '';
    ws.getCell(r, 4).value = v.client.phone || '';
    const colIdxMap: Record<string, number> = { BAMS: 5, BHMS: 6, MBBS: 7, Gyn: 8, Pead: 9, OTHER: 10 };
    ws.getCell(r, colIdxMap[col.col]).value = col.label;
    ws.getCell(r, 11).value = v.pobValue != null ? Number(v.pobValue) : null;
    for (let c = 1; c <= 11; c++) border(ws.getCell(r, c));
  });
  // Always leave at least 12 rows so the sheet looks like the template.
  const minRows = 12;
  const padded = Math.max(rows.length, minRows);
  for (let i = rows.length; i < padded; i++) {
    const r = startRow + 1 + i;
    ws.getCell(r, 1).value = i + 1;
    for (let c = 1; c <= 11; c++) border(ws.getCell(r, c));
  }
  return startRow + padded;
}

function writeChemistsTable(
  ws: ExcelJS.Worksheet,
  rows: Array<{
    client: { name: string; address: string | null; city: string | null; phone: string | null };
    productsDiscussed: string[];
    pobValue: any;
  }>,
  startRow: number,
) {
  ws.mergeCells(startRow, 4, startRow, 11);
  const tHead = ws.getCell(startRow, 4);
  tHead.value = 'NAME OF PRODUCT ORDERED';
  headerFill(tHead);

  const h = ['', 'RETAILER/CHEMIST NAME', 'PLACE', 'MOBILE NO', '1', '2', '3', '4', '5', '6', 'POB VALUE'];
  h.forEach((val, i) => {
    const c = ws.getCell(startRow + 1, i + 1);
    c.value = val;
    headerFill(c);
  });

  rows.forEach((v, i) => {
    const r = startRow + 2 + i;
    ws.getCell(r, 1).value = i + 1;
    ws.getCell(r, 2).value = v.client.name;
    ws.getCell(r, 3).value = v.client.city || v.client.address || '';
    ws.getCell(r, 4).value = v.client.phone || '';
    (v.productsDiscussed || []).slice(0, 6).forEach((p, j) => {
      ws.getCell(r, 5 + j).value = p;
    });
    ws.getCell(r, 11).value = v.pobValue != null ? Number(v.pobValue) : null;
    for (let c = 1; c <= 11; c++) border(ws.getCell(r, c));
  });
  const minRows = 7;
  const padded = Math.max(rows.length, minRows);
  for (let i = rows.length; i < padded; i++) {
    const r = startRow + 2 + i;
    ws.getCell(r, 1).value = i + 1;
    for (let c = 1; c <= 11; c++) border(ws.getCell(r, c));
  }
  return startRow + 1 + padded;
}

function writeStockistsTable(
  ws: ExcelJS.Worksheet,
  rows: Array<{
    client: { name: string; address: string | null; city: string | null; phone: string | null };
    pobValue: any; paymentCollected: any; expiryAmount: any; breakageAmount: any; overdueOutstanding: any; orderValue: any;
  }>,
  startRow: number,
) {
  const h = ['', 'DISTRIBUTOR/STOCKIEST NAME', 'PLACE', 'MOBILE NO', 'POB VALUE', 'PAYMENT', 'EXPIRY', 'BRK', 'O. OS', 'ORDER VAL'];
  h.forEach((val, i) => {
    const c = ws.getCell(startRow, i + 1);
    c.value = val;
    headerFill(c);
  });

  rows.forEach((v, i) => {
    const r = startRow + 1 + i;
    ws.getCell(r, 1).value = i + 1;
    ws.getCell(r, 2).value = v.client.name;
    ws.getCell(r, 3).value = v.client.city || v.client.address || '';
    ws.getCell(r, 4).value = v.client.phone || '';
    ws.getCell(r, 5).value = v.pobValue != null ? Number(v.pobValue) : null;
    ws.getCell(r, 6).value = v.paymentCollected != null ? Number(v.paymentCollected) : null;
    ws.getCell(r, 7).value = v.expiryAmount != null ? Number(v.expiryAmount) : null;
    ws.getCell(r, 8).value = v.breakageAmount != null ? Number(v.breakageAmount) : null;
    ws.getCell(r, 9).value = v.overdueOutstanding != null ? Number(v.overdueOutstanding) : null;
    ws.getCell(r, 10).value = v.orderValue != null ? Number(v.orderValue) : null;
    for (let c = 1; c <= 10; c++) border(ws.getCell(r, c));
  });
  const minRows = 3;
  const padded = Math.max(rows.length, minRows);
  for (let i = rows.length; i < padded; i++) {
    const r = startRow + 1 + i;
    ws.getCell(r, 1).value = i + 1;
    for (let c = 1; c <= 10; c++) border(ws.getCell(r, c));
  }
  return startRow + padded;
}

function writeSummary(
  ws: ExcelJS.Worksheet,
  dayVisits: Array<{
    client: { type: string; speciality: string | null };
    pobValue: any;
  }>,
  startRow: number,
) {
  const h = ['', 'WORK SUMMERY', 'Ayush', 'BAMS', 'BHMS', 'PF', 'AF', 'OTHERS', 'CHEMIST', 'POB'];
  h.forEach((val, i) => {
    const c = ws.getCell(startRow, i + 1);
    c.value = val;
    headerFill(c);
  });

  let bams = 0, bhms = 0, others = 0, chemist = 0, pob = 0, ayush = 0;
  for (const v of dayVisits) {
    if (v.client.type === 'DOCTOR') {
      const col = doctorColumn(v.client.speciality);
      if (col.col === 'BAMS') { bams++; ayush++; }
      else if (col.col === 'BHMS') { bhms++; ayush++; }
      else others++;
    } else if (v.client.type === 'CHEMIST') {
      chemist++;
    }
    if (v.pobValue != null) pob += Number(v.pobValue);
  }

  ws.getCell(startRow + 1, 2).value = 'B/F';
  ws.getCell(startRow + 2, 2).value = 'TODAY';
  ws.getCell(startRow + 3, 2).value = 'TOTAL';
  // Today row
  ws.getCell(startRow + 2, 3).value = ayush;
  ws.getCell(startRow + 2, 4).value = bams;
  ws.getCell(startRow + 2, 5).value = bhms;
  ws.getCell(startRow + 2, 8).value = others;
  ws.getCell(startRow + 2, 9).value = chemist;
  ws.getCell(startRow + 2, 10).value = pob || null;
  // Total row (B/F unknown — equals today for now).
  ws.getCell(startRow + 3, 3).value = ayush;
  ws.getCell(startRow + 3, 4).value = bams;
  ws.getCell(startRow + 3, 5).value = bhms;
  ws.getCell(startRow + 3, 8).value = others;
  ws.getCell(startRow + 3, 9).value = chemist;
  ws.getCell(startRow + 3, 10).value = pob || null;
  for (let r = startRow + 1; r <= startRow + 3; r++) {
    for (let c = 1; c <= 10; c++) border(ws.getCell(r, c));
  }

  ws.getCell(startRow + 5, 8).value = 'Signature with date';
  ws.getCell(startRow + 5, 8).font = { italic: true };
}

export default router;
