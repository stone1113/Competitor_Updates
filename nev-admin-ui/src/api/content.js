import api from '../composables/useApi'

/**
 * 查询采集新闻
 * @param {Object} params { brand, keyword, crawlFrom, crawlTo, publishedFrom, publishedTo, sourceTool, sortBy, page, pageSize }
 */
export const queryNews = (params) =>
  api.get('/api/v1/content/news', { params })

/**
 * 查询社交平台内容
 * @param {Object} params { brand, keyword, crawlFrom, crawlTo, platforms, sortBy, page, pageSize }
 *   platforms 为逗号分隔字符串，如 "xhs,dy"
 */
export const querySocial = (params) =>
  api.get('/api/v1/content/social', { params })

/** 查询某条社交内容的评论，按点赞降序 */
export const queryComments = (platform, contentId, limit = 20) =>
  api.get('/api/v1/content/comments', { params: { platform, contentId, limit } })
