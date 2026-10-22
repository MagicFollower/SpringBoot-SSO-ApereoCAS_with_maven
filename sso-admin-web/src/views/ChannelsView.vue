<template>
  <div>
    <div class="page-card">
      <div class="page-header">
        <div>
          <h3 class="page-title">条线管理</h3>
          <p class="page-desc">
            维护 <code>t_channel</code>；停用条线后该条线下所有授权立即失效。数据经 admin-server 落库。
          </p>
        </div>
        <div>
          <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
          <el-button type="primary" :icon="Plus" @click="openCreate">新增 / 更新条线</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="channels" border stripe>
        <el-table-column prop="channelCode" label="条线代码" width="140" />
        <el-table-column prop="channelName" label="名称" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="theme" label="主题" width="120" />
        <el-table-column label="MFA" width="90" align="center">
          <template #default="{ row }">{{ row.mfaRequired === 1 ? '必须' : '否' }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="editing ? '更新条线' : '新增 / 更新条线'" width="480px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="条线代码" required>
          <el-input
            v-model="form.channelCode"
            placeholder="2-32 位大写字母、数字或下划线，字母开头"
            :disabled="editing"
          />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.channelName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
        <el-form-item label="主题">
          <el-input v-model="form.theme" placeholder="可选" />
        </el-form-item>
        <el-form-item label="MFA">
          <el-switch
            v-model="form.mfaRequired"
            :active-value="1"
            :inactive-value="0"
            active-text="必须"
            inactive-text="否"
          />
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
import { ElMessage } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { api } from '../api'

const channels = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref(false)
const saving = ref(false)
const form = reactive({ channelCode: '', channelName: '', status: 1, theme: '', mfaRequired: 0 })

async function load() {
  loading.value = true
  try {
    channels.value = await api.channels()
  } catch (e) {
    ElMessage.error(`加载条线失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  Object.assign(form, { channelCode: '', channelName: '', status: 1, theme: '', mfaRequired: 0 })
  dialogVisible.value = true
}

function openEdit(row) {
  editing.value = true
  Object.assign(form, {
    channelCode: row.channelCode,
    channelName: row.channelName,
    status: row.status ?? 1,
    theme: row.theme ?? '',
    mfaRequired: row.mfaRequired ?? 0
  })
  dialogVisible.value = true
}

async function save() {
  if (!form.channelCode.trim() || !form.channelName.trim()) {
    ElMessage.warning('条线代码与名称不能为空')
    return
  }
  saving.value = true
  try {
    await api.upsertChannel({ ...form, theme: form.theme || null })
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(`保存失败：${e.message}`)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>