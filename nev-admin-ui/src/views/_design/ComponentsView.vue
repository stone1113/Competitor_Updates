<template>
  <div class="cv-page">
    <header class="cv-masthead">
      <div class="eyebrow">No. 02 / Components</div>
      <h1 class="display cv-title">The Library.</h1>
      <span class="accent-rule"></span>
      <p class="cv-deck">所有自研组件的实样。每个区块旁标注 token 名，方便后续在页面里使用。</p>
    </header>

    <!-- Buttons -->
    <Section eyebrow="§ 01" title="Button">
      <div class="cv-row">
        <UButton variant="primary">Primary</UButton>
        <UButton variant="ghost">Ghost</UButton>
        <UButton variant="danger">Danger</UButton>
        <UButton variant="quiet">Quiet</UButton>
        <UButton variant="link">Link</UButton>
        <UButton disabled>Disabled</UButton>
        <UButton loading>Loading</UButton>
      </div>
      <div class="cv-row">
        <UButton size="sm">Small</UButton>
        <UButton size="md">Medium</UButton>
        <UButton size="lg">Large</UButton>
      </div>
    </Section>

    <!-- Inputs / Switch / Tag -->
    <Section eyebrow="§ 02" title="Input · Switch · Tag">
      <div class="cv-grid">
        <UField label="文本">
          <UInput v-model="t1" placeholder="请输入" clearable />
        </UField>
        <UField label="多行" hint="支持垂直拉伸">
          <UInput type="textarea" v-model="t2" placeholder="多行..." />
        </UField>
        <UField label="开关">
          <USwitch v-model="sw" />
        </UField>
        <UField label="标签">
          <div class="cv-row" style="margin:0">
            <UTag>default</UTag>
            <UTag variant="accent">accent</UTag>
            <UTag variant="positive">positive</UTag>
            <UTag variant="alert">alert</UTag>
            <UTag variant="neutral">neutral</UTag>
            <UTag variant="ghost">ghost</UTag>
          </div>
        </UField>
      </div>
    </Section>

    <!-- Selects -->
    <Section eyebrow="§ 03" title="Select">
      <div class="cv-grid">
        <UField label="单选">
          <USelect v-model="s1" :options="brandOpts" placeholder="选品牌" clearable />
        </UField>
        <UField label="多选">
          <USelect v-model="s2" :options="platOpts" multiple placeholder="选平台" />
        </UField>
      </div>
    </Section>

    <!-- Date -->
    <Section eyebrow="§ 04" title="Date · Range">
      <div class="cv-grid">
        <UField label="单日期">
          <UDatePicker v-model="d1" />
        </UField>
        <UField label="日期范围">
          <UDateRange v-model="d2" />
        </UField>
      </div>
    </Section>

    <!-- Card -->
    <Section eyebrow="§ 05" title="Card">
      <div class="cv-cards">
        <UCard title="无 rule 卡片" eyebrow="STANDARD">
          <p style="color:var(--ink-muted)">主体内容区域，padding 24px。</p>
        </UCard>
        <UCard title="带 accent rule" eyebrow="EDITORIAL" rule>
          <p style="color:var(--ink-muted)">标题下面有一条红色短横，用于重要卡片。</p>
        </UCard>
        <UCard flush>
          <template #header><span class="eyebrow">FLUSH</span><h3 class="display" style="font-size:20px">无内边距</h3></template>
          <div style="padding:16px;background:var(--bg)">flush 模式下，body 不带 padding，方便嵌表格。</div>
          <template #footer><UButton size="sm" variant="quiet">操作</UButton></template>
        </UCard>
      </div>
    </Section>

    <!-- Tabs -->
    <Section eyebrow="§ 06" title="Tabs">
      <UTabs v-model="tab">
        <UTabPane name="first" label="新闻">
          <p>这里是「新闻」的内容。</p>
        </UTabPane>
        <UTabPane name="second" label="社交平台">
          <p>这里是「社交平台」的内容。</p>
        </UTabPane>
        <UTabPane name="third" label="另一个">
          <p>第三个 Tab。</p>
        </UTabPane>
      </UTabs>
    </Section>

    <!-- Table -->
    <Section eyebrow="§ 07" title="Table">
      <UCard flush>
        <UTable :data="rows" row-clickable stripe @row-click="onRowClick">
          <UTableColumn prop="id" label="ID" :width="60" />
          <UTableColumn prop="brand" label="品牌" :width="100" />
          <UTableColumn label="标题" prop="title" min-width="220" />
          <UTableColumn label="情感" :width="100">
            <template #default="{ row }">
              <UTag :variant="row.sent === 'pos' ? 'positive' : row.sent === 'neg' ? 'accent' : 'neutral'">
                {{ row.sent === 'pos' ? '正面' : row.sent === 'neg' ? '负面' : '中性' }}
              </UTag>
            </template>
          </UTableColumn>
          <UTableColumn label="互动量" :width="100" align="right">
            <template #default="{ row }">
              <span class="tabular">{{ row.eng.toLocaleString() }}</span>
            </template>
          </UTableColumn>
          <UTableColumn label="操作" :width="120">
            <template #default="{ row }">
              <UButton size="sm" variant="link" @click.stop="alert(row.title)">查看</UButton>
              <UPopconfirm message="确认删除该条？" @confirm="alert('删了 ' + row.id)">
                <UButton size="sm" variant="link">删除</UButton>
              </UPopconfirm>
            </template>
          </UTableColumn>
        </UTable>
      </UCard>
    </Section>

    <!-- Pagination -->
    <Section eyebrow="§ 08" title="Pagination">
      <UPagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="247"
      />
    </Section>

    <!-- Drawer / Dialog -->
    <Section eyebrow="§ 09" title="Drawer · Dialog · Toast">
      <div class="cv-row">
        <UButton @click="drawerOpen = true">打开 Drawer</UButton>
        <UButton @click="dialogOpen = true">打开 Dialog</UButton>
        <UButton @click="toast.info('Hello info — 这是一条信息')">Toast info</UButton>
        <UButton variant="ghost" @click="toast.success('保存成功')">Toast success</UButton>
        <UButton variant="danger" @click="toast.error('请求失败：超时')">Toast error</UButton>
        <UButton variant="quiet" @click="toast.warning('请确认输入是否正确')">Toast warning</UButton>
      </div>

      <UDrawer v-model="drawerOpen" title="抽屉示例" eyebrow="DRAWER">
        <p style="color:var(--ink-muted);line-height:1.7">
          抽屉从右侧滑入，遮罩半透明。用于展示详情、表单等需要较大空间的内容。
          按 ESC 或点击遮罩关闭。
        </p>
      </UDrawer>

      <UDialog v-model="dialogOpen" title="对话框">
        <p style="color:var(--ink)">这里是对话框正文。</p>
        <template #footer>
          <UButton variant="quiet" @click="dialogOpen = false">取消</UButton>
          <UButton variant="primary" @click="dialogOpen = false">确定</UButton>
        </template>
      </UDialog>
    </Section>

    <!-- Divider -->
    <Section eyebrow="§ 10" title="Divider">
      <UDivider />
      <UDivider>section break</UDivider>
    </Section>

    <footer class="cv-foot">
      <span class="eyebrow">— end of components —</span>
    </footer>
  </div>
