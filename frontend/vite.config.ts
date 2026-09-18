import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ command, isPreview }) => ({
  plugins: [react()],
  // `vite preview` serves the BUILD, so it must serve it at the base the build was made for. Its command is 'serve',
  // not 'build', so without isPreview it served the build at '/' while the page asked for /projectdraugr/assets/…,
  // and every script and stylesheet came back as the HTML fallback: a blank page with no error (#239).
  base: command === 'build' || isPreview ? '/projectdraugr/' : '/',
  // #243: the build manifest names each entry's static imports, dynamic imports and assets, which is what lets the
  // budget gate tell an image that ships in the initial payload from one fetched only when a scene asks for it.
  build: { manifest: true },
  server: { port: 5173 },
}));
