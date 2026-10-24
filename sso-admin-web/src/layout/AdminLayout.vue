<template>
  <el-container class="layout">
    <el-aside class="layout-aside" width="var(--admin-aside-width)">
      <div class="aside-brand">
        <el-icon :size="22"><Lock /></el-icon>
        <div>
          <div class="brand-title">SSO 管理台</div>
          <div class="brand-sub">CAS 统一认证</div>
        </div>
      </div>
      <el-menu
        class="aside-menu"
        :default-active="route.path"
        background-color="transparent"
        text-color="#aab6c4"
        active-text-color="#ffffff"
        router
      >
        <el-menu-item index="/channels">
          <el-icon><Connection /></el-icon>
          <span>条线管理</span>
        </el-menu-item>
        <el-menu-item index="/channel-users">
          <el-icon><UserFilled /></el-icon>
          <span>条线用户</span>
        </el-menu-item>
        <el-menu-item index="/user-admin">
          <el-icon><Avatar /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>用户授权</span>
        </el-menu-item>
        <el-menu-item index="/services">
          <el-icon><Menu /></el-icon>
          <span>接入系统</span>
        </el-menu-item>
      </el-menu>
      <div class="aside-footer">
        <span>后端：127.0.0.1:8084</span>
        <span>控制面：CAS 回环</span>
      </div>
    </el-aside>

    <el-container>
      <el-header class="layout-header">
        <div class="header-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item>管理台</el-breadcrumb-item>
            <el-breadcrumb-item>{{ route.meta.title ?? '' }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-tag type="success" effect="plain" size="small">令牌已加载</el-tag>
          <el-dropdown @command="handleCommand">
            <span class="user-chip">
              <el-avatar :size="26" class="user-avatar"><el-icon><Key /></el-icon></el-avatar>
              <span class="user-name">管理会话</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="switch">切换令牌</el-dropdown-item>
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
import { useRoute, useRouter } from 'vue-router'
import { clearToken } from '../api'

const route = useRoute()
const router = useRouter()

function handleCommand(command) {
  if (command === 'switch') {
    clearToken()
    router.replace('/gate')
  }
}
</script>

<style scoped>
.layout {
  height: 100%;
}

.layout-aside {
  display: flex;
  flex-direction: column;
  background: var(--admin-aside-bg);
}

.aside-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 62px;
  padding: 0 18px;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-title {
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.brand-sub {
  color: #7d8b9a;
  font-size: 12px;
}

.aside-menu {
  flex: 1;
  border-right: none;
  padding-top: 8px;
}

.aside-menu :deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
}

.aside-menu :deep(.el-menu-item.is-active) {
  margin: 0 8px;
  border-radius: 6px;
  background: var(--admin-accent);
}

.aside-footer {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 18px;
  color: #6f7d8c;
  font-size: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.layout-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 62px;
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
  outline: none;
}

.user-avatar {
  background: var(--admin-accent);
  color: #fff;
}

.user-name {
  font-size: 14px;
}

.layout-main {
  padding: 20px;
  overflow-y: auto;
}
</style>