<template>
  <div class="page">
    <PageMast title="新闻 URL 黑名单">
      <template #controls>
        <span class="muted">采集时按 url.contains(pattern) 直接过滤 · 改后下次采集生效</span>
        <UButton variant="primary" size="sm" @click="openAdd">新增 pattern</UButton>
      </template>
    </PageMast>

    <UCard flush>
      <UTable :data="rows" stripe empty-text="— 暂无黑名单 —">
        <UTableColumn prop="id" label="ID" :width="60" />
        <UTableColumn label="启用" :width="70">
          <template #default="{ row }">
            <USwitch v-model="row.isEnabled" @change="handleToggle(row)" />
          </template>
        </UTableColumn>
        <UTableColumn prop="pattern" label="URL pattern (子串匹配)" min-width="280">
          <template #default="{ row }">
            <code>{{ row.pattern }}</code>
          </template>
        </UTableColumn>
        <UTableColumn prop="reason" label="原因/备注" min-width="200" />
        <UTableColumn label="操作" :width="160">
          <template #default="{ row }">
            <UButton variant="link" size="sm" @click="openEdit(row)">编辑</UButton>
            <UPopconfirm message="确认删除此 pattern？" @confirm="handleDelete(row.id)">
              <UButton variant="link" size="sm">删除</UButton>
            </UPopconfirm>
          </template>
        </UTableColumn>
      </UTable>
    </UCard>

    <UDialog v-model="dialogVisible" :title="form.id ? '编辑黑名单' : '新增黑名单'" width="520px">
      <UField label="URL pattern" hint="子串匹配，区分大小写。例：autohome.com.cn/dealer/">
        <UInput v-model="form.pattern" placeholder="autohome.com.cn/dealer/" />
      </UField>
      <UField label="原因/备注">
        <UInput v-model="form.reason" placeholder="汽车之家经销商页" />
      </UField>
      <UField label="启用" inline>
        <USwitch v-model="form.isEnabled" />
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
  UButton, UInput, USwitch, UCard, UTable, UTableColumn,
  UDialog, UPopconfirm, UField, toast
} from '../../components/ui'
import {
  listBlacklist, createBlacklist, updateBlacklist, deleteBlacklist
} from '../../api/blacklist'

const rows = ref([])
const dialogVisible = ref(false)
const form = ref({})

async function loadData() {
  try {
    rows.value = await listBlacklist() || []
  } catch (e) { rows.value = [] }
}

function openAdd() {
  form.value = { pattern: '', reason: '', isEnabled: true }
  dialogVisible.value = true
}
function openEdit(row) {
  form.value = { ...row }
  dialogVisible.value = true
}

async function handleSave() {
  if (!form.value.pattern?.trim()) { toast.warning('pattern 必填'); return }
  try {
    if (form.value.id) {
      await updateBlacklist(form.value.id, form.value)
    } else {
      await createBlacklist(form.value)
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
    await updateBlacklist(row.id, row)
  } catch (e) {
    toast.error('切换失败')
    row.isEnabled = !row.isEnabled
  }
}

async function handleDelete(id) {
  try {
    await deleteBlacklist(id)
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
.muted { color: var(--ink-subtle); font-size: var(--text-sm); margin-right: var(--space-4); }
code {
  font-family: var(--font-mono);
  font-size: var(--text-sm);
  background: var(--surface-sunk);
  padding: 2px 6px;
}
</style>
