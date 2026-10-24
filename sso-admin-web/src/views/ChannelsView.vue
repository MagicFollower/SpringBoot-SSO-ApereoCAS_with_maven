<template>
  <div class="ds-page ds-page--wide">
    <header class="page-head">
      <div>
        <h1 class="ds-h1">条线管理</h1>
        <p class="ds-caption">
          维护 <code>t_channel</code>；停用条线后该条线下所有授权立即失效。数据经 admin-server 落库。
        </p>
      </div>
      <div class="ds-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="open()">新增 / 更新条线</el-button>
      </div>
    </header>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="channels" row-key="channelCode" stripe>
        <el-table-column prop="channelCode" label="条线代码" width="140" />
        <el-table-column prop="channelName" label="名称" min-width="160" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="plain">
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
            <el-button link type="primary" @click="open(row)">编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="还没有条线，点击右上角新增" />
        </template>
      </el-table>
    </el-card>

    <AppDialog
      v-model="visible"
      :title="mode === 'edit' ? '更新条线' : '新增 / 更新条线'"
      width="520px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item label="条线代码" prop="channelCode">
          <el-input
            v-model="form.channelCode"
            placeholder="2-32 位大写字母、数字或下划线，字母开头"
            :disabled="mode === 'edit'"
          />
        </el-form-item>
        <el-form-item label="名称" prop="channelName">
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
import { Plus, Refresh } from '@element-plus/icons-vue'
import AppDialog from '../components/AppDialog.vue'
import { useDialogForm } from '../composables/use-crud'
import { toast } from '../composables/use-toast'
import { api } from '../api'

const channels = ref([])
const loading = ref(false)
const saving = ref(false)
const formRef = ref()

const { visible, mode, form, open, close } = useDialogForm({
  channelCode: '',
  channelName: '',
  status: 1,
  theme: '',
  mfaRequired: 0,
})

const rules = {
  channelCode: [
    { required: true, message: '请填写条线代码', trigger: 'blur' },
    {
      pattern: /^[A-Z][A-Z0-9_]{1,31}$/,
      message: '2-32 位大写字母、数字或下划线，字母开头',
      trigger: 'blur',
    },
  ],
  channelName: [{ required: true, message: '请填写名称', trigger: 'blur' }],
}

async function load() {
  loading.value = true
  try {
    channels.value = await api.channels()
  } catch (e) {
    toast.error(`加载条线失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await api.upsertChannel({ ...form, theme: form.theme || null })
    toast.info('条线已保存')
    close()
    await load()
  } catch (e) {
    toast.error(`保存失败：${e.message}`)
  } finally {
    saving.value = false
  }
}

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
