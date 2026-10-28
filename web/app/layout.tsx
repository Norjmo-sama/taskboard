import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Task Board',
  description: 'Shared Firestore task board',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
