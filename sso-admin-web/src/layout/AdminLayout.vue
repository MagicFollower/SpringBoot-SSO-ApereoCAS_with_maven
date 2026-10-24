<template>
  <div class="shell" :class="{ 'is-collapsed': collapsed }">
    <aside class="shell__side" :class="{ 'is-collapsed': collapsed }">
      <div class="shell__brand">
        <span class="shell__logo" data-g2-exempt>S</span>
        <span v-show="!collapsed" class="ds-h3">SSO 管理台</span>
      </div>

      <el-menu
        class="shell__menu"
        :default-active="route.path"
        :collapse="collapsed"
        :collapse-transition="false"
        router
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>

      <div class="shell__side-foot">
        <el-button
          text
          :icon="collapsed ? Expand : Fold"
          :aria-label="collapsed ? '展开侧栏' : '收起侧栏'"
          @click="collapsed = !collapsed"
        />
      </div>
    </aside>

    <div class="shell__main">
      <header class="shell__header">
        <el-button
          class="shell__burger"
          text
          :icon="Menu"
          aria-label="打开菜单"
          @click="mobileOpen = true"
        />
        <el-breadcrumb separator="/">
          <el-breadcrumb-item>管理台</el-breadcrumb-item>
          <el-breadcrumb-item>{{ route.meta.title ?? '' }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="shell__header-actions">
          <el-tooltip :content="dark ? '切换到亮色模式' : '切换到暗色模式'" placement="bottom">
            <el-button text :icon="dark ? Sunny : Moon" aria-label="切换明暗主题" @click="toggleDark" />
          </el-tooltip>
          <el-dropdown @command="handleCommand">
            <span class="shell__user">
              <el-avatar :size="28" class="shell__avatar"><el-icon><Key /></el-icon></el-avatar>
              <span class="ds-body">管理会话</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="switch" divided>切换令牌</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="shell__content">
        <router-view v-slot="{ Component, route: r }">
          <transition name="ds-page" mode="out-in" appear>
            <component :is="Component" :key="r.path" />
          </transition>
        </router-view>
      </main>
    </div>

    <el-drawer v-model="mobileOpen" direction="ltr" size="240px" :with-header="false">
      <el-menu :default-active="route.path" router @select="mobileOpen = false">
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  Connection,
  Expand,
  Fold,
  Key,
  Menu,
  Moon,
  Sunny,
  User,
  UserFilled,
} from '@element-plus/icons-vue'
import { applyTheme } from '../styles/theme'
import { clearToken } from '../api'

const PRIMARY = '#2F80ED'

const route = useRoute()
const router = useRouter()
const collapsed = ref(false)
const mobileOpen = ref(false)
const dark = ref(document.documentElement.classList.contains('dark'))

const menus = [
  { path: '/channels', title: '条线管理', icon: Connection },
  { path: '/channel-users', title: '条线用户', icon: UserFilled },
  { path: '/user-admin', title: '用户管理', icon: User },
  { path: '/users', title: '用户授权', icon: Key },
  { path: '/services', title: '接入系统', icon: Menu },
]

const activeMenu = computed(() => route.path)

function toggleDark() {
  dark.value = !dark.value
  localStorage.setItem('sso_admin_theme', dark.value ? 'dark' : 'light')
  applyTheme(PRIMARY, dark.value)
}

function handleCommand(command) {
  if (command === 'switch') {
    clearToken()
    router.replace('/gate')
  }
}
</script>

<style scoped>
.shell {
  --side-w: 240px;
  display: grid;
  grid-template-columns: var(--side-w) minmax(0, 1fr);
  min-height: 100vh;
  background: var(--el-bg-color-page);
}

.shell.is-collapsed {
  --side-w: 64px;
}

.shell__side {
  display: flex;
  flex-direction: column;
  width: var(--side-w);
  border-right: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  position: sticky;
  top: 0;
  height: 100vh;
  overflow: hidden;
}

.shell__brand {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  height: 64px;
  padding: 0 var(--space-4);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.shell__logo {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  background: var(--el-color-primary);
  color: #fff;
  font-weight: 600;
  /* 品牌角标为正圆，登记 G2 例外 */
  border-radius: 50%;
}

.shell__menu {
  flex: 1;
  border-right: 0;
  padding-top: var(--space-2);
}

.shell__side-foot {
  padding: var(--space-2);
  border-top: 1px solid var(--el-border-color-lighter);
}

.shell__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.shell__header {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  height: 64px;
  padding: 0 var(--space-6);
  background: var(--el-bg-color);
  border-bottom: 1px solid var(--el-border-color-light);
  position: sticky;
  top: 0;
  z-index: var(--el-index-top);
}

.shell__header-actions {
  margin-inline-start: auto;
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.shell__user {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  cursor: pointer;
  outline: none;
}

.shell__avatar {
  background: var(--el-color-primary);
  color: #fff;
}

.shell__content {
  flex: 1;
  min-width: 0;
}

.shell__burger {
  display: none;
}

@media (max-width: 839px) {
  .shell {
    grid-template-columns: minmax(0, 1fr);
  }

  .shell__side {
    display: none;
  }

  .shell__burger {
    display: inline-flex;
  }

  .shell__header {
    padding: 0 var(--space-4);
  }
}
</style>
