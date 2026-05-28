<template>
  <div class="ds-page">
    <!-- Masthead -->
    <header class="ds-masthead">
      <div class="ds-eyebrow">No. 01 / Design System</div>
      <h1 class="ds-title">A Quiet Terminal for Loud Data.</h1>
      <span class="accent-rule"></span>
      <p class="ds-deck">
        编辑感、纸感、克制 — 数据在前，装饰在后。
        Fraunces 提供态度，General Sans 承载阅读，JetBrains Mono 守住数字。
      </p>
    </header>

    <!-- Type scale -->
    <section class="ds-section">
      <div class="ds-section-head">
        <span class="ds-eyebrow">§ 02</span>
        <h2 class="ds-h2">Typography</h2>
      </div>

      <div class="ds-type-row">
        <div class="ds-type-label">Display · Fraunces 60</div>
        <div class="ds-type-sample display" style="font-size:60px">舆情之声，铸于此处</div>
      </div>
      <div class="ds-type-row">
        <div class="ds-type-label">Display · Fraunces 44</div>
        <div class="ds-type-sample display" style="font-size:44px">A quiet terminal for loud data.</div>
      </div>
      <div class="ds-type-row">
        <div class="ds-type-label">H1 · Fraunces 34</div>
        <div class="ds-type-sample display" style="font-size:34px">小米 SU7 舆情周报</div>
      </div>
      <div class="ds-type-row">
        <div class="ds-type-label">H2 · Fraunces 26</div>
        <div class="ds-type-sample display" style="font-size:26px">竞品对标 · 本周观察</div>
      </div>
      <div class="ds-type-row">
        <div class="ds-type-label">Body · General Sans 15</div>
        <div class="ds-type-sample" style="font-size:15px;max-width:560px">
          通过对小红书、抖音、B站、微博、快手五个平台的内容采集，AI 模型自动生成了今日的情感摘要。
          The body weight is calibrated for sustained reading at 15px.
        </div>
      </div>
      <div class="ds-type-row">
        <div class="ds-type-label">Mono · JetBrains 18</div>
        <div class="ds-type-sample tabular" style="font-size:18px">12,847  ·  +8.4%  ·  0.728</div>
      </div>
      <div class="ds-type-row">
        <div class="ds-type-label">Eyebrow</div>
        <div class="ds-type-sample"><span class="eyebrow">FILED · 2026-04-28 · 09:42</span></div>
      </div>
    </section>

    <!-- Color -->
    <section class="ds-section">
      <div class="ds-section-head">
        <span class="ds-eyebrow">§ 03</span>
        <h2 class="ds-h2">Palette</h2>
      </div>

      <div class="ds-swatches">
        <div class="ds-swatch" v-for="s in swatches" :key="s.token">
          <div class="ds-chip" :style="{ background: s.value, color: s.fg || 'var(--ink)' }">
            <span class="tabular">{{ s.value }}</span>
          </div>
          <div class="ds-swatch-meta">
            <span class="tabular ds-token">{{ s.token }}</span>
            <span class="ds-purpose">{{ s.purpose }}</span>
          </div>
        </div>
      </div>
    </section>

    <!-- Spacing -->
    <section class="ds-section">
      <div class="ds-section-head">
        <span class="ds-eyebrow">§ 04</span>
        <h2 class="ds-h2">Spacing</h2>
      </div>
      <div class="ds-spacing">
        <div class="ds-spacing-row" v-for="s in spacing" :key="s.name">
          <span class="tabular ds-spacing-name">{{ s.name }}</span>
          <span class="tabular ds-spacing-px">{{ s.px }}</span>
          <span class="ds-spacing-bar" :style="{ width: s.px }"></span>
        </div>
      </div>
    </section>

    <!-- Sample card -->
    <section class="ds-section">
      <div class="ds-section-head">
        <span class="ds-eyebrow">§ 05</span>
        <h2 class="ds-h2">In situ</h2>
      </div>

      <article class="ds-article">
        <div class="ds-article-meta">
          <span class="eyebrow">舆情日报 · 猛士品牌 · 2026-04-28</span>
        </div>
        <h3 class="display ds-article-title">猛士 917 越野俱乐部首发，<br>用户讨论集中于"实用通过性"。</h3>
        <span class="accent-rule"></span>

        <div class="ds-kpis">
          <div class="ds-kpi">
            <div class="eyebrow">提及</div>
            <div class="display tabular ds-kpi-value">12,847</div>
            <div class="ds-kpi-delta positive">↑ 8.4%</div>
          </div>
          <div class="ds-kpi">
            <div class="eyebrow">情感得分</div>
            <div class="display tabular ds-kpi-value">0.728</div>
            <div class="ds-kpi-delta positive">↑ 0.04</div>
          </div>
          <div class="ds-kpi">
            <div class="eyebrow">风险事件</div>
            <div class="display tabular ds-kpi-value">3</div>
            <div class="ds-kpi-delta alert">2 待处理</div>
          </div>
          <div class="ds-kpi">
            <div class="eyebrow">互动总量</div>
            <div class="display tabular ds-kpi-value">2.41M</div>
            <div class="ds-kpi-delta">vs 上周</div>
          </div>
        </div>

        <p class="ds-article-body">
          越野通过性、底盘悬挂、车机响应是本周的三大讨论焦点。负面集中于"售后等待时间"，
          建议销售口径强调"全国 200+ 服务点，平均响应 18 小时"。
        </p>
      </article>
    </section>

    <footer class="ds-foot">
      <span class="eyebrow">— end of design system —</span>
    </footer>
  </div>
