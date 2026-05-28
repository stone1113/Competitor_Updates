<template>
  <div class="page">
    <PageMast title="数据源配置">
      <template #controls>
        <USelect v-model="filterType" :options="typeOpts" placeholder="全部类型" clearable style="width:140px" @change="loadData" />
        <UButton variant="primary" size="sm" @click="showDialog()">新增数据源</UButton>
      </template>
    </PageMast>

    <UCard flush>
      <UTable :data="list" stripe>
        <UTableColumn prop="id" label="ID" :width="60" />
        <UTableColumn label="类型" :width="120">
          <template #default="{ row }">
            <UTag>{{ row.sourceType }}</UTag>
          </template>
        </UTableColumn>
        <UTableColumn prop="sourceName" label="名称" min-width="200" />
        <UTableColumn prop="brandName" label="品牌" :width="100" />
        <UTableColumn prop="cronExpression" label="调度表达式" :width="160">
          <template #default="{ row }">
            <span class="tabular">{{ row.cronExpression || '-' }}</span>
          </template>
        </UTableColumn>
        <UTableColumn label="状态" :width="80">
          <template #default="{ row }">
            <USwitch v-model="row.isEnabled" @change="handleToggle(row)" />
          </template>
        </UTableColumn>
        <UTableColumn label="操作" :width="140">
          <template #default="{ row }">
            <UButton variant="link" size="sm" @click.stop="showDialog(row)">编辑</UButton>
            <UPopconfirm message="确认删除该数据源？" @confirm="handleDelete(row.id)">
              <UButton variant="link" size="sm">删除</UButton>
            </UPopconfirm>
          </template>
        </UTableColumn>
      </UTable>
    </UCard>

    <UDialog v-model="dialogVisible" :title="form.id ? '编辑数据源' : '新增数据源'" width="560px">
      <UField label="类型">
        <USelect v-model="form.sourceType" :options="typeOpts" />
      </UField>
      <UField label="名称">
        <UInput v-model="form.sourceName" />
      </UField>
      <UField label="品牌">
        <UInput v-model="form.brandName" />
      </UField>
      <UField label="关键词" hint='JSON 数组，如 ["关键词1","关键词2"]'>
        <UInput type="textarea" v-model="form.keywords" :rows="3" />
      </UField>
      <UField label="调度表达式" hint="cron, 例如 0 0 8 * * ?">
        <UInput v-model="form.cronExpression" />
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
  UButton, USelect, UInput, USwitch, UTag, UCard, UTable, UTableColumn,
  UDialog, UPopconfirm, UField, toast
} from '../../components/ui'
import { listDataSources, createDataSource, updateDataSource, deleteDataSource } from '../../api/dataSource'

const list = ref([])
const filterType = ref('')
const dialogVisible = ref(false)
const form = ref({})

const typeOpts = [
  { label: 'BochaAI', value: 'BOCHA_API' },
  { label: '爬虫', value: 'CRAWLER' },
  { label: 'RSS', value: 'RSS' }
]

async function loadData() {
  try { list.value = await listDataSources(filterType.value) || [] }
  catch (e) { list.value = [] }
}

function showDialog(row) {
  form.value = row ? { ...row } : { sourceType: 'BOCHA_API', isEnabled: true }
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (form.value.id) await updateDataSource(form.value.id, form.value)
    else await createDataSource(form.value)
    toast.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) { toast.error('保存失败：' + (e.message || e)) }
}

async function handleToggle(row) {
  try { await updateDataSource(row.id, row) }
  catch (e) { toast.error('更新失败') }
}

async function handleDelete(id) {
  try {
    await deleteDataSource(id)
    toast.success('已删除')
    loadData()
  } catch (e) { toast.error('删除失败') }
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
</style>
