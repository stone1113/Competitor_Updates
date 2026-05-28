<template>
  <div class="page">
    <PageMast title="社交爬虫">
      <template #controls>
        <span class="health" :class="{ ok: healthOk, bad: !healthOk }">
          爬虫服务：{{ healthOk ? '在线' : '离线' }}
        </span>
        <UButton variant="quiet" size="sm" @click="refreshHealth">检测</UButton>
        <UButton
          variant="ghost"
          size="sm"
          :disabled="runAll.running"
          @click="handleRunAll"
        >{{ runAll.running ? '全量爬取中…' : '一键全量爬取' }}</UButton>
      </template>
    </PageMast>

    <UCard flush style="margin-bottom: var(--space-4)">
      <template #header>
        <h3 class="display card-title">登录管理</h3>
      </template>
      <div class="login-mgr">
        <span class="muted">cookie 失效或首次使用时点击对应平台扫码登录：</span>
        <div class="login-btns">
          <UButton
            v-for="p in platformOpts"
            :key="p.value"
            variant="ghost"
            size="sm"
            @click="handleRelogin(p.value, p.label)"
          >{{ p.label }}</UButton>
        </div>
      </div>
    </UCard>

    <UCard flush style="margin-bottom: var(--space-4)">
      <template #header>
        <h3 class="display card-title">定时调度</h3>
      </template>
      <div class="sched">
        <UField label="启用" inline>
          <USwitch v-model="schedForm.enabled" />
        </UField>
        <UField label="Cron 表达式" hint="6 字段：秒 分 时 日 月 周。例：0 0 2 * * ? = 每天 02:00；0 0 */6 * * ? = 每 6 小时整点">
          <UInput v-model="schedForm.cron" placeholder="0 0 2 * * ?" />
        </UField>
        <div class="sched-meta">
          <span v-if="schedDirty" class="muted">改动未保存</span>
          <span v-else-if="schedNextRunStr" class="tabular">下次执行：{{ schedNextRunStr }}</span>
          <span v-else-if="schedSaved.enabled" class="muted err-tip">cron 解析失败</span>
          <span v-else class="muted">未启用</span>
        </div>
        <UButton variant="primary" size="sm" @click="handleSaveSchedule">保存配置</UButton>
      </div>
    </UCard>

    <UCard flush v-if="runAll.running || runAll.lastFinished" style="margin-bottom: var(--space-4)">
      <template #header>
        <h3 class="display card-title">全量串行爬取</h3>
      </template>
      <div class="runall">
        <div class="runall-row">
          <UTag :variant="runAll.running ? 'alert' : (runAll.lastError ? 'accent' : 'positive')">
            {{ runAll.running ? '运行中' : (runAll.lastError ? '失败' : '完成') }}
          </UTag>
          <span class="tabular runall-prog">{{ runAll.done }}/{{ runAll.total }}</span>
          <span v-if="runAll.running && runAll.currentBrand" class="runall-cur">
            正在：{{ runAll.currentBrand }} / {{ platformLabel(runAll.currentPlatform) }}
          </span>
          <span v-if="runAll.lastError" class="err" style="margin-left:auto">{{ runAll.lastError }}</span>
        </div>
        <div class="runall-bar"><div class="runall-bar-fill" :style="{ width: runAll.total ? (runAll.done/runAll.total*100)+'%' : '0%' }"></div></div>
        <pre class="runall-log" v-if="(runAll.log || []).length"><template v-for="(l,i) in runAll.log" :key="i">{{ l }}
