import api from '../composables/useApi'

/** 启动深度对标任务，返 { taskId } */
export const startDeepBenchmark = (selfModel, competitorModels) =>
  api.post('/api/v1/deep-benchmark/start', null, {
    params: { selfModel, competitorModels: competitorModels.join(',') }
  })

/** 拉取任务完整结果（用于重连） */
export const getDeepBenchmark = (taskId) =>
  api.get(`/api/v1/deep-benchmark/${taskId}`)

/** 推飞书 */
export const pushDeepBenchmarkToFeishu = (taskId) =>
  api.post(`/api/v1/deep-benchmark/${taskId}/push-feishu`)

/** SSE 订阅（返 EventSource，由调用方手动 close）。
 *  EventSource 不支持自定义 header，但项目 API 走 same-origin 代理无需 token。
 */
export const subscribeDeepBenchmark = (taskId, onEvent) => {
  const es = new EventSource(`/api/v1/deep-benchmark/${taskId}/stream`)
  // 服务端用 event name = step name；client 监听通用 message 也行
  const handler = (e) => {
    try {
      const data = JSON.parse(e.data)
      onEvent(data)
    } catch (err) {
      console.error('SSE parse error', err)
    }
  }
  // 监听所有可能的事件名
  ;['DATA_COLLECTOR', 'POWER', 'BODY', 'OFFROAD', 'PRICE', 'SALES', 'SYNTHESIZER', 'TASK']
    .forEach(name => es.addEventListener(name, handler))
  es.onerror = (err) => {
    // 任务完成后 server 会 close，浏览器会触发 error 事件，属正常关闭
    if (es.readyState === EventSource.CLOSED) return
    console.warn('SSE error', err)
  }
  return es
}
