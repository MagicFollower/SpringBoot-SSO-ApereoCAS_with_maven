<template>
  <div class="board">
    <div class="page-card board-header">
      <div>
        <h3 class="page-title">ERP 专属看板</h3>
        <p class="page-desc">
          该菜单仅下发给 <el-tag size="small" type="warning">channelCode = ERP</el-tag> 的用户；
          其他条线登录时不会出现此入口，用于验证按条线的菜单隔离。
        </p>
      </div>
      <el-tag type="success" effect="dark">已通过 CAS 条线准入</el-tag>
    </div>

    <el-row :gutter="16">
      <el-col v-for="metric in metrics" :key="metric.label" :span="8">
        <div class="page-card metric-card">
          <el-icon :size="22" :color="metric.color"><component :is="metric.icon" /></el-icon>
          <div class="metric-body">
            <div class="metric-value">{{ metric.value }}</div>
            <div class="metric-label">{{ metric.label }}</div>
            <div class="metric-trend" :class="metric.up ? 'up' : 'down'">
              {{ metric.up ? '▲' : '▼' }} {{ metric.trend }}
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <div class="page-card">
      <h3 class="page-title">库存预警清单（示例）</h3>
      <el-table :data="alerts" border stripe>
        <el-table-column prop="sku" label="物料编码" width="150" />
        <el-table-column prop="name" label="物料名称" min-width="180" />
        <el-table-column prop="stock" label="当前库存" width="110" align="right" />
        <el-table-column prop="safety" label="安全库存" width="110" align="right" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.stock < row.safety * 0.5 ? 'danger' : 'warning'" size="small">
              {{ row.stock < row.safety * 0.5 ? '严重不足' : '低于安全线' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
const metrics = [
  { label: '在制工单', value: 28, trend: '较昨日 +4', up: true, icon: 'SetUp', color: '#2f80ed' },
  { label: '按时交付率', value: '96.2%', trend: '较上周 +1.3%', up: true, icon: 'CircleCheck', color: '#27ae60' },
  { label: '物料周转天数', value: 17.5, trend: '较上周 -0.8', up: false, icon: 'Timer', color: '#e6a23c' }
]

const alerts = [
  { sku: 'MAT-100231', name: '冷轧钢板 1.2mm', stock: 120, safety: 500 },
  { sku: 'MAT-100466', name: '锂电池模组 48V', stock: 340, safety: 400 },
  { sku: 'MAT-100877', name: 'PLC 控制器 X20', stock: 45, safety: 120 }
]
</script>

<style scoped>
.board .page-card {
  margin-bottom: 16px;
}

.board-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
}

.metric-card {
  display: flex;
  align-items: center;
  gap: 14px;
}

.metric-value {
  font-size: 22px;
  font-weight: 600;
}

.metric-label {
  color: #909399;
  font-size: 13px;
}

.metric-trend {
  font-size: 12px;
  margin-top: 2px;
}

.metric-trend.up {
  color: #27ae60;
}

.metric-trend.down {
  color: #e6a23c;
}
</style>