<template>
  <div class="page">
    <PageMast :title="doc?.title || '销售培训文档'">
      <template #controls>
        <UButton @click="$router.push('/sales-training')">← 返回列表</UButton>
      </template>
    </PageMast>

    <UCard flush v-if="loading">
      <div class="muted center pad">加载中…</div>
    </UCard>
    <UCard flush v-else-if="!doc">
      <div class="muted center pad">文档不存在或已删除</div>
    </UCard>
    <UCard flush v-else>
      <div class="doc-meta">
        <div class="meta-row">
          <span class="meta-key">本品</span>
          <UTag>{{ doc.targetModel }}</UTag>
          <span class="meta-key">竞品</span>
          <span>{{ doc.competitorModels || '—' }}</span>
          <span class="meta-key">生成器</span>
          <span>{{ doc.generator }}</span>
          <span class="meta-key">创建</span>
          <span>{{ formatTime(doc.addTs) }}</span>
          <span class="meta-key">👁</span>
          <span>{{ doc.viewCount }}</span>
        </div>
      </div>
      <div class="md-body" v-html="renderedHtml"></div>
      <details v-if="sources?.length" class="sources">
        <summary>📚 资料来源 ({{ sources.length }})</summary>
        <ul>
          <li v-for="(s, i) in sources" :key="i">
            <UTag size="xs">{{ s.type }}</UTag>
            <a :href="s.url" target="_blank" rel="noopener">{{ s.title || s.url }}</a>
          </li>
        </ul>
      </details>
    </UCard>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { marked } from 'marked'
import PageMast from '../../components/layout/PageMast.vue'
import { UCard, UButton, UTag } from '../../components/ui'
import { getDoc } from '../../api/salesTrainingDoc'

const route = useRoute()
const doc = ref(null)
const loading = ref(true)

const renderedHtml = computed(() => {
  if (!doc.value?.contentMd) return ''
  return marked.parse(doc.value.contentMd)
})

const sources = computed(() => {
  if (!doc.value?.sourcesJson) return []
  try { return JSON.parse(doc.value.sourcesJson) }
  catch { return [] }
})

function formatTime(ts) {
  if (!ts) return '—'
  const d = new Date(ts)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

onMounted(async () => {
  try { doc.value = await getDoc(route.params.id) }
  finally { loading.value = false }
})
</script>

<style scoped>
.page {
  background: var(--bg);
  padding: var(--space-6) var(--space-8) var(--space-8);
  max-width: 1080px;
  margin: 0 auto;
}
.doc-meta {
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--hairline);
  background: var(--surface-sunk);
}
.meta-row {
  display: flex; flex-wrap: wrap; gap: var(--space-3) var(--space-4);
  align-items: center; font-size: var(--text-sm); color: var(--ink-muted);
}
.meta-key { font-weight: 600; color: var(--ink-subtle); }
.md-body {
  padding: var(--space-7) var(--space-8);
  font-family: var(--font-body);
  font-size: 16px;
  line-height: 1.8;
  color: var(--ink);
}
.md-body :deep(h1) {
  font-family: var(--font-display); font-weight: 600;
  font-size: var(--text-2xl); margin-top: var(--space-7); margin-bottom: var(--space-4);
}
.md-body :deep(h2) {
  font-family: var(--font-display); font-weight: 600;
  font-size: var(--text-xl); margin-top: var(--space-6); margin-bottom: var(--space-3);
  padding-bottom: var(--space-2); border-bottom: 1px solid var(--hairline);
}
.md-body :deep(h3) {
  font-weight: 600; font-size: var(--text-lg);
  margin-top: var(--space-5); margin-bottom: var(--space-2);
}
.md-body :deep(table) {
  width: 100%; border-collapse: collapse; margin: var(--space-4) 0;
  font-size: var(--text-sm);
}
.md-body :deep(th), .md-body :deep(td) {
  padding: var(--space-2) var(--space-3); border: 1px solid var(--hairline); text-align: left;
}
.md-body :deep(th) { background: var(--surface-sunk); font-weight: 600; }
.md-body :deep(blockquote) {
  border-left: 3px solid var(--accent);
  padding: var(--space-2) var(--space-4); margin: var(--space-3) 0;
  background: var(--surface-sunk); color: var(--ink-muted);
}
.md-body :deep(code) {
  font-family: var(--font-mono); font-size: 0.9em;
  background: var(--surface-sunk); padding: 2px 6px;
}
.md-body :deep(pre) {
  background: var(--surface-sunk); padding: var(--space-4);
  overflow-x: auto; margin: var(--space-3) 0;
}
.md-body :deep(pre code) { background: transparent; padding: 0; }
.md-body :deep(ul), .md-body :deep(ol) {
  padding-left: var(--space-6); margin: var(--space-3) 0;
}
.md-body :deep(li) { margin: var(--space-1) 0; }
.sources {
  padding: var(--space-5);
  border-top: 1px solid var(--hairline);
  background: var(--surface-sunk);
}
.sources summary {
  cursor: pointer; font-weight: 600;
  font-size: var(--text-sm); color: var(--ink);
}
.sources ul { margin: var(--space-3) 0 0; padding-left: var(--space-5); }
.sources li {
  margin: var(--space-2) 0; display: flex; gap: var(--space-2); align-items: center;
  font-size: var(--text-sm);
}
.sources a { color: var(--accent); text-decoration: none; }
.sources a:hover { text-decoration: underline; }
.center { text-align: center; }
.pad { padding: var(--space-8); }
.muted { color: var(--ink-subtle); }
</style>
