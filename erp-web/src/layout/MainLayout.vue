<template>
  <div class="shell" :class="{ 'is-collapsed': collapsed }">
    <aside class="shell__side" :class="{ 'is-collapsed': collapsed }">
      <div class="shell__brand">
        <span class="shell__logo" data-g2-exempt>E</span>
        <span v-show="!collapsed" class="ds-h3">ERP 企业资源平台</span>
      </div>

      <el-menu
        class="shell__menu"
        :default-active="route.path"
        :collapse="collapsed"
        :collapse-transition="false"
        router
      >
        <sidebar-menu-item v-for="item in store.menus" :key="item.id" :item="item" />
      </el-menu>

      <div class="shell__side-foot">
        <el-tooltip :content="collapsed ? store.user?.channelCode ?? '-' : ''" placement="top" :disabled="!collapsed">
          <div v-show="!collapsed" class="ds-caption side-channel">
            channelCode：{{ store.user?.channelCode ?? '-' }}
          </div>
        </el-tooltip>
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
          <el-breadcrumb-item>ERP</el-breadcrumb-item>
          <el-breadcrumb-item>{{ route.meta.title ?? '首页' }}</el-breadcrumb-item>
        </el-breadcrumb>
        <div class="shell__header-actions">
          <el-tooltip :content="dark ? '切换到亮色模式' : '切换到暗色模式'" placement="bottom">
            <el-button text :icon="dark ? Sunny : Moon" aria-label="切换明暗主题" @click="toggleDark" />
          </el-tooltip>
          <el-tag type="success" effect="plain">CAS 单点登录</el-tag>
          <el-dropdown @command="handleCommand">
            <span class="shell__user">
              <el-avatar :size="28" class="shell__avatar">{{ avatarText }}</el-avatar>
              <span class="ds-body">{{ store.displayName }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>账号：{{ store.user?.username }}</el-dropdown-item>
                <el-dropdown-item disabled>部门：{{ store.user?.deptId ?? '-' }}</el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
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
        <sidebar-menu-item v-for="item in store.menus" :key="item.id" :item="item" />
      </el-menu>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowDown, Expand, Fold, Menu, Moon, Sunny, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import { applyTheme } from '../styles/theme'
import { toast } from '../composables/use-toast'
import SidebarMenuItem from './SidebarMenuItem.vue'

const PRIMARY = '#2F80ED'

const route = useRoute()
const store = useUserStore()
const collapsed = ref(false)
const mobileOpen = ref(false)
const dark = ref(document.documentElement.classList.contains('dark'))

const avatarText = computed(() => (store.displayName || '?').slice(0, 1).toUpperCase())

function toggleDark() {
  dark.value = !dark.value
  localStorage.setItem('erp_theme', dark.value ? 'dark' : 'light')
  applyTheme(PRIMARY, dark.value)
}

onMounted(async () => {
  try {
    await store.loadMenus()
  } catch (error) {
    toast.error(`菜单加载失败：${error.message}`)
  }
})

async function handleCommand(command) {
  if (command !== 'logout') {
    return
  }
  try {
    await ElMessageBox.confirm('退出后将同步注销 CAS 单点登录会话，是否继续？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  await store.signOut()
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
  overflow-y: auto;
  border-right: 0;
  padding-top: var(--space-2);
}

.shell__side-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: var(--space-2);
  border-top: 1px solid var(--el-border-color-lighter);
}

.side-channel {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
