// Lightweight date helpers — no external lib

export function pad(n) { return String(n).padStart(2, '0') }

export function ymd(date) {
  if (!date) return ''
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function parseYmd(str) {
  if (!str) return null
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(str)
  if (!m) return null
  return new Date(+m[1], +m[2] - 1, +m[3])
}

export function isSameDay(a, b) {
  if (!a || !b) return false
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()
}

export function inRange(d, start, end) {
  if (!start || !end || !d) return false
  const t = d.getTime()
  return t >= start.getTime() && t <= end.getTime()
}

export function monthMatrix(year, month) {
  // Returns array of weeks, each week is array of {date, currentMonth}
  const first = new Date(year, month, 1)
  const offset = first.getDay() // 0..6 (Sun..Sat); we use Mon-first below
  const startOffset = (offset + 6) % 7 // shift so Monday=0
  const start = new Date(year, month, 1 - startOffset)
  const weeks = []
  for (let w = 0; w < 6; w++) {
    const week = []
    for (let d = 0; d < 7; d++) {
      const dt = new Date(start.getFullYear(), start.getMonth(), start.getDate() + w * 7 + d)
      week.push({ date: dt, currentMonth: dt.getMonth() === month })
    }
    weeks.push(week)
  }
  return weeks
}

export const WEEK_LABELS_CN = ['一', '二', '三', '四', '五', '六', '日']

export function monthLabel(year, month) {
  return `${year} · ${pad(month + 1)}月`
}
