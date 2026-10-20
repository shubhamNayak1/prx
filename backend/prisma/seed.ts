import { PrismaClient, Role, ClientType, ExpenseType, ExpenseStatus, TourPlanStatus } from '@prisma/client';
import bcrypt from 'bcryptjs';

const prisma = new PrismaClient();

async function main() {
  const company = await prisma.company.upsert({
    where: { id: 'seed-company' },
    update: {},
    create: { id: 'seed-company', name: 'Baseras Demo Pharma' },
  });

  const hash = (pw: string) => bcrypt.hashSync(pw, 10);
  const PIN = '1234';
  const PIN_HASH = hash(PIN);

  const admin = await prisma.user.upsert({
    where: { email: 'admin@baseras.test' },
    update: { phone: '9000000001', pinHash: PIN_HASH, pinSetAt: new Date() },
    create: {
      companyId: company.id,
      email: 'admin@baseras.test',
      phone: '9000000001',
      passwordHash: hash('admin123'),
      pinHash: PIN_HASH,
      pinSetAt: new Date(),
      name: 'Baseras Admin',
      role: Role.ADMIN,
      employeeCode: 'ADM001',
    },
  });

  const rsm = await prisma.user.upsert({
    where: { email: 'rsm.north@baseras.test' },
    update: { phone: '9000000002', pinHash: PIN_HASH, pinSetAt: new Date() },
    create: {
      companyId: company.id,
      email: 'rsm.north@baseras.test',
      phone: '9000000002',
      passwordHash: hash('manager123'),
      pinHash: PIN_HASH,
      pinSetAt: new Date(),
      name: 'Rajesh Kumar',
      role: Role.SENIOR_MANAGER,
      grade: 'RSM',
      managerId: admin.id,
      employeeCode: 'RSM001',
    },
  });

  const asm = await prisma.user.upsert({
    where: { email: 'asm.delhi@baseras.test' },
    update: { phone: '9000000003', pinHash: PIN_HASH, pinSetAt: new Date() },
    create: {
      companyId: company.id,
      email: 'asm.delhi@baseras.test',
      phone: '9000000003',
      passwordHash: hash('manager123'),
      pinHash: PIN_HASH,
      pinSetAt: new Date(),
      name: 'Suresh Sharma',
      role: Role.REGIONAL_MANAGER,
      grade: 'ASM',
      managerId: rsm.id,
      employeeCode: 'ASM001',
    },
  });

  const mrSeeds = [
    { email: 'mr.amit@baseras.test',  phone: '9000000011', name: 'Amit Singh',  code: 'MR001' },
    { email: 'mr.priya@baseras.test', phone: '9000000012', name: 'Priya Patel', code: 'MR002' },
    { email: 'mr.ravi@baseras.test',  phone: '9000000013', name: 'Ravi Verma',  code: 'MR003' },
    { email: 'mr.neha@baseras.test',  phone: '9000000014', name: 'Neha Gupta',  code: 'MR004' },
  ];
  const mrs = await Promise.all(mrSeeds.map((m) =>
    prisma.user.upsert({
      where: { email: m.email },
      update: { phone: m.phone, pinHash: PIN_HASH, pinSetAt: new Date() },
      create: {
        companyId: company.id,
        email: m.email,
        phone: m.phone,
        passwordHash: hash('mr123'),
        pinHash: PIN_HASH,
        pinSetAt: new Date(),
        name: m.name,
        role: Role.FIELD_PERSON,
        grade: 'MR1',
        managerId: asm.id,
        employeeCode: m.code,
      },
    })
  ));

  // Expense policies
  const policies = [
    { grade: 'MR1', taRatePerKm: 4, daFlatRate: 250 },
    { grade: 'ASM', taRatePerKm: 6, daFlatRate: 400 },
    { grade: 'RSM', taRatePerKm: 8, daFlatRate: 600 },
  ];
  for (const p of policies) {
    await prisma.expensePolicy.upsert({
      where: { companyId_grade: { companyId: company.id, grade: p.grade } },
      update: {},
      create: { companyId: company.id, ...p },
    });
  }

  // Demo clients (only insert if none exist for this company)
  const existingClients = await prisma.client.count({ where: { companyId: company.id } });
  if (existingClients === 0) {
    await prisma.client.createMany({
      data: [
        { companyId: company.id, name: 'Dr. Anand Mehta', type: ClientType.DOCTOR, speciality: 'Cardiology', city: 'Mumbai', phone: '+91-9820000001' },
        { companyId: company.id, name: 'Dr. Sneha Iyer', type: ClientType.DOCTOR, speciality: 'Pediatrics', city: 'Mumbai', phone: '+91-9820000002' },
        { companyId: company.id, name: 'Dr. Rajiv Khanna', type: ClientType.DOCTOR, speciality: 'Orthopedics', city: 'Pune', phone: '+91-9820000003' },
        { companyId: company.id, name: 'Apollo Pharmacy Bandra', type: ClientType.CHEMIST, city: 'Mumbai', phone: '+91-2226670001' },
        { companyId: company.id, name: 'Wellness Pharmacy', type: ClientType.CHEMIST, city: 'Pune', phone: '+91-2025550001' },
        { companyId: company.id, name: 'MedPlus Stockist Hub', type: ClientType.STOCKIST, city: 'Mumbai' },
        { companyId: company.id, name: 'Lilavati Hospital', type: ClientType.HOSPITAL, city: 'Mumbai' },
      ],
    });
  }

  // Sample products + issuance to first MR
  const product1 = await prisma.sampleProduct.upsert({
    where: { id: 'seed-product-1' },
    update: {},
    create: { id: 'seed-product-1', companyId: company.id, name: 'Baseraz 200mg Tablets', unitType: 'strip' },
  });
  const product2 = await prisma.sampleProduct.upsert({
    where: { id: 'seed-product-2' },
    update: {},
    create: { id: 'seed-product-2', companyId: company.id, name: 'Pharma-branded notepad', unitType: 'piece', isGift: true },
  });

  const existingIssues = await prisma.sampleIssue.count({ where: { userId: mrs[0].id } });
  if (existingIssues === 0) {
    await prisma.sampleIssue.createMany({
      data: [
        { productId: product1.id, userId: mrs[0].id, quantity: 50 },
        { productId: product2.id, userId: mrs[0].id, quantity: 20 },
      ],
    });
  }

  // E-detail demo deck (uses placeholder image URLs — replace with real S3 URLs in prod)
  const existingDecks = await prisma.edetailDeck.count({ where: { companyId: company.id } });
  if (existingDecks === 0) {
    const deck = await prisma.edetailDeck.create({
      data: {
        companyId: company.id,
        name: 'Baseraz 200mg — Cardiology',
        product: 'Baseraz 200mg',
      },
    });
    await prisma.edetailSlide.createMany({
      data: [
        { deckId: deck.id, order: 1, title: 'Mechanism of action', imageUrl: 'https://placehold.co/1080x1920/0F766E/FFFFFF/png?text=Slide+1%5CnMechanism' },
        { deckId: deck.id, order: 2, title: 'Clinical efficacy', imageUrl: 'https://placehold.co/1080x1920/14B8A6/FFFFFF/png?text=Slide+2%5CnEfficacy' },
        { deckId: deck.id, order: 3, title: 'Dosage & safety', imageUrl: 'https://placehold.co/1080x1920/134E4A/FFFFFF/png?text=Slide+3%5CnDosage' },
      ],
    });
  }

  console.log('Seeded:');
  console.log('  Admin    →  phone 9000000001 · pin 1234  (email admin@baseras.test)');
  console.log('  Senior   →  phone 9000000002 · pin 1234  (Rajesh Kumar)');
  console.log('  Regional →  phone 9000000003 · pin 1234  (Suresh Sharma)');
  console.log('  Field 1  →  phone 9000000011 · pin 1234  (Amit)');
  console.log('  Field 2  →  phone 9000000012 · pin 1234  (Priya)');
  console.log('  Field 3  →  phone 9000000013 · pin 1234  (Ravi)');
  console.log('  Field 4  →  phone 9000000014 · pin 1234  (Neha)');
  console.log('  + clients, sample products, demo e-detail deck');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(() => prisma.$disconnect());
