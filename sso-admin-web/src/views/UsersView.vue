<template>
  <div class="ds-page ds-page--wide">
    <header class="page-head">
      <div>
        <h1 class="ds-h1">用户授权</h1>
        <p class="ds-caption">按用户查询条线授权与在线 CAS 会话（最多返回 200 条用户）。</p>
      </div>
    </header>

    <div class="two-col">
      <el-card shadow="never" class="col-users">
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
            <el-button type="primary" :loading="usersLoading" @click="queryUsers">查询</el-button>
          </div>

          <el-table
            v-loading="usersLoading"
            :data="users"
            row-key="username"
            highlight-current-row
            @current-change="selectUser"
          >
            <el-table-column prop="username" label="用户名" width="110" />
            <el-table-column prop="displayName" label="姓名" width="110" />
            <el-table-column prop="deptId" label="部门" width="90" />
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" effect="plain">
                  {{ row.enabled === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>

      <div v-if="!selected" class="ds-card empty-panel">
        <el-empty description="请选择左侧用户，查看授权与在线会话" />
      </div>

      <div v-else class="ds-stack">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <div>
                <div class="ds-h3">
                  {{ selected.displayName || selected.username }}（{{ selected.username }}）
                </div>
                <p class="ds-caption" style="margin: 4px 0 0">
                  部门 {{ selected.deptId ?? '-' }} · 账号{{
                    selected.enabled === 1 ? '启用' : '停用'
                  }}
                </p>
              </div>
              <div class="ds-actions">
                <el-button type="primary" :icon="Plus" @click="openGrant">授予 / 重新启用</el-button>
              </div>
            </div>
          </template>

          <el-table v-loading="grantsLoading" :data="grants" row-key="channelCode" stripe>
            <el-table-column prop="channelCode" label="条线" width="100" />
            <el-table-column prop="channelName" label="名称" min-width="120" />
            <el-table-column label="授权状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="plain">
                  {{ row.status === 1 ? '有效' : '已撤销' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="条线状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.channelStatus === 1 ? 'success' : 'danger'" effect="plain">
                  {{ row.channelStatus === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="validFrom" label="生效时间" min-width="150">
              <template #default="{ row }">{{ row.validFrom || '-' }}</template>
            </el-table-column>
            <el-table-column prop="validTo" label="失效时间" min-width="150">
              <template #default="{ row }">{{ row.validTo || '-' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 1" link type="danger" @click="revoke(row)">
                  撤销
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <div>
                <div class="ds-h3">在线会话</div>
                <p class="ds-caption" style="margin: 4px 0 0">
                  来自 CAS 控制面（仅本机可访问）；踢下线会触发 SLO，注销业务系统本地会话。
                </p>
              </div>
              <div class="ds-actions">
                <el-button :icon="Refresh" :loading="sessionsLoading" @click="loadSessions">刷新</el-button>
                <el-button type="danger" plain :disabled="sessions.length === 0" @click="kickout">
                  强制踢下线
                </el-button>
              </div>
            </div>
          </template>

          <el-table v-loading="sessionsLoading" :data="sessions" row-key="ticketId">
            <el-table-column prop="ticketId" label="TGT" min-width="240" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="创建时间" min-width="200" />
            <el-table-column label="已接入服务" min-width="220">
              <template #default="{ row }">
                <div v-for="service in row.services" :key="service" class="service-item ds-caption">
                  {{ service }}
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="无在线会话" :image-size="60" />
            </template>
          </el-table>
        </el-card>
      </div>
    </div>

    <AppDialog v-model="grantVisible" title="授予 / 重新启用授权" width="520px" destroy-on-close>
      <el-form ref="grantFormRef" :model="grantForm" :rules="grantRules" label-width="96px">
        <el-form-item label="条线" prop="channelCode">
          <el-select v-model="grantForm.channelCode" placeholder="选择条线" style="width: 100%">
            <el-option
              v-for="c in channels"
              :key="c.channelCode"
              :label="`${c.channelCode} · ${c.channelName}`"
              :value="c.channelCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="生效时间">
          <el-date-picker
            v-model="grantForm.validFrom"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空表示立即生效"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="失效时间">
          <el-date-picker
            v-model="grantForm.validTo"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="留空表示永久有效"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="ds-actions">
          <el-button @click="grantVisible = false">取消</el-button>
          <el-button type="primary" :loading="granting" @click="submitGrant">保存</el-button>
        </div>
      </template>
    </AppDialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import AppDialog from '../components/AppDialog.vue'
import { toast } from '../composables/use-toast'
import { api } from '../api'

const keyword = ref('')
const users = ref([])
const usersLoading = ref(false)
const selected = ref(null)
const grants = ref([])
const grantsLoading = ref(false)
const sessions = ref([])
const sessionsLoading = ref(false)
const channels = ref([])
const grantVisible = ref(false)
const granting = ref(false)
const grantFormRef = ref()
const grantForm = reactive({ channelCode: '', validFrom: '', validTo: '' })

const grantRules = {
  channelCode: [{ required: true, message: '请选择条线', trigger: 'change' }],
}

async function loadUsers() {
  usersLoading.value = true
  try {
    users.value = await api.users(keyword.value)
  } catch (e) {
    toast.error(`用户查询失败：${e.message}`)
  } finally {
    usersLoading.value = false
  }
}

function queryUsers() {
  loadUsers()
}

function selectUser(row) {
  if (!row) {
    return
  }
  selected.value = row
  loadGrants()
  loadSessions()
}

async function loadGrants() {
  if (!selected.value) {
    return
  }
  grantsLoading.value = true
  try {
    grants.value = await api.grants(selected.value.username)
  } catch (e) {
    toast.error(`授权加载失败：${e.message}`)
  } finally {
    grantsLoading.value = false
  }
}

async function loadSessions() {
  if (!selected.value) {
    return
  }
  sessionsLoading.value = true
  try {
    sessions.value = await api.sessions(selected.value.username)
  } catch (e) {
    toast.error(`会话查询失败：${e.message}`)
  } finally {
    sessionsLoading.value = false
  }
}

function openGrant() {
  grantForm.channelCode = ''
  grantForm.validFrom = ''
  grantForm.validTo = ''
  grantVisible.value = true
}

async function submitGrant() {
  const valid = await grantFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (grantForm.validFrom && grantForm.validTo && grantForm.validFrom > grantForm.validTo) {
    toast.warn('生效时间不能晚于失效时间')
    return
  }
  granting.value = true
  try {
    await api.grant(selected.value.username, {
      channelCode: grantForm.channelCode,
      validFrom: grantForm.validFrom || null,
      validTo: grantForm.validTo || null
    })
    toast.info('授权已保存')
    grantVisible.value = false
    await loadGrants()
  } catch (e) {
    toast.error(`授权失败：${e.message}`)
  } finally {
    granting.value = false
  }
}

async function revoke(row) {
  try {
    await ElMessageBox.confirm(
      `确认撤销 ${selected.value.username} 的 ${row.channelCode} 条线授权？`,
      '撤销授权',
      { type: 'warning', confirmButtonText: '撤销', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await api.revoke(selected.value.username, row.channelCode)
    toast.info('授权已撤销')
    await loadGrants()
  } catch (e) {
    toast.error(`撤销失败：${e.message}`)
  }
}

async function kickout() {
  try {
    await ElMessageBox.confirm(
      `将强制注销 ${selected.value.username} 的全部 CAS 会话，并通知业务系统注销本地会话。是否继续？`,
      '强制踢下线',
      { type: 'warning', confirmButtonText: '踢下线', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    const result = await api.kickout(selected.value.username)
    toast.info(
      `已处理 ${result.sessions} 个会话，发出 ${result.logoutRequests} 个 SLO 请求` +
        (result.errors?.length ? `，${result.errors.length} 个错误` : '')
    )
    await loadSessions()
  } catch (e) {
    toast.error(`踢下线失败：${e.message}`)
  }
}

async function loadChannels() {
  try {
    channels.value = await api.channels()
  } catch (e) {
    toast.error(`条线加载失败：${e.message}`)
  }
}

onMounted(async () => {
  await Promise.all([loadUsers(), loadChannels()])
})
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
  grid-template-columns: minmax(320px, 2fr) minmax(0, 3fr);
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

.service-item {
  word-break: break-all;
  line-height: 1.6;
}
</style>
