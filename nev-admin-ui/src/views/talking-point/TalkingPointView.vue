<template>
  <div class="page">
    <PageMast title="话术管理">
      <template #controls>
        <UInput v-model="tpId" placeholder="话术 ID" style="width: 160px" />
        <UButton variant="ghost" size="sm" @click="loadVersions">查询</UButton>
      </template>
    </PageMast>

    <div class="tp-grid">
      <UCard flush>
        <template #header>
          <h3 class="display tp-card-title">当前版本</h3>
        </template>
        <div class="tp-current">
          <template v-if="currentVersion">
            <div class="tp-current-head">
              <span class="display tp-version">v{{ currentVersion.version }}</span>
              <UTag variant="positive">CURRENT</UTag>
            </div>
            <p class="tp-current-content">{{ currentVersion.content }}</p>
          </template>
          <div v-else class="tp-empty">— 输入话术 ID 查询 —</div>
        </div>
      </UCard>

      <UCard flush>
        <template #header>
          <h3 class="display tp-card-title">版本历史</h3>
        </template>
        <ol v-if="versions.length" class="tp-timeline">
          <li v-for="v in versions" :key="v.id" class="tp-tl-item" :class="{ current: v.isCurrent }">
            <div class="tp-tl-marker">
              <span class="tp-tl-dot" :class="{ filled: v.isCurrent }"></span>
              <span class="tp-tl-line"></span>
            </div>
            <div class="tp-tl-body">
              <div class="tp-tl-head">
                <div>
                  <span class="display tp-tl-ver">v{{ v.version }}</span>
                  <UTag v-if="v.isCurrent" variant="positive" style="margin-left: 8px">当前</UTag>
                  <span class="tp-tl-author">· {{ v.author }}</span>
                </div>
                <UPopconfirm
                  v-if="!v.isCurrent"
                  message="确认回滚到此版本？"
                  ok-text="回滚"
                  @confirm="handleRollback(v.version)"
                >
                  <UButton size="sm" variant="ghost">回滚</UButton>
                </UPopconfirm>
              </div>
              <div class="tp-tl-time tabular">{{ v.createTime }}</div>
              <p class="tp-tl-reason">{{ v.changeReason || '— 无说明 —' }}</p>
            </div>
          </li>
        </ol>
        <div v-else class="tp-empty">— 暂无版本记录 —</div>
      </UCard>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PageMast from '../../components/layout/PageMast.vue'
import {
  UButton, UInput, UCard, UTag, UPopconfirm, toast
} from '../../components/ui'
import { getVersions, getCurrentVersion, rollback } from '../../api/talkingPoint'

const tpId = ref('')
const versions = ref([])
const currentVersion = ref(null)

async function loadVersions() {
  if (!tpId.value) {
    toast.warning('请输入话术 ID')
    return
  }
  try {
    versions.value = await getVersions(tpId.value) || []
    currentVersion.value = await getCurrentVersion(tpId.value)
  } catch (e) {
    versions.value = []
    currentVersion.value = null
    toast.error('加载失败')
  }
}

async function handleRollback(version) {
  try {
    await rollback(tpId.value, version)
    toast.success(`已回滚到 v${version}`)
    loadVersions()
  } catch (e) { toast.error('回滚失败') }
}
</script>

<style scoped>
.page {
  background: var(--bg);
  padding: var(--space-6) var(--space-8) var(--space-10);
  max-width: 1320px;
  margin: 0 auto;
}
.tp-grid {
  display: grid;
  grid-template-columns: 1fr 2fr;
  gap: var(--space-5);
}
@media (max-width: 1024px) { .tp-grid { grid-template-columns: 1fr; } }
.tp-card-title {
  font-size: var(--text-md);
  font-weight: 500;
  margin-top: 4px;
  letter-spacing: var(--tracking-tight);
}
.tp-current {
  padding: var(--space-6);
  min-height: 240px;
}
.tp-current-head {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-4);
}
.tp-version {
  font-size: var(--text-2xl);
  font-weight: 500;
  font-family: var(--font-display);
  letter-spacing: var(--tracking-tight);
}
.tp-current-content {
  font-size: var(--text-base);
  color: var(--ink);
  line-height: var(--leading-loose);
  white-space: pre-wrap;
}
.tp-empty {
  text-align: center;
  padding: var(--space-12);
  color: var(--ink-subtle);
  font-style: italic;
  font-family: var(--font-display);
}

/* Timeline */
.tp-timeline {
  list-style: none;
  padding: var(--space-4) var(--space-6) var(--space-6);
  margin: 0;
}
.tp-tl-item {
  display: grid;
  grid-template-columns: 24px 1fr;
  gap: var(--space-3);
  padding-bottom: var(--space-5);
  position: relative;
}
.tp-tl-item:last-child { padding-bottom: 0; }
.tp-tl-marker {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 6px;
}
.tp-tl-dot {
  width: 10px;
  height: 10px;
  border: 1.5px solid var(--ink-muted);
  background: var(--surface);
  flex-shrink: 0;
}
.tp-tl-dot.filled {
  background: var(--accent);
  border-color: var(--accent);
}
.tp-tl-line {
  flex: 1;
  width: 1px;
  background: var(--hairline);
  margin-top: 4px;
  min-height: 24px;
}
.tp-tl-item:last-child .tp-tl-line { display: none; }

.tp-tl-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 2px;
}
.tp-tl-ver {
  font-size: var(--text-md);
  font-weight: 500;
  font-family: var(--font-display);
  letter-spacing: var(--tracking-tight);
}
.tp-tl-author {
  font-size: var(--text-xs);
  color: var(--ink-muted);
  margin-left: 6px;
}
.tp-tl-time {
  font-size: var(--text-xs);
  color: var(--ink-subtle);
  margin-bottom: 4px;
}
.tp-tl-reason {
  font-size: var(--text-sm);
  color: var(--ink-muted);
  line-height: var(--leading-snug);
}
</style>
