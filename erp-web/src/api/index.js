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