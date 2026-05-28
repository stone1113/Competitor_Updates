<template>
  <div class="page">
    <PageMast title="事件浏览 / 复核">
      <template #controls>
        <USelect v-model="filterCategory" :options="categoryOpts" placeholder="全部分类" clearable style="width:130px" @change="onFilter" />
        <UInput v-model="filterBrand" placeholder="品牌过滤" clearable style="width:120px" @blur="onFilter" />
        <UButton variant="ghost" size="sm" @click="loadAll">刷新</UButton>
      </template>
    </PageMast>

    <UTabs v-model="filterEventType" @change="onFilter" class="cat-tabs">
      <UTabPane :label="`全部 (${counts.total ?? 0})`" name="" />
      <UTabPane :label="`🚀 上市 (${counts.launch ?? 0})`" name="launch" />
      <UTabPane :label="`💰 价金 (${counts.price_finance ?? 0})`" name="price_finance" />
      <UTabPane :label="`📣 营销 (${counts.campaign ?? 0})`" name="campaign" />
      <UTabPane :label="`📈 销量 (${counts.sales_milestone ?? 0})`" name="sales_milestone" />
      <UTabPane :label="`其他 (${counts.other ?? 0})`" name="other" />
      <UTabPane :label="`spam (${counts.spam ?? 0})`" name="spam" />
      <UTabPane :label="`待分类 (${counts.pending ?? 0})`" name="pending" />
    </UTabs>

    <UCard flush>
      <UTable :data="rows" stripe empty-text="— 无事件 —">
        <UTableColumn label="时间" :width="120">
          <template #default="{ row }">
            <span class="muted">{{ formatTs(row.addTs) }}</span>
          </template>
        </UTableColumn>
        <UTableColumn prop="brandName" label="品牌" :width="90" />
        <UTableColumn label="event_type" :width="150">
          <template #default="{ row }">
            <USelect
              v-model="row.eventType"
              :options="eventTypeOpts"
              size="sm"
              @change="(v) => handleUpdate(row, { eventType: v })"
            />
          </template>
        </UTableColumn>
        <UTableColumn label="imp" :width="60">
          <template #default="{ row }">
            <span :class="{ high: row.eventImportance >= 8 }">{{ row.eventImportance ?? '-' }}</span>
          </template>
        </UTableColumn>
        <UTableColumn label="事件摘要 / 标题" min-width="380">
          <template #default="{ row }">
            <div>
              <a :href="row.url" target="_blank" rel="noopener" class="title-link">
                {{ row.eventSummary || row.title }}
              </a>
              <div v-if="row.eventSummary && row.title" class="muted small">原标题: {{ row.title }}</div>
            </div>
          </template>
        </UTableColumn>
        <UTableColumn label="车型" :width="140">
          <template #default="{ row }">
            <span class="muted small">{{ row.modelMentioned || '-' }}</span>
          </template>
        </UTableColumn>
        <UTableColumn label="操作" :width="100">
          <template #default="{ row }">
            <UButton variant="link" size="sm" :loading="reclassifyingId === row.id" @click="handleReclassify(row)">重新分类</UButton>
          </template>
        </UTableColumn>
      </UTable>

      <div class="pagination-wrap">
        <UPagination v-model="page" :total="total" :page-size="pageSize" @change="loadList" />
      </div>
    </UCard>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PageMast from '../../components/layout/PageMast.vue'
import {
  UButton, USelect, UInput, UCard, UTable, UTableColumn,
  UTabs, UTabPane, UPagination, toast
} from '../../components/ui'
import { listEvents, statsEvents, updateEvent, reclassifyEvent } from '../../api/events'

const rows = ref([])
const counts = ref({})
const total = ref(0)
const page = ref(1)
const pageSize = ref(30)
const reclassifyingId = ref(null)

const filterCategory = ref('')
const filterEventType = ref('')
const filterBrand = ref('')

const categoryOpts = [
  { label: '本品 (self)', value: 'self' },
  { label: '竞品 (competitor)', value: 'competitor' },
  { label: '行业 (industry)', value: 'industry' },
]

const eventTypeOpts = [
  { label: '🚀 launch', value: 'launch' },
  { label: '💰 price_finance', value: 'price_finance' },
  { label: '📣 campaign', value: 'campaign' },
  { label: '📈 sales_milestone', value: 'sales_milestone' },
  { label: 'other', value: 'other' },
  { label: 'spam', value: 'spam' },
]

function formatTs(ts) {
  if (!ts) return '-'
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadList() {
  try {
    const params = { page: page.value, pageSize: pageSize.value }
    if (filterCategory.value) params.category = filterCategory.value
    if (filterEventType.value) params.eventType = filterEventType.value
    if (filterBrand.value) params.brand = filterBrand.value
    const r = await listEvents(params)
    rows.value = r.records || []
    total.value = r.total || 0
  } catch (e) {
    rows.value = []; total.value = 0
  }
}

async function loadStats() {
  try {
    counts.value = await statsEvents() || {}
  } catch (e) { counts.value = {} }
}

async function loadAll() {
  await Promise.all([loadList(), loadStats()])
}

function onFilter() {
  page.value = 1
  loadList()
}

async function handleUpdate(row, patch) {
  try {
    await updateEvent(row.id, patch)
    toast.success('已保存')
    loadStats()
  } catch (e) {
    toast.error('保存失败：' + (e.message || e))
  }
}

async function handleReclassify(row) {
  reclassifyingId.value = row.id
  try {
    const r = await reclassifyEvent(row.id)
    row.eventType = r.eventType
    row.eventImportance = r.importance
    row.eventSummary = r.eventSummary
    row.modelMentioned = r.models
    toast.success(`已重分类为 ${r.eventType}`)
    loadStats()
  } catch (e) {
    toast.error('重分类失败：' + (e.message || e))
  } finally {
    reclassifyingId.value = null
  }
}

onMounted(loadAll)
</script>

<style scoped>
.page {
  background: var(--bg);
  padding: var(--space-6) var(--space-8) var(--space-10);
  max-width: 1480px;
  margin: 0 auto;
}
.cat-tabs {
  margin: var(--space-3) 0;
}
.muted { color: var(--ink-subtle); }
.small { font-size: var(--text-xs); margin-top: 2px; }
.title-link { color: var(--ink); text-decoration: none; }
.title-link:hover { color: var(--accent); text-decoration: underline; }
.high { color: var(--accent); font-weight: var(--weight-semibold); }
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: var(--space-3);
}
</style>
