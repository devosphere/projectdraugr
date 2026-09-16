import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ command }) => ({
  plugins: [react()],
  base: command === 'build' ? '/projectdraugr/' : '/',
  // #243: the build manifest names each entry's static imports, dynamic imports and assets, which is what lets the
  // budget gate tell an image that ships in the initial payload from one fetched only when a scene asks for it.
  build: { manifest: true },
  server: { port: 5173 },
}));
