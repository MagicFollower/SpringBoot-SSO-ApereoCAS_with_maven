<template>
  <div class="ds-page ds-page--wide">
    <header class="page-head">
      <div>
        <h1 class="ds-h1">菜单管理</h1>
        <p class="ds-caption">
          数据来自后端 <code>GET /erp/api/menus</code>，按当前用户条线
          <el-tag size="small" effect="plain">{{ store.user?.channelCode }}</el-tag>
          过滤（MySQL erp_menu 表）。
        </p>
      </div>
      <div class="ds-actions">
        <el-button :icon="Refresh" :loading="loading" @click="reload">刷新</el-button>
      </div>
    </header>

    <el-card shadow="never">
      <div class="ds-data-grid">
        <div class="ds-row">
          <el-input
            v-model="filterText"
            placeholder="菜单名称 / 编码"
            clearable
            style="width: 240px"
          />
          <el-switch v-model="onlyChannelSpecific" active-text="仅看当前条线专属" />
        </div>

        <el-table
          v-loading="loading"
          :data="visibleTree"
          row-key="id"
          :tree-props="{ children: 'children' }"
          default-expand-all
        >
          <el-table-column prop="menuName" label="菜单名称" min-width="200" />
          <el-table-column prop="menuCode" label="编码" width="150" />
          <el-table-column prop="path" label="路由" width="170">
            <template #default="{ row }">
              <span v-if="row.path">{{ row.path }}</span>
              <el-tag v-else size="small" type="info" effect="plain">目录</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="图标" width="90" align="center">
            <template #default="{ row }">
              <el-icon v-if="row.icon"><component :is="row.icon" /></el-icon>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="80" align="center" />
          <el-table-column label="条线归属" width="130">
            <template #default="{ row }">
              <el-tag v-if="row.channelCode" size="small" type="warning" effect="plain">
                {{ row.channelCode }} 专属
              </el-tag>
              <el-tag v-else size="small" type="info" effect="plain">公共</el-tag>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="没有符合条件的菜单" />
          </template>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { useUserStore } from '../stores/user'
import { toast } from '../composables/use-toast'

const store = useUserStore()
const loading = ref(false)
const filterText = ref('')
const onlyChannelSpecific = ref(false)

const visibleTree = computed(() => {
  const keyword = filterText.value.trim().toLowerCase()
  const onlySpecific = onlyChannelSpecific.value

  const prune = (nodes) =>
    nodes
      .map((node) => {
        const children = prune(node.children ?? [])
        const selfMatch =
          (!keyword ||
            node.menuName.toLowerCase().includes(keyword) ||
            node.menuCode.toLowerCase().includes(keyword)) &&
          (!onlySpecific || Boolean(node.channelCode))
        return selfMatch || children.length > 0 ? { ...node, children } : null
      })
      .filter(Boolean)

  return prune(store.menus)
})

async function reload() {
  loading.value = true
  try {
    await store.loadMenus()
  } catch (error) {
    toast.error(`菜单查询失败：${error.message}`)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (store.menus.length === 0) {
    reload()
  }
})
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}
</style>
