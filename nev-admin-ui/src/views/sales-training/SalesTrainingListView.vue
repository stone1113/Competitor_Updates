<template>
  <div class="page">
    <PageMast title="销售培训文档">
      <template #controls>
        <span class="muted">RAGFlow Agent 产出的销售对标话术合集</span>
      </template>
    </PageMast>

    <UCard flush>
      <div class="filter-bar">
        <UInput
          v-model="filter.targetModel"
          placeholder="按本品过滤（如 猛士917）"
          style="width: 240px"
          @keyup.enter="reload"
        />
        <UButton @click="reload" :loading="loading">刷新</UButton>
      </div>

      <div v-if="loading && !docs.length" class="muted center pad">加载中…</div>
      <div v-else-if="!docs.length" class="muted center pad">
        — 暂无销售培训文档 —<br />
        <small>在 RAGFlow Agent 里跑「为 X 生成销售培训文档」即会自动写入这里</small>
      </div>
      <div v-else class="doc-grid">
        <article
          v-for="d in docs"
          :key="d.id"
          class="doc-card"
          @click="$router.push(`/sales-training/${d.id}`)"
        >
          <header class="doc-card-head">
            <h3>{{ d.title }}</h3>
            <UTag>{{ d.targetModel }}</UTag>
          </header>
          <p v-if="d.summary" class="doc-card-summary">{{ d.summary }}</p>
          <p v-else class="doc-card-summary muted">
            竞品：{{ d.competitorModels || '—' }}
          </p>
          <footer class="doc-card-foot muted small">
            <span>{{ d.generator }}</span>
            <span>·</span>
            <span>{{ formatTime(d.addTs) }}</span>
            <span>·</span>
            <span>👁 {{ d.viewCount || 0 }}</span>
          </footer>
        </article>
      </div>

      <UPagination
        v-if="total > pageSize"
        :total="total"
        :page="page"
        :page-size="pageSize"
        @update:page="onPageChange"
      />
    </UCard>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import PageMast from '../../components/layout/PageMast.vue'
import { UCard, UInput, UButton, UTag, UPagination } from '../../components/ui'
import { listDocs } from '../../api/salesTrainingDoc'

const docs = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)
const filter = reactive({ targetModel: '' })

async function reload() {
  loading.value = true
  try {
    const r = await listDocs({
      page: page.value,
      size: pageSize,
      ...(filter.targetModel ? { targetModel: filter.targetModel } : {})
    })
    docs.value = r.records || []
    total.value = r.total || 0
  } finally {
    loading.value = false
  }
}

function onPageChange(p) { page.value = p; reload() }

function formatTime(ts) {
  if (!ts) return '—'
  const d = new Date(ts)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

onMounted(reload)
</script>

<style scoped>
.page {
  background: var(--bg);
  padding: var(--space-6) var(--space-8) var(--space-8);
  max-width: 1480px;
  margin: 0 auto;
}
.filter-bar {
  display: flex; gap: var(--space-3); align-items: center;
  padding: var(--space-4); border-bottom: 1px solid var(--hairline);
}
.doc-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: var(--space-4);
  padding: var(--space-5);
}
.doc-card {
  background: var(--surface);
  border: 1px solid var(--hairline);
  padding: var(--space-5);
  cursor: pointer;
  transition: border-color var(--duration-fast) var(--ease-out);
}
.doc-card:hover { border-color: var(--accent); }
.doc-card-head {
  display: flex; justify-content: space-between; align-items: flex-start;
  gap: var(--space-3); margin-bottom: var(--space-3);
}
.doc-card h3 {
  font-family: var(--font-display);
  font-weight: 600; font-size: var(--text-lg);
  color: var(--ink); margin: 0; line-height: 1.3;
}
.doc-card-summary {
  font-size: var(--text-sm); color: var(--ink-muted);
  line-height: 1.6; margin: 0 0 var(--space-3);
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;
}
.doc-card-foot {
  display: flex; gap: var(--space-2); align-items: center;
  padding-top: var(--space-3); border-top: 1px solid var(--hairline);
}
.center { text-align: center; }
.pad { padding: var(--space-8); }
.muted { color: var(--ink-subtle); }
.small { font-size: var(--text-xs); }
</style>
