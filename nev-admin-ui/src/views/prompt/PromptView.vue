<template>
  <div class="page">
    <PageMast title="Prompt 管理">
      <template #controls>
        <USelect v-model="filterAgent" :options="agentOpts" placeholder="全部 Agent" clearable style="width:200px" @change="loadData" />
        <UButton variant="primary" size="sm" @click="showDialog()">新增模板</UButton>
      </template>
    </PageMast>

    <UCard flush>
      <UTable :data="list" stripe>
        <UTableColumn prop="id" label="ID" :width="60" />
        <UTableColumn prop="agentName" label="Agent" :width="200">
          <template #default="{ row }">
            <span class="agent">{{ row.agentName }}</span>
          </template>
        </UTableColumn>
        <UTableColumn prop="templateName" label="模板名称" :width="160" />
        <UTableColumn label="版本" :width="80" align="right">
          <template #default="{ row }">
            <span class="tabular">v{{ row.version }}</span>
          </template>
        </UTableColumn>
        <UTableColumn label="状态" :width="90">
          <template #default="{ row }">
            <UTag :variant="row.isActive ? 'positive' : 'neutral'">{{ row.isActive ? '启用' : '禁用' }}</UTag>
          </template>
        </UTableColumn>
        <UTableColumn label="更新时间" :width="180">
          <template #default="{ row }">
            <span class="tabular ts">{{ row.updateTime || '-' }}</span>
          </template>
        </UTableColumn>
        <UTableColumn label="操作" :width="100">
          <template #default="{ row }">
            <UButton variant="link" size="sm" @click.stop="showDialog(row)">编辑</UButton>
          </template>
        </UTableColumn>
      </UTable>
    </UCard>

    <UDialog v-model="dialogVisible" :title="form.id ? '编辑 Prompt' : '新增 Prompt'" width="780px">
      <UField label="Agent">
        <USelect v-model="form.agentName" :options="agentOpts" />
      </UField>
      <UField label="模板名称">
        <UInput v-model="form.templateName" />
      </UField>
      <UField label="Prompt 内容" hint="支持多行，等宽字体便于编辑">
        <UInput type="textarea" v-model="form.templateContent" :rows="16" class="prompt-editor" />
      </UField>
      <UField label="版本号">
        <UInput type="number" v-model="form.version" />
      </UField>
      <UField label="启用" inline>
        <USwitch v-model="form.isActive" />
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
  UDialog, UField, toast
} from '../../components/ui'
import { listPrompts, createPrompt, updatePrompt } from '../../api/prompt'

const list = ref([])
const filterAgent = ref('')
const dialogVisible = ref(false)
const form = ref({})

const agentOpts = [
  { label: 'SENTIMENT_AGENT',  value: 'SENTIMENT_AGENT' },
  { label: 'COMPETITOR_AGENT', value: 'COMPETITOR_AGENT' },
  { label: 'PITCH_AGENT',      value: 'PITCH_AGENT' },
  { label: 'ASSEMBLY_AGENT',   value: 'ASSEMBLY_AGENT' }
]

async function loadData() {
  try { list.value = await listPrompts(filterAgent.value) || [] }
  catch (e) { list.value = [] }
}

function showDialog(row) {
  form.value = row ? { ...row } : { agentName: 'SENTIMENT_AGENT', templateName: 'default', version: 1, isActive: true, templateContent: '' }
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (form.value.id) await updatePrompt(form.value.id, form.value)
    else await createPrompt(form.value)
    toast.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) { toast.error('保存失败：' + (e.message || e)) }
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
.agent {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  color: var(--ink);
  font-weight: 500;
}
.ts { font-size: var(--text-xs); color: var(--ink-muted); }
.prompt-editor :deep(.ui-input-control) {
  font-family: var(--font-mono);
  font-size: var(--text-xs);
  line-height: 1.6;
}
</style>
