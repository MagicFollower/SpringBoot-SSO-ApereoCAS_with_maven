<template>
  <div class="gate">
    <div class="gate__panel">
      <div class="gate__head">
        <span class="gate__logo" data-g2-exempt>
          <el-icon :size="20"><Lock /></el-icon>
        </span>
        <h1 class="ds-display">SSO 管理台</h1>
        <p class="ds-body ds-muted">CAS 统一认证 · 条线 / 用户授权 / 接入系统</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="管理令牌" prop="token">
          <el-input
            v-model="form.token"
            type="password"
            show-password
            size="large"
            placeholder="请输入 ADMIN_UI_TOKEN"
            autocomplete="off"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          native-type="submit"
          :loading="loading"
          class="gate__submit ds-touch"
        >
          进入管理台
        </el-button>
      </el-form>

      <div class="gate__foot ds-caption">
        <span>令牌仅保存在当前标签页的 sessionStorage，不会写入服务端。</span>
        <span>后端仅监听 127.0.0.1:8084，控制面请求仅发往本机 CAS。</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import { api, getToken, saveToken, clearToken } from '../api'
import { toast } from '../composables/use-toast'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ token: '' })

const rules = {
  token: [{ required: true, message: '请输入管理令牌', trigger: 'blur' }],
}

onMounted(() => {
  if (getToken()) {
    router.replace('/channels')
  }
})

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  const value = form.token.trim()
  saveToken(value)
  try {
    await api.channels()
    await router.replace('/channels')
  } catch (e) {
    clearToken()
    toast.error(
      e.status === 503
        ? '管理接口未启用：服务端未配置 ADMIN_UI_TOKEN，请先在 admin-server 侧配置'
        : `令牌校验失败：${e.message}`
    )
  } finally {
    loading.value = false
  }
}
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

.gate__submit {
  width: 100%;
  margin-top: var(--space-2);
}

.gate__foot {
  display: grid;
  gap: var(--space-1);
  padding-top: var(--space-4);
  border-top: 1px solid var(--el-border-color-lighter);
  text-align: center;
}
</style>