</template></pre>
      </div>
    </UCard>

    <div class="grid">
      <!-- 触发表单 -->
      <UCard flush>
        <template #header>
          <h3 class="display card-title">触发任务</h3>
        </template>
        <div class="form">
          <UField label="品牌">
            <USelect v-model="form.brand" :options="brandOpts" placeholder="选择品牌" />
          </UField>

          <UField label="平台" hint="可多选">
            <USelect v-model="form.platforms" :options="platformOpts" multiple placeholder="选择平台" />
          </UField>

          <UField label="关键词" hint="逗号分隔，例如：猛士917,猛士M9">
            <UInput type="textarea" v-model="form.keywordsInput" :rows="3" />
          </UField>

          <UField label="单平台最大条数">
            <UInput type="number" v-model="form.maxNotes" />
          </UField>

          <UField label="登录方式">
            <USelect v-model="form.loginType" :options="loginOpts" />
          </UField>

          <UField label="爬取评论" inline>
            <USwitch v-model="form.enableComments" />
          </UField>

          <UButton
            variant="primary"
            size="lg"
            :loading="triggering"
            style="margin-top: var(--space-3); width: 100%"
            @click="handleTrigger"
          >
            {{ triggering ? '触发中…' : '启动爬取' }}
          </UButton>
        </div>
      </UCard>

      <!-- 任务列表 -->
      <UCard flush>
        <template #header>
          <div style="display:flex;justify-content:space-between;align-items:baseline;width:100%;gap:12px">
            <h3 class="display card-title">任务历史</h3>
            <div style="display:flex;align-items:center;gap:12px">
              <span class="poll-hint" v-if="hasRunning">每 5 秒自动刷新</span>
              <UPopconfirm message="确认清空所有非运行中的任务记录？" @confirm="handleClearAll">
                <UButton variant="quiet" size="sm">清空历史</UButton>
              </UPopconfirm>
            </div>
          </div>
        </template>
        <UTable :data="tasks" stripe empty-text="— 暂无任务 —">
          <UTableColumn prop="id" label="ID" :width="60" />
          <UTableColumn label="平台" :width="80">
            <template #default="{ row }">
              <UTag>{{ platformLabel(row.platform) }}</UTag>
            </template>
          </UTableColumn>
          <UTableColumn prop="brandName" label="品牌" :width="80" />
          <UTableColumn label="关键词" min-width="200">
            <template #default="{ row }">
              <span class="kw">{{ formatKeywords(row.keywords) }}</span>
            </template>
          </UTableColumn>
          <UTableColumn label="评论" :width="60" align="center">
            <template #default="{ row }">
              <span v-if="row.enableComments">●</span><span v-else style="color:var(--ink-subtle)">○</span>
            </template>
          </UTableColumn>
          <UTableColumn label="状态" :width="100">
            <template #default="{ row }">
              <UTag :variant="statusVariant(row.status)">{{ statusLabel(row.status) }}</UTag>
            </template>
          </UTableColumn>
          <UTableColumn label="触发" :width="160">
            <template #default="{ row }">
              <span class="ts tabular">{{ formatTs(row.startedAt || row.addTs) }}</span>
            </template>
          </UTableColumn>
          <UTableColumn label="耗时" :width="80" align="right">
            <template #default="{ row }">
              <span class="tabular">{{ formatDuration(row) }}</span>
            </template>
          </UTableColumn>
          <UTableColumn label="错误" min-width="140">
            <template #default="{ row }">
              <span class="err" v-if="row.errorMessage" :title="row.errorMessage">
                {{ truncate(row.errorMessage, 30) }}
              </span>
              <span v-else style="color:var(--ink-subtle)">—</span>
            </template>
          </UTableColumn>
          <UTableColumn label="操作" :width="160">
            <template #default="{ row }">
              <UButton variant="link" size="sm" @click="openLogs(row)">日志</UButton>
              <UButton variant="link" size="sm" @click="handleRerun(row)">重跑</UButton>
              <UPopconfirm message="确认删除此任务？" @confirm="handleDeleteTask(row.id)">
                <UButton variant="link" size="sm">删除</UButton>
              </UPopconfirm>
            </template>
          </UTableColumn>
        </UTable>
      </UCard>
    </div>

    <UDrawer v-model="logDrawer" :title="`任务 #${currentLogTask?.id || ''} · ${currentLogTask?.platform || ''} 日志`" size="60%">
      <div class="log-meta" v-if="currentLogTask">
        <UTag :variant="statusVariant(logState.status || currentLogTask.status)">{{ statusLabel(logState.status || currentLogTask.status) }}</UTag>
        <span style="margin-left:12px;color:var(--ink-muted);font-size:13px">
          {{ logState.logs?.length || 0 }} 行 · 每 2s 刷新（任务进行中）
        </span>
      </div>
      <pre v-if="logState.errorMessage" class="log-err">{{ logState.errorMessage }}</pre>
      <pre class="log-pre" ref="logPre"><template v-for="(line, i) in (logState.logs || [])" :key="i">{{ line }}
</template><span v-if="!(logState.logs || []).length" style="color:var(--ink-subtle)">（暂无输出）</span></pre>
    </UDrawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import PageMast from '../../components/layout/PageMast.vue'
