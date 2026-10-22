<template>
  <div class="page-card">
    <div class="page-header">
      <div>
        <h3 class="page-title">接入系统</h3>
        <p class="page-desc">
          服务定义写入 <code>etc/cas/services/*.json</code>，CAS watcher 自动加载；删除经控制面卸载运行时服务。
        </p>
      </div>
      <div>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新增服务</el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="services" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="名称" width="140" />
      <el-table-column prop="serviceId" label="serviceId 正则" min-width="260" show-overflow-tooltip />
      <el-table-column prop="channelCode" label="条线" width="100" />
      <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
      <el-table-column prop="logoutUrl" label="SLO 注销地址" min-width="220" show-overflow-tooltip />
      <el-table-column prop="fileName" label="文件" width="160" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" title="新增服务定义" width="620px">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="serviceId 必须以 ^ 开头、$ 结尾，形如 ^http://host:port/路径$；id 不可重复；条线必须已存在。"
        class="dialog-hint"
      />
      <el-form :model="form" label-width="110px">
        <el-form-item label="服务 ID" required>
          <el-input-number v-model="form.id" :min="1" :step="1" style="width: 180px" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="serviceId" required>
          <el-input v-model="form.serviceId" placeholder="^http://localhost:9003/.*$" />
        </el-form-item>
        <el-form-item label="条线" required>
          <el-select v-model="form.channelCode" placeholder="选择条线" style="width: 100%">
            <el-option
              v-for="c in channels"
              :key="c.channelCode"
              :label="`${c.channelCode} · ${c.channelName}`"
              :value="c.channelCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" maxlength="256" show-word-limit />
        </el-form-item>
        <el-form-item label="logoutUrl" required>
          <el-input v-model="form.logoutUrl" placeholder="http://localhost:9003/logout" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.evaluationOrder" :min="0" :step="1" style="width: 180px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { api } from '../api'

const services = ref([])
const channels = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const saving = ref(false)
const form = reactive({
  id: null,
  name: '',
  serviceId: '',
  channelCode: '',
  description: '',
  logoutUrl: '',
  evaluationOrder: 10
})

async function load() {
  loading.value = true
  try {
    services.value = await api.services()
  } catch (e) {
    ElMessage.error(`加载服务失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

async function loadChannels() {
  try {
    channels.value = await api.channels()
  } catch (e) {
    ElMessage.error(`加载条线失败：${e.message}`)
  }
}

function openCreate() {
  Object.assign(form, {
    id: null,
    name: '',
    serviceId: '',
    channelCode: '',
    description: '',
    logoutUrl: '',
    evaluationOrder: 10
  })
  dialogVisible.value = true
}

async function save() {
  if (!form.id || !form.name.trim() || !form.serviceId.trim() || !form.channelCode || !form.logoutUrl.trim()) {
    ElMessage.warning('请完整填写服务 ID、名称、serviceId、条线与 logoutUrl')
    return
  }
  saving.value = true
  try {
    await api.createService({ ...form })
    ElMessage.success('服务定义已写入，CAS 将自动加载')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(`保存失败：${e.message}`)
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除服务 ${row.name}（id=${row.id}）？将同时从 CAS 运行时注册表卸载，已接入系统立即失效。`,
      '删除服务',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await api.deleteService(row.id)
    ElMessage.success('服务已删除并从运行时卸载')
    await load()
  } catch (e) {
    ElMessage.error(`删除失败：${e.message}`)
  }
}

onMounted(async () => {
  await Promise.all([load(), loadChannels()])
})
</script>

<style scoped>
.dialog-hint {
  margin-bottom: 16px;
}
</style>