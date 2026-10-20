-- AlterEnum
ALTER TYPE "OtpPurpose" ADD VALUE 'LOGIN';
ALTER TYPE "OtpPurpose" ADD VALUE 'RESET_PIN';

-- AlterTable
ALTER TABLE "User" ADD COLUMN "pinHash" TEXT;
ALTER TABLE "User" ADD COLUMN "pinSetAt" TIMESTAMP(3);

-- CreateIndex (unique on phone — safe here because seeded users have distinct phones)
CREATE UNIQUE INDEX "User_phone_key" ON "User"("phone");
