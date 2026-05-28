<template>
  <div class="page">
    <PageMast title="清洗规则">
      <template #controls>
        <UButton variant="primary" size="sm" @click="showDialog()">新增规则</UButton>
      </template>
    </PageMast>

    <UCard flush>
      <UTable :data="list" stripe empty-text="— 后端接口待实现 —">
        <UTableColumn prop="id" label="ID" :width="60" />
        <UTableColumn label="类型" :width="170">
          <template #default="{ row }">
            <UTag :variant="row.ruleType === 'SENSITIVE_WORD' ? 'accent' : 'default'">{{ row.ruleType }}</UTag>
          </template>
        </UTableColumn>
        <UTableColumn prop="ruleName" label="规则名称" min-width="220" />
        <UTableColumn label="优先级" :width="90" align="right">
          <template #default="{ row }">
            <span class="tabular">{{ row.priority }}</span>
          </template>
        </UTableColumn>
        <UTableColumn label="状态" :width="80">
          <template #default="{ row }">
            <USwitch v-model="row.isEnabled" />
          </template>
        </UTableColumn>
        <UTableColumn label="操作" :width="100">
          <template #default="{ row }">
            <UButton variant="link" size="sm" @click.stop="showDialog(row)">编辑</UButton>
          </template>
        </UTableColumn>
      </UTable>
    </UCard>

    <UDialog v-model="dialogVisible" :title="form.id ? '编辑规则' : '新增规则'" width="560px">
      <UField label="类型">
        <USelect v-model="form.ruleType" :options="typeOpts" />
      </UField>
      <UField label="名称">
        <UInput v-model="form.ruleName" />
      </UField>
      <UField label="配置" hint='JSON, 例如 {"words":["xxx"]}'>
        <UInput type="textarea" v-model="form.ruleConfig" :rows="4" />
      </UField>
      <UField label="优先级" hint="0-100">
        <UInput type="number" v-model="form.priority" />
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

const list = ref([])
const dialogVisible = ref(false)
const form = ref({})

const typeOpts = [
  { label: '敏感词过滤', value: 'SENSITIVE_WORD' },
  { label: 'SimHash 去重', value: 'SIMHASH_DEDUP' },
  { label: '正则过滤', value: 'REGEX_FILTER' }
]

async function loadData() {
  list.value = []
}

function showDialog(row) {
  form.value = row ? { ...row } : { ruleType: 'SENSITIVE_WORD', priority: 0, isEnabled: true }
  dialogVisible.value = true
}

async function handleSave() {
  toast.info('清洗规则后端接口待实现')
  dialogVisible.value = false
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
