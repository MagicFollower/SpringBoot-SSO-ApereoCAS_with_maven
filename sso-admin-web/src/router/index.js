import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api'

const routes = [
  {
    path: '/gate',
    name: 'gate',
    component: () => import('../views/TokenGateView.vue'),
    meta: { public: true, title: '访问令牌' }
  },
  {
    path: '/',
    component: () => import('../layout/AdminLayout.vue'),
    redirect: '/channels',
    children: [
      {
        path: 'channels',
        name: 'channels',
        component: () => import('../views/ChannelsView.vue'),
        meta: { title: '条线管理' }
      },
      {
        path: 'channel-users',
        name: 'channel-users',
        component: () => import('../views/ChannelUsersView.vue'),
        meta: { title: '条线用户' }
      },
      {
        path: 'user-admin',
        name: 'user-admin',
        component: () => import('../views/UserAdminView.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('../views/UsersView.vue'),
        meta: { title: '用户授权' }
      },
      {
        path: 'services',
        name: 'services',
        component: () => import('../views/ServicesView.vue'),
        meta: { title: '接入系统' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/channels' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) {
    return true
  }
  if (!getToken()) {
    return { name: 'gate' }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - SSO 管理台` : 'SSO 管理台'
})

export default router