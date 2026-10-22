import axios from 'axios'

const TOKEN_KEY = 'sso_admin_token'

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY) || ''
}

export function saveToken(token) {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function clearToken() {
  sessionStorage.removeItem(TOKEN_KEY)
}

const http = axios.create({
  baseURL: '/api',
  timeout: 10000
})

http.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers['X-Admin-Token'] = token
  }
  config.headers['Cache-Control'] = 'no-store'
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && typeof payload === 'object' && 'code' in payload) {
      if (payload.code === 0) {
        return payload.data
      }
      return Promise.reject(new Error(payload.message || '请求失败'))
    }
    return payload
  },
  (error) => {
    const status = error.response?.status
    const payload = error.response?.data
    if (status === 401) {
      clearToken()
      if (window.location.pathname !== '/gate') {
        window.location.assign('/gate')
      }
    }
    const message = payload?.message || payload?.error || error.message || '网络请求失败'
    const wrapped = new Error(message)
    wrapped.status = status
    return Promise.reject(wrapped)
  }
)

export const api = {
  channels: () => http.get('/channels'),
  upsertChannel: (data) => http.post('/channels', data),
  users: (q) => http.get('/users', { params: { q } }),
  grants: (username) => http.get(`/users/${encodeURIComponent(username)}/grants`),
  grant: (username, data) => http.post(`/users/${encodeURIComponent(username)}/grants`, data),
  revoke: (username, channelCode) =>
    http.delete(`/users/${encodeURIComponent(username)}/grants/${encodeURIComponent(channelCode)}`),
  services: () => http.get('/services'),
  createService: (data) => http.post('/services', data),
  deleteService: (id) => http.delete(`/services/${id}`),
  sessions: (username) => http.get(`/users/${encodeURIComponent(username)}/sessions`),
  kickout: (username) => http.post(`/users/${encodeURIComponent(username)}/kickout`)
}