import {
  UButton, USelect, UInput, USwitch, UTag, UCard, UField, UTable, UTableColumn, UDrawer, UPopconfirm, toast
} from '../../components/ui'
import { triggerCrawl, listTasks, checkHealth, getTaskLogs, triggerRunAll, getRunAllStatus, getSchedule, updateSchedule, triggerLogin, rerunTask, deleteTask, deleteAllTasks, getActivePlatforms } from '../../api/crawler'
import { listBrands, listKeywords } from '../../api/brandKeyword'

const brandOpts = ref([])
const ALL_PLATFORMS = [
  { label: '小红书', value: 'xhs' },
  { label: '抖音',   value: 'dy' },
  { label: 'B站',    value: 'bili' },
  { label: '微博',   value: 'wb' },
  { label: '快手',   value: 'ks' }
]
const platformOpts = ref([...ALL_PLATFORMS])
async function loadActivePlatforms() {
  try {
    const list = await getActivePlatforms() || []
    platformOpts.value = ALL_PLATFORMS.filter(p => list.includes(p.value))
    if (Array.isArray(form.value?.platforms)) {
      form.value.platforms = form.value.platforms.filter(p => list.includes(p))
    }
  } catch (e) { /* keep all */ }
}
const loginOpts = [
  { label: 'Cookie（推荐）', value: 'cookie' },
  { label: '扫码', value: 'qrcode' }
]

const platformLabelMap = { xhs: '小红书', dy: '抖音', bili: 'B站', wb: '微博', ks: '快手' }

const form = ref({
  brand: '',
  platforms: ['xhs'],
  keywordsInput: '',
  maxNotes: 20,
  enableComments: true,
  loginType: 'cookie'
})

// 选品牌时自动加载预设关键词
watch(() => form.value.brand, async (brand) => {
  if (!brand) return
  try {
    const list = await listKeywords(brand) || []
    const enabled = list.filter(k => k.enabled).map(k => k.keyword)
    form.value.keywordsInput = enabled.join(',')
  } catch (e) { /* keep current */ }
})

async function loadBrandOpts() {
  try {
    const list = await listBrands() || []
    brandOpts.value = list.map(b => ({ label: b, value: b }))
    if (!form.value.brand && list.length) form.value.brand = list[0]
  } catch (e) { brandOpts.value = [] }
}

const tasks = ref([])
const triggering = ref(false)
const healthOk = ref(false)

let pollTimer = null

const hasRunning = computed(() =>
  tasks.value.some(t => t.status === 'RUNNING' || t.status === 'PENDING')
)

function platformLabel(p) { return platformLabelMap[p] || p }

function statusVariant(s) {
  return ({
    SUCCESS: 'positive',
    RUNNING: 'alert',
    PENDING: 'neutral',
    WAITING_LOGIN: 'alert',
    FAILED: 'accent',
    TIMEOUT: 'accent'
  }[s] || 'default')
}
function statusLabel(s) {
  return ({
    SUCCESS: '完成',
    RUNNING: '运行中',
    PENDING: '排队',
    WAITING_LOGIN: '等待登录',
    FAILED: '失败',
    TIMEOUT: '超时'
  }[s] || s)
}

