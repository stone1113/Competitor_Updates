<template>
  <div class="page">
    <PageMast title="技术参数对标">
      <template #controls>
        <USelect
          v-model="selectedIds"
          :options="seriesOpts"
          placeholder="选择 2-6 款车型对比"
          multiple
          style="min-width:360px"
        />
        <UButton variant="primary" size="sm" :loading="loading" @click="loadCompare">对比</UButton>
      </template>
    </PageMast>

    <UCard v-if="!comp" flush>
      <div class="empty-hint">
        从上方下拉选 <b>2-6 款车型</b>，点「对比」生成参数矩阵。<br>
        车型清单来自 <a href="/competitor/autohome-config">汽车之家车系配置</a>，只展示已抓取过参数的车系。
      </div>
    </UCard>

    <template v-else>
      <UCard flush class="head-card">
        <div class="series-strip">
          <div v-for="s in comp.series" :key="s.seriesId" class="series-chip">
            <UTag :variant="s.role === 'self' ? 'accent' : 'default'">
              {{ s.role === 'self' ? '本品' : '竞品' }}
            </UTag>
            <div class="model">{{ s.modelName }}</div>
            <div class="muted">{{ s.specName || '—' }}</div>
          </div>
        </div>
      </UCard>

      <div v-for="cg in comp.categories" :key="cg.category" class="cat-block">
        <h3 class="cat-title">{{ cg.category || '其他' }}</h3>
        <UCard flush>
          <UTable :data="cg.params" stripe>
            <UTableColumn prop="name" label="参数" :width="180" />
            <UTableColumn
              v-for="s in comp.series"
              :key="s.seriesId"
              :label="s.modelName"
              min-width="160"
            >
              <template #default="{ row }">
                <span :class="{ highlight: isBest(row, s.seriesId) }">
                  {{ row.values[s.seriesId] || '-' }}
                </span>
              </template>
            </UTableColumn>
          </UTable>
        </UCard>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PageMast from '../../components/layout/PageMast.vue'
import { UButton, USelect, UCard, UTable, UTableColumn, UTag, toast } from '../../components/ui'
import { listSeries, compareSpecs } from '../../api/autohome'

const seriesOpts = ref([])
const selectedIds = ref([])
const comp = ref(null)
const loading = ref(false)

async function loadList() {
  try {
    const list = await listSeries() || []
    seriesOpts.value = list
      .filter(s => s.latestParamCount > 0)
      .map(s => ({
        label: `${s.role === 'self' ? '★' : '·'} ${s.modelName} (${s.brandName})`,
        value: s.seriesId
      }))
  } catch (e) {
    seriesOpts.value = []
  }
}

async function loadCompare() {
  if (!selectedIds.value || selectedIds.value.length < 2) {
    toast.warning('请至少选 2 款车型')
    return
  }
  if (selectedIds.value.length > 6) {
    toast.warning('最多对比 6 款')
    return
  }
  loading.value = true
  try {
    comp.value = await compareSpecs(selectedIds.value)
  } catch (e) {
    toast.error('加载失败：' + (e.message || e))
    comp.value = null
  } finally {
    loading.value = false
  }
}

// 简单 best-value 高亮：数值类参数中找最大或最小（v1 先标准化提取数字后比较），非数值则不标
function isBest(row, sid) {
  const v = row.values[sid]
  if (!v) return false
  const nums = Object.entries(row.values)
    .map(([k, val]) => [k, parseNumeric(val)])
    .filter(([_, n]) => n != null)
  if (nums.length < 2) return false
  const myNum = parseNumeric(v)
  if (myNum == null) return false
  // 大多数参数「越大越好」（功率/续航/扭矩/空间），少数「越小越好」（百公里加速/能耗/质量）
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

onMounted(loadList)
</script>

<style scoped>
.page {
  background: var(--bg);
  padding: var(--space-6) var(--space-8) var(--space-10);
  max-width: 1480px;
  margin: 0 auto;
}
.empty-hint {
  padding: var(--space-8) var(--space-6);
  text-align: center;
  color: var(--ink-muted);
  font-size: var(--text-md);
  line-height: 1.7;
}
.head-card {
  margin-bottom: var(--space-5);
}
.series-strip {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-4);
  padding: var(--space-3) var(--space-4);
}
.series-chip {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--hairline);
  background: var(--surface);
  min-width: 180px;
}
.series-chip .model {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: var(--text-md);
}
.muted { color: var(--ink-subtle); font-size: var(--text-xs); }
.cat-block {
  margin-bottom: var(--space-6);
}
.cat-title {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: var(--text-lg);
  letter-spacing: var(--tracking-tight);
  margin: var(--space-4) 0 var(--space-2);
  color: var(--ink);
}
.highlight {
  color: var(--accent);
  font-weight: var(--weight-semibold);
}
</style>
