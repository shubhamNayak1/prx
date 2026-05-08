import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./app/**/*.{ts,tsx}', './components/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          DEFAULT: '#0F766E', // teal-700 — clinical & professional
          light: '#14B8A6',
          dark: '#134E4A',
        },
      },
    },
  },
  plugins: [],
};

export default config;
