var _a, _b, _c, _d, _e;
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
var frontendPort = Number((_b = (_a = process.env.PORT) !== null && _a !== void 0 ? _a : process.env.FRONTEND_PORT) !== null && _b !== void 0 ? _b : 4175);
var backendPort = (_d = (_c = process.env.BACKEND_PORT) !== null && _c !== void 0 ? _c : process.env.SHOPIFY_BRIDGE_BACKEND_PORT) !== null && _d !== void 0 ? _d : '8080';
var backendTarget = (_e = process.env.SHOPIFY_BRIDGE_PROXY_TARGET) !== null && _e !== void 0 ? _e : "http://127.0.0.1:".concat(backendPort);
export default defineConfig({
    plugins: [react()],
    server: {
        host: '0.0.0.0',
        port: frontendPort,
        proxy: {
            '/api': backendTarget,
            '/auth': backendTarget,
        },
    },
});
