<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <el-icon :size="30" color="#2f80ed"><OfficeBuilding /></el-icon>
        <h1>ERP 企业资源平台</h1>
        <p>统一身份认证 · CAS 单点登录</p>
      </div>

      <template v-if="mode === 'redirecting'">
        <div class="state">
          <el-icon class="is-loading" :size="26"><Loading /></el-icon>
          <p>正在跳转到 CAS 统一登录页…</p>
        </div>
      </template>

      <template v-else-if="mode === 'ok'">
        <div class="state">
          <el-icon class="is-loading" :size="26"><Loading /></el-icon>
          <p>登录成功，正在进入系统…</p>
        </div>
      </template>

      <template v-else>
        <el-alert type="error" :closable="false" title="单点登录失败" :description="message" show-icon />
        <div class="state actions">
          <el-button type="primary" @click="gotoCas">重新登录</el-button>
        </div>
      </template>

      <div class="footnote">
        <span>登录入口：CAS Server（localhost:8080/cas）</span>
        <span>接入条线：channelCode = ERP</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'
import { casLoginPath } from '../api'

const route = useRoute()
const router = useRouter()
const store = useUserStore()

const mode = ref('redirecting')
const message = ref('')

const REDIRECT_KEY = 'erp_login_redirect'

function gotoCas() {
  const redirect = route.query.redirect
  if (typeof redirect === 'string' && redirect.startsWith('/')) {
    sessionStorage.setItem(REDIRECT_KEY, redirect)
  }
  window.location.href = casLoginPath
}

function targetPath() {
  const fromQuery = route.query.redirect
  if (typeof fromQuery === 'string' && fromQuery.startsWith('/')) {
    return fromQuery
  }
  const stored = sessionStorage.getItem(REDIRECT_KEY)
  if (stored && stored.startsWith('/')) {
    sessionStorage.removeItem(REDIRECT_KEY)
    return stored
  }
  return '/home'
}

onMounted(async () => {
  const cas = route.query.cas
  if (cas === 'fail') {
    mode.value = 'fail'
    message.value = String(route.query.message || 'CAS 票据校验未通过')
    return
  }
  if (cas === 'ok') {
    mode.value = 'ok'
    try {
      const authenticated = await store.loadSession()
      if (authenticated) {
        await router.replace(targetPath())
        return
      }
      mode.value = 'fail'
      message.value = '会话建立失败，请重新登录'
    } catch (error) {
      mode.value = 'fail'
      message.value = error.message
    }
    return
  }
  gotoCas()
})
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(135deg, #1d2b3a 0%, #2f80ed 100%);
}

.login-card {
  width: 420px;
  padding: 36px 32px 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 18px 50px rgba(12, 30, 60, 0.35);
}

.brand {
  text-align: center;
  margin-bottom: 26px;
}

.brand h1 {
  margin: 10px 0 6px;
  font-size: 20px;
  color: #1d2b3a;
}

.brand p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 10px 0 22px;
  color: #606266;
  font-size: 14px;
}

.state.actions {
  padding-top: 20px;
}

.footnote {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
  color: #a8abb2;
  font-size: 12px;
  text-align: center;
}
</style>