<template>
  <div class="page">
    <PageMast title="日报控制台" />

    <div class="rc-grid">
      <UCard flush>
        <template #header>
          <h3 class="display rc-card-title">参数</h3>
        </template>
        <div class="rc-form">
          <UField label="日期">
            <UDatePicker v-model="selectedDate" />
          </UField>
          <UField label="持久化到数据库" inline>
            <USwitch v-model="persist" />
          </UField>
          <UField label="推送测试群" inline>
            <USwitch v-model="push" />
          </UField>
          <UButton
            variant="primary"
            size="lg"
            :loading="generating"
            style="margin-top: var(--space-3); width: 100%"
            @click="handleGenerate"
          >
            {{ generating ? '生成中…' : '启动管线' }}
          </UButton>
        </div>
      </UCard>

      <UCard flush>
        <template #header>
          <h3 class="display rc-card-title">运行结果</h3>
        </template>

        <div v-if="!result" class="rc-empty">
          <p class="display rc-empty-text">尚未运行</p>
          <p class="rc-empty-deck">填写左侧参数后点击「启动管线」。</p>
        </div>

        <div v-else-if="result.success" class="rc-result">
          <div class="rc-banner success">
            <h3 class="display rc-banner-title">日报已生成</h3>
            <p class="rc-banner-deck">{{ selectedDate }} · {{ result.kpi?.brandName || '' }}</p>
          </div>

          <dl class="rc-kv">
            <div>
              <dt class="eyebrow">态势等级</dt>
              <dd class="display tabular" :class="`tone-${result.kpi?.statusLevel || 'neutral'}`">
                {{ statusText(result.kpi?.statusLevel) }}
              </dd>
            </div>
            <div>
              <dt class="eyebrow">总声量</dt>
              <dd class="display tabular">{{ Number(result.kpi?.totalMentions || 0).toLocaleString() }}</dd>
            </div>
            <div>
              <dt class="eyebrow">正面占比</dt>
              <dd class="display tabular tone-positive">{{ ((result.kpi?.positiveRatio || 0) * 100).toFixed(1) }}%</dd>
            </div>
            <div>
              <dt class="eyebrow">负面占比</dt>
              <dd class="display tabular tone-accent">{{ ((result.kpi?.negativeRatio || 0) * 100).toFixed(1) }}%</dd>
            </div>
            <div>
              <dt class="eyebrow">情感健康度</dt>
              <dd class="display tabular">{{ result.kpi?.avgSentimentScore?.toFixed(2) || '—' }} / 5</dd>
            </div>
          </dl>
        </div>

        <div v-else class="rc-result">
          <div class="rc-banner error">
            <h3 class="display rc-banner-title">运行失败</h3>
            <p class="rc-banner-deck">{{ result.error || '未知错误' }}</p>
          </div>
        </div>
      </UCard>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PageMast from '../../components/layout/PageMast.vue'
import {
  UButton, UDatePicker, USwitch, UCard, UField, toast
} from '../../components/ui'
import { generateReport } from '../../api/pipeline'

const today = new Date().toISOString().slice(0, 10)
const selectedDate = ref(today)
const persist = ref(true)
const push = ref(false)
const generating = ref(false)
const result = ref(null)

function statusText(s) {
  return ({ green: '平稳', yellow: '关注', red: '预警' }[s] || '—')
}

async function handleGenerate() {
  generating.value = true
  result.value = null
  try {
    const res = await generateReport(selectedDate.value, persist.value, push.value)
    result.value = res
    if (res?.success) toast.success('日报生成成功')
    else toast.error(res?.error || '生成失败')
  } catch (e) {
    result.value = { success: false, error: e.message }
    toast.error('请求失败：' + e.message)
  } finally {
    generating.value = false
  }
}
</script>

<style scoped>
.page {
  background: var(--bg);
  padding: var(--space-6) var(--space-8) var(--space-10);
  max-width: 1320px;
  margin: 0 auto;
}
.rc-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: var(--space-5);
}
@media (max-width: 1024px) { .rc-grid { grid-template-columns: 1fr; } }

.rc-card-title {
  font-size: var(--text-md);
  font-weight: 500;
  margin-top: 4px;
  letter-spacing: var(--tracking-tight);
}
.rc-form { padding: var(--space-6); }

.rc-empty {
  padding: var(--space-16) var(--space-6);
  text-align: center;
}
.rc-empty-text {
  font-size: var(--text-2xl);
  font-style: italic;
  color: var(--ink-subtle);
  font-weight: 500;
  letter-spacing: var(--tracking-tight);
  margin-bottom: var(--space-2);
}
.rc-empty-deck {
  color: var(--ink-muted);
  font-size: var(--text-sm);
}

.rc-result { padding: 0; }
.rc-banner {
  padding: var(--space-6);
  border-bottom: 1px solid var(--hairline);
}
.rc-banner.success { background: var(--positive-soft); }
.rc-banner.error { background: var(--accent-soft); }
.rc-banner.success .eyebrow { color: var(--positive); }
.rc-banner.error .eyebrow { color: var(--accent); }
.rc-banner-title {
  font-size: var(--text-2xl);
  font-weight: 500;
  letter-spacing: var(--tracking-tight);
  margin-top: var(--space-2);
}
.rc-banner-deck {
  color: var(--ink-muted);
  font-size: var(--text-sm);
  margin-top: var(--space-2);
}

.rc-kv {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  margin: 0;
}
.rc-kv > div {
  padding: var(--space-5) var(--space-4);
  border-right: 1px solid var(--hairline);
}
.rc-kv > div:last-child { border-right: none; }
.rc-kv dt { margin-bottom: var(--space-2); }
.rc-kv dd {
  font-size: var(--text-xl);
  font-weight: 500;
  letter-spacing: var(--tracking-tight);
  color: var(--ink);
  margin: 0;
}
.tone-positive { color: var(--positive); }
.tone-accent { color: var(--accent); }
.tone-yellow, .tone-alert { color: var(--alert); }
.tone-red { color: var(--accent); }
.tone-green { color: var(--positive); }
@media (max-width: 720px) {
  .rc-kv { grid-template-columns: repeat(2, 1fr); }
  .rc-kv > div:nth-child(2n) { border-right: none; }
  .rc-kv > div { border-bottom: 1px solid var(--hairline); }
}
</style>
