<template>
  <div class="page">
    <PageMast title="智能深度对标">
      <template #controls>
        <span class="muted">基于 RAGFlow 知识库 · 对话式多维分析 · 支持引用 + 多轮追问</span>
      </template>
    </PageMast>

    <UCard v-if="!configured" flush>
      <div class="empty-hint">
        <h3>RAGFlow Chat Assistant 尚未配置</h3>
        <p>{{ message || '请在 RAGFlow UI 创建 Chat Assistant 并将 share token 写入环境变量 RAGFLOW_DEEP_ANALYSIS_SHARED_ID' }}</p>
        <div class="steps">
          <div class="step">
            <span class="step-num">1</span>
            <span>打开 <a :href="ragflowUi" target="_blank" rel="noopener">{{ ragflowUi }}</a> 登录</span>
          </div>
          <div class="step">
            <span class="step-num">2</span>
            <span>进入「Chat」→ 「Create Chat Assistant」，关联知识库「竞品分析」</span>
          </div>
          <div class="step">
            <span class="step-num">3</span>
            <span>点击 Chat 右上「embed」→ 复制 Beta Token</span>
          </div>
          <div class="step">
            <span class="step-num">4</span>
            <span>把 token 写入项目根 <code>.env</code>：<code>RAGFLOW_DEEP_ANALYSIS_SHARED_ID=...</code></span>
          </div>
          <div class="step">
            <span class="step-num">5</span>
            <span>重启后端：<code>docker compose up -d app</code></span>
          </div>
        </div>
      </div>
    </UCard>

    <div v-else class="iframe-wrap">
      <iframe
        :src="url"
        sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox"
        allow="clipboard-read; clipboard-write"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import PageMast from '../../components/layout/PageMast.vue'
import { UCard } from '../../components/ui'
import { getDeepAnalysisUrl } from '../../api/competitorReport'

const configured = ref(false)
const url = ref('')
const message = ref('')

const ragflowUi = computed(() => {
  // 提取 RAGFlow UI 根（如 url=http://localhost/chat/share?... → http://localhost）
  try {
    if (url.value) {
      const u = new URL(url.value)
      return `${u.protocol}//${u.host}`
    }
  } catch (e) { /* ignore */ }
  return 'http://localhost'
})

async function load() {
  try {
    const r = await getDeepAnalysisUrl()
    configured.value = !!r.configured
    url.value = r.url || ''
    message.value = r.message || ''
  } catch (e) {
    configured.value = false
    message.value = '获取配置失败：' + (e.message || e)
  }
}

onMounted(load)
</script>

<style scoped>
.page {
  background: var(--bg);
  padding: var(--space-6) var(--space-8) var(--space-4);
  max-width: 1480px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  height: 100vh;
}
.iframe-wrap {
  flex: 1;
  border: 1px solid var(--hairline);
  overflow: hidden;
  background: var(--surface);
}
iframe {
  width: 100%;
  height: 100%;
  border: none;
}
.muted {
  color: var(--ink-subtle);
  font-size: var(--text-sm);
}
.empty-hint {
  padding: var(--space-8) var(--space-6);
  color: var(--ink-muted);
}
.empty-hint h3 {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: var(--text-xl);
  color: var(--ink);
  margin: 0 0 var(--space-3);
}
.empty-hint p { line-height: 1.7; margin: 0 0 var(--space-5); }
.steps {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.step {
  display: flex;
  gap: var(--space-3);
  align-items: baseline;
  line-height: 1.7;
}
.step-num {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  background: var(--accent);
  color: white;
  font-weight: 600;
  font-size: var(--text-xs);
  flex-shrink: 0;
}
code {
  background: var(--surface-sunk);
  padding: 2px 6px;
  font-family: var(--font-mono);
  font-size: 0.9em;
}
a { color: var(--accent); }
</style>
