import api from '../composables/useApi'

export const listBlacklist = (enabledOnly) =>
  api.get('/api/v1/blacklist', { params: enabledOnly ? { enabledOnly: true } : {} })

export const createBlacklist = (data) =>
  api.post('/api/v1/blacklist', data)

export const updateBlacklist = (id, data) =>
  api.put(`/api/v1/blacklist/${id}`, data)

export const deleteBlacklist = (id) =>
  api.delete(`/api/v1/blacklist/${id}`)
