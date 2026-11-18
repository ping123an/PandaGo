<template>
  <div class="admin-users-page">
    <div class="page-head">
      <div>
        <h1>用户管理</h1>
        <p>仅管理员可操作；启用/禁用用户状态，不允许在此修改角色。</p>
      </div>
      <el-input
        v-model="keyword"
        class="search-input"
        placeholder="按用户名搜索"
        clearable
        @keyup.enter="reload"
        @clear="reload"
      >
        <template #append>
          <el-button @click="reload">搜索</el-button>
        </template>
      </el-input>
    </div>

    <div v-loading="loading" class="table-card card">
      <el-table :data="users" empty-text="没有用户" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="nickname" label="昵称" min-width="140">
          <template #default="{ row }">{{ row.nickname || '—' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="联系电话" min-width="150">
          <template #default="{ row }">{{ row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <el-tag v-for="role in row.roles" :key="role" size="small" class="role-tag">{{ role }}</el-tag>
            <span v-if="!row.roles.length">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" min-width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 1"
              link
              type="danger"
              :disabled="isSelf(row.id)"
              @click="toggleStatus(row, 0)"
            >禁用</el-button>
            <el-button v-else link type="success" @click="toggleStatus(row, 1)">启用</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="total > 0" class="pagination">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          background
          @change="loadUsers"
        />
      </div>
    </div>

    <div class="logs-card card">
      <h2>最近操作日志</h2>
      <el-table :data="logs" size="small" empty-text="暂无操作记录">
        <el-table-column prop="createdAt" label="时间" min-width="170" />
        <el-table-column prop="operatorName" label="操作人" min-width="120" />
        <el-table-column prop="module" label="模块" width="100" />
        <el-table-column prop="operation" label="操作" width="150" />
        <el-table-column prop="detail" label="说明" min-width="200">
          <template #default="{ row }">{{ row.detail || '—' }}</template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminUsers, updateAdminUserStatus, getOperationLogs } from '@/api/admin'
import { useUserStore } from '@/stores/user'
import type { AdminUser, OperationLog } from '@/types/admin'

const userStore = useUserStore()
const loading = ref(false)
const users = ref<AdminUser[]>([])
const logs = ref<OperationLog[]>([])
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)

function isSelf(id: number): boolean {
  return userStore.userInfo?.userId === id
}

async function loadUsers(): Promise<void> {
  loading.value = true
  try {
    const { data } = await getAdminUsers({
      username: keyword.value || undefined,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    users.value = data.data.list
    total.value = data.data.total
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载用户失败')
  } finally {
    loading.value = false
  }
}

async function loadLogs(): Promise<void> {
  try {
    const { data } = await getOperationLogs({ pageNum: 1, pageSize: 8 })
    logs.value = data.data.list
  } catch {
    logs.value = []
  }
}

function reload(): void {
  pageNum.value = 1
  void loadUsers()
}

async function toggleStatus(row: AdminUser, status: number): Promise<void> {
  const label = status === 1 ? '启用' : '禁用'
  try {
    await ElMessageBox.confirm(`确定${label}用户「${row.username}」吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await updateAdminUserStatus(row.id, status)
    ElMessage.success(`已${label}用户`)
    await Promise.all([loadUsers(), loadLogs()])
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  }
}

onMounted(() => {
  void loadUsers()
  void loadLogs()
})
</script>

<style scoped lang="scss">
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;

  h1 { margin: 0 0 6px; font-size: 22px; }
  p { margin: 0; color: var(--pd-muted); font-size: 13px; }
}
.search-input { width: 260px; }
.card { background: #fff; border: 1px solid var(--pd-border); border-radius: 12px; padding: 18px; }
.role-tag { margin-right: 4px; }
.pagination { margin-top: 16px; justify-content: flex-end; display: flex; }
.logs-card { margin-top: 18px; }
.logs-card h2 { margin: 0 0 14px; font-size: 16px; }
</style>
