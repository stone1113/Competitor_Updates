<template>
  <div class="page">
    <PageMast title="按对标车型金融政策">
      <template #controls>
        <UInput v-model="searchKey" placeholder="搜车型（如 豹8）" clearable style="width:160px" @keyup.enter="searchOne" />
        <UButton variant="ghost" size="sm" @click="searchOne" :loading="searching">查询</UButton>
        <UButton variant="ghost" size="sm" @click="loadAll" :loading="loading">刷新全部</UButton>
      </template>
    </PageMast>

    <div v-if="loading" class="muted center">加载中…</div>
    <div v-else-if="groups.length === 0" class="muted center">— 暂无数据 —</div>

    <div v-else class="vs-groups">
      <div v-for="(group, vsKey) in groupedByVs" :key="vsKey" class="vs-group">
        <h2 class="vs-title">🔸 vs {{ vsKey }}</h2>
        <div class="card-grid">
          <article
            v-for="g in group"
            :key="g.model"
            class="model-card"
            :class="{ self: g.role === 'self', empty: g.count === 0 }"
          >
            <header class="model-head">
              <span class="model-name">
                <template v-if="g.role === 'self'">🔹 <strong>{{ g.model }}</strong></template>
                <template v-else>{{ g.model }}</template>
              </span>
              <span class="badge" :class="g.role === 'self' ? 'self-tag' : 'comp-tag'">
                {{ g.role === 'self' ? '本品' : '竞品' }}
              </span>
            </header>

            <div v-if="g.count === 0" class="empty-msg">
              <span class="muted">该车型官号暂无金融/上市政策帖</span>
            </div>

            <ul v-else class="post-list">
              <li v-for="(post, idx) in g.posts" :key="post.id" class="post-item">
                <div class="post-row">
                  <span class="badge official">🅾️ 官号</span>
                  <span v-if="post.eventImportance >= 8" class="badge star">⭐</span>
                  <span class="event-tag" :class="post.eventType">
                    {{ post.eventType === 'launch' ? '🚀 上市' : '💰 价金' }}
                  </span>
                  <span class="muted small">{{ formatTime(post.addTs) }}</span>
                </div>
                <a :href="post.url" target="_blank" class="summary-link">
                  {{ post.eventSummary || post.title }}
                </a>
                <div v-if="post.hasPolicy" class="policy-pill">✓ 已识别政策</div>

                <div v-if="post.imageUrls" class="img-row">
                  <img
                    v-for="(url, ii) in parseImages(post.imageUrls).slice(0, 3)"
                    :key="ii"
                    :src="proxyImage(url)"
                    @click="openImage(url)"
                    class="thumb"
                    loading="lazy"
                  />
                  <span v-if="parseImages(post.imageUrls).length > 3" class="more-imgs muted">
                    +{{ parseImages(post.imageUrls).length - 3 }}
                  </span>
                </div>

                <details v-if="post.imageOcrText" class="ocr-details">
                  <summary>展开 OCR 文字</summary>
                  <pre class="ocr-text">{{ post.imageOcrText }}</pre>
                </details>
              </li>
            </ul>
          </article>
        </div>
      </div>
    </div>

    <!-- 图片放大查看 -->
    <UDialog v-model="imgDialogOpen" :title="''" width="800px">
      <img v-if="imgDialogUrl" :src="proxyImage(imgDialogUrl)" class="img-large" />
    </UDialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageMast from '../../components/layout/PageMast.vue'
import { UButton, UInput, UDialog, toast } from '../../components/ui'
import { financeAllModels, financeByModel } from '../../api/finance'

const loading = ref(false)
const searching = ref(false)
const groups = ref([])
const searchKey = ref('')

const imgDialogOpen = ref(false)
const imgDialogUrl = ref('')

// 按 vs_self_model 分组（self 自成一组，竞品归到对应 self 下）
const groupedByVs = computed(() => {
  const map = {}
  // 1. 收集所有 self 作为分组 key
  for (const g of groups.value) {
    if (g.role === 'self') {
      map[g.model] = []
    }
  }
  // 2. self 放第一位
  for (const g of groups.value) {
    if (g.role === 'self') {
      map[g.model].push(g)
    }
  }
  // 3. competitor 按 vsSelfModel 归类
  for (const g of groups.value) {
    if (g.role === 'competitor' && g.vsSelfModel && map[g.vsSelfModel]) {
      map[g.vsSelfModel].push(g)
    }
  }
  return map
})

