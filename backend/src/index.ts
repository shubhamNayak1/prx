import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import authRoutes from './routes/auth';
import userRoutes from './routes/users';
import attendanceRoutes from './routes/attendance';
import uploadRoutes, { UPLOAD_DIR } from './routes/uploads';
import clientRoutes from './routes/clients';
import tourPlanRoutes from './routes/tour-plans';
import visitRoutes from './routes/visits';
import expenseRoutes from './routes/expenses';
import expensePolicyRoutes from './routes/expense-policies';
import sampleRoutes from './routes/samples';
import edetailRoutes from './routes/edetail';
import rcpaRoutes from './routes/rcpa';
import dashboardRoutes from './routes/dashboard';

const app = express();

app.use(cors({ origin: process.env.CORS_ORIGIN || '*', credentials: true }));
app.use(express.json({ limit: '5mb' }));

app.use('/uploads', express.static(UPLOAD_DIR, { maxAge: '7d' }));

app.get('/health', (_req, res) => res.json({ ok: true, service: 'fieldpharma-api' }));

app.use('/api/auth', authRoutes);
app.use('/api/users', userRoutes);
app.use('/api/attendance', attendanceRoutes);
app.use('/api/uploads', uploadRoutes);
app.use('/api/clients', clientRoutes);
app.use('/api/tour-plans', tourPlanRoutes);
app.use('/api/visits', visitRoutes);
app.use('/api/expenses', expenseRoutes);
app.use('/api/expense-policies', expensePolicyRoutes);
app.use('/api/samples', sampleRoutes);
app.use('/api/edetail', edetailRoutes);
app.use('/api/rcpa', rcpaRoutes);
app.use('/api/dashboard', dashboardRoutes);

const port = Number(process.env.PORT) || 4000;
app.listen(port, () => {
  console.log(`FieldPharma API listening on http://localhost:${port}`);
});
