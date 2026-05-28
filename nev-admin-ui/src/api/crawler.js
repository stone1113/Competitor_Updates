import api from '../composables/useApi'

/**
 * 触发社交平台爬取（一次可触发多个平台）
 * @param {Object} req { brand, platforms: string[], keywords: string[], maxNotes, enableComments, loginType }
 */
export const triggerCrawl = (req) =>
  api.post('/api/v1/crawler/trigger', req)

export const listTasks = (limit = 50) =>
  api.get('/api/v1/crawler/tasks', { params: { limit } })

export const getTask = (id) =>
  api.get(`/api/v1/crawler/tasks/${id}`)

export const getTaskLogs = (id) =>
  api.get(`/api/v1/crawler/tasks/${id}/logs`)

export const checkHealth = () =>
  api.get('/api/v1/crawler/health')

export const getActivePlatforms = () =>
  api.get('/api/v1/crawler/active-platforms')

export const triggerRunAll = () =>
  api.post('/api/v1/crawler/run-all')

export const getRunAllStatus = () =>
  api.get('/api/v1/crawler/run-all/status')

export const getSchedule = () =>
  api.get('/api/v1/crawler/schedule')

export const updateSchedule = (enabled, cron) =>
  api.put('/api/v1/crawler/schedule', { enabled, cron })

export const triggerLogin = (platform) =>
  api.post('/api/v1/crawler/login', null, { params: { platform } })

export const rerunTask = (id) =>
  api.post(`/api/v1/crawler/tasks/${id}/rerun`)

export const deleteTask = (id) =>
  api.delete(`/api/v1/crawler/tasks/${id}`)

export const deleteAllTasks = (includeRunning = false) =>
  api.delete('/api/v1/crawler/tasks', { params: { includeRunning } })
