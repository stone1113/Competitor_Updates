import api from '../composables/useApi'

export const listEvents = (params = {}) =>
  api.get('/api/v1/events', { params })

export const statsEvents = (since) =>
  api.get('/api/v1/events/stats', { params: since ? { since } : {} })

export const updateEvent = (id, data) =>
  api.put(`/api/v1/events/${id}`, data)

export const reclassifyEvent = (id) =>
  api.post(`/api/v1/events/${id}/reclassify`)
