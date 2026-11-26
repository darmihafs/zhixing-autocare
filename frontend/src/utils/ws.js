/**
 * 可重连 WebSocket 小工具：
 * - 每 15s 发送一次 PING，后端回 PONG 完成心跳保活；
 * - 非主动关闭时按指数退避重连（1s/2s/4s/8s，上限 15s）；
 * - 状态通过 onState 回调给页面：connecting / online / offline。
 */
export function createReconnectingSocket(url, options = {}) {
  const {
    onMessage,
    onState,
    heartbeatMs = 15000,
    maxDelayMs = 15000
  } = options

  let socket = null
  let heartbeatTimer = null
  let retryTimer = null
  let attempts = 0
  let stopped = false

  function clearHeartbeat() {
    if (heartbeatTimer) {
      clearInterval(heartbeatTimer)
      heartbeatTimer = null
    }
  }

  function clearRetry() {
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
  }

  function startHeartbeat() {
    clearHeartbeat()
    heartbeatTimer = setInterval(() => {
      if (socket?.readyState === WebSocket.OPEN) {
        socket.send('PING')
      }
    }, heartbeatMs)
  }

  function scheduleReconnect() {
    if (stopped) return
    const delay = Math.min(1000 * 2 ** attempts, maxDelayMs)
    attempts += 1
    clearRetry()
    retryTimer = setTimeout(connect, delay)
  }

  function connect() {
    if (stopped) return
    onState?.('connecting')
    try {
      socket = new WebSocket(url)
    } catch {
      onState?.('offline')
      scheduleReconnect()
      return
    }
    socket.onopen = () => {
      attempts = 0
      onState?.('online')
      startHeartbeat()
    }
    socket.onmessage = (event) => {
      if (event.data === 'PONG') return
      onMessage?.(event.data)
    }
    socket.onerror = () => {
      onState?.('offline')
    }
    socket.onclose = () => {
      clearHeartbeat()
      socket = null
      if (!stopped) {
        onState?.('offline')
        scheduleReconnect()
      }
    }
  }

  connect()

  return {
    send(data) {
      if (socket?.readyState === WebSocket.OPEN) socket.send(data)
    },
    stop() {
      stopped = true
      clearHeartbeat()
      clearRetry()
      if (socket) {
        socket.onclose = null
        socket.close()
        socket = null
      }
    }
  }
}
