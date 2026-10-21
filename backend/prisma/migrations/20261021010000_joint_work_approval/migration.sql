-- Approval column for joint-work claims. null = pending, true = approved, false = rejected.
ALTER TABLE "Visit" ADD COLUMN "jointWorkApproved" BOOLEAN;
