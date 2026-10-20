/** next.config.mjs — plain JS on purpose (cPanel runtime has no build step). */
const basePath = process.env.BASE_PATH || ''
const nextConfig = {
  basePath,
  output: undefined, // NOT 'standalone' — Passenger runs app.js itself
  compress: true,
  reactStrictMode: false,
  typescript: { ignoreBuildErrors: true },
  // Build into a side dir so `next build` never clobbers the running dev server's .next/
  distDir: process.env.NEXT_DIST_DIR || '.next',
}
export default nextConfig
