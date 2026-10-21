/* callmony — theme tokens (light + dark), matching the web edition palette */

import { Appearance } from "react-native";
import { useColorScheme } from "react-native";

export const BRAND = "#16a085";
export const BRAND_DARK = "#2fc4a6";
export const INK = "#172b2a";
export const CANVAS = "#f7faf9";

export interface ThemeColors {
  mode: "light" | "dark";
  canvas: string;
  card: string;
  cardAlt: string;
  ink: string;
  sub: string;
  faint: string;
  line: string;
  brand: string;
  brandSoft: string;
  onBrand: string;
  green: string;
  red: string;
  amber: string;
  blue: string;
  chip: string;
  scrim: string;
  inputBg: string;
  headerBg: string;
}

export const LIGHT: ThemeColors = {
  mode: "light",
  canvas: CANVAS,
  card: "#ffffff",
  cardAlt: "#eef4f2",
  ink: INK,
  sub: "#5c6f6c",
  faint: "#8ba09c",
  line: "#e2ebe8",
  brand: BRAND,
  brandSoft: "#e0f2ee",
  onBrand: "#ffffff",
  green: "#1d9a6b",
  red: "#d64545",
  amber: "#b97d10",
  blue: "#4a7d9b",
  chip: "#e7efec",
  scrim: "rgba(9, 22, 21, 0.45)",
  inputBg: "#ffffff",
  headerBg: CANVAS,
};

export const DARK: ThemeColors = {
  mode: "dark",
  canvas: "#0e1514",
  card: "#16211f",
  cardAlt: "#1c2927",
  ink: "#e8f1ef",
  sub: "#9db4b0",
  faint: "#6b827e",
  line: "#243230",
  brand: BRAND_DARK,
  brandSoft: "#16443c",
  onBrand: "#08201c",
  green: "#3ecf94",
  red: "#ff7b72",
  amber: "#e3b341",
  blue: "#7ab3d0",
  chip: "#20302d",
  scrim: "rgba(0, 0, 0, 0.6)",
  inputBg: "#1c2927",
  headerBg: "#0e1514",
};

/** Resolve theme from the user setting ("auto" follows the OS). */
export const resolveTheme = (
  setting: "light" | "dark" | "auto"
): ThemeColors => {
  const os = Appearance.getColorScheme();
  const dark = setting === "dark" || (setting === "auto" && os === "dark");
  return dark ? DARK : LIGHT;
};

export const useThemeColors = (setting: "light" | "dark" | "auto"): ThemeColors => {
  const osScheme = useColorScheme();
  const dark = setting === "dark" || (setting === "auto" && osScheme === "dark");
  return dark ? DARK : LIGHT;
};

/* type scales — nothing smaller than 13 (readability requirement) */
export const F = {
  hero: 30,
  title: 22,
  h2: 18,
  body: 16,
  small: 14,
  tiny: 13, // absolute minimum
  keypad: 30,
};
