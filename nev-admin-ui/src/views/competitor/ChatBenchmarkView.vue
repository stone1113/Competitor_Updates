<template>
  <div class="chat-page">
    <!-- 左侧历史会话栏 -->
    <aside class="sidebar">
      <div class="sidebar-head">
        <button class="new-chat" @click="onNewChat">
          <span>＋</span> 新对标
        </button>
      </div>
      <nav class="session-list">
        <div v-if="loadingSessions" class="muted small">加载中...</div>
        <div v-else-if="sessions.length === 0" class="muted small empty">— 暂无历史 —</div>
        <div
          v-for="s in sessions"
          :key="s.id"
          class="session-item"
          :class="{ active: currentSession && currentSession.id === s.id }"
          @click="loadSession(s.id)"
        >
          <div class="session-title">{{ s.title }}</div>
          <div class="session-meta muted small">
            {{ s.selfModel }} vs {{ truncateBrand(s.competitorModels) }}
          </div>
          <div class="session-time muted small">{{ formatTime(s.lastActiveTs) }}</div>
        </div>
      </nav>
    </aside>

    <!-- 右侧对话主区 -->
    <main class="chat-main">
      <header class="chat-header">
        <h1>🤖 智能深度对标</h1>
        <p class="muted small">对话式 · 5 维分析 · 图文报告 · 多轮追问</p>
      </header>

      <!-- 起始状态：选车型 -->
      <section v-if="!currentSession" class="welcome">
        <div class="welcome-card">
          <h2>📊 选择对标车型</h2>
          <div class="form-row">
            <label>本品车型</label>
            <USelect v-model="selfModel" :options="selfOpts" style="width:240px" />
          </div>
          <div class="form-row">
            <label>竞品（多选）</label>
            <div class="competitor-grid">
              <label v-for="o in competitorOpts" :key="o.value" class="competitor-chip" :class="{ selected: competitorModels.includes(o.value) }">
                <input type="checkbox" :value="o.value" v-model="competitorModels" />
                <span>{{ o.label }}</span>
              </label>
            </div>
          </div>
          <div class="form-row">
            <label>分析诉求<span class="muted small">（可选，模型据此发挥）</span></label>
            <UInput v-model="userPrompt" placeholder="如：重点对比电池和越野能力 / 给销售应对话术" style="flex:1" />
          </div>
          <UButton variant="primary" :loading="busy" :disabled="!canStart" @click="onStart" size="lg">
            🚀 开始深度对标（5 分析师并行）
          </UButton>
          <p class="muted small">预期 90-120 秒 · ~¥1-2 / 次</p>
        </div>
      </section>

      <!-- 已有会话：消息流 -->
      <section v-else class="messages-area">
        <div ref="messagesRef" class="messages-scroll">
          <div v-for="m in messages" :key="m.id" class="message" :class="m.role">
            <div class="msg-avatar">{{ m.role === 'user' ? '👤' : '🤖' }}</div>
            <div class="msg-body">
              <div class="msg-role muted small">{{ roleLabel(m.role) }} · {{ formatTime(m.addTs) }}</div>
              <!-- user 简单文本，assistant Markdown 渲染 -->
              <div v-if="m.role === 'user'" class="msg-user-text">{{ m.content }}</div>
              <MdPreview
                v-else
                :model-value="m.content"
                preview-theme="vuepress"
                code-theme="github"
                no-img-zoom-in
                class="msg-md"
              />
              <div v-if="m.metadata && parseMeta(m).analystResults" class="analyst-tags">
                <span
                  v-for="(r, name) in parseMeta(m).analystResults"
                  :key="name"
                  class="analyst-tag"
                  :title="`${name}: ${r.winner} (${r.scores ? r.scores[r.winner] : '?'}/10)`"
                >{{ dimIcon(name) }} {{ dimCn(name) }} <strong>{{ r.winner }}</strong></span>
              </div>
            </div>
          </div>
          <div v-if="busy" class="message assistant typing">
            <div class="msg-avatar">🤖</div>
            <div class="msg-body">
              <div class="muted small">分析师工作中...</div>
              <div class="typing-dots"><span></span><span></span><span></span></div>
            </div>
          </div>
        </div>
        <!-- 追问输入框 -->
        <div class="composer">
          <UInput
            v-model="followupText"
            placeholder="追问（如：详细讲下电池为啥落后 / 给猛士M817 的应对话术）"
            :disabled="busy"
            @keyup.enter="onFollowup"
            style="flex:1"
          />
          <UButton variant="primary" :loading="busy" :disabled="!followupText.trim() || busy" @click="onFollowup">
            发送
          </UButton>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/preview.css'
