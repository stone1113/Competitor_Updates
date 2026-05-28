<template>
  <div class="h5-page">
    <!-- 顶部 banner — 车型卡 -->
    <header class="banner">
      <div class="banner-inner">
        <div class="banner-title">
          <span class="vs-label">技术对标矩阵</span>
          <h1>{{ selfModel || '—' }} <span class="vs-sep">vs</span> {{ competitorTitle }}</h1>
          <p class="banner-meta" v-if="comp">
            <span>{{ comp.series.length }} 款车型</span>
            <span>·</span>
            <span>{{ totalParamCount }} 参数项</span>
            <span>·</span>
            <span>数据源：汽车之家</span>
          </p>
        </div>
        <div class="banner-tabs">
          <a
            v-for="opt in selfModelOpts"
            :key="opt"
            :href="`?self=${encodeURIComponent(opt)}`"
            class="tab"
            :class="{ active: opt === selfModel }"
          >{{ opt }}</a>
        </div>
      </div>
    </header>

    <!-- 加载/空态 -->
    <div v-if="loading" class="state-msg">加载中…</div>
    <div v-else-if="!comp || comp.series.length === 0" class="state-msg empty">
      <h3>暂无对标数据</h3>
      <p>请到 <a href="/competitor/autohome-config">车系配置</a> 设置 vs_self_model 并触发抓取。</p>
    </div>

    <!-- 车型 sticky 头 -->
    <div v-else class="compare-table">
      <div class="series-row" :style="gridStyle">
        <div class="param-head">车型</div>
        <div
          v-for="s in comp.series"
          :key="s.seriesId"
          class="series-card"
          :class="{ self: s.role === 'self' }"
        >
          <div class="series-brand">{{ s.brandName }}</div>
          <div class="series-model">{{ s.modelName }}</div>
          <div class="series-spec">{{ s.specName || '—' }}</div>
          <a
            :href="`https://car.autohome.com.cn/config/series/${s.seriesId}.html`"
            target="_blank"
            rel="noopener"
            class="series-link"
          >汽车之家 ↗</a>
        </div>
      </div>

      <!-- 参数类别块 -->
      <section v-for="cg in comp.categories" :key="cg.category" class="cat-block">
        <h3 class="cat-title">{{ cg.category || '其他' }}</h3>
        <div v-for="row in cg.params" :key="row.name" class="param-row" :style="gridStyle">
          <div class="param-name">{{ row.name }}</div>
          <div
            v-for="s in comp.series"
            :key="s.seriesId"
            class="param-cell"
            :class="{ best: isBest(row, s.seriesId), 'self-cell': s.role === 'self' }"
          >
            <span v-if="row.values[s.seriesId]">{{ row.values[s.seriesId] }}</span>
            <span v-else class="dash">—</span>
          </div>
        </div>
      </section>

      <p class="footer-note">
        参数来源：汽车之家公开配置页。车款代表：每车系取最新车款（spec_id 字典序最大）。
        数值类参数按「越大越好」（功率/续航/扭矩/空间/接近角）或「越小越好」（百公里加速/油耗/能耗/质量）自动高亮。
      </p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { benchmarkBySelf } from '../../api/autohome'

const route = useRoute()
const selfModel = ref('')
const comp = ref(null)
const loading = ref(false)
const selfModelOpts = ['猛士M817', '猛士917']

const competitorTitle = computed(() => {
  if (!comp.value) return ''
  return comp.value.series
    .filter(s => s.role !== 'self')
    .map(s => s.modelName)
    .join(' / ') || '—'
})

const totalParamCount = computed(() => {
  if (!comp.value) return 0
  return comp.value.categories.reduce((sum, cg) => sum + cg.params.length, 0)
})

const gridStyle = computed(() => {
  const cols = comp.value ? comp.value.series.length : 0
  return { 'grid-template-columns': `180px repeat(${cols}, minmax(160px, 1fr))` }
})

async function load() {
  if (!selfModel.value) return
  loading.value = true
  try {
    comp.value = await benchmarkBySelf(selfModel.value)
  } catch (e) {
    comp.value = null
  } finally {
    loading.value = false
  }
}

function isBest(row, sid) {
  const v = row.values[sid]
  if (!v) return false
  const nums = Object.entries(row.values)
    .map(([k, val]) => [k, parseNumeric(val)])
    .filter(([_, n]) => n != null)
  if (nums.length < 2) return false
  const myNum = parseNumeric(v)
  if (myNum == null) return false
  const name = row.name || ''
  const lowerBetter = /加速时间|油耗|能耗|质量|百公里电耗/.test(name)
  const best = lowerBetter
    ? Math.min(...nums.map(n => n[1]))
    : Math.max(...nums.map(n => n[1]))
  return Math.abs(myNum - best) < 1e-6
}

function parseNumeric(s) {
  if (s == null) return null
  const m = String(s).match(/-?\d+(\.\d+)?/)
  return m ? parseFloat(m[0]) : null
}

watch(() => route.query.self, (newSelf) => {
  selfModel.value = newSelf || '猛士M817'
  load()
}, { immediate: true })

