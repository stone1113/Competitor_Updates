<template>
  <div class="dr-page">
    <PageMast title="舆情日报">
      <template #controls>
        <UDatePicker v-model="selectedDate" />
        <USelect v-model="brand" :options="brandOpts" style="width:120px" />
        <UButton variant="ghost" size="sm" @click="loadData">刷新</UButton>
      </template>
    </PageMast>

    <p class="dr-deck" v-if="leadDeck">{{ leadDeck }}</p>

    <section class="dr-section">
      <div class="dr-section-head">
        <h2 class="display dr-section-h2">关键指标</h2>
      </div>

      <div class="dr-numbers">
        <div class="dr-num" v-for="k in kpiCards" :key="k.label">
          <div class="dr-num-label eyebrow">{{ k.label }}</div>
          <div class="dr-num-value display tabular" :class="`v-${k.tone}`">{{ k.value }}</div>
          <div class="dr-num-delta" :class="`v-${k.tone}`" v-if="k.delta">{{ k.delta }}</div>
        </div>
      </div>
    </section>

    <section class="dr-section">
      <div class="dr-section-head">
        <h2 class="display dr-section-h2">情感与声量</h2>
      </div>

      <div class="dr-charts">
        <UCard flush>
          <template #header>
            <div class="dr-card-head">
              <h3 class="display dr-card-title">{{ trendDays }} 天趋势</h3>
              <div class="dr-period-toggle">
                <button
                  v-for="p in [7, 30]"
                  :key="p"
                  class="dr-period-btn"
                  :class="{ active: trendDays === p }"
                  @click="setTrendDays(p)"
                >{{ p }}天</button>
              </div>
            </div>
          </template>
          <div class="dr-chart-pad">
            <v-chart :option="trendChartOption" style="height: 320px" autoresize />
          </div>
        </UCard>

        <UCard flush>
          <template #header>
            <h3 class="display dr-card-title">情感分布</h3>
          </template>
          <div class="dr-chart-pad">
            <v-chart :option="pieChartOption" style="height: 320px" autoresize />
          </div>
        </UCard>
      </div>
    </section>

    <section class="dr-section">
      <div class="dr-section-head">
        <h2 class="display dr-section-h2">本期专题</h2>
      </div>

      <UTabs v-model="activeTab">
        <UTabPane name="news" label="核心资讯">
          <div v-if="!topNews.length" class="dr-empty">— 暂无资讯 —</div>
          <article v-for="(item, i) in topNews" :key="item.title" class="dr-news">
            <span class="dr-news-num tabular">{{ String(i + 1).padStart(2, '0') }}</span>
            <div class="dr-news-body">
              <a :href="item.url" target="_blank" rel="noopener" class="dr-news-title display">{{ item.title }}</a>
              <div class="dr-news-byline">
                <span class="dr-news-meta">{{ item.brandName }}</span>
                <span class="dr-news-meta">·</span>
                <span class="dr-news-meta tabular">{{ item.publishedDate || '日期未知' }}</span>
              </div>
            </div>
          </article>
        </UTabPane>

        <UTabPane name="buzz" label="热议话题">
          <UCard flush>
            <UTable :data="topBuzz" stripe row-clickable @row-click="openBuzz">
              <UTableColumn prop="platformName" label="平台" :width="80">
                <template #default="{ row }">
                  <UTag variant="default">{{ row.platformName }}</UTag>
                </template>
              </UTableColumn>
              <UTableColumn prop="nickname" label="作者" :width="140" />
              <UTableColumn prop="title" label="标题" min-width="280" />
              <UTableColumn label="互动量" :width="120" align="right">
                <template #default="{ row }">
                  <span class="tabular">{{ Number(row.engagementScore || 0).toLocaleString() }}</span>
                </template>
              </UTableColumn>
            </UTable>
          </UCard>
        </UTabPane>

        <UTabPane name="competitor" label="竞品动态">
          <div class="dr-empty">竞品动态数据将在日报生成后展示</div>
        </UTabPane>

        <UTabPane name="risk" label="风险预警">
          <div class="dr-empty">风险预警数据将在日报生成后展示</div>
        </UTabPane>

        <UTabPane name="pitch" label="销售话术">
          <div class="dr-empty">销售话术将在日报生成后展示</div>
        </UTabPane>
      </UTabs>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import { TooltipComponent, GridComponent, LegendComponent } from 'echarts/components'
import { getBriefingOverview, getBriefingTrend } from '../../api/briefing'
import { getTopNews, getTopBuzz } from '../../api/dailyReport'
import PageMast from '../../components/layout/PageMast.vue'
import {
  UDatePicker, USelect, UButton, UCard, UTabs, UTabPane,
  UTable, UTableColumn, UTag, toast
} from '../../components/ui'
import { chartPalette, baseAxisStyle, baseTooltip, sentimentPalette } from '../../utils/chartTheme'

use([CanvasRenderer, LineChart, BarChart, PieChart, TooltipComponent, GridComponent, LegendComponent])

