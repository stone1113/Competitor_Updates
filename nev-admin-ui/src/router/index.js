import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/_design-system',
    name: 'DesignSystem',
    component: () => import('../views/_design/DesignSystemView.vue'),
    meta: { title: '设计系统' }
  },
  {
    path: '/_components',
    name: 'Components',
    component: () => import('../views/_design/ComponentsView.vue'),
    meta: { title: '组件库' }
  },
  {
    path: '/h5/benchmark',
    name: 'BenchmarkH5Standalone',
    component: () => import('../views/h5/BenchmarkH5StandaloneView.vue'),
    meta: { title: '智能竞品对标' }
  },
  {
    path: '/',
    component: () => import('../components/layout/AppLayout.vue'),
    redirect: '/daily-report',
    children: [
      {
        path: 'daily-report',
        name: 'DailyReport',
        component: () => import('../views/daily-report/DailyReportView.vue'),
        meta: { title: '舆情日报', icon: 'DataAnalysis' }
      },
      {
        path: 'competitor-dashboard',
        name: 'CompetitorDashboard',
        component: () => import('../views/competitor/CompetitorDashboardView.vue'),
        meta: { title: '竞品仪表盘', icon: 'TrendCharts' }
      },
      {
        path: 'competitor/autohome-config',
        name: 'AutohomeConfig',
        component: () => import('../views/competitor/AutohomeConfigView.vue'),
        meta: { title: '汽车之家车系配置', icon: 'Setting' }
      },
      {
        path: 'competitor/autohome-spec',
        name: 'AutohomeSpec',
        component: () => import('../views/competitor/AutohomeSpecView.vue'),
        meta: { title: '技术参数对标', icon: 'DataAnalysis' }
      },
      {
        path: 'competitor/deep-analysis',
        name: 'DeepAnalysis',
        component: () => import('../views/competitor/DeepAnalysisView.vue'),
        meta: { title: '智能深度对标', icon: 'ChatLineSquare' }
      },
      {
        path: 'competitor/deep-benchmark',
        name: 'DeepBenchmark',
        component: () => import('../views/competitor/ChatBenchmarkView.vue'),
        meta: { title: '自建对标助手（备用）', icon: 'Robot' }
      },
      {
        path: 'competitor/events',
        name: 'EventBrowser',
        component: () => import('../views/competitor/EventBrowserView.vue'),
        meta: { title: '事件浏览', icon: 'List' }
      },
      {
        path: 'competitor/benchmark',
        name: 'BenchmarkH5',
        component: () => import('../views/competitor/BenchmarkH5View.vue'),
        meta: { title: '技术对标矩阵', icon: 'Connection' }
      },
      {
        path: 'competitor/finance-by-model',
        name: 'FinanceByModel',
        component: () => import('../views/competitor/FinanceByModelView.vue'),
        meta: { title: '车型金融政策', icon: 'Coin' }
      },
      {
        path: 'collector/data-source',
        name: 'DataSource',
        component: () => import('../views/collector/DataSourceView.vue'),
        meta: { title: '数据源配置', icon: 'Connection' }
      },
      {
        path: 'collector/cleaning-rule',
        name: 'CleaningRule',
        component: () => import('../views/collector/CleaningRuleView.vue'),
        meta: { title: '清洗规则', icon: 'Filter' }
      },
      {
        path: 'collector/content-view',
        name: 'ContentView',
        component: () => import('../views/collector/ContentViewView.vue'),
        meta: { title: '采集内容查看', icon: 'View' }
      },
      {
        path: 'collector/social-crawler',
        name: 'SocialCrawler',
        component: () => import('../views/collector/SocialCrawlerView.vue'),
        meta: { title: '社交爬虫', icon: 'Avatar' }
      },
      {
        path: 'collector/brand-keywords',
        name: 'BrandKeywords',
        component: () => import('../views/collector/BrandKeywordView.vue'),
        meta: { title: '关键词配置', icon: 'Edit' }
      },
      {
        path: 'collector/official-accounts',
        name: 'OfficialAccounts',
        component: () => import('../views/collector/OfficialAccountConfigView.vue'),
        meta: { title: '官方账号', icon: 'User' }
      },
      {
        path: 'knowledge',
        name: 'Knowledge',
        component: () => import('../views/knowledge/KnowledgeView.vue'),
        meta: { title: '知识库管理', icon: 'Collection' }
      },
      {
        path: 'talking-point',
        name: 'TalkingPoint',
        component: () => import('../views/talking-point/TalkingPointView.vue'),
        meta: { title: '话术管理', icon: 'ChatLineSquare' }
      },
      {
        path: 'prompt',
        name: 'Prompt',
        component: () => import('../views/prompt/PromptView.vue'),
        meta: { title: 'Prompt 管理', icon: 'MagicStick' }
      },
      {
        path: 'report-console',
        name: 'ReportConsole',
        component: () => import('../views/console/ReportConsoleView.vue'),
        meta: { title: '日报控制台', icon: 'Monitor' }
      },
      {
        path: 'sales-training',
        name: 'SalesTrainingList',
        component: () => import('../views/sales-training/SalesTrainingListView.vue'),
        meta: { title: '销售培训文档', icon: 'Document' }
      },
      {
        path: 'sales-training/:id',
        name: 'SalesTrainingDetail',
        component: () => import('../views/sales-training/SalesTrainingDetailView.vue'),
        meta: { title: '销售培训文档详情', icon: 'Document' }
      },
      {
        path: 'system/url-blacklist',
        name: 'UrlBlacklist',
        component: () => import('../views/system/UrlBlacklistView.vue'),
        meta: { title: 'URL 黑名单', icon: 'Filter' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} · 猛士舆情系统` : '猛士舆情系统'
  next()
})

export default router