onMounted(() => {
  document.title = `技术对标 · ${selfModel.value} · 猛士舆情系统`
})
</script>

<style scoped>
.h5-page {
  background: var(--bg);
  min-height: 100vh;
  font-family: var(--font-body);
  color: var(--ink);
}

/* ====== Banner ====== */
.banner {
  background: linear-gradient(135deg, #1a1614 0%, #2a221d 100%);
  color: #fbf9f4;
  padding: var(--space-6) var(--space-6) var(--space-4);
}
.banner-inner {
  max-width: 1480px;
  margin: 0 auto;
}
.vs-label {
  display: inline-block;
  background: var(--accent);
  color: #fbf9f4;
  padding: 2px 10px;
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  letter-spacing: 0.05em;
  margin-bottom: var(--space-2);
}
.banner h1 {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 28px;
  line-height: 1.2;
  margin: 0 0 var(--space-2);
  letter-spacing: var(--tracking-tight);
}
.vs-sep {
  color: var(--accent);
  font-style: italic;
  font-weight: 400;
  margin: 0 6px;
}
.banner-meta {
  margin: 0;
  opacity: 0.7;
  font-size: var(--text-sm);
}
.banner-meta span { margin-right: 8px; }
.banner-tabs {
  margin-top: var(--space-4);
  display: flex;
  gap: var(--space-2);
}
.tab {
  display: inline-block;
  padding: 6px 14px;
  background: rgba(255,255,255,0.08);
  color: rgba(255,255,255,0.7);
  text-decoration: none;
  font-size: var(--text-sm);
  border-radius: 2px;
  font-weight: 500;
}
.tab:hover { background: rgba(255,255,255,0.14); color: white; }
.tab.active {
  background: var(--accent);
  color: white;
}

/* ====== 状态 ====== */
.state-msg {
  padding: var(--space-10) var(--space-6);
  text-align: center;
  color: var(--ink-muted);
}
.state-msg.empty h3 {
  font-family: var(--font-display);
  font-size: var(--text-xl);
  color: var(--ink);
  margin-bottom: var(--space-3);
}
.state-msg a { color: var(--accent); }

/* ====== Compare table ====== */
.compare-table {
  max-width: 1480px;
  margin: 0 auto;
  padding: 0 var(--space-6) var(--space-10);
}

/* sticky 车型行 */
.series-row {
  position: sticky;
  top: 0;
  z-index: 5;
  background: var(--bg-elevated);
  display: grid;
  border-bottom: 2px solid var(--ink);
}
.param-head {
  padding: var(--space-3) var(--space-3);
  font-family: var(--font-display);
  font-weight: 600;
  font-size: var(--text-sm);
  color: var(--ink-muted);
  border-right: 1px solid var(--hairline);
}
.series-card {
  padding: var(--space-3) var(--space-3);
  border-right: 1px solid var(--hairline);
  background: var(--surface);
}
.series-card:last-child { border-right: none; }
.series-card.self {
  background: var(--accent-soft);
}
.series-brand {
  font-size: var(--text-xs);
  color: var(--ink-subtle);
}
.series-model {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: var(--text-md);
  color: var(--ink);
  margin: 2px 0;
}
.series-spec {
  font-size: var(--text-xs);
  color: var(--ink-muted);
  line-height: 1.4;
}
.series-link {
  display: inline-block;
  margin-top: 6px;
  font-size: 11px;
  color: var(--accent);
  text-decoration: none;
}
.series-link:hover { text-decoration: underline; }

/* 参数块 */
.cat-block {
  margin-top: var(--space-5);
}
.cat-title {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: var(--text-lg);
  letter-spacing: var(--tracking-tight);
  color: var(--ink);
  margin: 0 0 var(--space-2);
  padding: var(--space-2) 0;
  border-bottom: 1px solid var(--hairline);
}
.param-row {
  display: grid;
  border-bottom: 1px solid var(--hairline);
  font-size: var(--text-sm);
}
.param-row:hover { background: var(--surface-sunk); }
.param-name {
  padding: var(--space-3) var(--space-3);
  color: var(--ink-muted);
  font-weight: 500;
  border-right: 1px solid var(--hairline);
}
.param-cell {
  padding: var(--space-3) var(--space-3);
  border-right: 1px solid var(--hairline);
  color: var(--ink);
}
.param-cell:last-child { border-right: none; }
.param-cell.self-cell {
  background: rgba(192, 57, 43, 0.04);
}
.param-cell.best {
  color: var(--accent);
  font-weight: var(--weight-semibold);
}
.dash { color: var(--ink-subtle); }

/* ====== Footer ====== */
.footer-note {
  margin-top: var(--space-6);
  font-size: var(--text-xs);
  color: var(--ink-subtle);
  line-height: 1.6;
  padding-top: var(--space-3);
  border-top: 1px solid var(--hairline);
}

/* ====== Responsive ====== */
@media (max-width: 768px) {
  .banner { padding: var(--space-4); }
  .banner h1 { font-size: 22px; }
  .compare-table {
    padding: 0 var(--space-2) var(--space-6);
    overflow-x: auto;
  }
  .series-row, .param-row {
    min-width: 720px;
  }
}
</style>
