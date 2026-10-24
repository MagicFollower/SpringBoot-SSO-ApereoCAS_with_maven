<template>
  <div class="ds-page ds-page--wide">
    <header class="page-head">
      <div>
        <h1 class="ds-h1">接入系统</h1>
        <p class="ds-caption">
          服务定义写入 <code>etc/cas/services/*.json</code>，CAS watcher 自动加载；删除经控制面卸载运行时服务。
        </p>
      </div>
      <div class="ds-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="open()">新增服务</el-button>
      </div>
    </header>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="services" row-key="id" stripe>
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
        <template #empty>
          <el-empty description="还没有接入系统，点击右上角新增" />
        </template>
      </el-table>
    </el-card>

    <AppDialog v-model="visible" title="新增服务定义" width="620px" destroy-on-close>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="serviceId 必须以 ^ 开头、$ 结尾，形如 ^http://host:port/路径$；id 不可重复；条线必须已存在。"
        style="margin-bottom: 16px"
      />
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="服务 ID" prop="id">
          <el-input-number v-model="form.id" :min="1" :step="1" style="width: 180px" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="serviceId" prop="serviceId">
          <el-input v-model="form.serviceId" placeholder="^http://localhost:9003/.*$" />
        </el-form-item>
        <el-form-item label="条线" prop="channelCode">
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
        <el-form-item label="logoutUrl" prop="logoutUrl">
          <el-input v-model="form.logoutUrl" placeholder="http://localhost:9003/logout" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.evaluationOrder" :min="0" :step="1" style="width: 180px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="ds-actions">
          <el-button @click="close">取消</el-button>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
        </div>
      </template>
    </AppDialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import AppDialog from '../components/AppDialog.vue'
import { useDialogForm } from '../composables/use-crud'
import { toast } from '../composables/use-toast'
import { api } from '../api'

const services = ref([])
const channels = ref([])
const loading = ref(false)
const saving = ref(false)
const formRef = ref()

const { visible, form, open, close } = useDialogForm({
  id: null,
  name: '',
  serviceId: '',
  channelCode: '',
  description: '',
  logoutUrl: '',
  evaluationOrder: 10
})

const rules = {
  id: [{ required: true, message: '请填写服务 ID', trigger: 'blur' }],
  name: [{ required: true, message: '请填写名称', trigger: 'blur' }],
  serviceId: [
    { required: true, message: '请填写 serviceId 正则', trigger: 'blur' },
    {
      pattern: /^\^https?:\/\/.+\$$/,
      message: '必须以 ^ 开头、$ 结尾，形如 ^http://host:port/路径$',
      trigger: 'blur',
    },
  ],
  channelCode: [{ required: true, message: '请选择条线', trigger: 'change' }],
  logoutUrl: [
    { required: true, message: '请填写 SLO 注销地址', trigger: 'blur' },
    {
      pattern: /^https?:\/\/.+/,
      message: '必须是 http(s):// 开头的完整地址',
      trigger: 'blur',
    },
  ],
}

async function load() {
  loading.value = true
  try {
    services.value = await api.services()
  } catch (e) {
    toast.error(`加载服务失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

async function loadChannels() {
  try {
    channels.value = await api.channels()
  } catch (e) {
    toast.error(`加载条线失败：${e.message}`)
  }
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await api.createService({ ...form })
    toast.info('服务定义已写入，CAS 将自动加载')
    close()
    await load()
  } catch (e) {
    toast.error(`保存失败：${e.message}`)
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
    toast.info('服务已删除并从运行时卸载')
    await load()
  } catch (e) {
    toast.error(`删除失败：${e.message}`)
  }
}

onMounted(async () => {
  await Promise.all([load(), loadChannels()])
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
