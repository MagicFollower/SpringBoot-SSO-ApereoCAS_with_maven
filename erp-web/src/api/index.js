import axios from 'axios'

const http = axios.create({
  baseURL: '/erp',
  timeout: 10000,
  withCredentials: true
})

http.interceptors.response.use(
  (response) => response.data,
  (error) => {
    const status = error.response?.status
    // 会话失效（ErpSessionFilter 401）：整页跳登录页，带上当前位置供 CAS 回调后还原深链接。
    // 用 location.assign 而非 router：api 模块被 router→stores 依赖，反向引入会成环；
    // 整页跳转同时重置内存中的 Pinia 状态，避免 store.loaded 残留 true 掩盖未登录。
    if (status === 401 && window.location.pathname !== '/login') {
      const redirect = encodeURIComponent(window.location.pathname + window.location.search)
      window.location.assign(`/login?redirect=${redirect}`)
      return new Promise(() => {})
    }
    const payload = error.response?.data
    const message = payload?.message || error.message || '网络请求失败'
    const wrapped = new Error(message)
    wrapped.status = status
    return Promise.reject(wrapped)
  }
)

export function fetchSession() {
  return http.get('/api/session')
}

export function fetchMenus() {
  return http.get('/api/menus')
}

export function queryUsers(params) {
  return http.get('/api/users', { params })
}

export function logout() {
  return http.post('/api/logout')
}

export const casLoginPath = '/erp/login/cas'