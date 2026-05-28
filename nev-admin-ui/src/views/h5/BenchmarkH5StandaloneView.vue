<template>
  <div class="h5-page">
    <!-- 顶栏：仅在登录态/loading/error 时显示；iframe 模式下完全隐藏给 RAGFlow 最大空间 -->
    <header v-if="!iframeUrl" class="h5-bar">
      <div class="title">
        <span class="dot"></span>
        <span>智能竞品对标</span>
      </div>
    </header>

    <!-- iframe 模式下的浮动用户徽章（右上角，可点击查看/退出） -->
    <div v-if="iframeUrl && user" class="user-badge" :class="{ open: badgeOpen }">
      <img
        v-if="user.avatar && !user.avatarBroken"
        :src="user.avatar"
        class="badge-avatar"
        @error="user.avatarBroken = true"
        @click="badgeOpen = !badgeOpen"
        :alt="user.name"
      />
      <span v-else class="badge-avatar fallback" @click="badgeOpen = !badgeOpen">
        {{ (user.name || '?').slice(0, 1) }}
      </span>
      <div v-show="badgeOpen" class="badge-popup">
        <div class="pop-name">{{ user.name }}</div>
        <div class="pop-meta">{{ user.email || user.mobile || '飞书用户' }}</div>
        <button class="pop-logout" @click="logout">退出登录</button>
      </div>
    </div>

    <main class="h5-main">
      <div v-if="loading" class="center pad">
        <div class="spinner"></div>
        <span>{{ loadingMessage }}</span>
      </div>

      <div v-else-if="error" class="center pad muted">
        <h3 class="error-title">⚠ {{ error.title }}</h3>
        <p>{{ error.message }}</p>
        <button v-if="error.retry" class="btn" @click="load">重试</button>
      </div>

      <div v-else-if="needLogin" class="center pad login-panel">
        <div class="logo">🚗</div>
        <h2>欢迎使用猛士竞品对标</h2>
        <p class="muted">请用飞书账号登录后查看本品 vs 竞品的技术对标分析</p>
        <a class="login-btn" :href="loginUrl">
          <span class="feishu-icon">F</span>
          飞书登录
        </a>
        <p class="muted small">登录后会根据你的身份保留独立对话</p>
      </div>

      <iframe
        v-else-if="iframeUrl"
        ref="iframeRef"
        :key="iframeKey"
        :src="iframeUrl"
        sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox"
        allow="clipboard-read; clipboard-write"
        @load="onIframeLoad"
      />
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const loadingMessage = ref('加载中…')
const user = ref(null)
const needLogin = ref(false)
const loginUrl = ref('')
const iframeUrl = ref('')
const iframeKey = ref(0)
const iframeRef = ref(null)
const badgeOpen = ref(false)
const error = ref(null)

const STORAGE_KEY = 'feishu_user_v1'

function readCachedUser() {
  try { const raw = localStorage.getItem(STORAGE_KEY); return raw ? JSON.parse(raw) : null }
  catch { return null }
}

function logout() {
  localStorage.removeItem(STORAGE_KEY)
  user.value = null
  iframeUrl.value = ''
  badgeOpen.value = false
  load()
}

async function exchangeCode(code) {
  loadingMessage.value = '正在验证飞书登录…'
  const res = await fetch(`/api/v1/feishu-auth/callback?code=${encodeURIComponent(code)}`)
  const r = await res.json()
  if (r.code !== 200 || !r.data?.user) throw new Error(r.message || '飞书登录失败')
  return r.data.user
}

async function fetchIframeUrl(userId) {
  loadingMessage.value = '正在加载对标会话…'
  const q = userId ? `?userId=${encodeURIComponent(userId)}` : ''
  const res = await fetch(`/api/v1/competitor-report/deep-analysis-url${q}`)
  const r = await res.json()
  if (r.code !== 200) throw new Error(r.message || '获取对话地址失败')
  return r.data
}

/** iframe 加载完成后注入 CSS 压缩 RAGFlow 内边距（同源才能注入） */
function onIframeLoad() {
  try {
    const doc = iframeRef.value?.contentDocument
    if (!doc) return
    // CSS 注入：移动端紧凑化 RAGFlow share 页
    const style = doc.createElement('style')
    style.textContent = `
      /* 隐藏 RAGFlow 自带 header（标题栏） */
      [class*="EmbedContainer"] > div:first-child,
      header[class*="header"] { display: none !important; }
      /* 缩小聊天主区上下 padding */
      [class*="h-\\[90vh\\]"] { height: 100vh !important; margin: 0 !important; }
      .p-2\\.5, .m-3 { padding: 0.25rem !important; margin: 0 !important; }
      /* 消息列表两侧 margin 在小屏全宽 */
      @media (max-width: 768px) {
        .md\\:w-5\\/6 { width: 100% !important; padding: 0 8px !important; }
        .md\\:mb-8 { margin-bottom: 0.5rem !important; }
        /* 缩小气泡内边距 */
        [class*="message-item"] { padding: 6px 8px !important; }
        /* 输入框下方占用变小 */
        textarea { min-height: 36px !important; }
      }
    `
    doc.head.appendChild(style)
  } catch (e) {
    // 跨 origin 静默失败
    console.debug('iframe CSS injection skipped:', e.message)
  }
}

