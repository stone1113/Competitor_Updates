import api from '../composables/useApi'

export const getDeepAnalysisUrl = () =>
  api.get('/api/v1/competitor-report/deep-analysis-url')

export const generateReport = (push = false, date) =>
  api.post('/api/v1/competitor-report/generate', null, {
    params: { push, ...(date ? { date } : {}) }
  })

export const syncAutohome = () =>
  api.post('/api/v1/competitor-report/sync-autohome')

export const uploadAutohomeToRagflow = () =>
  api.post('/api/v1/competitor-report/upload-autohome-to-ragflow')
