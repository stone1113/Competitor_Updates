<template>
  <div class="page">
    <PageMast title="爬取关键词配置">
      <template #controls>
        <USelect v-model="filterBrand" :options="brandOpts" placeholder="全部品牌" clearable style="width:140px" @change="loadData" />
        <UButton variant="ghost" size="sm" @click="openBulkDialog">批量编辑</UButton>
        <UButton variant="primary" size="sm" @click="openAddDialog">新增关键词</UButton>
      </template>
    </PageMast>

    <UTabs v-model="filterCategory" @change="loadData" class="cat-tabs">
      <UTabPane label="全部" name="" />
      <UTabPane :label="`本品 (${counts.self ?? 0})`" name="self" />
      <UTabPane :label="`竞品 (${counts.competitor ?? 0})`" name="competitor" />
      <UTabPane :label="`行业 (${counts.industry ?? 0})`" name="industry" />
    </UTabs>

    <UCard flush>
      <UTable :data="rows" stripe empty-text="— 暂无配置 —">
        <UTableColumn prop="id" label="ID" :width="60" />
        <UTableColumn label="分类" :width="90">
          <template #default="{ row }">
            <UTag :variant="categoryVariant(row.category)">{{ categoryLabel(row.category) }}</UTag>
          </template>
        </UTableColumn>
        <UTableColumn prop="brandName" label="品牌" :width="120" />
        <UTableColumn prop="keyword" label="关键词" min-width="200" />
        <UTableColumn label="启用" :width="70">
          <template #default="{ row }">
            <USwitch v-model="row.enabled" @change="handleToggle(row)" />
          </template>
        </UTableColumn>
        <UTableColumn prop="remark" label="备注" min-width="160" />
        <UTableColumn label="操作" :width="140">
          <template #default="{ row }">
            <UButton variant="link" size="sm" @click="openEditDialog(row)">编辑</UButton>
            <UPopconfirm message="确认删除此关键词？" @confirm="handleDelete(row.id)">
              <UButton variant="link" size="sm">删除</UButton>
            </UPopconfirm>
          </template>
        </UTableColumn>
      </UTable>
    </UCard>

    <!-- 单条新增/编辑 -->
    <UDialog v-model="dialogVisible" :title="form.id ? '编辑关键词' : '新增关键词'" width="480px">
      <UField label="分类">
        <USelect v-model="form.category" :options="categoryOpts" />
      </UField>
      <UField label="品牌">
        <UInput v-model="form.brandName" placeholder="例如：猛士 / 仰望 / 行业" />
      </UField>
      <UField label="关键词">
        <UInput v-model="form.keyword" placeholder="例如：猛士917 / 仰望U8 / 新能源汽车" />
      </UField>
      <UField label="启用" inline>
        <USwitch v-model="form.enabled" />
      </UField>
      <UField label="备注（可选）">
        <UInput v-model="form.remark" />
      </UField>
      <template #footer>
        <UButton variant="quiet" @click="dialogVisible = false">取消</UButton>
        <UButton variant="primary" @click="handleSave">保存</UButton>
      </template>
    </UDialog>

    <!-- 批量替换某品牌的所有关键词 -->
    <UDialog v-model="bulkDialog" title="批量编辑关键词" width="520px">
      <UField label="分类">
        <USelect v-model="bulkForm.category" :options="categoryOpts" />
      </UField>
      <UField label="品牌" hint="覆盖该 (品牌 × 分类) 的全部关键词（先删后插）">
        <UInput v-model="bulkForm.brand" placeholder="例如：猛士" />
      </UField>
      <UField label="关键词列表" hint="一行一个，或用逗号分隔">
        <UInput type="textarea" v-model="bulkForm.text" :rows="8" placeholder="猛士917&#10;猛士M9&#10;猛士M817" />
      </UField>
      <template #footer>
        <UButton variant="quiet" @click="bulkDialog = false">取消</UButton>
        <UButton variant="primary" @click="handleBulkReplace">覆盖保存</UButton>
      </template>
    </UDialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import PageMast from '../../components/layout/PageMast.vue'
import {
  UButton, USelect, UInput, USwitch, UCard, UTable, UTableColumn,
  UDialog, UPopconfirm, UField, UTabs, UTabPane, UTag, toast
} from '../../components/ui'
import {
  listKeywords, listBrands, createKeyword, updateKeyword, deleteKeyword, bulkReplaceKeywords
} from '../../api/brandKeyword'

