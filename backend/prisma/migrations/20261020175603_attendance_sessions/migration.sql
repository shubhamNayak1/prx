/*
  Warnings:

  - Made the column `punchInAt` on table `Attendance` required. This step will fail if there are existing NULL values in that column.

*/
-- DropIndex
DROP INDEX "Attendance_userId_date_key";

-- AlterTable
ALTER TABLE "Attendance" ADD COLUMN     "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
ALTER COLUMN "punchInAt" SET NOT NULL;

-- CreateIndex
CREATE INDEX "Attendance_userId_date_idx" ON "Attendance"("userId", "date");
