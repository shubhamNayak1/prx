-- HQ printed on DCR header
ALTER TABLE "User" ADD COLUMN "hq" TEXT;

-- Visit money columns captured at check-out so DCR has real numbers.
ALTER TABLE "Visit" ADD COLUMN "pobValue"           DECIMAL(12,2);
ALTER TABLE "Visit" ADD COLUMN "paymentCollected"   DECIMAL(12,2);
ALTER TABLE "Visit" ADD COLUMN "expiryAmount"       DECIMAL(12,2);
ALTER TABLE "Visit" ADD COLUMN "breakageAmount"     DECIMAL(12,2);
ALTER TABLE "Visit" ADD COLUMN "overdueOutstanding" DECIMAL(12,2);
ALTER TABLE "Visit" ADD COLUMN "orderValue"         DECIMAL(12,2);

-- DCR header metadata, one row per (user, date).
CREATE TABLE "DcrDay" (
    "id"           TEXT NOT NULL,
    "userId"       TEXT NOT NULL,
    "date"         DATE NOT NULL,
    "workTypeI"    TEXT,
    "workTypeII"   TEXT,
    "placeOfWork"  TEXT,
    "baseTownOnOs" TEXT,
    "routeNoInSvl" TEXT,
    "kmTravelled"  DOUBLE PRECISION,
    "dfrNo"        INTEGER,
    "createdAt"    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt"    TIMESTAMP(3) NOT NULL,
    CONSTRAINT "DcrDay_pkey" PRIMARY KEY ("id")
);

CREATE UNIQUE INDEX "DcrDay_userId_date_key" ON "DcrDay"("userId", "date");
CREATE INDEX "DcrDay_userId_date_idx" ON "DcrDay"("userId", "date");

ALTER TABLE "DcrDay"
  ADD CONSTRAINT "DcrDay_userId_fkey"
  FOREIGN KEY ("userId") REFERENCES "User"("id")
  ON DELETE RESTRICT ON UPDATE CASCADE;