const today = new Date().toISOString().slice(0, 10)
const selectedDate = ref(today)
const brand = ref('猛士')
const trendDays = ref(7)
const activeTab = ref('news')

const overview = ref({})
const trendData = ref([])
const topNews = ref([])
const topBuzz = ref([])

const brandOpts = [
  { label: '猛士', value: '猛士' },
  { label: '坦克', value: '坦克' },
  { label: '方程豹', value: '方程豹' }
]

const leadDeck = computed(() => {
  const o = overview.value || {}
  if (o.totalMentions == null) return ''
  const pos = o.positiveRatio != null ? (o.positiveRatio * 100).toFixed(1) + '%' : '—'
  const neg = o.negativeRatio != null ? (o.negativeRatio * 100).toFixed(1) + '%' : '—'
  return `${brand.value} · 今日采集 ${o.totalMentions.toLocaleString()} 条；正面 ${pos}，负面 ${neg}。`
})

const kpiCards = computed(() => {
  const o = overview.value || {}
  const statusToneMap = { green: 'positive', yellow: 'alert', red: 'accent' }
  return [
    {
      label: '态势等级',
      value: o.statusLevel === 'green' ? '平稳' : o.statusLevel === 'yellow' ? '关注' : o.statusLevel === 'red' ? '预警' : '—',
      tone: statusToneMap[o.statusLevel] || 'ink'
    },
    {
      label: '正面占比',
      value: o.positiveRatio != null ? (o.positiveRatio * 100).toFixed(1) + '%' : '—',
      tone: 'positive'
    },
    {
      label: '负面占比',
      value: o.negativeRatio != null ? (o.negativeRatio * 100).toFixed(1) + '%' : '—',
      tone: 'accent'
    },
    {
      label: '情感健康度',
      value: o.avgSentimentScore != null ? o.avgSentimentScore.toFixed(2) : '—',
      delta: '/ 5.0',
      tone: 'ink'
    },
    {
      label: '总声量',
      value: o.totalMentions != null ? Number(o.totalMentions).toLocaleString() : '—',
      tone: 'ink'
    },
    {
      label: '风险预警',
      value: o.riskAlertCount != null ? o.riskAlertCount : '—',
      tone: o.riskAlertCount > 0 ? 'accent' : 'positive'
    }
  ]
})

const trendChartOption = computed(() => {
  const p = chartPalette()
  return {
    tooltip: { trigger: 'axis', ...baseTooltip() },
    legend: {
      data: ['情感健康度', '总声量'],
      bottom: 0,
      textStyle: { color: p.inkMuted, fontFamily: p.fontBody, fontSize: 11 },
      itemWidth: 14, itemHeight: 2
    },
    grid: { left: 50, right: 50, top: 20, bottom: 40, containLabel: true },
    xAxis: { type: 'category', data: trendData.value.map(pt => pt.date), ...baseAxisStyle() },
    yAxis: [
      { type: 'value', name: '健康度', min: 1, max: 5, ...baseAxisStyle(),
        nameTextStyle: { color: p.inkSubtle, fontFamily: p.fontBody, fontSize: 10 } },
      { type: 'value', name: '声量', ...baseAxisStyle(),
        nameTextStyle: { color: p.inkSubtle, fontFamily: p.fontBody, fontSize: 10 } }
    ],
    series: [
      { name: '情感健康度', type: 'line', smooth: true,
        data: trendData.value.map(pt => pt.avgSentimentScore),
        lineStyle: { color: p.ink, width: 2 },
        itemStyle: { color: p.ink },
        symbol: 'circle', symbolSize: 5
      },
      { name: '总声量', type: 'bar', yAxisIndex: 1,
        data: trendData.value.map(pt => pt.totalMentions),
        itemStyle: { color: p.accent, opacity: 0.85 },
        barMaxWidth: 18
      }
    ]
  }
})

