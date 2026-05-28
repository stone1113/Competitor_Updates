import api from '../composables/useApi'

export const listPrompts = (agentName) =>
  api.get('/api/v1/prompt', { params: agentName ? { agentName } : {} })

export const getActivePrompt = (agentName) =>
  api.get(`/api/v1/prompt/active/${agentName}`)

export const createPrompt = (data) =>
  api.post('/api/v1/prompt', data)

export const updatePrompt = (id, data) =>
  api.put(`/api/v1/prompt/${id}`, data)
