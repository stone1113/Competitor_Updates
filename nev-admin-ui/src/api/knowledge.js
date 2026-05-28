import api from '../composables/useApi'

export const listDocuments = () =>
  api.get('/api/v1/knowledge')

export const createDocument = (data) =>
  api.post('/api/v1/knowledge', data)

export const deleteDocument = (id) =>
  api.delete(`/api/v1/knowledge/${id}`)