</template>

<script setup>
import { ref, h } from 'vue'
import {
  UButton, UInput, USwitch, UTag, USelect, UDatePicker, UDateRange,
  UCard, UTabs, UTabPane, UTable, UTableColumn, UPagination,
  UDrawer, UDialog, UPopconfirm, UField, UDivider, toast
} from '../../components/ui'

// Local "Section" wrapper for consistent layout
const Section = (props, { slots }) => h('section', { class: 'cv-section' }, [
  h('div', { class: 'cv-section-head' }, [
    h('span', { class: 'eyebrow' }, props.eyebrow),
    h('h2', { class: 'display cv-h2' }, props.title)
  ]),
  slots.default?.()
])
Section.props = { eyebrow: String, title: String }

const t1 = ref(''), t2 = ref(''), sw = ref(true)
const s1 = ref(''), s2 = ref([])
const d1 = ref(''), d2 = ref([])
const tab = ref('first')
const drawerOpen = ref(false), dialogOpen = ref(false)
const page = ref(1), size = ref(20)

const brandOpts = [
  { label: '猛士', value: '猛士' },
  { label: '坦克', value: '坦克' },
  { label: '方程豹', value: '方程豹' }
]
const platOpts = [
  { label: '小红书', value: 'xhs' },
  { label: '抖音', value: 'dy' },
  { label: 'B站', value: 'bili' },
  { label: '微博', value: 'wb' },
  { label: '快手', value: 'ks' }
]

const rows = [
  { id: 1, brand: '猛士', title: '猛士 917 越野俱乐部首发，用户讨论集中于"实用通过性"', sent: 'pos', eng: 12847 },
  { id: 2, brand: '坦克', title: '坦克 700 Hi4-T 智能版上市，关注度持续走高', sent: 'pos', eng: 8421 },
  { id: 3, brand: '方程豹', title: '方程豹 5 召回事件，部分用户表达不满', sent: 'neg', eng: 5630 },
  { id: 4, brand: '猛士', title: '行业评论：硬派越野市场新格局', sent: 'neutral', eng: 3211 }
]

function onRowClick(row) {
  toast.info(`点击了行：${row.title}`)
}
</script>

<style scoped>
.cv-page {
  background: var(--bg);
  min-height: 100vh;
  padding: var(--space-16) var(--space-12);
  max-width: 1200px;
  margin: 0 auto;
  color: var(--ink);
}
.cv-masthead {
  border-top: 2px solid var(--ink);
  padding-top: var(--space-6);
  margin-bottom: var(--space-16);
}
.cv-title {
  font-size: 84px;
  font-weight: 500;
  letter-spacing: var(--tracking-tight);
  line-height: var(--leading-tight);
  margin-top: var(--space-3);
}
.cv-deck { color: var(--ink-muted); max-width: 560px; line-height: var(--leading-loose); }

.cv-section { margin-bottom: var(--space-16); }
.cv-section-head {
  display: flex;
  align-items: baseline;
  gap: var(--space-4);
  padding-bottom: var(--space-3);
  margin-bottom: var(--space-6);
  border-bottom: 1px solid var(--hairline);
}
.cv-h2 {
  font-size: var(--text-2xl);
  font-weight: 500;
  letter-spacing: var(--tracking-tight);
}

.cv-row {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  align-items: center;
  margin-bottom: var(--space-4);
}
.cv-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-6);
}
.cv-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--space-5);
}

.cv-foot {
  text-align: center;
  padding: var(--space-12) 0 var(--space-6);
  border-top: 1px solid var(--hairline);
}
</style>
