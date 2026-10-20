/* Generates the dial. PWA icon set from an SVG master (teal brand). */
import sharp from "sharp";
import { mkdirSync } from "fs";

const OUT = "public/icons";
mkdirSync(OUT, { recursive: true });

const PHONE_PATH =
  "M22 16.92v3a2 2 0 0 1-2.18 2 19.8 19.8 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.12 4.18 2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72c.12.96.36 1.9.7 2.8a2 2 0 0 1-.45 2.11L8.1 9.9a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45c.9.34 1.84.58 2.8.7A2 2 0 0 1 22 16.92z";

/** standard icon: rounded-square teal gradient + white phone glyph */
const iconSvg = (size: number, glyphScale = 0.52, rounded = true) => {
  const r = rounded ? Math.round(size * 0.22) : 0;
  const g = size * glyphScale;
  const off = (size - g) / 2;
  return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="t" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#18b394"/>
      <stop offset="1" stop-color="#128d76"/>
    </linearGradient>
  </defs>
  <rect width="${size}" height="${size}" rx="${r}" fill="url(#t)"/>
  <circle cx="${size * 0.82}" cy="${size * 0.2}" r="${size * 0.05}" fill="#9fe8d8"/>
  <g transform="translate(${off} ${off}) scale(${g / 24})">
    <path d="${PHONE_PATH}" fill="none" stroke="#ffffff" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"/>
  </g>
</svg>`;
};

/** maskable: full-bleed solid, glyph inside the 80% safe zone */
const maskableSvg = (size: number) => {
  const g = size * 0.44;
  const off = (size - g) / 2;
  return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">
  <rect width="${size}" height="${size}" fill="#16a085"/>
  <g transform="translate(${off} ${off}) scale(${g / 24})">
    <path d="${PHONE_PATH}" fill="none" stroke="#ffffff" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"/>
  </g>
</svg>`;
};

const appleSvg = (size: number) => {
  const g = size * 0.52;
  const off = (size - g) / 2;
  return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">
  <rect width="${size}" height="${size}" fill="#16a085"/>
  <g transform="translate(${off} ${off}) scale(${g / 24})">
    <path d="${PHONE_PATH}" fill="none" stroke="#ffffff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  </g>
</svg>`;
};

const jobs: [string, Buffer][] = [
  [`${OUT}/icon-192.png`, Buffer.from(iconSvg(192))],
  [`${OUT}/icon-512.png`, Buffer.from(iconSvg(512))],
  [`${OUT}/icon-384.png`, Buffer.from(iconSvg(384))],
  [`${OUT}/maskable-512.png`, Buffer.from(maskableSvg(512))],
  [`${OUT}/apple-touch-icon.png`, Buffer.from(appleSvg(180))],
  [`${OUT}/favicon.svg`, Buffer.from(iconSvg(64, 0.55))],
];

for (const [file, svg] of jobs) {
  await sharp(svg).png({ compressionLevel: 9 }).toFile(file);
  console.log("✓", file);
}
