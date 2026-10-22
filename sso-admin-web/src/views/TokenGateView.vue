<template>
  <div class="gate-page">
    <div class="gate-card">
      <div class="brand">
        <el-icon :size="30" color="#2f80ed"><Lock /></el-icon>
        <h1>SSO 管理台</h1>
        <p>CAS 统一认证 · 条线 / 用户授权 / 接入系统</p>
      </div>

      <el-alert
        v-if="error"
        type="error"
        :closable="false"
        :title="error"
        show-icon
        class="gate-error"
      />

      <el-form @submit.prevent="submit">
        <el-form-item label="管理令牌">
          <el-input
            v-model="token"
            type="password"
            show-password
            placeholder="请输入 ADMIN_UI_TOKEN"
            autocomplete="off"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" class="gate-submit" :loading="loading" @click="submit">
          进入管理台
        </el-button>
      </el-form>

      <div class="footnote">
        <span>令牌仅保存在当前标签页的 sessionStorage，不会写入服务端。</span>
        <span>后端仅监听 127.0.0.1:8084，控制面请求仅发往本机 CAS。</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, getToken, saveToken, clearToken } from '../api'

const router = useRouter()
const token = ref('')
const loading = ref(false)
const error = ref('')

onMounted(() => {
  if (getToken()) {
    router.replace('/channels')
  }
})

async function submit() {
  const value = token.value.trim()
  if (!value) {
    error.value = '请输入管理令牌'
    return
  }
  loading.value = true
  error.value = ''
  saveToken(value)
  try {
    await api.channels()
    await router.replace('/channels')
  } catch (e) {
    clearToken()
    error.value = e.status === 503
      ? '管理接口未启用：服务端未配置 ADMIN_UI_TOKEN'
      : `令牌校验失败：${e.message}`
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.gate-page {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(135deg, #17202b 0%, #2f80ed 100%);
}

.gate-card {
  width: 420px;
  padding: 36px 32px 20px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 18px 50px rgba(12, 30, 60, 0.35);
}

.brand {
  text-align: center;
  margin-bottom: 24px;
}

.brand h1 {
  margin: 10px 0 6px;
  font-size: 20px;
  color: #17202b;
}

.brand p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}

.gate-error {
  margin-bottom: 16px;
}

.gate-submit {
  width: 100%;
}

.footnote {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 16px;
  margin-top: 20px;
  border-top: 1px solid #ebeef5;
  color: #a8abb2;
  font-size: 12px;
  line-height: 1.5;
  text-align: center;
}
</style>