function formatKeywords(json) {
  try {
    const arr = JSON.parse(json || '[]')
    return Array.isArray(arr) ? arr.join('、') : json
  } catch (e) { return json }
}
function formatTs(ms) {
  if (!ms) return '—'
  const d = new Date(Number(ms))
  if (isNaN(d.getTime())) return '—'
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function formatDuration(row) {
  if (row.durationSec != null) return `${row.durationSec}s`
  if (row.startedAt && row.finishedAt) {
    return `${Math.round((row.finishedAt - row.startedAt) / 1000)}s`
  }
  if (row.startedAt && (row.status === 'RUNNING' || row.status === 'PENDING')) {
    return `${Math.round((Date.now() - row.startedAt) / 1000)}s…`
  }
  return '—'
}
function truncate(s, n) { return s && s.length > n ? s.slice(0, n) + '…' : s }

async function handleTrigger() {
  if (!form.value.brand) { toast.warning('请选择品牌'); return }
  if (!form.value.platforms?.length) { toast.warning('请选择至少一个平台'); return }
  const keywords = form.value.keywordsInput
    .split(/[,，]/)
    .map(k => k.trim())
    .filter(Boolean)
  if (!keywords.length) { toast.warning('请输入至少一个关键词'); return }

  triggering.value = true
  try {
    const res = await triggerCrawl({
      brand: form.value.brand,
      platforms: form.value.platforms,
      keywords,
      maxNotes: Number(form.value.maxNotes) || 20,
      enableComments: form.value.enableComments,
      loginType: form.value.loginType
    })
    toast.success(`已触发 ${res?.length || 0} 个任务`)
    refreshTasks()
  } catch (e) {
    toast.error('触发失败：' + (e.message || e))
  } finally {
    triggering.value = false
  }
}

async function refreshTasks() {
  try { tasks.value = await listTasks(50) || [] } catch (e) { /* keep last */ }
}

async function refreshHealth() {
  try {
    const r = await checkHealth()
    healthOk.value = !!r.crawler_service_reachable
  } catch (e) { healthOk.value = false }
}

const runAll = ref({ running: false, lastFinished: false, done: 0, total: 0, log: [] })
let runAllTimer = null

async function refreshRunAll() {
  try {
    runAll.value = await getRunAllStatus() || runAll.value
  } catch (e) { /* keep */ }
}
async function handleRunAll() {
  try {
    const r = await triggerRunAll()
    if (r?.started) toast.success('已启动全量串行爬取')
    else toast.warning(r?.message || '已有任务在运行')
    refreshRunAll()
  } catch (e) {
    toast.error('触发失败：' + (e.message || e))
  }
}
function startRunAllPolling() {
  if (runAllTimer) return
  runAllTimer = setInterval(refreshRunAll, 5000)
}
function stopRunAllPolling() {
  if (runAllTimer) { clearInterval(runAllTimer); runAllTimer = null }
}

const schedForm = ref({ enabled: false, cron: '0 0 2 * * ?' })
const schedSaved = ref({ enabled: false, cron: '0 0 2 * * ?' })
const schedDirty = computed(() =>
  schedForm.value.enabled !== schedSaved.value.enabled ||
  (schedForm.value.cron || '').trim() !== (schedSaved.value.cron || '').trim()
)
const schedNextRun = ref(null)
const schedNextRunStr = computed(() => {
  if (!schedNextRun.value) return ''
  const d = new Date(Number(schedNextRun.value))
  if (isNaN(d.getTime())) return ''
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
})

async function loadSchedule() {
  try {
    const r = await getSchedule()
    const v = { enabled: !!r.enabled, cron: r.cron || '0 0 2 * * ?' }
    schedForm.value = { ...v }
    schedSaved.value = { ...v }
    schedNextRun.value = r.nextRunAt
  } catch (e) { /* keep defaults */ }
}

async function handleDeleteTask(id) {
  try {
    await deleteTask(id)
    toast.success('已删除')
    refreshTasks()
  } catch (e) {
    toast.error('删除失败：' + (e.message || e))
  }
}

async function handleClearAll() {
  try {
    const r = await deleteAllTasks(false)
    toast.success(`已清空 ${r?.deleted || 0} 条`)
    refreshTasks()
  } catch (e) {
    toast.error('清空失败：' + (e.message || e))
  }
}

async function handleRerun(row) {
  try {
    const r = await rerunTask(row.id)
    const id = r?.[0]?.id
    toast.success(`已重跑 (新任务 #${id || '?'})`)
    refreshTasks()
  } catch (e) {
    toast.error('重跑失败：' + (e.message || e))
  }
}

async function handleRelogin(platform, label) {
  try {
    const r = await triggerLogin(platform)
    toast.info(`已触发 ${label} 登录任务，请到 Mac 上扫码`)
  } catch (e) {
    toast.error('触发失败：' + (e.message || e))
  }
}

async function handleSaveSchedule() {
  try {
    const r = await updateSchedule(schedForm.value.enabled, schedForm.value.cron)
    schedSaved.value = { enabled: !!r.enabled, cron: r.cron }
    schedNextRun.value = r.nextRunAt
    toast.success('已保存定时配置')
  } catch (e) {
    toast.error('保存失败：' + (e.message || e))
  }
}

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(() => {
    if (hasRunning.value) refreshTasks()
  }, 5000)
}
function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

const logDrawer = ref(false)
const currentLogTask = ref(null)
const logState = ref({ status: '', errorMessage: '', logs: [] })
const logPre = ref(null)
let logTimer = null