import { UButton, UInput, USelect, toast } from '../../components/ui'
import { listSeries } from '../../api/autohome'
import { listSessions, getSession, startChat, followup } from '../../api/chatBenchmark'
import * as echarts from 'echarts'

// ===== State =====
const allSeries = ref([])
const sessions = ref([])
const loadingSessions = ref(false)
const currentSession = ref(null)
const messages = ref([])

const selfModel = ref('猛士M817')
const competitorModels = ref([])
const userPrompt = ref('')
const followupText = ref('')
const busy = ref(false)
const messagesRef = ref(null)

const DIM_META = {
  POWER: { icon: '⚡', cn: '动力' },
  BODY: { icon: '🚗', cn: '车身' },
  OFFROAD: { icon: '🛞', cn: '越野' },
  PRICE: { icon: '💰', cn: '价格' },
  SALES: { icon: '📈', cn: '销量' }
}
const dimIcon = (n) => DIM_META[n]?.icon || ''
const dimCn = (n) => DIM_META[n]?.cn || n

// ===== 车型候选 =====
const selfOpts = computed(() =>
  allSeries.value.filter(s => s.role === 'self').map(s => ({ value: s.modelName, label: s.modelName }))
)
const competitorOpts = computed(() =>
  allSeries.value
    .filter(s => s.role === 'competitor' && s.modelName !== selfModel.value)
    .map(s => ({
      value: s.modelName,
      label: s.modelName + (s.vsSelfModel ? ` (vs ${s.vsSelfModel})` : '')
    }))
)

const canStart = computed(() =>
  selfModel.value && competitorModels.value.length > 0 && !busy.value
)

function roleLabel(r) {
  return r === 'user' ? '你' : r === 'assistant' ? '深度对标助手' : '系统'
}
function truncateBrand(s) {
  if (!s) return ''
  const ms = s.split(',')
  return ms.length > 2 ? `${ms[0]} 等 ${ms.length} 款` : ms.join(' / ')
}
function formatTime(ts) {
  if (!ts) return ''
  const diff = Date.now() - ts
  if (diff < 60_000) return '刚刚'
  if (diff < 3600_000) return Math.floor(diff / 60_000) + ' 分钟前'
  if (diff < 86400_000) return Math.floor(diff / 3600_000) + ' 小时前'
  if (diff < 7 * 86400_000) return Math.floor(diff / 86400_000) + ' 天前'
  return new Date(ts).toLocaleString('zh-CN', { hour12: false }).slice(5, 16).replace(/\//g, '-')
}
function parseMeta(m) {
  if (!m.metadata) return {}
  if (typeof m.metadata === 'string') {
    try { return JSON.parse(m.metadata) } catch { return {} }
  }
  return m.metadata
}

// ===== 数据加载 =====
async function loadSessionList() {
  loadingSessions.value = true
  try {
    sessions.value = await listSessions(30)
  } catch (e) {
    toast.error('加载会话失败: ' + e.message)
  } finally {
    loadingSessions.value = false
  }
}

async function loadSession(id) {
  try {
    const r = await getSession(id)
    currentSession.value = r.session
    messages.value = r.messages || []
    scrollToBottom()
    // 等 markdown 渲染后再处理 chart 代码块
    nextTick(renderEmbeddedCharts)
  } catch (e) {
    toast.error('加载会话失败: ' + e.message)
  }
}

function onNewChat() {
  currentSession.value = null
  messages.value = []
  selfModel.value = selfOpts.value[0]?.value || '猛士M817'
  competitorModels.value = []
  userPrompt.value = ''
  followupText.value = ''
}

async function onStart() {
  if (!canStart.value) return
  busy.value = true
  try {
    const r = await startChat(selfModel.value, competitorModels.value, userPrompt.value)
    currentSession.value = r.session
    messages.value = [r.userMessage, r.assistantMessage]
    await loadSessionList()  // 刷新左侧
    scrollToBottom()
    nextTick(renderEmbeddedCharts)
    toast.success('深度对标完成')
  } catch (e) {
    toast.error('深度对标失败: ' + (e.response?.data?.message || e.message))
  } finally {
    busy.value = false
  }
}

async function onFollowup() {
  const txt = followupText.value.trim()
  if (!txt || busy.value) return
  followupText.value = ''
  busy.value = true
  try {
    const r = await followup(currentSession.value.id, txt)
    messages.value.push(r.userMessage)
    messages.value.push(r.assistantMessage)
    scrollToBottom()
    nextTick(renderEmbeddedCharts)
  } catch (e) {
    toast.error('追问失败: ' + (e.response?.data?.message || e.message))
  } finally {
    busy.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    const el = messagesRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

// ===== chart 代码块 → ECharts 渲染 =====
function renderEmbeddedCharts() {
  if (!messagesRef.value) return
  // md-editor-v3 把代码块渲染为 <pre><code class="language-chart">...</code></pre>
  const blocks = messagesRef.value.querySelectorAll('code.language-chart, pre code.language-chart')
  blocks.forEach((codeEl) => {
    const pre = codeEl.closest('pre')
    if (!pre || pre.dataset.echartsRendered === '1') return
    let cfg
    try {
      cfg = JSON.parse(codeEl.textContent.trim())
    } catch (e) {
      console.warn('chart parse fail', e)
      return
    }
    const container = document.createElement('div')
    container.className = 'echart-block'
    container.style.cssText = 'width:100%;height:340px;margin:12px 0;'
    pre.parentNode.replaceChild(container, pre)
    pre.dataset.echartsRendered = '1'
    const chart = echarts.init(container)
    chart.setOption(buildEchartsOption(cfg))
  })
}

function buildEchartsOption(cfg) {
  const { type, data } = cfg
  if (type === 'radar') {
    const indicators = data.labels.map(l => ({ name: l, max: 10 }))
    return {
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, data: data.datasets.map(d => d.label) },
      radar: { indicator: indicators, radius: '60%' },
      series: [{
        type: 'radar',
        data: data.datasets.map(d => ({
          name: d.label,
          value: d.data
        }))
      }]
    }
  }
  // line / bar 通用
  const xLabels = data.labels
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, data: data.datasets.map(d => d.label) },
    grid: { top: 20, left: 50, right: 30, bottom: 50 },
    xAxis: { type: 'category', data: xLabels },
    yAxis: { type: 'value' },
    series: data.datasets.map(d => ({
      name: d.label,
      type: type || 'line',
      data: d.data,
      smooth: type === 'line'
    }))
  }
}