</template>

<script setup>
const swatches = [
  { token: '--bg',         value: '#fbf9f4', purpose: '页面底纸' },
  { token: '--surface',    value: '#ffffff', purpose: '卡片/抽屉' },
  { token: '--surface-hover', value: '#fdf6e8', purpose: '行 hover' },
  { token: '--ink',        value: '#171614', purpose: '主文字', fg: '#fbf9f4' },
  { token: '--ink-muted',  value: '#56524a', purpose: '次文字', fg: '#fbf9f4' },
  { token: '--hairline',   value: '#e8e3d8', purpose: '1px 描边' },
  { token: '--accent',     value: '#c0392b', purpose: '唯一强调色', fg: '#fbf9f4' },
  { token: '--positive',   value: '#2d6a4f', purpose: '正面情感', fg: '#fbf9f4' },
  { token: '--alert',      value: '#cc8b1a', purpose: '风险/警示', fg: '#fbf9f4' },
]

const spacing = [
  { name: 'space-1', px: '4px' },
  { name: 'space-2', px: '8px' },
  { name: 'space-3', px: '12px' },
  { name: 'space-4', px: '16px' },
  { name: 'space-6', px: '24px' },
  { name: 'space-8', px: '32px' },
  { name: 'space-12', px: '48px' },
  { name: 'space-16', px: '64px' },
  { name: 'space-24', px: '96px' },
]
</script>

<style scoped>
.ds-page {
  background: var(--bg);
  color: var(--ink);
  min-height: 100vh;
  padding: var(--space-16) var(--space-12);
  max-width: 1200px;
  margin: 0 auto;
}

/* Masthead */
.ds-masthead {
  border-top: 2px solid var(--ink);
  padding-top: var(--space-6);
  margin-bottom: var(--space-24);
}
.ds-eyebrow {
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wider);
  text-transform: uppercase;
  color: var(--ink-muted);
}
.ds-title {
  font-family: var(--font-display);
  font-weight: 500;
  font-size: 84px;
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
  margin-top: var(--space-3);
}
.ds-deck {
  font-size: var(--text-md);
  color: var(--ink-muted);
  max-width: 560px;
  line-height: var(--leading-loose);
}

/* Sections */
.ds-section {
  margin-bottom: var(--space-24);
}
.ds-section-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-4);
  margin-bottom: var(--space-8);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--hairline);
}
.ds-h2 {
  font-family: var(--font-display);
  font-weight: 500;
  font-size: var(--text-2xl);
  letter-spacing: var(--tracking-tight);
}

/* Type sample rows */
.ds-type-row {
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: var(--space-8);
  padding: var(--space-5) 0;
  border-bottom: 1px solid var(--hairline);
  align-items: baseline;
}
.ds-type-row:last-child { border-bottom: none; }
.ds-type-label {
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  letter-spacing: var(--tracking-wider);
  text-transform: uppercase;
  color: var(--ink-subtle);
}

/* Swatches */
.ds-swatches {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--space-6);
}
.ds-swatch {
  border: 1px solid var(--hairline);
  background: var(--surface);
}
.ds-chip {
  height: 96px;
  display: flex;
  align-items: flex-end;
  padding: var(--space-3);
  font-size: var(--text-sm);
}
.ds-swatch-meta {
  padding: var(--space-3) var(--space-4);
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: var(--space-3);
}
.ds-token {
  font-size: var(--text-xs);
  color: var(--ink);
}
.ds-purpose {
  font-size: var(--text-xs);
  color: var(--ink-muted);
}

/* Spacing */
.ds-spacing-row {
  display: grid;
  grid-template-columns: 96px 64px 1fr;
  gap: var(--space-4);
  align-items: center;
  padding: var(--space-2) 0;
}
.ds-spacing-name { font-size: var(--text-sm); color: var(--ink-muted); }
.ds-spacing-px { font-size: var(--text-sm); color: var(--ink); }
.ds-spacing-bar {
  height: 12px;
  background: var(--accent);
  display: block;
}

/* Sample article */
.ds-article {
  background: var(--surface);
  padding: var(--space-12);
  border: 1px solid var(--hairline);
}
.ds-article-meta {
  margin-bottom: var(--space-3);
}
.ds-article-title {
  font-size: var(--text-3xl);
  line-height: var(--leading-tight);
  letter-spacing: var(--tracking-tight);
  font-weight: 500;
}
.ds-kpis {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 0;
  margin: var(--space-6) 0;
  border-top: 1px solid var(--hairline);
  border-bottom: 1px solid var(--hairline);
}
.ds-kpi {
  padding: var(--space-5) var(--space-4);
  border-right: 1px solid var(--hairline);
}
.ds-kpi:last-child { border-right: none; }
.ds-kpi-value {
  font-size: var(--text-3xl);
  font-weight: 500;
  margin: var(--space-2) 0;
}
.ds-kpi-delta {
  font-size: var(--text-xs);
  color: var(--ink-muted);
  font-family: var(--font-mono);
}
.ds-kpi-delta.positive { color: var(--positive); }
.ds-kpi-delta.alert { color: var(--alert); }
.ds-article-body {
  color: var(--ink-muted);
  font-size: var(--text-base);
  line-height: var(--leading-loose);
  max-width: 560px;
}

.ds-foot {
  text-align: center;
  padding: var(--space-12) 0 var(--space-6);
  border-top: 1px solid var(--hairline);
}
</style>
