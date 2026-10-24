<template>
  <div class="ds-page ds-page--wide">
    <header class="page-head">
      <div>
        <h1 class="ds-h1">用户管理</h1>
        <p class="ds-caption">
          维护 <code>t_user</code> 账号；密码使用 BCrypt，请求与响应都不包含密码。
          彻底删除会级联移除该用户的条线授权。
        </p>
      </div>
      <div class="ds-actions">
        <el-button type="primary" :icon="Plus" @click="open()">新增用户</el-button>
      </div>
    </header>

    <el-card shadow="never">
      <div class="ds-data-grid">
        <div class="ds-row">
          <el-input
            v-model="keyword"
            placeholder="用户名 / 姓名"
            clearable
            style="width: 220px"
            @keyup.enter="query"
            @clear="query"
          />
          <el-select v-model="enabledFilter" style="width: 130px" @change="query">
            <el-option label="全部状态" value="" />
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" @click="query">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>

        <el-alert
          v-if="error"
          :title="error.message ?? '加载失败，请重试'"
          type="error"
          show-icon
          :closable="false"
        />

        <el-table v-loading="loading" :data="rows" row-key="username" stripe>
          <el-table-column prop="username" label="用户名" min-width="130" />
          <el-table-column prop="displayName" label="姓名" min-width="120" />
          <el-table-column prop="deptId" label="部门" min-width="100" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" effect="plain">
                {{ row.enabled === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" min-width="170" />
          <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
          <el-table-column label="操作" width="130" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="open(row)">编辑</el-button>
              <el-button link type="danger" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="没有符合条件的用户" />
          </template>
        </el-table>

        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          background
          class="ds-data-grid__pager"
        />
      </div>
    </el-card>

    <AppDialog v-model="visible" :title="mode === 'edit' ? '编辑用户' : '新增用户'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
        <el-form-item v-if="mode === 'create'" label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="2-64 位字母、数字、点、下划线或连字符"
            autocomplete="off"
          />
        </el-form-item>
        <el-form-item v-else label="用户名">
          <el-input v-model="form.username" disabled />
        </el-form-item>
        <el-form-item label="初始密码" prop="password" :rules="mode === 'create' ? rules.password : editPasswordRules">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="mode === 'create' ? '8-64 位' : '留空表示不修改'"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="再次输入密码"
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.displayName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="form.deptId" maxlength="64" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="form.enabled"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
          <div v-if="mode === 'edit'" class="ds-caption form-hint">
            停用会尝试注销该用户的 CAS 会话；密码重置不会自动注销会话。
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="ds-actions">
          <el-button @click="close">取消</el-button>
          <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
        </div>
      </template>
    </AppDialog>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import AppDialog from '../components/AppDialog.vue'
import { useDialogForm, usePagination } from '../composables/use-crud'
import { toast } from '../composables/use-toast'
import { api } from '../api'

const USERNAME_RE = /^[A-Za-z0-9._-]{2,64}$/

const rows = ref([])
const loading = ref(false)
const error = ref(null)
const keyword = ref('')
const enabledFilter = ref('')

const { currentPage, pageSize, total } = usePagination({ pageSize: 20 })

const saving = ref(false)
const formRef = ref()
const { visible, mode, form, open, close } = useDialogForm({
  username: '',
  password: '',
  confirmPassword: '',
  displayName: '',
  deptId: '',
  enabled: 1
})

const rules = {
  username: [
    { required: true, message: '请填写用户名', trigger: 'blur' },
    {
      pattern: USERNAME_RE,
      message: '2-64 位字母、数字、点、下划线或连字符',
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 64, message: '密码长度必须为 8-64 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) callback(new Error('两次输入的密码不一致'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
}

// 编辑态：留空表示不修改，非必填
const editPasswordRules = [
  { min: 8, max: 64, message: '密码长度必须为 8-64 位', trigger: 'blur' },
  {
    validator: (_rule, value, callback) => {
      if (value && value.length > 0 && value.length < 8) callback(new Error('密码长度必须为 8-64 位'))
      else callback()
    },
    trigger: 'blur',
  },
]

const editConfirmRules = [
  {
    validator: (_rule, value, callback) => {
      if (value !== form.password) callback(new Error('两次输入的密码不一致'))
      else callback()
    },
    trigger: 'blur',
  },
]

async function load() {
  loading.value = true
  error.value = null
  try {
    const params = { q: keyword.value, page: currentPage.value, size: pageSize.value }
    if (enabledFilter.value !== '') {
      params.enabled = enabledFilter.value
    }
    const data = await api.userAccounts(params)
    rows.value = data.items
    total.value = data.total
    if (rows.value.length === 0 && currentPage.value > 1) {
      currentPage.value -= 1
      await load()
    }
  } catch (e) {
    error.value = e
    toast.error(`加载用户失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

function query() {
  if (currentPage.value !== 1) currentPage.value = 1
  else load()
}

function resetFilter() {
  keyword.value = ''
  enabledFilter.value = ''
  query()
}

watch([currentPage, pageSize], () => load())

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (mode.value === 'create') {
      await api.createUserAccount({
        username: form.username.trim(),
        password: form.password,
        displayName: form.displayName.trim() || null,
        deptId: form.deptId.trim() || null,
        enabled: form.enabled
      })
      toast.info('用户已创建')
      currentPage.value = 1
    } else {
      const result = await api.updateUserAccount(form.username, {
        password: form.password || null,
        displayName: form.displayName.trim(),
        deptId: form.deptId.trim(),
        enabled: form.enabled
      })
      if (result.warnings?.length) {
        toast.warn(`账号已更新；${result.warnings.join('；')}`)
      } else {
        toast.info('账号已更新')
      }
    }
    close()
    await load()
  } catch (e) {
    toast.error(`${mode.value === 'create' ? '创建' : '更新'}失败：${e.message}`)
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `彻底删除账号 ${row.username}？将级联删除其所有条线授权，并尝试注销 CAS 会话；此操作不可恢复。`,
      '删除用户',
      { type: 'error', confirmButtonText: '彻底删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const result = await api.deleteUserAccount(row.username)
    if (result.warnings?.length) {
      toast.warn(`账号已删除；${result.warnings.join('；')}`)
    } else {
      toast.info('账号已删除')
    }
    await load()
  } catch (e) {
    toast.error(`删除失败：${e.message}`)
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

.form-hint {
  width: 100%;
  margin-top: var(--space-1);
}
</style>
