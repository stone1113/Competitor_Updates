import api from '../composables/useApi'

export const getKpi = (brand, date) =>
  api.get('/api/v1/daily-report/kpi', { params: { brand, date } })

export const getTopNews = (brand, date, limit = 10) =>
  api.get('/api/v1/daily-report/top-news', { params: { brand, date, limit } })

export const getTopBuzz = (brand, date, limit = 10) =>
  api.get('/api/v1/daily-report/top-buzz', { params: { brand, date, limit } })

export const getBuzzPaged = (brand, date, platform, page = 0, size = 20) =>
  api.get('/api/v1/daily-report/buzz', { params: { brand, date, platform, page, size } })
