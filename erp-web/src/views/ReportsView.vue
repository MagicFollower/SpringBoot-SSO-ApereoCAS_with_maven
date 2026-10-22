<template>
  <div class="reports">
    <el-row :gutter="16">
      <el-col v-for="item in summary" :key="item.label" :span="6">
        <div class="page-card summary-card">
          <div class="summary-label">{{ item.label }}</div>
          <div class="summary-value">{{ item.value }}</div>
          <el-progress :percentage="item.percent" :color="item.color" :stroke-width="8" />
        </div>
      </el-col>
    </el-row>

    <div class="page-card">
      <h3 class="page-title">条线营收对比（示例数据）</h3>
      <p class="page-desc">柱形为前端渲染的示例统计，用于验证页面布局与图表占位。</p>
      <div class="bars">
        <div v-for="bar in bars" :key="bar.name" class="bar-item">
          <div class="bar-track">
            <div class="bar-fill" :style="{ height: bar.percent + '%', background: bar.color }" />
          </div>
          <div class="bar-name">{{ bar.name }}</div>
          <div class="bar-value">{{ bar.value }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
const summary = [
  { label: '本月订单额（万元）', value: '386.5', percent: 77, color: '#2f80ed' },
  { label: '回款率', value: '82%', percent: 82, color: '#27ae60' },
  { label: '毛利率', value: '31%', percent: 31, color: '#e6a23c' },
  { label: '客户满意度', value: '94%', percent: 94, color: '#8e44ad' }
]

const bars = [
  { name: '零售', value: '126.0 万', percent: 62, color: '#2f80ed' },
  { name: '信贷', value: '98.4 万', percent: 48, color: '#27ae60' },
  { name: 'ERP', value: '156.2 万', percent: 76, color: '#8e44ad' },
  { name: '其他', value: '5.9 万', percent: 8, color: '#e6a23c' }
]
</script>

<style scoped>
.reports .page-card {
  margin-bottom: 16px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.summary-label {
  color: #909399;
  font-size: 13px;
}

.summary-value {
  font-size: 24px;
  font-weight: 600;
}

.bars {
  display: flex;
  align-items: flex-end;
  gap: 48px;
  height: 240px;
  padding: 10px 20px 0;
}

.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  height: 100%;
  width: 72px;
}

.bar-track {
  display: flex;
  align-items: flex-end;
  width: 100%;
  flex: 1;
  background: #f2f3f5;
  border-radius: 6px;
  overflow: hidden;
}

.bar-fill {
  width: 100%;
  border-radius: 6px 6px 0 0;
  transition: height 0.4s ease;
}

.bar-name {
  font-size: 13px;
  color: #606266;
}

.bar-value {
  font-size: 12px;
  color: #909399;
}
</style>