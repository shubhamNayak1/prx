import './globals.css';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Baseras FieldPharma — Admin',
  description: 'Field-force management admin panel',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
