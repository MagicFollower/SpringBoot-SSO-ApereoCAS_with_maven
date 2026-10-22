import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('../layout/MainLayout.vue'),
    redirect: '/home',
    children: [
      {
        path: 'home',
        name: 'home',
        component: () => import('../views/HomeView.vue'),
        meta: { title: '首页' }
      },
      {
        path: 'system/users',
        name: 'system-users',
        component: () => import('../views/SystemUsersView.vue'),
        meta: { title: '用户管理' }
      },
      {
        path: 'system/menus',
        name: 'system-menus',
        component: () => import('../views/SystemMenusView.vue'),
        meta: { title: '菜单管理' }
      },
      {
        path: 'example/orders',
        name: 'example-orders',
        component: () => import('../views/ExampleOrdersView.vue'),
        meta: { title: '订单示例' }
      },
      {
        path: 'reports',
        name: 'reports',
        component: () => import('../views/ReportsView.vue'),
        meta: { title: '报表中心' }
      },
      {
        path: 'erp/board',
        name: 'erp-board',
        component: () => import('../views/ErpBoardView.vue'),
        meta: { title: 'ERP 专属看板' }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/home' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  if (to.meta.public) {
    return true
  }
  const store = useUserStore()
  if (!store.loaded) {
    try {
      await store.loadSession()
    } catch {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
  }
  if (!store.authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - ERP 企业资源平台` : 'ERP 企业资源平台'
})

export default router