async function refreshLogs(autoScroll = true) {
  if (!currentLogTask.value) return
  try {
    const res = await getTaskLogs(currentLogTask.value.id)
    logState.value = res || { logs: [] }
    if (autoScroll) {
      await nextTick()
      if (logPre.value) logPre.value.scrollTop = logPre.value.scrollHeight
    }
    // Stop polling once terminal
    const s = logState.value.status
    if (s === 'SUCCESS' || s === 'FAILED' || s === 'TIMEOUT') {
      if (logTimer) { clearInterval(logTimer); logTimer = null }
    }
  } catch (e) { /* keep last */ }
}

function openLogs(row) {
  currentLogTask.value = row
  logState.value = { status: row.status, errorMessage: row.errorMessage, logs: [] }
  logDrawer.value = true
  refreshLogs(true)
  if (logTimer) clearInterval(logTimer)
  logTimer = setInterval(refreshLogs, 2000)
}

function stopLogPolling() {
  if (logTimer) { clearInterval(logTimer); logTimer = null }
}

// Stop log polling when drawer closes
watch(logDrawer, (open) => { if (!open) stopLogPolling() })

onMounted(() => {
  loadBrandOpts()
  loadActivePlatforms()
  loadSchedule()
  refreshTasks()
  refreshHealth()
  refreshRunAll()
  startPolling()
  startRunAllPolling()
})
onBeforeUnmount(() => { stopPolling(); stopLogPolling(); stopRunAllPolling() })
</script>

<style scoped>
.page {
  background: var(--bg);
  padding: var(--space-6) var(--space-8) var(--space-10);
  max-width: 1480px;
  margin: 0 auto;
}
.grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: var(--space-5);
  align-items: start;
}
@media (max-width: 1024px) { .grid { grid-template-columns: 1fr; } }

.card-title {
  font-size: var(--text-md);
  font-weight: 500;
  margin-top: 4px;
  letter-spacing: var(--tracking-tight);
}
.form { padding: var(--space-4); }
.form :deep(.ui-field) { margin-bottom: var(--space-3); }

.health {
  font-size: var(--text-xs);
  font-family: var(--font-mono);
}
.health.ok { color: var(--positive); }
.health.bad { color: var(--accent); }

.poll-hint {
  font-size: var(--text-xs);
  color: var(--ink-subtle);
}
.kw { font-size: var(--text-sm); color: var(--ink); }
.ts { font-size: var(--text-xs); color: var(--ink-muted); }
.err { font-size: var(--text-xs); color: var(--accent); cursor: help; }

.log-meta {
  display: flex;
  align-items: center;
  margin-bottom: var(--space-3);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--hairline);
}
.log-err {
  background: var(--accent-soft);
  color: var(--accent);
  padding: var(--space-3);
  font-size: var(--text-xs);
  font-family: var(--font-mono);
  white-space: pre-wrap;
  word-break: break-word;
  margin-bottom: var(--space-3);
}
.log-pre {
  background: var(--ink);
  color: var(--bg);
  padding: var(--space-4);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: calc(100vh - 240px);
  overflow-y: auto;
  margin: 0;
}

.runall { padding: var(--space-4) var(--space-6); }
.runall-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin-bottom: var(--space-3);
}
.runall-prog { font-size: var(--text-md); font-weight: 600; }
.runall-cur { color: var(--ink-muted); font-size: var(--text-sm); }
.runall-bar {
  height: 4px;
  background: var(--surface-sunk);
  margin-bottom: var(--space-3);
  position: relative;
}
.runall-bar-fill {
  height: 100%;
  background: var(--accent);
  transition: width 0.5s ease-out;
}
.runall-log {
  background: var(--surface-sunk);
  padding: var(--space-3);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  line-height: 1.5;
  white-space: pre-wrap;
  max-height: 200px;
  overflow-y: auto;
  margin: 0;
}

.sched { padding: var(--space-4) var(--space-6); }
.sched-meta {
  margin: var(--space-3) 0;
  font-size: var(--text-sm);
  color: var(--ink);
}
.sched-meta .muted { color: var(--ink-subtle); }
.sched-meta .err-tip { color: var(--accent); }
.login-mgr {
  padding: var(--space-4) var(--space-6);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}
.login-mgr .muted { color: var(--ink-muted); font-size: var(--text-sm); }
.login-btns { display: flex; flex-wrap: wrap; gap: var(--space-2); }
</style>
