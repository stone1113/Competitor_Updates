<template>
  <aside class="sidebar" :class="{ collapsed }">
    <RouterLink to="/daily-report" class="brand">
      <span class="brand-mark">猛士</span>
      <span class="brand-name display" v-show="!collapsed">舆情</span>
    </RouterLink>

    <nav class="nav">
      <div class="nav-group" v-for="group in groups" :key="group.label">
        <div class="nav-eyebrow" v-show="!collapsed">{{ group.label }}</div>
        <ul class="nav-list">
          <li v-for="item in group.items" :key="item.path">
            <RouterLink
              :to="item.path"
              class="nav-link"
              :class="{ active: isActive(item.path) }"
              :title="collapsed ? item.label : ''"
            >
              <span class="nav-marker" aria-hidden="true"></span>
              <span class="nav-label" v-show="!collapsed">{{ item.label }}</span>
              <span class="nav-glyph" v-show="collapsed">{{ item.glyph }}</span>
            </RouterLink>
          </li>
        </ul>
      </div>
    </nav>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { useAppStore } from '../../stores/app'

const route = useRoute()
const appStore = useAppStore()
const collapsed = computed(() => appStore.sidebarCollapsed)

const groups = [
  {
    label: '出版',
    items: [
      { path: '/daily-report',          label: '舆情日报',  glyph: '日' },
      { path: '/competitor-dashboard',  label: '竞品仪表盘', glyph: '竞' }
    ]
  },
  {
    label: '竞品',
    items: [
      { path: '/competitor/events',           label: '事件浏览',     glyph: '事' },
      { path: '/competitor/autohome-config',  label: '车系配置',     glyph: '车' },
      { path: '/competitor/autohome-spec',    label: '技术参数对标', glyph: '标' },
      { path: '/competitor/finance-by-model', label: '车型金融政策', glyph: '金' },
      { path: '/competitor/deep-analysis',    label: '智能深度对标', glyph: '🤖' },
      { path: '/sales-training',              label: '销售培训文档', glyph: '训' }
    ]
  },
  {
    label: '采集',
    items: [
      { path: '/collector/data-source',     label: '数据源配置',     glyph: '源' },
      { path: '/collector/cleaning-rule',   label: '清洗规则',       glyph: '清' },
      { path: '/collector/content-view',    label: '采集内容查看',   glyph: '览' },
      { path: '/collector/social-crawler',  label: '社交爬虫',       glyph: '爬' },
      { path: '/collector/brand-keywords',  label: '关键词配置',     glyph: '词' },
      { path: '/collector/official-accounts',label: '官方账号',       glyph: '官' }
    ]
  },
  {
    label: '知识',
    items: [
      { path: '/knowledge',     label: '知识库管理', glyph: '库' },
      { path: '/talking-point', label: '话术管理',  glyph: '话' }
    ]
  },
  {
    label: '系统',
    items: [
      { path: '/prompt',              label: 'Prompt 管理', glyph: 'P' },
      { path: '/report-console',      label: '日报控制台',  glyph: '台' },
      { path: '/system/url-blacklist', label: 'URL 黑名单',  glyph: '黑' }
    ]
  }
]

function isActive(path) {
  return route.path === path
}
</script>

<style scoped>
.sidebar {
  background: var(--bg-elevated);
  border-right: 1px solid var(--hairline);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  height: 100vh;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  padding: var(--space-5) var(--space-5) var(--space-4);
  text-decoration: none;
  color: var(--ink);
  border-bottom: 1px solid var(--hairline);
}
.brand-mark {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: var(--text-xl);
  letter-spacing: var(--tracking-tight);
  line-height: 1;
}
.brand-name {
  font-family: var(--font-display);
  font-weight: 400;
  font-style: italic;
  font-size: var(--text-xl);
  letter-spacing: var(--tracking-tight);
  line-height: 1;
  color: var(--accent);
}
.collapsed .brand {
  justify-content: center;
  padding: var(--space-5) 0 var(--space-4);
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-5) 0 var(--space-4);
}
.nav-group { margin-bottom: var(--space-5); }
.nav-eyebrow {
  font-size: var(--text-xs);
  font-weight: var(--weight-semibold);
  color: var(--ink-subtle);
  padding: 0 var(--space-5);
  margin-bottom: var(--space-2);
}
.nav-list { list-style: none; margin: 0; padding: 0; }
.nav-link {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-5);
  font-family: var(--font-body);
  font-size: var(--text-sm);
  color: var(--ink-muted);
  text-decoration: none;
  transition: color var(--duration-fast) var(--ease-out),
              background var(--duration-fast) var(--ease-out);
}
.nav-link:hover { color: var(--ink); background: rgba(0,0,0,0.02); }
.nav-marker {
  display: inline-block;
  width: 2px;
  height: 14px;
  background: transparent;
  transition: background var(--duration-base) var(--ease-out);
  flex-shrink: 0;
}
.nav-link.active {
  color: var(--ink);
  font-weight: var(--weight-semibold);
  background: var(--surface);
}
.nav-link.active .nav-marker { background: var(--accent); }
.nav-label {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.nav-glyph {
  font-family: var(--font-display);
  font-size: var(--text-md);
  font-weight: 500;
  width: 100%;
  text-align: center;
  color: inherit;
}
.collapsed .nav-link {
  justify-content: center;
  padding: var(--space-3) 0;
  position: relative;
}
.collapsed .nav-marker {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  height: 18px;
}
.collapsed .nav-eyebrow { text-align: center; padding: 0; }
</style>
