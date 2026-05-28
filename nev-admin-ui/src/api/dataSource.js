import api from '../composables/useApi'

export const listDataSources = (sourceType) =>
  api.get('/api/v1/data-source', { params: sourceType ? { sourceType } : {} })

export const createDataSource = (data) =>
  api.post('/api/v1/data-source', data)

export const updateDataSource = (id, data) =>
  api.put(`/api/v1/data-source/${id}`, data)

export const deleteDataSource = (id) =>
  api.delete(`/api/v1/data-source/${id}`)
