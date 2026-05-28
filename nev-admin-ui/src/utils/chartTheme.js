/**
 * Editorial / FT-like chart theme tokens for ECharts.
 * Reads from CSS variables at runtime so chart colors stay in sync with design tokens.
 */

function cssVar(name, fallback) {
  if (typeof window === 'undefined') return fallback
  const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return v || fallback
}

export function chartPalette() {
  return {
    ink: cssVar('--ink', '#171614'),
    inkMuted: cssVar('--ink-muted', '#56524a'),
    inkSubtle: cssVar('--ink-subtle', '#8a857b'),
    hairline: cssVar('--hairline', '#e8e3d8'),
    accent: cssVar('--accent', '#c0392b'),
    positive: cssVar('--positive', '#2d6a4f'),
    alert: cssVar('--alert', '#cc8b1a'),
    surface: cssVar('--surface', '#ffffff'),
    bg: cssVar('--bg', '#fbf9f4'),
    fontBody: "'General Sans', -apple-system, sans-serif",
    fontMono: "'JetBrains Mono', monospace",
    fontDisplay: "'Fraunces', Georgia, serif"
  }
}

export function baseAxisStyle() {
  const p = chartPalette()
  return {
    axisLine: { lineStyle: { color: p.hairline } },
    axisTick: { lineStyle: { color: p.hairline } },
    axisLabel: {
      color: p.inkMuted,
      fontSize: 11,
      fontFamily: p.fontMono
    },
    splitLine: { lineStyle: { color: p.hairline, type: 'dashed' } }
  }
}

export function baseTooltip() {
  const p = chartPalette()
  return {
    backgroundColor: p.surface,
    borderColor: p.ink,
    borderWidth: 1,
    textStyle: { color: p.ink, fontFamily: p.fontBody, fontSize: 12 },
    extraCssText: 'box-shadow: 0 4px 12px -6px rgba(23,22,20,0.08); border-radius: 0;'
  }
}

/** Sentiment categories palette ordered very-positive → very-negative */
export function sentimentPalette() {
  const p = chartPalette()
  return {
    veryPositive: p.positive,
    positive:     '#5b8a73',
    neutral:      p.inkSubtle,
    negative:     '#cc6e5d',
    veryNegative: p.accent
  }
}
