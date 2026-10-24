<template>
  <div class="ds-page ds-page--wide">
    <el-card shadow="never">
      <div class="welcome">
        <div>
          <h1 class="ds-h2">你好，{{ store.displayName }} 👋</h1>
          <p class="ds-body ds-muted" style="margin: var(--space-1) 0 0">
            当前通过 <strong>CAS 单点登录</strong> 进入 ERP 系统，所属条线
            <el-tag size="small" effect="plain">{{ store.user?.channelCode }}</el-tag>
            ，部门 {{ store.user?.deptId ?? '-' }}。
          </p>
        </div>
        <div class="ds-actions">
          <el-button type="primary" @click="router.push('/system/users')">用户管理</el-button>
          <el-button @click="router.push('/erp/board')">ERP 专属看板</el-button>
        </div>
      </div>
    </el-card>

    <div class="metric-grid">
      <el-card v-for="card in statCards" :key="card.label" shadow="never">
        <el-statistic :value="card.value" :title="card.label" />
      </el-card>
    </div>

    <div class="two-col">
      <el-card shadow="never">
        <template #header><div class="ds-h3">认证链路</div></template>
        <p class="ds-caption" style="margin: 0 0 var(--space-4)">
          当前会话由以下环节串联完成，全部为真实调用。
        </p>
        <el-steps direction="vertical" :active="4">
          <el-step title="浏览器访问 ERP 前端" description="Vue3 应用检测到未登录，跳转后端 /erp/login/cas" />
          <el-step title="CAS 统一登录页" description="携带 service 与 channelCode=ERP，校验条线准入后签发 ST" />
          <el-step title="后端回调校验" description="erp-server 调用 /p3/serviceValidate 校验票据并建立服务端会话" />
          <el-step title="前端进入主页" description="跳转 /login?cas=ok，拉取会话与菜单后进入工作台" />
        </el-steps>
      </el-card>

      <el-card shadow="never">
        <template #header><div class="ds-h3">会话信息</div></template>
        <p class="ds-caption" style="margin: 0 0 var(--space-4)">来自后端 /erp/api/session 接口。</p>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户名">{{ store.user?.username }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{ store.user?.displayName ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ store.user?.deptId ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="条线">{{ store.user?.channelCode ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="菜单数量">{{ store.menus.length }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = useRouter()
const store = useUserStore()

const statCards = computed(() => [
  { label: '待处理订单', value: 12 },
  { label: '本月营收（万元）', value: 386.5 },
  { label: '库存预警', value: 3 },
  { label: '可见菜单', value: store.menus.length }
])
</script>

<style scoped>
.welcome {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-4);
}

.two-col {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
  gap: var(--space-4);
  align-items: start;
}

@media (max-width: 1199px) {
  .two-col {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
