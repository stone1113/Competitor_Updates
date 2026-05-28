import api from '../composables/useApi'

const USER_TOKEN_KEY = 'nev_user_token'

/** 拿 / 生成持久 user token（v9.5 localStorage；v9.6 替换为飞书 user_id） */
export function getUserToken() {
  let t = localStorage.getItem(USER_TOKEN_KEY)
  if (!t) {
    t = 'u-' + Math.random().toString(36).substring(2, 12) + Date.now().toString(36)
    localStorage.setItem(USER_TOKEN_KEY, t)
  }
  return t
}

function headers() {
  return { 'X-User-Token': getUserToken() }
}

export const listSessions = (limit = 30) =>
  api.get('/api/v1/chat/sessions', { params: { limit }, headers: headers() })

export const getSession = (id) =>
  api.get(`/api/v1/chat/session/${id}`, { headers: headers() })

export const startChat = (selfModel, competitorModels, userPrompt) =>
  api.post('/api/v1/chat/start', null, {
    params: { selfModel, competitorModels: competitorModels.join(','), userPrompt },
    headers: headers(),
    timeout: 300_000  // 5 min for full 5-analyst + synthesizer
  })

export const followup = (sessionId, userPrompt) =>
  api.post('/api/v1/chat/followup', null, {
    params: { sessionId, userPrompt },
    headers: headers(),
    timeout: 120_000  // 2 min
  })
