<template>
  <div class="page">
    <PageMast title="采集内容查看">
      <template #controls>
        <USelect v-model="commonFilter.brand" :options="brandOpts" placeholder="全部品牌" clearable style="width:130px" />
        <UInput v-model="commonFilter.keyword" placeholder="关键词" style="width: 180px" clearable />
        <UDateRange v-model="commonFilter.crawlRange" placeholder="采集日期" />
        <USelect v-model="commonFilter.sortBy" :options="sortOpts" style="width: 120px" />
        <UButton variant="primary" size="sm" @click="handleQuery">查询</UButton>
        <UButton variant="quiet" size="sm" @click="handleReset">重置</UButton>
      </template>
    </PageMast>

    <UTabs v-model="activeTab" @change="handleTabChange">
      <UTabPane name="news" label="新闻">
        <div class="cv-subfilter">
          <UField label="发布日期" inline>
            <UDateRange v-model="newsFilter.publishedRange" placeholder="发布日期范围" />
          </UField>
          <UField label="数据源" inline>
            <USelect v-model="newsFilter.sourceTool" :options="sourceOpts" placeholder="全部" clearable style="width:140px" />
          </UField>
        </div>

        <UCard flush>
          <UTable :data="newsList" stripe row-clickable @row-click="openDrawer">
            <UTableColumn prop="title" label="标题" min-width="320" />
            <UTableColumn prop="brandName" label="品牌" :width="80" />
            <UTableColumn prop="searchQuery" label="搜索词" :width="120" />
            <UTableColumn label="发布" :width="110">
              <template #default="{ row }"><span class="tabular ts">{{ row.publishedDate || '—' }}</span></template>
            </UTableColumn>
            <UTableColumn label="采集" :width="110">
              <template #default="{ row }"><span class="tabular ts">{{ row.crawlDate }}</span></template>
            </UTableColumn>
            <UTableColumn label="数据源" :width="100">
              <template #default="{ row }"><UTag>{{ row.sourceTool }}</UTag></template>
            </UTableColumn>
            <UTableColumn label="相关度" :width="90" align="right">
              <template #default="{ row }">
                <span class="tabular">{{ row.relevanceScore != null ? row.relevanceScore.toFixed(2) : '—' }}</span>
              </template>
            </UTableColumn>
          </UTable>
        </UCard>
      </UTabPane>

      <UTabPane name="social" label="社交平台">
        <div class="cv-subfilter">
          <UField label="平台" inline>
            <USelect v-model="socialFilter.platforms" :options="platformOpts" multiple placeholder="全部平台" style="width: 280px" />
          </UField>
        </div>

        <UCard flush>
          <UTable :data="socialList" stripe row-clickable @row-click="openDrawer">
            <UTableColumn label="平台" :width="80">
              <template #default="{ row }"><UTag>{{ row.platformName }}</UTag></template>
            </UTableColumn>
            <UTableColumn prop="title" label="标题" min-width="280" />
            <UTableColumn prop="nickname" label="作者" :width="120" />
            <UTableColumn label="点赞" :width="80" align="right">
              <template #default="{ row }"><span class="tabular">{{ row.likedCount }}</span></template>
            </UTableColumn>
            <UTableColumn label="评论" :width="80" align="right">
              <template #default="{ row }"><span class="tabular">{{ row.commentCount }}</span></template>
            </UTableColumn>
            <UTableColumn label="分享" :width="80" align="right">
              <template #default="{ row }"><span class="tabular">{{ row.shareCount }}</span></template>
            </UTableColumn>
            <UTableColumn label="发布时间" :width="160">
              <template #default="{ row }"><span class="tabular ts">{{ formatTime(row.time) }}</span></template>
            </UTableColumn>
            <UTableColumn label="互动量" :width="100" align="right">
              <template #default="{ row }">
                <span class="tabular" style="font-weight:500">{{ Number(row.engagementScore || 0).toLocaleString() }}</span>
              </template>
            </UTableColumn>
          </UTable>
        </UCard>
      </UTabPane>
    </UTabs>

    <div class="cv-page-bar">
      <UPagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        @current-change="loadData"
        @size-change="loadData"
      />
    </div>

    <UDrawer v-model="drawerVisible" :title="drawerTitle" eyebrow="DETAIL" size="56%">
      <template v-if="currentRow">
        <h2 class="display drawer-title">{{ currentRow.title || '— 无标题 —' }}</h2>
        <span class="accent-rule"></span>

        <p v-if="currentRow.url" class="drawer-url">
          <a :href="currentRow.url" target="_blank" rel="noopener">{{ currentRow.url }}</a>
        </p>

        <dl class="drawer-meta">
          <template v-if="activeTab === 'news'">
            <div><dt>品牌</dt><dd>{{ currentRow.brandName }}</dd></div>
            <div><dt>搜索词</dt><dd>{{ currentRow.searchQuery }}</dd></div>
            <div><dt>发布日期</dt><dd class="tabular">{{ currentRow.publishedDate || '—' }}</dd></div>
            <div><dt>采集日期</dt><dd class="tabular">{{ currentRow.crawlDate }}</dd></div>
            <div><dt>数据源</dt><dd>{{ currentRow.sourceTool }}</dd></div>
            <div v-if="currentRow.relevanceScore != null"><dt>相关度</dt><dd class="tabular">{{ currentRow.relevanceScore.toFixed(2) }}</dd></div>
          </template>
          <template v-else>
            <div><dt>平台</dt><dd>{{ currentRow.platformName }}</dd></div>
            <div><dt>作者</dt><dd>{{ currentRow.nickname }}</dd></div>
            <div><dt>发布时间</dt><dd class="tabular">{{ formatTime(currentRow.time) }}</dd></div>
            <div><dt>互动量</dt><dd class="tabular">{{ Number(currentRow.engagementScore || 0).toLocaleString() }}</dd></div>
          </template>
        </dl>

        <div v-if="activeTab === 'social'" class="drawer-stats">
          <div><span class="eyebrow">点赞</span><span class="display tabular">{{ currentRow.likedCount }}</span></div>
          <div><span class="eyebrow">评论</span><span class="display tabular">{{ currentRow.commentCount }}</span></div>
          <div><span class="eyebrow">分享</span><span class="display tabular">{{ currentRow.shareCount }}</span></div>
          <div><span class="eyebrow">收藏</span><span class="display tabular">{{ currentRow.collectedCount }}</span></div>
          <div><span class="eyebrow">播放</span><span class="display tabular">{{ currentRow.viewCount }}</span></div>
        </div>

        <div class="drawer-content-label eyebrow">CONTENT</div>
        <div class="drawer-content">{{ currentRow.content || '（无正文）' }}</div>

        <template v-if="activeTab === 'social'">
          <div class="drawer-content-label eyebrow" style="margin-top:24px">
            COMMENTS · {{ commentList.length }}
            <span v-if="commentLoading" style="color:var(--ink-subtle);margin-left:8px">加载中...</span>
          </div>
          <ul v-if="commentList.length" class="comments">
            <li v-for="c in commentList" :key="c.commentId" class="comment">
              <div class="comment-head">
                <span class="comment-nick">{{ c.nickname || '匿名' }}</span>
                <span class="comment-meta tabular">♥ {{ c.likeCount || 0 }}</span>
                <span v-if="c.subCommentCount && c.subCommentCount !== '0'" class="comment-meta tabular">↳ {{ c.subCommentCount }}</span>
              </div>
              <div class="comment-body">{{ c.content }}</div>
            </li>
          </ul>
          <div v-else-if="!commentLoading" class="comments-empty">— 暂无评论 —</div>
        </template>
      </template>
    </UDrawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageMast from '../../components/layout/PageMast.vue'
