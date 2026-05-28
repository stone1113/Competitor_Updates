<template>
  <div class="page">
    <PageMast title="竞品官方账号">
      <template #controls>
        <USelect v-model="filterPlatform" :options="platformOpts" placeholder="全部平台" clearable style="width:140px" @change="loadData" />
        <UButton variant="ghost" size="sm" :loading="crawling" @click="handleCrawlAll">立即抓取</UButton>
        <UButton variant="ghost" size="sm" :loading="ingesting" @click="handleIngest">立即 Ingest</UButton>
        <UButton variant="primary" size="sm" @click="openAdd">新增账号</UButton>
      </template>
    </PageMast>

    <UCard flush>
      <UTable :data="rows" stripe empty-text="— 暂无配置 —">
        <UTableColumn prop="id" label="ID" :width="60" />
        <UTableColumn label="平台" :width="80">
          <template #default="{ row }">
            <UTag :variant="row.platform === 'wb' ? 'accent' : 'default'">{{ platformLabel(row.platform) }}</UTag>
          </template>
        </UTableColumn>
        <UTableColumn prop="brandName" label="品牌" :width="100" />
        <UTableColumn prop="accountName" label="账号昵称" min-width="140" />
        <UTableColumn label="account_id" min-width="160">
          <template #default="{ row }">
            <code>{{ row.accountId }}</code>
          </template>
        </UTableColumn>
        <UTableColumn label="主页" :width="100">
          <template #default="{ row }">
            <a v-if="row.accountUrl" :href="row.accountUrl" target="_blank" rel="noopener" class="muted">打开 ↗</a>
            <span v-else class="muted">—</span>
          </template>
        </UTableColumn>
        <UTableColumn label="启用" :width="70">
          <template #default="{ row }">
            <USwitch v-model="row.isEnabled" @change="handleToggle(row)" />
          </template>
        </UTableColumn>
        <UTableColumn label="上次抓取" :width="140">
          <template #default="{ row }">
            <span class="muted small">{{ formatTs(row.lastCrawlTs) }}</span>
          </template>
        </UTableColumn>
        <UTableColumn label="操作" :width="120">
          <template #default="{ row }">
            <UButton variant="link" size="sm" @click="openEdit(row)">编辑</UButton>
            <UPopconfirm message="确认删除？" @confirm="handleDelete(row.id)">
              <UButton variant="link" size="sm">删除</UButton>
            </UPopconfirm>
          </template>
        </UTableColumn>
      </UTable>
    </UCard>

    <UDialog v-model="dialogVisible" :title="form.id ? '编辑官方账号' : '新增官方账号'" width="520px">
      <UField label="平台">
        <USelect v-model="form.platform" :options="platformOpts" />
      </UField>
      <UField label="品牌">
        <UInput v-model="form.brandName" placeholder="例如：理想 / 问界 / 仰望" />
      </UField>
      <UField label="account_id" hint="微博：weibo.com/u/XXX 里的数字；抖音：从分享链接抓 sec_uid；小红书：profile id">
        <UInput v-model="form.accountId" placeholder="例如：6196111796 (微博)" />
      </UField>
      <UField label="昵称">
        <UInput v-model="form.accountName" placeholder="例如：理想汽车" />
      </UField>
      <UField label="主页 URL（可选）">
        <UInput v-model="form.accountUrl" placeholder="https://weibo.com/u/6196111796" />
      </UField>
      <UField label="启用" inline>
        <USwitch v-model="form.isEnabled" />
      </UField>
      <UField label="备注">
        <UInput v-model="form.remark" />
      </UField>
      <template #footer>
        <UButton variant="quiet" @click="dialogVisible = false">取消</UButton>
        <UButton variant="primary" @click="handleSave">保存</UButton>
      </template>
    </UDialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PageMast from '../../components/layout/PageMast.vue'
import {
  UButton, USelect, UInput, USwitch, UCard, UTable, UTableColumn,
  UDialog, UPopconfirm, UField, UTag, toast
} from '../../components/ui'
import {
  listOfficialAccounts, createOfficialAccount, updateOfficialAccount,
  deleteOfficialAccount, crawlNow, ingestNow
} from '../../api/officialAccount'

const rows = ref([])
const filterPlatform = ref('')
const dialogVisible = ref(false)
const form = ref({})
const crawling = ref(false)
const ingesting = ref(false)

const platformOpts = [
  { label: '微博 (wb)', value: 'wb' },
  { label: '抖音 (dy)', value: 'dy' },
  { label: '小红书 (xhs)', value: 'xhs' }
]

function platformLabel(p) {
  return { wb: '微博', dy: '抖音', xhs: '小红书' }[p] || p
}

function formatTs(ts) {
  if (!ts) return '从未'
  const d = new Date(ts)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadData() {
  try {
    const params = {}
    if (filterPlatform.value) params.platform = filterPlatform.value
    rows.value = await listOfficialAccounts(params) || []
  } catch (e) { rows.value = [] }
}

function openAdd() {
  form.value = { brandName: '', platform: 'wb', accountId: '', accountName: '', accountUrl: '', isEnabled: true, remark: '' }
  dialogVisible.value = true
}
function openEdit(row) {
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (form.value.id) {
      await updateOfficialAccount(form.value.id, form.value)
    } else {
      await createOfficialAccount(form.value)
    }
    toast.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    toast.error('保存失败：' + (e.message || e))
  }
}

async function handleToggle(row) {
  try {
    await updateOfficialAccount(row.id, row)
  } catch (e) {
    toast.error('切换失败')
    row.isEnabled = !row.isEnabled
  }
}

async function handleDelete(id) {
  try {
    await deleteOfficialAccount(id)
    toast.success('已删除')
    loadData()
  } catch (e) { toast.error('删除失败') }
}

async function handleCrawlAll() {
  crawling.value = true
  try {
    const r = await crawlNow(filterPlatform.value)
    toast.success(`已触发 ${r.tasksFired} 个任务，覆盖 ${r.accountsCovered} 个账号`)
    loadData()
  } catch (e) {
    toast.error('抓取触发失败：' + (e.message || e))
  } finally {
    crawling.value = false
  }
}

async function handleIngest() {
  ingesting.value = true
  try {
    const r = await ingestNow()
    toast.success(`已 ingest：扫描 ${r.scanned} 条，新增 ${r.inserted} 条`)
  } catch (e) {
    toast.error('Ingest 失败：' + (e.message || e))
  } finally {
    ingesting.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.page {
  background: var(--bg);
  padding: var(--space-6) var(--space-8) var(--space-10);
  max-width: 1320px;
  margin: 0 auto;
}
.muted { color: var(--ink-subtle); }
.small { font-size: var(--text-xs); }
code {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  background: var(--surface-sunk);
  padding: 2px 6px;
}
a.muted { text-decoration: none; }
a.muted:hover { color: var(--accent); }
</style>
