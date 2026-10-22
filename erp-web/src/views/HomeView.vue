<template>
  <div class="home">
    <div class="page-card welcome">
      <div class="welcome-text">
        <h2>你好，{{ store.displayName }} 👋</h2>
        <p>
          当前通过 <strong>CAS 单点登录</strong> 进入 ERP 系统，所属条线
          <el-tag size="small" effect="plain">{{ store.user?.channelCode }}</el-tag>
          ，部门 {{ store.user?.deptId ?? '-' }}。
        </p>
      </div>
      <div class="welcome-actions">
        <el-button type="primary" @click="router.push('/system/users')">用户管理</el-button>
        <el-button @click="router.push('/erp/board')">ERP 专属看板</el-button>
      </div>
    </div>

    <el-row :gutter="16" class="stat-row">
      <el-col v-for="card in statCards" :key="card.label" :span="6">
        <div class="page-card stat-card">
          <div class="stat-icon" :style="{ background: card.color }">
            <el-icon :size="20"><component :is="card.icon" /></el-icon>
          </div>
          <div>
            <div class="stat-value">{{ card.value }}</div>
            <div class="stat-label">{{ card.label }}</div>
          </div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :span="14">
        <div class="page-card">
          <h3 class="page-title">认证链路</h3>
          <p class="page-desc">当前会话由以下环节串联完成，全部为真实调用。</p>
          <el-steps direction="vertical" :active="4" class="auth-steps">
            <el-step title="浏览器访问 ERP 前端" description="Vue3 应用检测到未登录，跳转后端 /erp/login/cas" />
            <el-step title="CAS 统一登录页" description="携带 service 与 channelCode=ERP，校验条线准入后签发 ST" />
            <el-step title="后端回调校验" description="erp-server 调用 /p3/serviceValidate 校验票据并建立服务端会话" />
            <el-step title="前端进入主页" description="跳转 /login?cas=ok，拉取会话与菜单后进入工作台" />
          </el-steps>
        </div>
      </el-col>
      <el-col :span="10">
        <div class="page-card">
          <h3 class="page-title">会话信息</h3>
          <p class="page-desc">来自后端 /erp/api/session 接口。</p>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="用户名">{{ store.user?.username }}</el-descriptions-item>
            <el-descriptions-item label="姓名">{{ store.user?.displayName ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ store.user?.deptId ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="条线">{{ store.user?.channelCode ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="菜单数量">{{ store.menus.length }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../stores/user'

const router = useRouter()
const store = useUserStore()

const statCards = computed(() => [
  { label: '待处理订单', value: 12, icon: 'List', color: '#2f80ed' },
  { label: '本月营收（万元）', value: 386.5, icon: 'TrendCharts', color: '#27ae60' },
  { label: '库存预警', value: 3, icon: 'Warning', color: '#e6a23c' },
  { label: '可见菜单', value: store.menus.length, icon: 'Menu', color: '#8e44ad' }
])
</script>

<style scoped>
.welcome {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(120deg, #ffffff 0%, #eef4ff 100%);
}

.welcome-text h2 {
  margin: 0 0 8px;
  font-size: 20px;
}

.welcome-text p {
  margin: 0;
  color: #606266;
  font-size: 14px;
}

.stat-row {
  margin: 16px 0;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  color: #fff;
  border-radius: 10px;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
}

.stat-label {
  color: #909399;
  font-size: 13px;
}

.auth-steps {
  height: 300px;
}
</style>