import {
  UButton, USelect, UInput, UDateRange, UCard, UTabs, UTabPane,
  UTable, UTableColumn, UTag, UPagination, UDrawer, UField, toast
} from '../../components/ui'
import { queryNews, querySocial, queryComments } from '../../api/content'
import { listBrands } from '../../api/brandKeyword'

const activeTab = ref('news')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const commonFilter = ref({ brand: '', keyword: '', crawlRange: [], sortBy: 'crawlDate' })
const newsFilter = ref({ publishedRange: [], sourceTool: '' })
const socialFilter = ref({ platforms: [] })

const newsList = ref([])
const socialList = ref([])

const drawerVisible = ref(false)
const currentRow = ref(null)
const drawerTitle = ref('详情')

const brandOpts = ref([])
async function loadBrandOpts() {
  try {
    const list = await listBrands() || []
    brandOpts.value = list.map(b => ({ label: b, value: b }))
  } catch (e) { brandOpts.value = [] }
}
const platformOpts = [
  { label: '小红书', value: 'xhs' },
  { label: '抖音',   value: 'dy' },
  { label: 'B站',    value: 'bili' },
  { label: '微博',   value: 'wb' },
  { label: '快手',   value: 'ks' }
]
const sourceOpts = [
  { label: 'BochaAI', value: 'bocha' },
  { label: 'Tavily',  value: 'tavily' },
  { label: 'Anspire', value: 'anspire' }
]
const sortOptsNews = [
  { label: '采集时间', value: 'crawlDate' },
  { label: '相关度',   value: 'relevance' }
]
const sortOptsSocial = [
  { label: '互动量',   value: 'engagement' },
  { label: '发布时间', value: 'time' }
]
const sortOpts = computed(() => activeTab.value === 'news' ? sortOptsNews : sortOptsSocial)

