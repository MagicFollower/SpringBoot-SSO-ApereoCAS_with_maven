<template>
  <div class="ds-page ds-page--wide">
    <header class="page-head">
      <div>
        <h1 class="ds-h1">条线用户</h1>
        <p class="ds-caption">选择条线后维护其用户授权；撤销为软删除，可恢复。</p>
      </div>
    </header>

    <div class="two-col">
      <el-card shadow="never" class="col-channels">
        <template #header>
          <div class="card-head">
            <div class="ds-h3">条线</div>
            <div class="ds-actions">
              <el-button :icon="Refresh" :loading="channelsLoading" @click="loadChannels">刷新</el-button>
              <el-button type="primary" :icon="Plus" @click="openChannel()">新增</el-button>
            </div>
          </div>
        </template>

        <el-table
          v-loading="channelsLoading"
          :data="channels"
          row-key="channelCode"
          :current-row-key="selectedCode"
          highlight-current-row
          @current-change="selectChannel"
        >
          <el-table-column prop="channelCode" label="代码" width="110" />
          <el-table-column prop="channelName" label="名称" min-width="110" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="plain">
                {{ row.status === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openChannel(row)">编辑</el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="还没有条线" :image-size="60" />
          </template>
        </el-table>
      </el-card>

      <div v-if="!selectedChannel" class="ds-card empty-panel">
        <el-empty description="请选择左侧条线，维护该条线下的用户授权" />
      </div>

      <el-card v-else shadow="never" class="col-users">
        <template #header>
          <div class="card-head">
            <div>
              <div class="ds-h3">
                {{ selectedChannel.channelName }}（{{ selectedChannel.channelCode }}）
              </div>
              <p class="ds-caption" style="margin: 4px 0 0">
                账号状态来自 <code>t_user</code>，授权状态来自 <code>t_user_channel</code>
              </p>
            </div>
            <div class="ds-actions">
              <el-button :icon="Refresh" :loading="usersLoading" @click="loadUsers">刷新</el-button>
              <el-button type="primary" :icon="Plus" @click="openAddUser">添加用户</el-button>
            </div>
          </div>
        </template>

        <div class="ds-data-grid">
          <div class="ds-row">
            <el-input
              v-model="keyword"
              placeholder="用户名 / 姓名"
              clearable
              style="width: 200px"
              @keyup.enter="queryUsers"
              @clear="queryUsers"
            />
            <el-select v-model="statusFilter" style="width: 130px" @change="queryUsers">
              <el-option label="全部状态" value="" />
              <el-option label="有效" :value="1" />
              <el-option label="已撤销" :value="0" />
            </el-select>
            <el-button type="primary" :loading="usersLoading" @click="queryUsers">查询</el-button>
          </div>

          <el-table
            v-loading="usersLoading"
            :data="channelUsers"
            row-key="username"
            stripe
            empty-text="该条线下没有符合条件的用户"
          >
            <el-table-column prop="username" label="用户名" width="110" />
            <el-table-column prop="displayName" label="姓名" width="110" />
            <el-table-column prop="deptId" label="部门" width="90" />
            <el-table-column label="账号" width="80">
              <template #default="{ row }">
                <el-tag :type="row.userEnabled === 1 ? 'success' : 'danger'" effect="plain">
                  {{ row.userEnabled === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="授权" width="90">
              <template #default="{ row }">
                <el-tag :type="row.grantStatus === 1 ? 'success' : 'info'" effect="plain">
                  {{ row.grantStatus === 1 ? '有效' : '已撤销' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="source" label="来源" width="90" />
            <el-table-column label="生效时间" min-width="150">
              <template #default="{ row }">{{ row.validFrom || '-' }}</template>
            </el-table-column>
            <el-table-column label="失效时间" min-width="150">
              <template #default="{ row }">{{ row.validTo || '-' }}</template>
            </el-table-column>
            <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
            <el-table-column label="操作" width="130" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditUser(row)">编辑</el-button>
                <el-button v-if="row.grantStatus === 1" link type="danger" @click="revokeUser(row)">
                  撤销
                </el-button>
                <el-button v-else link type="success" @click="restoreUser(row)">恢复</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>
    </div>

    <AppDialog v-model="addVisible" title="添加用户到条线" width="520px" destroy-on-close>
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="96px">
        <el-form-item label="用户" prop="username">
          <el-select
            v-model="addForm.username"
            filterable
            remote
            clearable
            :remote-method="searchCandidates"
            :loading="candidateLoading"
            placeholder="输入用户名或姓名搜索"
            style="width: 100%"
          >
            <el-option
              v-for="u in candidates"
              :key="u.username"
              :label="`${u.username} · ${u.displayName || '-'}`"
              :value="u.username"
            />
          </el-select>
          <div class="ds-caption form-hint">只支持已存在于 t_user 的账号；保存后授权立即生效。</div>
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker
            v-model="addForm.validFrom"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空表示立即生效"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="失效时间">
          <el-date-picker
            v-model="addForm.validTo"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空表示永久有效"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="ds-actions">
          <el-button @click="addVisible = false">取消</el-button>
          <el-button type="primary" :loading="adding" @click="submitAdd">保存</el-button>
        </div>
      </template>
    </AppDialog>

    <AppDialog v-model="editVisible" title="编辑用户授权" width="520px" destroy-on-close>
      <el-form :model="editForm" label-width="96px">
        <el-form-item label="用户">
          <el-input :model-value="editUserLabel" disabled />
        </el-form-item>
        <el-form-item label="授权状态">
          <el-switch
            v-model="editForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="有效"
            inactive-text="已撤销"
          />
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker
            v-model="editForm.validFrom"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空表示立即生效"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="失效时间">
          <el-date-picker
            v-model="editForm.validTo"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空表示永久有效"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="ds-actions">
          <el-button @click="editVisible = false">取消</el-button>
          <el-button type="primary" :loading="editSaving" @click="submitEdit">保存</el-button>
        </div>
      </template>
    </AppDialog>

    <AppDialog
      v-model="channelVisible"
      :title="channelMode === 'edit' ? '更新条线' : '新增条线'"
      width="520px"
      destroy-on-close
    >
      <el-form
        ref="channelFormRef"
        :model="channelForm"
        :rules="channelRules"
        label-width="96px"
      >
        <el-form-item label="条线代码" prop="channelCode">
          <el-input
            v-model="channelForm.channelCode"
            placeholder="2-32 位大写字母、数字或下划线，字母开头"
            :disabled="channelMode === 'edit'"
          />
        </el-form-item>
        <el-form-item label="名称" prop="channelName">
          <el-input v-model="channelForm.channelName" maxlength="128" show-word-limit />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="channelForm.status"
            :active-value="1"
            :inactive-value="0"
            active-text="启用"
            inactive-text="停用"
          />
        </el-form-item>
        <el-form-item label="主题">
          <el-input v-model="channelForm.theme" placeholder="可选" />
        </el-form-item>
        <el-form-item label="MFA">
          <el-switch
            v-model="channelForm.mfaRequired"
            :active-value="1"
            :inactive-value="0"
            active-text="必须"
            inactive-text="否"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="ds-actions">
          <el-button @click="closeChannel">取消</el-button>
          <el-button type="primary" :loading="channelSaving" @click="saveChannel">保存</el-button>
        </div>
      </template>
    </AppDialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import AppDialog from '../components/AppDialog.vue'
import { useDialogForm } from '../composables/use-crud'
import { toast } from '../composables/use-toast'
import { api } from '../api'

const channels = ref([])
const channelsLoading = ref(false)
const selectedCode = ref('')
const selectedChannel = computed(
  () => channels.value.find((c) => c.channelCode === selectedCode.value) || null
)

const channelUsers = ref([])
const usersLoading = ref(false)
const keyword = ref('')
const statusFilter = ref('')

const addVisible = ref(false)
const adding = ref(false)
const addFormRef = ref()
const addForm = reactive({ username: '', validFrom: '', validTo: '' })
const candidates = ref([])
const candidateLoading = ref(false)

const editVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive({
  username: '',
  displayName: '',
  validFrom: '',
  validTo: '',
  status: 1
})

const editUserLabel = computed(() =>
  editForm.displayName ? `${editForm.username}（${editForm.displayName}）` : editForm.username
)

const channelSaving = ref(false)
const channelFormRef = ref()
const {
  visible: channelVisible,
  mode: channelMode,
  form: channelForm,
  open: openChannelDialog,
  close: closeChannel,
} = useDialogForm({
  channelCode: '',
  channelName: '',
  status: 1,
  theme: '',
  mfaRequired: 0
})

const addRules = {
  username: [{ required: true, message: '请选择用户', trigger: 'change' }],
}

const channelRules = {
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

async function loadChannels() {
  channelsLoading.value = true
  try {
    channels.value = await api.channels()
    if (channels.value.length === 0) {
      selectedCode.value = ''
      channelUsers.value = []
      return
    }
    if (!channels.value.some((c) => c.channelCode === selectedCode.value)) {
      selectedCode.value = channels.value[0].channelCode
    }
    await loadUsers()
  } catch (e) {
    toast.error(`条线加载失败：${e.message}`)
  } finally {
    channelsLoading.value = false
  }
}

function selectChannel(row) {
  if (!row || row.channelCode === selectedCode.value) {
    return
  }
  selectedCode.value = row.channelCode
  keyword.value = ''
  statusFilter.value = ''
  loadUsers()
}

function queryUsers() {
  loadUsers()
}

async function loadUsers() {
  if (!selectedCode.value) {
    return
  }
  usersLoading.value = true
  try {
    const params = { q: keyword.value }
    if (statusFilter.value !== '') {
      params.status = statusFilter.value
    }
    channelUsers.value = await api.channelUsers(selectedCode.value, params)
  } catch (e) {
    toast.error(`用户加载失败：${e.message}`)
  } finally {
    usersLoading.value = false
  }
}

function openAddUser() {
  if (!selectedChannel.value) {
    return
  }
  Object.assign(addForm, { username: '', validFrom: '', validTo: '' })
  candidates.value = []
  addVisible.value = true
  searchCandidates('')
}

async function searchCandidates(query) {
  candidateLoading.value = true
  try {
    candidates.value = await api.users(query || '')
  } catch (e) {
    toast.error(`用户搜索失败：${e.message}`)
  } finally {
    candidateLoading.value = false
  }
}

async function submitAdd() {
  const valid = await addFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (addForm.validFrom && addForm.validTo && addForm.validFrom > addForm.validTo) {
    toast.warn('生效时间不能晚于失效时间')
    return
  }
  adding.value = true
  try {
    await api.addChannelUser(selectedCode.value, {
      username: addForm.username,
      validFrom: addForm.validFrom || null,
      validTo: addForm.validTo || null
    })
    toast.info('用户已加入条线')
    addVisible.value = false
    await loadUsers()
  } catch (e) {
    toast.error(`添加失败：${e.message}`)
  } finally {
    adding.value = false
  }
}

function openEditUser(row) {
  Object.assign(editForm, {
    username: row.username,
    displayName: row.displayName || '',
    validFrom: row.validFrom || '',
    validTo: row.validTo || '',
    status: row.grantStatus ?? 1
  })
  editVisible.value = true
}

async function submitEdit() {
  if (editForm.validFrom && editForm.validTo && editForm.validFrom > editForm.validTo) {
    toast.warn('生效时间不能晚于失效时间')
    return
  }
  editSaving.value = true
  try {
    await api.updateChannelUser(selectedCode.value, editForm.username, {
      validFrom: editForm.validFrom || null,
      validTo: editForm.validTo || null,
      status: editForm.status
    })
    toast.info('授权已更新')
    editVisible.value = false
    await loadUsers()
  } catch (e) {
    toast.error(`更新失败：${e.message}`)
  } finally {
    editSaving.value = false
  }
}

async function revokeUser(row) {
  try {
    await ElMessageBox.confirm(
      `确认撤销 ${row.username} 在 ${selectedCode.value} 的授权？撤销后该用户无法再进入此条线。`,
      '撤销授权',
      { type: 'warning', confirmButtonText: '撤销', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await api.removeChannelUser(selectedCode.value, row.username)
    toast.info('授权已撤销')
    await loadUsers()
  } catch (e) {
    toast.error(`撤销失败：${e.message}`)
  }
}

async function restoreUser(row) {
  try {
    await api.updateChannelUser(selectedCode.value, row.username, {
      validFrom: row.validFrom || null,
      validTo: row.validTo || null,
      status: 1
    })
    toast.info('授权已恢复')
    await loadUsers()
  } catch (e) {
    toast.error(`恢复失败：${e.message}`)
  }
}

function openChannel(row = null) {
  openChannelDialog(row)
}

async function saveChannel() {
  const valid = await channelFormRef.value.validate().catch(() => false)
  if (!valid) return
  channelSaving.value = true
  try {
    await api.upsertChannel({ ...channelForm, theme: channelForm.theme || null })
    toast.info('条线已保存')
    closeChannel()
    selectedCode.value = channelForm.channelCode.trim()
    await loadChannels()
  } catch (e) {
    toast.error(`保存失败：${e.message}`)
  } finally {
    channelSaving.value = false
  }
}

onMounted(loadChannels)
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.two-col {
  display: grid;
  grid-template-columns: minmax(280px, 2fr) minmax(0, 5fr);
  gap: var(--space-4);
  align-items: start;
}

@media (max-width: 1199px) {
  .two-col {
    grid-template-columns: minmax(0, 1fr);
  }
}

.card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-4);
}

.empty-panel {
  display: grid;
  place-items: center;
  min-height: 320px;
}

.form-hint {
  width: 100%;
  margin-top: var(--space-1);
}
</style>
