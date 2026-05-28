import api from '../composables/useApi'

export const listKeywords = (params = {}) =>
  api.get('/api/v1/brand-keywords', { params })

export const listBrands = () =>
  api.get('/api/v1/brand-keywords/brands')

export const createKeyword = (data) =>
  api.post('/api/v1/brand-keywords', data)

export const updateKeyword = (id, data) =>
  api.put(`/api/v1/brand-keywords/${id}`, data)

export const deleteKeyword = (id) =>
  api.delete(`/api/v1/brand-keywords/${id}`)

export const bulkReplaceKeywords = (brand, keywords, category) =>
  api.post('/api/v1/brand-keywords/bulk-replace',
    category ? { brand, keywords, category } : { brand, keywords })
