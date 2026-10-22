<template>
  <el-container class="layout">
    <el-aside class="layout-aside" :width="'var(--erp-aside-width)'">
      <div class="aside-brand">
        <el-icon :size="22"><OfficeBuilding /></el-icon>
        <span>ERP 企业资源平台</span>
      </div>
      <el-menu
        class="aside-menu"
        :default-active="route.path"
        background-color="transparent"
        text-color="#b9c4d0"
        active-text-color="#ffffff"
        :collapse="false"
        router
      >
        <sidebar-menu-item v-for="item in store.menus" :key="item.id" :item="item" />
      </el-menu>
      <div class="aside-footer">
        <span>channelCode：{{ store.user?.channelCode ?? '-' }}</span>
      </div>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>ERP</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title ?? '首页' }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tag class="sso-tag" type="success" effect="plain" size="small">CAS 单点登录</el-tag>
          <el-dropdown @command="handleCommand">
            <span class="user-chip">
              <el-avatar :size="28" class="user-avatar">{{ avatarText }}</el-avatar>
              <span class="user-name">{{ store.displayName }}</span>
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
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../stores/user'
import SidebarMenuItem from './SidebarMenuItem.vue'

const route = useRoute()
const store = useUserStore()

const avatarText = computed(() => (store.displayName || '?').slice(0, 1).toUpperCase())

onMounted(async () => {
  try {
    await store.loadMenus()
  } catch (error) {
    ElMessage.error(`菜单加载失败：${error.message}`)
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
.layout {
  height: 100%;
}

.layout-aside {
  display: flex;
  flex-direction: column;
  background: var(--erp-aside-bg);
}

.aside-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 60px;
  padding: 0 18px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.aside-menu {
  flex: 1;
  overflow-y: auto;
  border-right: none;
  padding: 8px 0;
}

.aside-menu :deep(.el-menu-item),
.aside-menu :deep(.el-sub-menu__title) {
  height: 44px;
  line-height: 44px;
  font-size: 14px;
}

.aside-menu :deep(.el-menu-item.is-active) {
  background: var(--erp-aside-bg-active);
  border-radius: 6px;
  margin: 0 8px;
}

.aside-footer {
  padding: 12px 18px;
  color: #7d8b9a;
  font-size: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 60px;
  padding: 0 20px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(24, 42, 66, 0.06);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: #303133;
  outline: none;
}

.user-avatar {
  background: #2f80ed;
  color: #fff;
  font-size: 13px;
}

.user-name {
  font-size: 14px;
}

.layout-main {
  padding: 20px;
  overflow-y: auto;
}
</style>