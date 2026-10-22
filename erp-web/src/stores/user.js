import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { fetchSession, fetchMenus, logout as apiLogout } from '../api'

export const useUserStore = defineStore('user', () => {
  const user = ref(null)
  const menus = ref([])
  const loaded = ref(false)

  const authenticated = computed(() => user.value !== null)
  const displayName = computed(() => user.value?.displayName || user.value?.username || '')

  async function loadSession() {
    const res = await fetchSession()
    user.value = res.data?.authenticated ? res.data.user : null
    loaded.value = true
    return authenticated.value
  }

  async function loadMenus() {
    const res = await fetchMenus()
    menus.value = res.data ?? []
    return menus.value
  }

  async function signOut() {
    const res = await apiLogout()
    user.value = null
    menus.value = []
    loaded.value = false
    window.location.href = res.data.casLogoutUrl
  }

  return { user, menus, loaded, authenticated, displayName, loadSession, loadMenus, signOut }
})