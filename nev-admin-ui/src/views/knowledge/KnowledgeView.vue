<template>
  <div class="page">
    <PageMast title="知识库管理">
      <template #controls>
        <UButton variant="primary" size="sm" @click="showDialog">上传文档</UButton>
      </template>
    </PageMast>

    <UCard flush>
      <UTable :data="list" stripe>
        <UTableColumn prop="id" label="ID" :width="60" />
        <UTableColumn prop="docName" label="文档名称" min-width="240" />
        <UTableColumn label="类型" :width="100">
          <template #default="{ row }">
            <UTag>{{ row.docType }}</UTag>
          </template>
        </UTableColumn>
        <UTableColumn label="切片数" :width="90" align="right">
          <template #default="{ row }">
            <span class="tabular">{{ row.chunkCount ?? '—' }}</span>
          </template>
        </UTableColumn>
        <UTableColumn label="状态" :width="120">
          <template #default="{ row }">
            <UTag :variant="statusVariant(row.status)">{{ row.status }}</UTag>
          </template>
        </UTableColumn>
        <UTableColumn label="创建时间" :width="180">
          <template #default="{ row }">
            <span class="tabular ts">{{ row.createTime || '-' }}</span>
          </template>
        </UTableColumn>
        <UTableColumn label="操作" :width="100">
          <template #default="{ row }">
            <UPopconfirm message="确认删除该文档？" @confirm="handleDelete(row.id)">
              <UButton variant="link" size="sm">删除</UButton>
            </UPopconfirm>
          </template>
        </UTableColumn>
      </UTable>
    </UCard>

    <UDialog v-model="dialogVisible" title="上传文档" width="480px">
      <UField label="文档名称">
        <UInput v-model="form.docName" />
      </UField>
      <UField label="文档类型">
        <USelect v-model="form.docType" :options="typeOpts" />
      </UField>
      <UField label="Milvus 集合" hint="留空使用默认 knowledge_entry">
        <UInput v-model="form.collectionName" placeholder="knowledge_entry" />
      </UField>
      <template #footer>
        <UButton variant="quiet" @click="dialogVisible = false">取消</UButton>
        <UButton variant="primary" @click="handleSave">提交</UButton>
      </template>
    </UDialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import PageMast from '../../components/layout/PageMast.vue'
import {
  UButton, USelect, UInput, UTag, UCard, UTable, UTableColumn,
  UDialog, UPopconfirm, UField, toast
} from '../../components/ui'
import { listDocuments, createDocument, deleteDocument } from '../../api/knowledge'

const list = ref([])
const dialogVisible = ref(false)
const form = ref({ docType: 'EXCEL', collectionName: 'knowledge_entry' })

const typeOpts = [
  { label: 'Excel', value: 'EXCEL' },
  { label: 'PDF', value: 'PDF' },
  { label: 'Markdown', value: 'MARKDOWN' }
]

function statusVariant(s) {
  return ({ DONE: 'positive', PROCESSING: 'alert', FAILED: 'accent', PENDING: 'neutral' }[s] || 'default')
}

async function loadData() {
  try { list.value = await listDocuments() || [] }
  catch (e) { list.value = [] }
}

function showDialog() {
  form.value = { docType: 'EXCEL', collectionName: 'knowledge_entry' }
  dialogVisible.value = true
}

async function handleSave() {
  try {
    await createDocument(form.value)
    toast.success('已提交')
    dialogVisible.value = false
    loadData()
  } catch (e) { toast.error('提交失败：' + (e.message || e)) }
}

async function handleDelete(id) {
  try {
    await deleteDocument(id)
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
.ts { font-size: var(--text-xs); color: var(--ink-muted); }
</style>
