import api from '../composables/useApi'

export const listOfficialAccounts = (params = {}) =>
  api.get('/api/v1/official-accounts', { params })

export const createOfficialAccount = (data) =>
  api.post('/api/v1/official-accounts', data)

export const updateOfficialAccount = (id, data) =>
  api.put(`/api/v1/official-accounts/${id}`, data)

export const deleteOfficialAccount = (id) =>
  api.delete(`/api/v1/official-accounts/${id}`)

export const crawlNow = (platform) =>
  api.post('/api/v1/official-accounts/crawl-now', null, { params: platform ? { platform } : {} })

export const ingestNow = () =>
  api.post('/api/v1/official-accounts/ingest-now')
