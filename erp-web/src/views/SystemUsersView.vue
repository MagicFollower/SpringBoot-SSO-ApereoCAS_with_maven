<template>
  <div class="ds-page ds-page--wide">
    <header class="page-head">
      <div>
        <h1 class="ds-h1">用户管理</h1>
        <p class="ds-caption">数据来自后端 <code>GET /erp/api/users</code>（tk-mapper 查询 MySQL t_user 表）。</p>
      </div>
    </header>

    <el-card shadow="never">
      <div class="ds-data-grid">
        <div class="ds-row">
          <el-input
            v-model="keyword"
            placeholder="用户名 / 姓名"
            clearable
            style="width: 240px"
            @keyup.enter="query"
            @clear="query"
          />
          <el-button type="primary" @click="query">查询</el-button>
          <el-button
            @click="
              keyword = '';
              resetPage();
              load()
            "
          >
            重置
          </el-button>
        </div>

        <el-table v-loading="loading" :data="rows" row-key="id" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="username" label="用户名" width="140" />
          <el-table-column prop="displayName" label="姓名" width="140" />
          <el-table-column prop="deptId" label="部门" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" effect="plain">
                {{ row.enabled === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" min-width="180" />
          <template #empty>
            <el-empty description="没有符合条件的用户" />
          </template>
        </el-table>

        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[5, 10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          class="ds-data-grid__pager"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { queryUsers } from '../api'
import { usePagination } from '../composables/use-crud'
import { toast } from '../composables/use-toast'

const keyword = ref('')
const rows = ref([])
const loading = ref(false)
const { currentPage, pageSize, total, resetPage } = usePagination({ pageSize: 10 })

async function load() {
  loading.value = true
  try {
    const res = await queryUsers({
      q: keyword.value,
      page: currentPage.value,
      size: pageSize.value
    })
    rows.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch (error) {
    toast.error(`用户查询失败：${error.message}`)
  } finally {
    loading.value = false
  }
}

function query() {
  if (currentPage.value !== 1) resetPage()
  else load()
}

watch([currentPage, pageSize], () => load())

onMounted(load)
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}
</style>
