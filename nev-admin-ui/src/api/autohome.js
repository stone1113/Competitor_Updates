import api from '../composables/useApi'

export const listSeries = (role) =>
  api.get('/api/v1/autohome/series', { params: role ? { role } : {} })

export const createSeries = (data) =>
  api.post('/api/v1/autohome/series', data)

export const updateSeries = (id, data) =>
  api.put(`/api/v1/autohome/series/${id}`, data)

export const deleteSeries = (id) =>
  api.delete(`/api/v1/autohome/series/${id}`)

export const syncSeries = (seriesId) =>
  api.post(`/api/v1/autohome/sync/${seriesId}`)

export const syncAllSeries = () =>
  api.post('/api/v1/autohome/sync-all')

export const compareSpecs = (seriesIds) =>
  api.get('/api/v1/autohome/specs', { params: { seriesIds: seriesIds.join(',') } })

export const benchmarkBySelf = (selfModel) =>
  api.get('/api/v1/autohome/benchmark/by-self', { params: { selfModel } })
