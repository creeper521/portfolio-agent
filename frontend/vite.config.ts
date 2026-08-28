import { configDefaults, defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    css: true,
    // Playwright specs are not Vitest tests; e2e/support/*.test.ts remains
    // directly executable so Browser body assertions have a deterministic
    // negative-injection matrix without starting a browser or Provider.
    exclude: [...configDefaults.exclude, 'e2e/**/*.spec.ts'],
    setupFiles: ['./src/test/setup.ts'],
  },
})
