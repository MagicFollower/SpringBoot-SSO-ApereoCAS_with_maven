<template>
  <div class="ds-page ds-page--wide">
    <header class="page-head">
      <div>
        <h1 class="ds-h1">订单示例</h1>
        <p class="ds-caption">演示型业务页面（前端示例数据），用于验证菜单下发、路由跳转与页面渲染。</p>
      </div>
    </header>

    <el-card shadow="never">
      <div class="ds-data-grid">
        <div class="ds-row">
          <el-input
            v-model="keyword"
            placeholder="订单号 / 客户"
            clearable
            style="width: 240px"
          />
          <el-select v-model="status" clearable placeholder="全部状态" style="width: 140px">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </div>

        <el-table :data="filteredOrders" row-key="orderNo" stripe>
          <el-table-column prop="orderNo" label="订单号" width="160" />
          <el-table-column prop="customer" label="客户" min-width="160" />
          <el-table-column prop="amount" label="金额（元）" width="140" align="right">
            <template #default="{ row }">{{ row.amount.toLocaleString('zh-CN') }}</template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" effect="plain">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="下单时间" min-width="170" />
          <template #empty>
            <el-empty description="没有符合条件的订单" />
          </template>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'

const keyword = ref('')
const status = ref('')

const statusOptions = [
  { value: 'pending', label: '待处理' },
  { value: 'processing', label: '处理中' },
  { value: 'done', label: '已完成' }
]

const orders = ref([
  { orderNo: 'SO-20261022-001', customer: '华东制造集团', amount: 128600, status: 'processing', createdAt: '2026-10-22 09:24' },
  { orderNo: 'SO-20261022-002', customer: '南方贸易有限公司', amount: 45200, status: 'pending', createdAt: '2026-10-22 10:05' },
  { orderNo: 'SO-20261021-018', customer: '西部能源科技', amount: 302000, status: 'done', createdAt: '2026-10-21 16:40' },
  { orderNo: 'SO-20261021-011', customer: '北方物流股份', amount: 78600, status: 'done', createdAt: '2026-10-21 14:12' },
  { orderNo: 'SO-20261020-007', customer: '东海电子元件', amount: 15900, status: 'processing', createdAt: '2026-10-20 11:36' }
])

const filteredOrders = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  return orders.value.filter((order) => {
    const matchKeyword =
      !kw ||
      order.orderNo.toLowerCase().includes(kw) ||
      order.customer.toLowerCase().includes(kw)
    const matchStatus = !status.value || order.status === status.value
    return matchKeyword && matchStatus
  })
})

function statusLabel(value) {
  return statusOptions.find((item) => item.value === value)?.label ?? value
}

function statusTagType(value) {
  return { pending: 'warning', processing: 'primary', done: 'success' }[value] ?? 'info'
}
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}
</style>
