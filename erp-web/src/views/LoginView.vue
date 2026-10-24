<template>
  <div class="gate">
    <div class="gate__panel">
      <div class="gate__head">
        <span class="gate__logo" data-g2-exempt>
          <el-icon :size="20"><OfficeBuilding /></el-icon>
        </span>
        <h1 class="ds-display">ERP 企业资源平台</h1>
        <p class="ds-body ds-muted">统一身份认证 · CAS 单点登录</p>
      </div>

      <div v-if="mode === 'redirecting'" class="gate__state">
        <el-icon class="is-loading" :size="26"><Loading /></el-icon>
        <p class="ds-body ds-muted">正在跳转到 CAS 统一登录页…</p>
      </div>

      <div v-else-if="mode === 'ok'" class="gate__state">
        <el-icon class="is-loading" :size="26"><Loading /></el-icon>
        <p class="ds-body ds-muted">登录成功，正在进入系统…</p>
      </div>

      <template v-else>
        <el-alert
          type="error"
          :closable="false"
          title="单点登录失败"
          :description="message"
          show-icon
        />
        <div class="ds-actions" style="padding-top: var(--space-4)">
          <el-button type="primary" @click="gotoCas">重新登录</el-button>
        </div>
      </template>

      <div class="gate__foot ds-caption">
        <span>登录入口：CAS Server（localhost:8080/cas）</span>
        <span>接入条线：channelCode = ERP</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading, OfficeBuilding } from '@element-plus/icons-vue'
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
.gate {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: var(--space-4);
  background: var(--el-bg-color-page);
}

.gate__panel {
  width: 100%;
  max-width: 400px;
  display: grid;
  gap: var(--space-6);
  padding: var(--space-8);
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--radius-lg);
}

.gate__head {
  display: grid;
  justify-items: center;
  gap: var(--space-3);
  text-align: center;
}

.gate__logo {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  background: var(--el-color-primary);
  color: #fff;
  /* 品牌角标为正圆，登记 G2 例外 */
  border-radius: 50%;
}

.gate__state {
  display: grid;
  justify-items: center;
  gap: var(--space-3);
  padding: var(--space-4) 0 var(--space-6);
}

.gate__foot {
  display: grid;
  gap: var(--space-1);
  padding-top: var(--space-4);
  border-top: 1px solid var(--el-border-color-lighter);
  text-align: center;
}
</style>
