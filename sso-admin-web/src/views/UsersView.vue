<template>
  <el-row :gutter="16">
    <el-col :span="9">
      <div class="page-card">
        <h3 class="page-title">用户</h3>
        <p class="page-desc">按用户名或姓名查询（最多返回 200 条）。</p>
        <el-form inline @submit.prevent="loadUsers">
          <el-form-item>
            <el-input
              v-model="keyword"
              placeholder="用户名 / 姓名"
              clearable
              style="width: 170px"
              @keyup.enter="loadUsers"
              @clear="loadUsers"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="usersLoading" @click="loadUsers">查询</el-button>
          </el-form-item>
        </el-form>

        <el-table
          v-loading="usersLoading"
          :data="users"
          border
          highlight-current-row
          @current-change="selectUser"
        >
          <el-table-column prop="username" label="用户名" width="110" />
          <el-table-column prop="displayName" label="姓名" width="110" />
          <el-table-column prop="deptId" label="部门" width="90" />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" size="small">
                {{ row.enabled === 1 ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-col>

    <el-col :span="15">
      <div v-if="!selected" class="page-card">
        <el-empty description="请选择左侧用户，查看授权与在线会话" />
      </div>

      <template v-else>
        <div class="page-card">
          <div class="page-header">
            <div>
              <h3 class="page-title">
                {{ selected.displayName || selected.username }}（{{ selected.username }}）
              </h3>
              <p class="page-desc">
                部门 {{ selected.deptId ?? '-' }} · 账号{{ selected.enabled === 1 ? '启用' : '停用' }}
              </p>
            </div>
            <el-button type="primary" :icon="Plus" @click="openGrant">授予 / 重新启用</el-button>
          </div>

          <el-table v-loading="grantsLoading" :data="grants" border stripe>
            <el-table-column prop="channelCode" label="条线" width="100" />
            <el-table-column prop="channelName" label="名称" min-width="120" />
            <el-table-column label="授权状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
                  {{ row.status === 1 ? '有效' : '已撤销' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="条线状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.channelStatus === 1 ? 'success' : 'danger'" size="small">
                  {{ row.channelStatus === 1 ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="validFrom" label="生效时间" min-width="150" />
            <el-table-column prop="validTo" label="失效时间" min-width="150" />
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status === 1" link type="danger" @click="revoke(row)">
                  撤销
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div class="page-card sessions-card">
          <div class="page-header">
            <div>
              <h3 class="page-title">在线会话</h3>
              <p class="page-desc">
                来自 CAS 控制面（仅本机可访问）；踢下线会触发 SLO，注销业务系统本地会话。
              </p>
            </div>
            <div>
              <el-button :icon="Refresh" :loading="sessionsLoading" @click="loadSessions">刷新</el-button>
              <el-button type="danger" :disabled="sessions.length === 0" @click="kickout">
                强制踢下线
              </el-button>
            </div>
          </div>

          <el-table v-loading="sessionsLoading" :data="sessions" border>
            <el-table-column prop="ticketId" label="TGT" min-width="240" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="创建时间" min-width="200" />
            <el-table-column label="已接入服务" min-width="220">
              <template #default="{ row }">
                <div v-for="service in row.services" :key="service" class="service-item">
                  {{ service }}
                </div>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!sessionsLoading && sessions.length === 0"
            description="无在线会话"
            :image-size="60"
          />
        </div>
      </template>
    </el-col>
  </el-row>

  <el-dialog v-model="grantVisible" title="授予 / 重新启用授权" width="520px">
    <el-form :model="grantForm" label-width="100px">
      <el-form-item label="条线" required>
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
      <el-button @click="grantVisible = false">取消</el-button>
      <el-button type="primary" :loading="granting" @click="submitGrant">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
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
const grantForm = reactive({ channelCode: '', validFrom: '', validTo: '' })

async function loadUsers() {
  usersLoading.value = true
  try {
    users.value = await api.users(keyword.value)
  } catch (e) {
    ElMessage.error(`用户查询失败：${e.message}`)
  } finally {
    usersLoading.value = false
  }
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
    ElMessage.error(`授权加载失败：${e.message}`)
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
    ElMessage.error(`会话查询失败：${e.message}`)
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
  if (!grantForm.channelCode) {
    ElMessage.warning('请选择条线')
    return
  }
  granting.value = true
  try {
    await api.grant(selected.value.username, {
      channelCode: grantForm.channelCode,
      validFrom: grantForm.validFrom || null,
      validTo: grantForm.validTo || null
    })
    ElMessage.success('授权已保存')
    grantVisible.value = false
    await loadGrants()
  } catch (e) {
    ElMessage.error(`授权失败：${e.message}`)
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
    ElMessage.success('授权已撤销')
    await loadGrants()
  } catch (e) {
    ElMessage.error(`撤销失败：${e.message}`)
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
    ElMessage.success(
      `已处理 ${result.sessions} 个会话，发出 ${result.logoutRequests} 个 SLO 请求` +
        (result.errors?.length ? `，${result.errors.length} 个错误` : '')
    )
    await loadSessions()
  } catch (e) {
    ElMessage.error(`踢下线失败：${e.message}`)
  }
}

async function loadChannels() {
  try {
    channels.value = await api.channels()
  } catch (e) {
    ElMessage.error(`条线加载失败：${e.message}`)
  }
}

onMounted(async () => {
  await Promise.all([loadUsers(), loadChannels()])
})
</script>

<style scoped>
.sessions-card {
  margin-top: 16px;
}

.service-item {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  word-break: break-all;
}
</style>