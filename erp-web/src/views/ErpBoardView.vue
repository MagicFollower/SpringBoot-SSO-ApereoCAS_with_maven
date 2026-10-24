<template>
  <div class="ds-page ds-page--wide">
    <el-card shadow="never">
      <div class="board-head">
        <div>
          <h1 class="ds-h2">ERP 专属看板</h1>
          <p class="ds-caption" style="margin: var(--space-1) 0 0">
            该菜单仅下发给 <el-tag size="small" type="warning" effect="plain">channelCode = ERP</el-tag>
            的用户；其他条线登录时不会出现此入口，用于验证按条线的菜单隔离。
          </p>
        </div>
        <el-tag type="success" effect="plain">已通过 CAS 条线准入</el-tag>
      </div>
    </el-card>

    <div class="metric-grid">
      <el-card v-for="metric in metrics" :key="metric.label" shadow="never">
        <div class="metric">
          <el-statistic :value="metric.value" :title="metric.label" :precision="metric.precision" />
          <div class="ds-caption" :class="metric.up ? 'trend-up' : 'trend-down'">
            {{ metric.up ? '▲' : '▼' }} {{ metric.trend }}
          </div>
        </div>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header><div class="ds-h3">库存预警清单（示例）</div></template>
      <el-table :data="alerts" row-key="sku" stripe>
        <el-table-column prop="sku" label="物料编码" width="160" />
        <el-table-column prop="name" label="物料名称" min-width="180" />
        <el-table-column prop="stock" label="当前库存" width="110" align="right" />
        <el-table-column prop="safety" label="安全库存" width="110" align="right" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.stock < row.safety * 0.5 ? 'danger' : 'warning'" effect="plain">
              {{ row.stock < row.safety * 0.5 ? '严重不足' : '低于安全线' }}
            </el-tag>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无库存预警" />
        </template>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
const metrics = [
  { label: '在制工单', value: 28, precision: 0, trend: '较昨日 +4', up: true },
  { label: '按时交付率（%）', value: 96.2, precision: 1, trend: '较上周 +1.3%', up: true },
  { label: '物料周转天数', value: 17.5, precision: 1, trend: '较上周 -0.8', up: false }
]

const alerts = [
  { sku: 'MAT-100231', name: '冷轧钢板 1.2mm', stock: 120, safety: 500 },
  { sku: 'MAT-100466', name: '锂电池模组 48V', stock: 340, safety: 400 },
  { sku: 'MAT-100877', name: 'PLC 控制器 X20', stock: 45, safety: 120 }
]
</script>

<style scoped>
.board-head {
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

.metric {
  display: grid;
  gap: var(--space-1);
}

.trend-up {
  color: var(--el-color-success);
}

.trend-down {
  color: var(--el-color-warning);
}
</style>
