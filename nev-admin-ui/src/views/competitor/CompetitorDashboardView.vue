<template>
  <div class="cd-page">
    <PageMast title="竞品仪表盘">
      <template #controls>
        <UDatePicker v-model="selectedDate" />
        <UButton variant="ghost" size="sm" @click="loadData">查询</UButton>
      </template>
    </PageMast>

    <div class="cd-grid">
      <UCard flush>
        <template #header>
          <h3 class="display cd-card-title">维度对比</h3>
        </template>
        <div style="padding: var(--space-4)">
          <v-chart :option="radarOption" style="height: 460px" autoresize />
        </div>
      </UCard>

      <UCard flush>
        <template #header>
          <h3 class="display cd-card-title">评分明细</h3>
        </template>
        <UTable :data="scores" stripe>
          <UTableColumn prop="brandName" label="品牌" :width="100" />
          <UTableColumn prop="dimension" label="维度" :width="100" />
          <UTableColumn label="评分" :width="80" align="right">
            <template #default="{ row }">
              <span class="tabular">{{ row.score }}</span>
            </template>
          </UTableColumn>
          <UTableColumn prop="evidence" label="依据" min-width="220" />
        </UTable>
      </UCard>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { RadarChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent } from 'echarts/components'
import PageMast from '../../components/layout/PageMast.vue'
import { UDatePicker, UButton, UField, UCard, UTable, UTableColumn } from '../../components/ui'
import { chartPalette, baseTooltip } from '../../utils/chartTheme'
import { getDashboard } from '../../api/competitor'

use([CanvasRenderer, RadarChart, TooltipComponent, LegendComponent])

const today = new Date().toISOString().slice(0, 10)
const selectedDate = ref(today)
const scores = ref([])

const dimensions = ['续航', '智驾', '底盘', '动力', '安全', '内饰', '空间', '价格', '口碑', '服务', '智能化', '设计']

const radarOption = computed(() => {
  const p = chartPalette()
  const brands = [...new Set(scores.value.map(s => s.brandName))]
  // Editorial palette ordered by emphasis
  const seriesColors = [p.ink, p.accent, p.positive, p.alert, p.inkSubtle]
  return {
    tooltip: { ...baseTooltip() },
    legend: {
      data: brands,
      bottom: 0,
      itemWidth: 14, itemHeight: 2,
      textStyle: { color: p.ink, fontFamily: p.fontBody, fontSize: 12 }
    },
    radar: {
      indicator: dimensions.map(d => ({ name: d, max: 10 })),
      radius: '62%',
      axisName: { color: p.inkMuted, fontFamily: p.fontBody, fontSize: 11 },
      splitLine: { lineStyle: { color: p.hairline } },
      splitArea: { areaStyle: { color: ['rgba(0,0,0,0)', 'rgba(0,0,0,0.015)'] } },
      axisLine: { lineStyle: { color: p.hairline } }
    },
    series: [{
      type: 'radar',
      data: brands.map((brand, i) => ({
        name: brand,
        value: dimensions.map(dim => {
          const found = scores.value.find(s => s.brandName === brand && s.dimension === dim)
          return found ? Number(found.score) : 0
        }),
        lineStyle: { color: seriesColors[i % seriesColors.length], width: 1.5 },
        itemStyle: { color: seriesColors[i % seriesColors.length] },
        areaStyle: { color: seriesColors[i % seriesColors.length], opacity: 0.05 }
      }))
    }]
  }
})

async function loadData() {
  try { scores.value = await getDashboard(selectedDate.value) || [] }
  catch (e) { scores.value = [] }
}
onMounted(loadData)
</script>

<style scoped>
.cd-page {
  background: var(--bg);
  padding: var(--space-6) var(--space-8) var(--space-10);
  max-width: 1320px;
  margin: 0 auto;
}
.cd-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-5);
}
.cd-card-title {
  font-size: var(--text-md);
  font-weight: 500;
  margin-top: 4px;
  letter-spacing: var(--tracking-tight);
}
@media (max-width: 1024px) {
  .cd-grid { grid-template-columns: 1fr; }
}
</style>
