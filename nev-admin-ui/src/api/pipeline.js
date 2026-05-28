import api from '../composables/useApi'

export const generateReport = (date, persist = true, push = false) =>
  api.post('/api/v1/report-pipeline/generate', null, {
    params: { date, persist, push },
    timeout: 300000
  })