const rows = ref([])
const brandOpts = ref([])
const filterBrand = ref('')
const filterCategory = ref('')

const dialogVisible = ref(false)
const form = ref({})

const bulkDialog = ref(false)
const bulkForm = ref({ brand: '', category: 'self', text: '' })

const categoryOpts = [
  { label: '本品 (self)', value: 'self' },
  { label: '竞品 (competitor)', value: 'competitor' },
  { label: '行业 (industry)', value: 'industry' },
]

const counts = ref({ self: 0, competitor: 0, industry: 0 })

function categoryLabel(c) {
  return { self: '本品', competitor: '竞品', industry: '行业' }[c] || c || '-'
}
function categoryVariant(c) {
  return { self: 'accent', competitor: 'default', industry: 'neutral' }[c] || 'default'
}

async function loadData() {
  try {
    const params = {}
    if (filterBrand.value) params.brand = filterBrand.value
    if (filterCategory.value) params.category = filterCategory.value
    rows.value = await listKeywords(params) || []
    if (!filterBrand.value && !filterCategory.value) {
      // 全量时顺便算 counts
      const c = { self: 0, competitor: 0, industry: 0 }
      rows.value.forEach(r => { if (c[r.category] != null) c[r.category]++ })
      counts.value = c
    } else if (!filterBrand.value) {
      // 仅按 category 过滤；分类计数补一次全量
      refreshCounts()
    }
  } catch (e) { rows.value = [] }
}

async function refreshCounts() {
  try {
    const all = await listKeywords({}) || []
    const c = { self: 0, competitor: 0, industry: 0 }
    all.forEach(r => { if (c[r.category] != null) c[r.category]++ })
    counts.value = c
  } catch (e) {}
}

async function loadBrands() {
  try {
    const list = await listBrands() || []
    brandOpts.value = list.map(b => ({ label: b, value: b }))
  } catch (e) { brandOpts.value = [] }
}

function openAddDialog() {
  form.value = {
    brandName: filterBrand.value || '',
    keyword: '',
    category: filterCategory.value || 'self',
    enabled: true,
    remark: ''
  }
  dialogVisible.value = true
}
function openEditDialog(row) {
  form.value = { ...row }
  dialogVisible.value = true
}
function openBulkDialog() {
  const brand = filterBrand.value
  const cat = filterCategory.value || 'self'
  const lines = brand
    ? rows.value.filter(r => r.brandName === brand && (!cat || r.category === cat)).map(r => r.keyword).join('\n')
    : ''
  bulkForm.value = { brand: brand || '', category: cat, text: lines }
  bulkDialog.value = true
}

async function handleSave() {
  try {
    if (form.value.id) {
      await updateKeyword(form.value.id, form.value)
    } else {
      await createKeyword(form.value)
    }
    toast.success('保存成功')
    dialogVisible.value = false
    loadData(); loadBrands(); refreshCounts()
  } catch (e) {
    toast.error('保存失败：' + (e.message || e))
  }
}

async function handleToggle(row) {
  try {
    await updateKeyword(row.id, row)
  } catch (e) {
    toast.error('切换失败')
    row.enabled = !row.enabled
  }
}

async function handleDelete(id) {
  try {
    await deleteKeyword(id)
    toast.success('已删除')
    loadData(); loadBrands(); refreshCounts()
  } catch (e) { toast.error('删除失败') }
}

async function handleBulkReplace() {
  if (!bulkForm.value.brand) { toast.warning('请填品牌'); return }
  const keywords = bulkForm.value.text
    .split(/[\n,，]+/)
    .map(s => s.trim())
    .filter(Boolean)
  if (!keywords.length) { toast.warning('请至少填一个关键词'); return }
  try {
    await bulkReplaceKeywords(bulkForm.value.brand, keywords, bulkForm.value.category)
    toast.success(`已替换 ${keywords.length} 条`)
    bulkDialog.value = false
    loadData(); loadBrands(); refreshCounts()
  } catch (e) {
    toast.error('保存失败：' + (e.message || e))
  }
}

onMounted(() => { loadData(); loadBrands() })
</script>

<style scoped>
.page {
  background: var(--bg);
  padding: var(--space-6) var(--space-8) var(--space-10);
  max-width: 1320px;
  margin: 0 auto;
}
.cat-tabs {
  margin: var(--space-3) 0;
}
</style>
