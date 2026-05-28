import api from '../composables/useApi'

export const getBriefingOverview = (brand = '猛士') =>
  api.get('/api/v1/briefing/overview', { params: { brand } })

export const getBriefingTrend = (brand = '猛士', days = 7) =>
  api.get('/api/v1/briefing/trend', { params: { brand, days } })
