<template>
  <div class="page-card">
    <h3 class="page-title">用户管理</h3>
    <p class="page-desc">数据来自后端 <code>GET /erp/api/users</code>（tk-mapper 查询 MySQL t_user 表）。</p>

    <el-form inline @submit.prevent="handleSearch">
      <el-form-item label="关键字">
        <el-input
          v-model="keyword"
          placeholder="用户名 / 姓名"
          clearable
          style="width: 240px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="displayName" label="姓名" width="140" />
      <el-table-column prop="deptId" label="部门" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" size="small">
            {{ row.enabled === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="180" />
    </el-table>

    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[5, 10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        @current-change="load"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { queryUsers } from '../api'

const keyword = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const rows = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await queryUsers({ q: keyword.value, page: page.value, size: size.value })
    rows.value = res.data?.records ?? []
    total.value = res.data?.total ?? 0
  } catch (error) {
    ElMessage.error(`用户查询失败：${error.message}`)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  load()
}

function handleReset() {
  keyword.value = ''
  page.value = 1
  size.value = 10
  load()
}

function handleSizeChange() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<style scoped>
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

code {
  padding: 1px 6px;
  background: #f2f3f5;
  border-radius: 4px;
  font-size: 12px;
}
</style>