async function loadAll() {
  loading.value = true
  try {
    const data = await financeAllModels(3)
    groups.value = data.groups || []
  } catch (e) {
    toast.error('加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function searchOne() {
  if (!searchKey.value.trim()) return loadAll()
  searching.value = true
  try {
    const data = await financeByModel(searchKey.value, 10)
    groups.value = [{
      model: data.model,
      brand: data.modelKey,
      role: 'competitor',
      vsSelfModel: '搜索结果',
      count: data.count,
      posts: data.posts || []
    }]
  } catch (e) {
    toast.error('查询失败: ' + e.message)
  } finally {
    searching.value = false
  }
}

function parseImages(csv) {
  if (!csv) return []
  return csv.split(',').map(s => s.trim()).filter(Boolean)
}

/** 通过后端代理加载外站图片（绕 sinaimg 等防盗链/ORB）。 */
function proxyImage(url) {
  if (!url) return ''
  return `/api/v1/proxy/image?url=${encodeURIComponent(url)}`
}

function formatTime(ts) {
  if (!ts) return ''
  return new Date(ts).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
}

function openImage(url) {
  imgDialogUrl.value = url
  imgDialogOpen.value = true
}

onMounted(loadAll)
</script>

<style scoped>
.page { padding: 16px 20px; }
.muted { color: #999; }
.center { text-align: center; padding: 40px 0; }
.small { font-size: 12px; }

.vs-group { margin-top: 24px; }
.vs-title {
  font-size: 16px; font-weight: 600; margin: 0 0 12px;
  color: #c0392b; border-bottom: 2px solid #c0392b; padding-bottom: 6px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
  gap: 16px;
}

.model-card {
  background: #fff; border: 1px solid #eee; border-radius: 8px;
  padding: 14px 16px; display: flex; flex-direction: column;
}
.model-card.self { border-color: #c0392b; background: #fffaf5; }
.model-card.empty { opacity: 0.6; }

.model-head {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 12px;
}
.model-name { font-size: 15px; color: #171614; }
.model-name strong { color: #c0392b; }

.badge {
  display: inline-block; padding: 2px 8px; border-radius: 10px; font-size: 11px;
}
.self-tag { background: #c0392b; color: #fff; }
.comp-tag { background: #f0f0f0; color: #666; }
.official { background: #fff3e0; color: #e65100; padding: 1px 6px; margin-right: 4px; }
.star { background: #ffd700; color: #333; padding: 1px 6px; margin-right: 4px; }

.event-tag {
  display: inline-block; padding: 1px 8px; border-radius: 10px; font-size: 11px;
  margin-right: 6px;
}
.event-tag.launch { background: #e3f2fd; color: #1976d2; }
.event-tag.price_finance { background: #ffebee; color: #c0392b; }

.empty-msg { padding: 20px 0; text-align: center; }

.post-list { list-style: none; padding: 0; margin: 0; }
.post-item {
  padding: 10px 0; border-top: 1px dashed #eee;
}
.post-item:first-child { border-top: none; padding-top: 0; }

.post-row { display: flex; align-items: center; margin-bottom: 6px; }

.summary-link {
  display: block; color: #171614; text-decoration: none;
  font-size: 13.5px; line-height: 1.5; margin-bottom: 6px;
}
.summary-link:hover { color: #c0392b; text-decoration: underline; }

.policy-pill {
  display: inline-block; background: #e8f5e9; color: #2e7d32;
  font-size: 11px; padding: 2px 8px; border-radius: 4px; margin: 4px 0;
}

.img-row { display: flex; gap: 6px; margin: 8px 0; }
.thumb {
  width: 60px; height: 60px; object-fit: cover; border-radius: 4px;
  cursor: pointer; border: 1px solid #eee;
}
.thumb:hover { border-color: #c0392b; }
.more-imgs { align-self: center; font-size: 12px; }

.ocr-details {
  margin-top: 8px;
  font-size: 12px;
}
.ocr-details summary {
  cursor: pointer; color: #999; padding: 4px 0;
}
.ocr-text {
  background: #f9f7f3; padding: 8px 10px; border-radius: 4px;
  white-space: pre-wrap; word-break: break-word; max-height: 200px;
  overflow-y: auto; font-family: 'JetBrains Mono', monospace; font-size: 11px;
  color: #555;
}

.img-large { max-width: 100%; max-height: 80vh; display: block; margin: 0 auto; }
</style>
