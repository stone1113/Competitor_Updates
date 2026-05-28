import api from '../composables/useApi'

/** 按对标车型查最新金融政策帖（不限时间窗）。 */
export const financeByModel = (model, limit = 5) =>
  api.get('/api/v1/finance/by-model', { params: { model, limit } })

/** 全 9 个对标车型一次性查（用于看板页）。 */
export const financeAllModels = (limit = 3) =>
  api.get('/api/v1/finance/all-models', { params: { limit } })
