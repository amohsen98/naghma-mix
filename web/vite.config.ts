import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In development each API prefix goes to its own Quarkus service (same routing as nginx).
const accounts = 'http://localhost:8081';
const catalog = 'http://localhost:8082';
const mix = 'http://localhost:8083';

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/auth': accounts,
      '/api/billing': accounts,
      '/api/uploads': catalog,
      '/api/tracks': catalog,
      '/api/admin': catalog,
      '/api/mix': mix,
      '/api/sets': mix,
    },
  },
});