async function load() {
  loading.value = true
  error.value = null
  needLogin.value = false
  iframeUrl.value = ''

  try {
    // 1. OAuth 回调
    const code = route.query.code
    if (code) {
      try {
        const u = await exchangeCode(code)
        localStorage.setItem(STORAGE_KEY, JSON.stringify(u))
        user.value = u
        router.replace({ path: route.path, query: {} })
      } catch (e) {
        console.warn('OAuth callback failed:', e)
      }
    }

    // 2. 缓存用户
    if (!user.value) user.value = readCachedUser()

    // 3. iframe url
    const info = await fetchIframeUrl(user.value?.openId)

    if (!info.configured) {
      error.value = { title: '系统未配置', message: info.message || '管理员需先配置 RAGFlow' }
      return
    }

    if (info.requireFeishuLogin && !user.value) {
      needLogin.value = true
      loginUrl.value = info.feishuLoginUrl || '/api/v1/feishu-auth/login'
      return
    }

    iframeUrl.value = info.url
    iframeKey.value++
  } catch (e) {
    error.value = { title: '加载失败', message: e.message || String(e), retry: true }
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.h5-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  background: var(--bg, #fbf9f4);
  overflow: hidden;
  position: relative;
}

/* 顶栏 — 仅 loading/error/login 时显示 */
.h5-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: var(--surface, #fff);
  border-bottom: 1px solid var(--hairline, #e5e1d8);
  flex-shrink: 0;
  height: 44px;
  box-sizing: border-box;
}
.title {
  display: flex; align-items: center; gap: 6px;
  font-weight: 600;
  color: var(--ink, #171614);
  font-size: 14px;
}
.dot {
  width: 6px; height: 6px;
  background: var(--accent, #c0392b);
  border-radius: 50%;
}

/* 浮动用户徽章 — iframe 模式下右上角悬浮 */
.user-badge {
  position: fixed;
  top: 10px;
  right: 10px;
  z-index: 100;
}
.badge-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  object-fit: cover;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(0,0,0,0.15);
  border: 2px solid white;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: var(--accent, #c0392b);
  color: white;
  font-weight: 600;
  font-size: 13px;
}
.badge-avatar.fallback { padding: 0; }
.badge-popup {
  position: absolute;
  top: 38px;
  right: 0;
  background: white;
  border: 1px solid var(--hairline, #e5e1d8);
  border-radius: 6px;
  padding: 12px 14px;
  min-width: 180px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.1);
}
.pop-name {
  font-weight: 600;
  color: var(--ink, #171614);
  font-size: 14px;
}
.pop-meta {
  color: var(--ink-muted, #6e6a63);
  font-size: 12px;
  margin: 4px 0 8px;
  word-break: break-all;
}
.pop-logout {
  background: none;
  border: 1px solid var(--hairline, #e5e1d8);
  color: var(--ink-muted, #6e6a63);
  padding: 6px 12px;
  cursor: pointer;
  font-size: 12px;
  border-radius: 4px;
  width: 100%;
}
.pop-logout:hover {
  color: var(--accent, #c0392b);
  border-color: var(--accent, #c0392b);
}

.h5-main {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
iframe {
  width: 100%;
  height: 100%;
  border: none;
  flex: 1;
  display: block;
}
.center {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  height: 100%; gap: 12px;
  padding: 40px 20px;
  text-align: center;
}
.pad { padding: 40px 20px; }
.muted { color: var(--ink-muted, #6e6a63); }
.small { font-size: 12px; }
.error-title { color: var(--accent, #c0392b); margin: 0 0 8px; }

.spinner {
  width: 28px; height: 28px;
  border: 3px solid var(--hairline, #e5e1d8);
  border-top-color: var(--accent, #c0392b);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.login-panel { gap: 16px; }
.logo { font-size: 48px; }
.login-panel h2 {
  font-weight: 600; font-size: 18px; margin: 0;
  color: var(--ink, #171614);
}
.login-btn {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 12px 32px;
  background: #3370ff;
  color: white;
  text-decoration: none;
  font-weight: 500;
  font-size: 15px;
  border-radius: 6px;
  margin-top: 8px;
  transition: opacity 0.2s;
}
.login-btn:hover { opacity: 0.9; }
.feishu-icon {
  background: white; color: #3370ff;
  width: 20px; height: 20px; border-radius: 4px;
  display: inline-flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 13px;
}
.btn {
  padding: 8px 20px;
  background: var(--accent, #c0392b);
  color: white; border: none; cursor: pointer;
  border-radius: 4px;
}

/* iOS safe area */
@supports (padding: env(safe-area-inset-top)) {
  .h5-bar { padding-top: calc(10px + env(safe-area-inset-top)); height: calc(44px + env(safe-area-inset-top)); }
  .user-badge { top: calc(10px + env(safe-area-inset-top)); }
}
</style>
