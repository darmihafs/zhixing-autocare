import { orderApi } from '../api'

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/**
 * 轮询异步下单状态，直到 CREATED / FAILED / NOT_FOUND 或超时。
 * 返回最后一次状态；网络异常向上抛出，由页面统一提示。
 */
export async function waitForOrderCreated(orderNo, onState, options = {}) {
  const attempts = options.attempts || 40
  const interval = options.interval || 850
  let last = null
  for (let i = 0; i < attempts; i += 1) {
    const state = await orderApi.state(orderNo)
    last = state
    if (onState) onState(state)
    if (state.state === 'CREATED' || state.state === 'FAILED' || state.state === 'NOT_FOUND') {
      return state
    }
    await sleep(interval)
  }
  return last
}