function formatTime(ms) {
  if (!ms) return '—'
  const d = new Date(Number(ms))
  if (isNaN(d.getTime())) return '—'
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function buildCommonParams() {
  const p = { page: page.value, pageSize: pageSize.value, sortBy: commonFilter.value.sortBy }
  if (commonFilter.value.brand) p.brand = commonFilter.value.brand
  if (commonFilter.value.keyword) p.keyword = commonFilter.value.keyword
  const r = commonFilter.value.crawlRange
  if (r && r.length === 2) { p.crawlFrom = r[0]; p.crawlTo = r[1] }
  return p
}

async function loadData() {
  try {
    if (activeTab.value === 'news') {
      const params = buildCommonParams()
      const pr = newsFilter.value.publishedRange
      if (pr && pr.length === 2) { params.publishedFrom = pr[0]; params.publishedTo = pr[1] }
      if (newsFilter.value.sourceTool) params.sourceTool = newsFilter.value.sourceTool
      const res = await queryNews(params)
      newsList.value = res.list || []
      total.value = res.total || 0
    } else {
      if (!commonFilter.value.brand) {
        toast.warning('社交平台查询需要选择品牌')
        socialList.value = []; total.value = 0
        return
      }
      const params = buildCommonParams()
      if (socialFilter.value.platforms && socialFilter.value.platforms.length > 0) {
        params.platforms = socialFilter.value.platforms.join(',')
      }
      const res = await querySocial(params)
      socialList.value = res.list || []
      total.value = res.total || 0
    }
  } catch (e) {
    toast.error('查询失败：' + (e.message || e))
  }
}

function handleQuery() { page.value = 1; loadData() }
function handleReset() {
  commonFilter.value = { brand: '', keyword: '', crawlRange: [], sortBy: activeTab.value === 'news' ? 'crawlDate' : 'engagement' }
  newsFilter.value = { publishedRange: [], sourceTool: '' }
  socialFilter.value = { platforms: [] }
  page.value = 1; loadData()
}
function handleTabChange() {
  page.value = 1
  commonFilter.value.sortBy = activeTab.value === 'news' ? 'crawlDate' : 'engagement'
  loadData()
}
const commentList = ref([])
const commentLoading = ref(false)

async function openDrawer(row) {
  currentRow.value = row
  drawerTitle.value = activeTab.value === 'news' ? '新闻详情' : '内容详情'
  commentList.value = []
  drawerVisible.value = true
  // 社交平台内容才有评论
  if (activeTab.value === 'social' && row.platform && row.contentId) {
    commentLoading.value = true
    try {
      commentList.value = await queryComments(row.platform, row.contentId, 30) || []
    } catch (e) { commentList.value = [] }
    finally { commentLoading.value = false }
  }
}

onMounted(() => { loadBrandOpts(); loadData() })
</script>

<style scoped>
.page {
  background: var(--bg);
  padding: var(--space-6) var(--space-8) var(--space-10);
  max-width: 1480px;
  margin: 0 auto;
}
.cv-subfilter {
  display: flex;
  gap: var(--space-5);
  align-items: center;
  margin-bottom: var(--space-4);
  flex-wrap: wrap;
}
.cv-page-bar {
  margin-top: var(--space-5);
  display: flex;
  justify-content: flex-end;
}
.ts { font-size: var(--text-xs); color: var(--ink-muted); }

.drawer-title {
  font-size: var(--text-2xl);
  font-weight: 500;
  letter-spacing: var(--tracking-tight);
  line-height: var(--leading-tight);
}
.drawer-url {
  margin-top: var(--space-4);
  word-break: break-all;
}
.drawer-url a {
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  text-decoration: underline;
  text-underline-offset: 3px;
}
.drawer-meta {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--space-4) var(--space-6);
  margin: var(--space-6) 0;
  padding: var(--space-4) 0;
  border-top: 1px solid var(--hairline);
  border-bottom: 1px solid var(--hairline);
}
.drawer-meta dt {
  font-size: 10px;
  font-weight: 600;
  letter-spacing: var(--tracking-wider);
  text-transform: uppercase;
  color: var(--ink-subtle);
  margin-bottom: 2px;
}
.drawer-meta dd {
  margin: 0;
  font-size: var(--text-sm);
  color: var(--ink);
}
.drawer-stats {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  border-top: 1px solid var(--hairline);
  border-bottom: 1px solid var(--hairline);
  margin-bottom: var(--space-6);
}
.drawer-stats > div {
  padding: var(--space-3) var(--space-2);
  border-right: 1px solid var(--hairline);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.drawer-stats > div:last-child { border-right: none; }
.drawer-stats .display {
  font-size: var(--text-md);
  font-weight: 500;
  color: var(--ink);
}
.drawer-content-label { margin-bottom: var(--space-3); }
.drawer-content {
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--ink);
  font-size: var(--text-base);
  line-height: var(--leading-loose);
}

.comments {
  list-style: none;
  margin: 0;
  padding: 0;
}
.comment {
  padding: var(--space-3) 0;
  border-bottom: 1px solid var(--hairline);
}
.comment:last-child { border-bottom: none; }
.comment-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-3);
  margin-bottom: 4px;
}
.comment-nick {
  font-size: var(--text-sm);
  font-weight: var(--weight-medium);
  color: var(--ink);
}
.comment-meta {
  font-size: var(--text-xs);
  color: var(--ink-muted);
}
.comment-body {
  font-size: var(--text-sm);
  color: var(--ink);
  line-height: var(--leading-normal);
  white-space: pre-wrap;
  word-break: break-word;
}
.comments-empty {
  padding: var(--space-6) 0;
  text-align: center;
  color: var(--ink-subtle);
  font-style: italic;
  font-family: var(--font-display);
}
</style>
