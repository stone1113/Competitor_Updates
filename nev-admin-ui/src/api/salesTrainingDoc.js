import api from '../composables/useApi'

const BASE = '/api/v1/sales-training-doc'

export const listDocs = (params = {}) => api.get(BASE, { params })
export const getDoc = (id) => api.get(`${BASE}/${id}`)
export const createDoc = (body) => api.post(BASE, body)
export const deleteDoc = (id) => api.delete(`${BASE}/${id}`)
