<template>
  <div class="ds-page ds-page--wide">
    <header class="page-head">
      <div>
        <h1 class="ds-h1">报表中心</h1>
        <p class="ds-caption">前端示例统计，用于验证页面布局与图表占位。</p>
      </div>
    </header>

    <div class="metric-grid">
      <el-card v-for="item in summary" :key="item.label" shadow="never">
        <el-statistic :value="item.display" :title="item.label" />
        <el-progress
          :percentage="item.percent"
          :stroke-width="8"
          :color="item.color"
          style="margin-top: var(--space-2)"
        />
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header><div class="ds-h3">条线营收对比（示例数据）</div></template>
      <div class="bars">
        <div v-for="bar in bars" :key="bar.name" class="bar-item">
          <div class="bar-track">
            <div class="bar-fill" :style="{ height: bar.percent + '%', background: bar.color }" />
          </div>
          <div class="ds-body bar-name">{{ bar.name }}</div>
          <div class="ds-caption">{{ bar.value }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
const summary = [
  { label: '本月订单额（万元）', display: 386.5, percent: 77, color: '#2F80ED' },
  { label: '回款率（%）', display: 82, percent: 82, color: '#27AE60' },
  { label: '毛利率（%）', display: 31, percent: 31, color: '#E6A23C' },
  { label: '客户满意度（%）', display: 94, percent: 94, color: '#8E44AD' }
]

const bars = [
  { name: '零售', value: '126.0 万', percent: 62, color: '#2F80ED' },
  { name: '信贷', value: '98.4 万', percent: 48, color: '#27AE60' },
  { name: 'ERP', value: '156.2 万', percent: 76, color: '#8E44AD' },
  { name: '其他', value: '5.9 万', percent: 8, color: '#E6A23C' }
]
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: var(--space-4);
}

.bars {
  display: flex;
  align-items: flex-end;
  gap: var(--space-12);
  height: 240px;
  padding: var(--space-2) var(--space-4) 0;
}

.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-1);
  height: 100%;
  width: 72px;
}

.bar-track {
  display: flex;
  align-items: flex-end;
  width: 100%;
  flex: 1;
  background: var(--el-fill-color);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.bar-fill {
  width: 100%;
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
  transition: height var(--motion-duration-slow) var(--motion-ease-standard);
}

.bar-name {
  color: var(--el-text-color-regular);
}
</style>