const pieChartOption = computed(() => {
  const o = overview.value || {}
  const p = chartPalette()
  const sp = sentimentPalette()
  return {
    tooltip: { trigger: 'item', ...baseTooltip() },
    legend: {
      orient: 'vertical', right: 0, top: 'middle',
      textStyle: { color: p.ink, fontFamily: p.fontBody, fontSize: 12 },
      itemWidth: 10, itemHeight: 10
    },
    series: [{
      type: 'pie',
      radius: ['46%', '72%'],
      center: ['38%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: p.surface, borderWidth: 2 },
      label: {
        show: true, position: 'inside',
        formatter: '{d}%',
        color: p.surface, fontFamily: p.fontMono, fontSize: 11
      },
      data: [
        { value: o.veryPositiveCount || 0, name: '非常正面', itemStyle: { color: sp.veryPositive } },
        { value: o.positiveCount || 0, name: '正面', itemStyle: { color: sp.positive } },
        { value: o.neutralCount || 0, name: '中性', itemStyle: { color: sp.neutral } },
        { value: o.negativeCount || 0, name: '负面', itemStyle: { color: sp.negative } },
        { value: o.veryNegativeCount || 0, name: '非常负面', itemStyle: { color: sp.veryNegative } }
      ]
    }]
  }
})

async function loadData() {
  try {
    overview.value = await getBriefingOverview(brand.value) || {}
    await loadTrend()
    topNews.value = await getTopNews(brand.value, selectedDate.value) || []
    topBuzz.value = await getTopBuzz(brand.value, selectedDate.value) || []
  } catch (e) {
    toast.error('加载失败：' + (e.message || e))
  }
}

async function loadTrend() {
  try {
    const res = await getBriefingTrend(brand.value, trendDays.value)
    trendData.value = res?.points || []
  } catch (e) {
    trendData.value = []
  }
}

function setTrendDays(d) {
  trendDays.value = d
  loadTrend()
}

function openBuzz(row) {
  if (row.url) window.open(row.url, '_blank', 'noopener')
}

watch([brand, selectedDate], () => loadData())
onMounted(loadData)
</script>

<style scoped>
.dr-page {
  background: var(--bg);
  padding: var(--space-6) var(--space-8) var(--space-10);
  max-width: 1320px;
  margin: 0 auto;
}

.dr-deck {
  font-size: var(--text-sm);
  color: var(--ink-muted);
  margin-bottom: var(--space-6);
  line-height: var(--leading-normal);
}

.dr-section { margin-bottom: var(--space-8); }
.dr-section-head {
  padding-bottom: var(--space-2);
  margin-bottom: var(--space-4);
  border-bottom: 1px solid var(--hairline);
}
.dr-section-h2 {
  font-size: var(--text-md);
  font-weight: 500;
  letter-spacing: var(--tracking-tight);
}

/* ===== Numbers grid ===== */
.dr-numbers {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  background: var(--surface);
  border: 1px solid var(--hairline);
}
.dr-num {
  padding: var(--space-5) var(--space-4);
  border-right: 1px solid var(--hairline);
}
.dr-num:last-child { border-right: none; }
.dr-num-label {
  margin-bottom: var(--space-3);
}
.dr-num-value {
  font-size: clamp(28px, 3vw, 40px);
  font-weight: 500;
  line-height: 1;
  letter-spacing: var(--tracking-tight);
}
.dr-num-value.v-positive { color: var(--positive); }
.dr-num-value.v-accent { color: var(--accent); }
.dr-num-value.v-alert { color: var(--alert); }
.dr-num-value.v-ink { color: var(--ink); }
.dr-num-delta {
  margin-top: var(--space-2);
  font-size: var(--text-xs);
  font-family: var(--font-mono);
  color: var(--ink-subtle);
}
.dr-num-delta.v-positive { color: var(--positive); }
.dr-num-delta.v-accent { color: var(--accent); }

/* ===== Charts ===== */
.dr-charts {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: var(--space-5);
}
.dr-card-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  width: 100%;
}
.dr-card-title {
  font-size: var(--text-md);
  font-weight: 500;
  margin-top: 4px;
  letter-spacing: var(--tracking-tight);
}
.dr-chart-pad { padding: var(--space-4); }
.dr-period-toggle {
  display: inline-flex;
  border: 1px solid var(--hairline);
}
.dr-period-btn {
  background: transparent;
  border: none;
  padding: 4px 10px;
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--ink-muted);
  cursor: pointer;
  border-right: 1px solid var(--hairline);
}
.dr-period-btn:last-child { border-right: none; }
.dr-period-btn:hover { color: var(--ink); }
.dr-period-btn.active {
  background: var(--ink);
  color: var(--ink-inverse);
}

/* ===== News list ===== */
.dr-news {
  display: grid;
  grid-template-columns: 60px 1fr;
  gap: var(--space-4);
  padding: var(--space-4) 0;
  border-bottom: 1px solid var(--hairline);
}
.dr-news:last-child { border-bottom: none; }
.dr-news-num {
  font-size: var(--text-xs);
  color: var(--ink-subtle);
  padding-top: 4px;
}
.dr-news-title {
  display: block;
  font-size: var(--text-md);
  font-weight: 500;
  line-height: var(--leading-snug);
  color: var(--ink);
  letter-spacing: var(--tracking-tight);
  text-decoration: none;
  transition: color var(--duration-fast);
}
.dr-news-title:hover { color: var(--accent); }
.dr-news-byline {
  margin-top: var(--space-2);
  display: flex;
  align-items: center;
  gap: var(--space-2);
}
.dr-news-meta {
  font-size: var(--text-xs);
  color: var(--ink-muted);
}

.dr-empty {
  padding: var(--space-12) 0;
  text-align: center;
  color: var(--ink-subtle);
  font-style: italic;
  font-family: var(--font-display);
}

@media (max-width: 960px) {
  .dr-numbers { grid-template-columns: repeat(2, 1fr); }
  .dr-num:nth-child(2n) { border-right: none; }
  .dr-num { border-bottom: 1px solid var(--hairline); }
  .dr-charts { grid-template-columns: 1fr; }
}
</style>