// ===== Lifecycle =====
onMounted(async () => {
  try {
    allSeries.value = await listSeries()
  } catch (e) {
    toast.error('加载车系失败: ' + e.message)
  }
  await loadSessionList()
})
</script>

<style scoped>
.chat-page {
  display: flex; height: calc(100vh - 80px);
  background: var(--surface-base, #fbf9f4);
}

.sidebar {
  width: 240px; background: #fff; border-right: 1px solid #eee;
  display: flex; flex-direction: column; flex-shrink: 0;
}
.sidebar-head {
  padding: 12px 14px; border-bottom: 1px solid #eee;
}
.new-chat {
  width: 100%; padding: 8px 12px; border-radius: 6px;
  background: #c0392b; color: #fff; border: none; cursor: pointer;
  display: flex; align-items: center; gap: 6px; justify-content: center;
  font-size: 13px; font-weight: 500;
}
.new-chat:hover { background: #5C0009; }
.new-chat span { font-size: 16px; line-height: 1; }

.session-list { flex: 1; overflow-y: auto; padding: 8px; }
.session-item {
  padding: 10px 12px; border-radius: 6px; cursor: pointer; margin-bottom: 4px;
  border-left: 3px solid transparent;
}
.session-item:hover { background: #f9f7f3; }
.session-item.active { background: #fffaf5; border-left-color: #c0392b; }
.session-title {
  font-size: 13px; color: #171614; font-weight: 500;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.session-meta, .session-time { font-size: 11px; margin-top: 2px; }

.chat-main {
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
}
.chat-header {
  padding: 16px 24px; border-bottom: 1px solid #eee; background: #fff;
}
.chat-header h1 {
  margin: 0; font-size: 18px; color: #c0392b; font-family: Georgia, serif;
}
.chat-header p { margin: 4px 0 0; }

.welcome {
  flex: 1; display: flex; align-items: center; justify-content: center;
  padding: 24px;
}
.welcome-card {
  max-width: 720px; width: 100%; background: #fff;
  padding: 32px; border-radius: 8px; border: 1px solid #eee;
  box-shadow: 0 4px 16px rgba(0,0,0,0.04);
}
.welcome-card h2 {
  margin: 0 0 20px; font-size: 22px; color: #c0392b; font-family: Georgia, serif;
}
.form-row {
  display: flex; align-items: center; gap: 14px; margin: 16px 0;
}
.form-row label {
  min-width: 90px; color: #4b5563; font-size: 13px; font-weight: 500;
}
.competitor-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 8px; flex: 1;
}
.competitor-chip {
  display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px;
  background: #fff; border: 1px solid #e5e7eb; border-radius: 14px;
  font-size: 13px; cursor: pointer;
}
.competitor-chip:hover { border-color: #c0392b; }
.competitor-chip.selected { background: #c0392b; color: #fff; border-color: #c0392b; }
.competitor-chip input { margin: 0; }

.messages-area {
  flex: 1; display: flex; flex-direction: column; overflow: hidden;
}
.messages-scroll {
  flex: 1; overflow-y: auto; padding: 24px;
  display: flex; flex-direction: column; gap: 20px;
}
.message {
  display: flex; gap: 12px; max-width: 1100px;
}
.msg-avatar {
  font-size: 24px; width: 36px; height: 36px;
  background: #f9f7f3; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
}
.message.user .msg-avatar { background: #fffaf5; }
.msg-body { flex: 1; min-width: 0; }
.msg-role { margin-bottom: 4px; }
.msg-user-text {
  background: #fffaf5; padding: 10px 14px; border-radius: 8px;
  color: #171614; line-height: 1.6;
  border: 1px solid #f0e6dc;
}
.msg-md :deep(.md-editor-preview-wrapper) { padding: 0 !important; background: transparent; }
.msg-md :deep(.md-editor-preview) {
  padding: 0; background: transparent;
  font-size: 14px; line-height: 1.75; color: #171614;
}
.msg-md :deep(h1) {
  font-size: 22px; font-family: Georgia, serif;
  color: #c0392b; border-bottom: 2px solid #c0392b;
  padding-bottom: 8px; margin: 16px 0 12px;
}
.msg-md :deep(h2) {
  font-size: 18px; font-family: Georgia, serif;
  color: #171614; margin: 20px 0 10px;
  border-left: 4px solid #c0392b; padding-left: 10px;
}
.msg-md :deep(h3) { font-size: 15px; color: #1a1a1a; margin: 12px 0 8px; }
.msg-md :deep(strong) { color: #c0392b; }
.msg-md :deep(table) { width: 100%; border-collapse: collapse; margin: 10px 0; }
.msg-md :deep(th), .msg-md :deep(td) {
  border: 1px solid #e5e7eb; padding: 6px 10px; text-align: left;
}
.msg-md :deep(th) { background: #fffaf5; color: #c0392b; font-weight: 600; }
.msg-md :deep(blockquote) {
  margin: 10px 0; padding: 6px 12px; border-left: 3px solid #c0392b;
  background: #fffaf5; color: #555; font-style: italic;
}
.msg-md :deep(.echart-block) { width: 100%; height: 340px; margin: 12px 0; }

.analyst-tags {
  display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px;
}
.analyst-tag {
  background: #f9f7f3; border: 1px solid #e5e7eb;
  padding: 3px 10px; border-radius: 12px; font-size: 11px;
  color: #4b5563;
}
.analyst-tag strong { color: #c0392b; margin-left: 2px; }

.typing .typing-dots {
  display: inline-flex; gap: 4px; padding: 8px 0;
}
.typing-dots span {
  width: 6px; height: 6px; background: #c0392b; border-radius: 50%;
  animation: typing 1.4s infinite;
}
.typing-dots span:nth-child(2) { animation-delay: 0.2s; }
.typing-dots span:nth-child(3) { animation-delay: 0.4s; }
@keyframes typing {
  0%, 60%, 100% { opacity: 0.3; transform: scale(0.85); }
  30% { opacity: 1; transform: scale(1); }
}

.composer {
  padding: 16px 24px; border-top: 1px solid #eee; background: #fff;
  display: flex; gap: 10px;
}

.muted { color: #999; }
.small { font-size: 12px; }
.empty { padding: 24px 0; text-align: center; }
</style>
