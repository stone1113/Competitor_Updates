<template>
  <div class="page">
    <PageMast title="汽车之家车系配置">
      <template #controls>
        <USelect v-model="filterRole" :options="roleOpts" placeholder="全部角色" clearable style="width:140px" @change="loadData" />
        <UButton variant="ghost" size="sm" :loading="syncingAll" @click="handleSyncAll">全量抓取</UButton>
        <UButton variant="primary" size="sm" @click="openAddDialog">新增车系</UButton>
      </template>
    </PageMast>

    <UCard flush>
      <UTable :data="rows" stripe empty-text="— 暂无配置 —">
        <UTableColumn prop="id" label="ID" :width="60" />
        <UTableColumn label="角色" :width="80">
          <template #default="{ row }">
            <UTag :variant="row.role === 'self' ? 'accent' : 'default'">
              {{ row.role === 'self' ? '本品' : '竞品' }}
            </UTag>
          </template>
        </UTableColumn>
        <UTableColumn prop="brandName" label="品牌" :width="110" />
        <UTableColumn prop="modelName" label="车型" min-width="160" />
        <UTableColumn prop="seriesId" label="series_id" :width="100" />
        <UTableColumn label="对标本品" :width="120">
          <template #default="{ row }">
            <span v-if="row.vsSelfModel" class="vs-tag">{{ row.vsSelfModel }}</span>
            <span v-else class="muted">—</span>
          </template>
        </UTableColumn>
        <UTableColumn label="启用" :width="70">
          <template #default="{ row }">
            <USwitch v-model="row.isEnabled" @change="handleToggle(row)" />
          </template>
        </UTableColumn>
        <UTableColumn label="最近抓取" :width="120">
          <template #default="{ row }">
            <span v-if="row.lastCrawlDate">{{ row.lastCrawlDate }} <span class="muted">({{ row.latestParamCount }})</span></span>
            <span v-else class="muted">—</span>
          </template>
        </UTableColumn>
        <UTableColumn label="操作" :width="220">
          <template #default="{ row }">
            <UButton variant="link" size="sm" :loading="syncingId === row.seriesId" @click="handleSyncOne(row)">立刻抓取</UButton>
            <UButton variant="link" size="sm" @click="openEditDialog(row)">编辑</UButton>
            <UPopconfirm message="确认删除此车系配置？" @confirm="handleDelete(row.id)">
              <UButton variant="link" size="sm">删除</UButton>
            </UPopconfirm>
          </template>
        </UTableColumn>
      </UTable>
    </UCard>

    <UDialog v-model="dialogVisible" :title="form.id ? '编辑车系' : '新增车系'" width="500px">
      <UField label="角色">
        <USelect v-model="form.role" :options="roleOpts" />
      </UField>
      <UField label="品牌">
        <UInput v-model="form.brandName" placeholder="例如：仰望" />
      </UField>
      <UField label="车型">
        <UInput v-model="form.modelName" placeholder="例如：仰望U8" />
      </UField>
      <UField label="series_id" hint="汽车之家 URL https://car.autohome.com.cn/config/series/{ID}.html 里的数字">
        <UInput v-model="form.seriesId" placeholder="例如：7227" />
      </UField>
      <UField label="对标本品" hint="留空 = 仅追踪不入对标矩阵；填本品车型名（如「猛士M817」「猛士917」）">
        <USelect v-model="form.vsSelfModel" :options="selfModelOpts" clearable />
      </UField>
      <UField label="启用" inline>
        <USwitch v-model="form.isEnabled" />
      </UField>
      <UField label="备注（可选）">
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
  listSeries, createSeries, updateSeries, deleteSeries,
  syncSeries, syncAllSeries
} from '../../api/autohome'

const rows = ref([])
const filterRole = ref('')
const roleOpts = [
  { label: '本品 (self)', value: 'self' },
  { label: '竞品 (competitor)', value: 'competitor' }
]
const selfModelOpts = [
  { label: '猛士M817', value: '猛士M817' },
  { label: '猛士917',  value: '猛士917' }
]

const dialogVisible = ref(false)
const form = ref({})

const syncingId = ref('')
const syncingAll = ref(false)

async function loadData() {
  try {
    rows.value = await listSeries(filterRole.value) || []
  } catch (e) { rows.value = [] }
}

function openAddDialog() {
  form.value = { brandName: '', modelName: '', seriesId: '', role: 'competitor', vsSelfModel: '', isEnabled: true, remark: '' }
  dialogVisible.value = true
}
function openEditDialog(row) {
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (form.value.id) {
      await updateSeries(form.value.id, form.value)
    } else {
      await createSeries(form.value)
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
    await updateSeries(row.id, row)
  } catch (e) {
    toast.error('切换失败')
    row.isEnabled = !row.isEnabled
  }
}

async function handleDelete(id) {
  try {
    await deleteSeries(id)
    toast.success('已删除')
    loadData()
  } catch (e) { toast.error('删除失败') }
}

async function handleSyncOne(row) {
  syncingId.value = row.seriesId
  try {
    const r = await syncSeries(row.seriesId)
    if (r && (r.totalRows > 0)) {
      toast.success(`抓取完成：${r.totalRows} 行参数`)
    } else {
      toast.warning('抓取无数据，请检查 series_id 是否正确')
    }
    loadData()
  } catch (e) {
    toast.error('抓取失败：' + (e.message || e))
  } finally {
    syncingId.value = ''
  }
}

async function handleSyncAll() {
  syncingAll.value = true
  try {
    const r = await syncAllSeries()
    toast.success(`全量抓取完成：${r.successSeriesCount} 车系 / ${r.totalRows} 行参数`)
    loadData()
  } catch (e) {
    toast.error('全量抓取失败：' + (e.message || e))
  } finally {
    syncingAll.value = false
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
.vs-tag {
  display: inline-block;
  padding: 1px 6px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: var(--text-xs);
  font-weight: var(--weight-medium);
}
</style>
