<script setup lang="ts">
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { userApi } from '@/api'
import { useUserStore } from '@/stores/user'
import type { Paged, UserDetail, UserListItem, UserQuery } from '@/types'
import { formatDateTime, roleLabel } from '@/utils/format'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<UserListItem[]>([])
const total = ref(0)
const detailVisible = ref(false)
const detailLoading = ref(false)
const currentDetail = ref<UserDetail | null>(null)

const filters = reactive({
  keyword: '',
  role: '' as UserQuery['role'],
  isAdmin: '' as UserQuery['isAdmin'],
  isActive: '' as UserQuery['isActive'],
})

const query = reactive<UserQuery>({
  keyword: '',
  role: '',
  isAdmin: '',
  isActive: '',
  page: 1,
  pageSize: 10,
})

async function loadUsers() {
  loading.value = true
  try {
    const result: Paged<UserListItem> = await userApi.list(query)
    tableData.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  query.keyword = filters.keyword.trim()
  query.role = filters.role
  query.isAdmin = filters.isAdmin
  query.isActive = filters.isActive
  query.page = 1
  loadUsers()
}

function resetFilters() {
  filters.keyword = ''
  filters.role = ''
  filters.isAdmin = ''
  filters.isActive = ''
  applyFilters()
}

function handlePageChange(page: number) {
  query.page = page
  loadUsers()
}

function handleSizeChange(size: number) {
  query.pageSize = size
  query.page = 1
  loadUsers()
}

async function openDetail(row: UserListItem) {
  detailVisible.value = true
  detailLoading.value = true
  currentDetail.value = null
  try {
    currentDetail.value = await userApi.detail(row.id)
  } finally {
    detailLoading.value = false
  }
}

async function toggleAdmin(row: UserListItem) {
  const nextAdmin = !row.isAdmin
  await ElMessageBox.confirm(
    nextAdmin ? `确定将「${row.name}」设为管理员吗？` : `确定取消「${row.name}」的管理员身份吗？`,
    nextAdmin ? '设为管理员' : '取消管理员',
    { confirmButtonText: '确定', cancelButtonText: '再想想', type: 'warning' }
  )
  await userApi.setAdmin(row.id, nextAdmin)
  ElMessage.success(nextAdmin ? '已设为管理员' : '已取消管理员')
  await loadUsers()
}

async function toggleActive(row: UserListItem) {
  const nextActive = !row.isActive
  await ElMessageBox.confirm(
    nextActive ? `确定启用「${row.name}」的账号吗？` : `确定禁用「${row.name}」的账号吗？禁用后该用户无法登录。`,
    nextActive ? '启用账号' : '禁用账号',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: nextActive ? 'info' : 'warning' }
  )
  await userApi.setActive(row.id, nextActive)
  ElMessage.success(nextActive ? '账号已启用' : '账号已禁用')
  await loadUsers()
}

onMounted(loadUsers)
</script>

<template>
  <div class="users-page">
    <section class="card filter-card">
      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" placeholder="手机号 / 姓名" clearable class="filter-input" @keyup.enter="applyFilters" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="filters.role" placeholder="全部角色" clearable class="filter-select">
            <el-option label="长辈" value="ELDER" />
            <el-option label="家属" value="FAMILY" />
          </el-select>
        </el-form-item>
        <el-form-item label="身份">
          <el-select v-model="filters.isAdmin" placeholder="全部身份" clearable class="filter-select">
            <el-option label="管理员" value="true" />
            <el-option label="普通用户" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.isActive" placeholder="全部状态" clearable class="filter-select">
            <el-option label="启用" value="true" />
            <el-option label="禁用" value="false" />
          </el-select>
        </el-form-item>
        <div class="filter-actions">
          <el-button type="primary" :icon="Search" @click="applyFilters">查询</el-button>
          <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
        </div>
      </el-form>
    </section>

    <section class="card table-card">
      <el-table v-loading="loading" :data="tableData" row-key="id" class="warm-table">
        <el-table-column prop="name" label="姓名" min-width="110" />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column label="角色" min-width="90">
          <template #default="{ row }: { row: UserListItem }">
            <el-tag :type="row.role === 'ELDER' ? 'success' : 'info'" effect="light" round>{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="身份" min-width="100">
          <template #default="{ row }: { row: UserListItem }">
            <el-tag :type="row.isAdmin ? 'success' : 'info'" effect="plain">{{ row.isAdmin ? '管理员' : '普通用户' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="90">
          <template #default="{ row }: { row: UserListItem }">
            <el-tag :type="row.isActive ? 'success' : 'danger'" effect="light">{{ row.isActive ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="注册时间" min-width="170">
          <template #default="{ row }: { row: UserListItem }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="250">
          <template #default="{ row }: { row: UserListItem }">
            <div class="table-actions">
              <el-button link type="primary" @click="openDetail(row)">详情</el-button>
              <el-button link :type="row.isAdmin ? 'warning' : 'success'" @click="toggleAdmin(row)">
                {{ row.isAdmin ? '取消管理员' : '设为管理员' }}
              </el-button>
              <el-button
                link
                :type="row.isActive ? 'danger' : 'success'"
                :disabled="row.id === userStore.user?.id"
                @click="toggleActive(row)"
              >
                {{ row.isActive ? '禁用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="detailVisible" title="用户详情" width="620px" destroy-on-close>
      <div v-loading="detailLoading">
        <el-descriptions v-if="currentDetail" :column="2" border>
          <el-descriptions-item label="姓名">{{ currentDetail.name }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ currentDetail.phone }}</el-descriptions-item>
          <el-descriptions-item label="角色">{{ roleLabel(currentDetail.role) }}</el-descriptions-item>
          <el-descriptions-item label="近 30 天打卡">{{ currentDetail.checkinCount30d }} 次</el-descriptions-item>
          <el-descriptions-item label="未处理告警">
            <el-tag :type="currentDetail.pendingAlerts > 0 ? 'danger' : 'success'" effect="light">
              {{ currentDetail.pendingAlerts }} 条
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ formatDateTime(currentDetail.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ currentDetail.address }}</el-descriptions-item>
          <el-descriptions-item label="绑定设备" :span="2">
            <el-empty v-if="currentDetail.devices.length === 0" description="暂无绑定设备" :image-size="60" />
            <div v-else class="device-tags">
              <el-tag v-for="device in currentDetail.devices" :key="device.id" type="success" effect="plain">
                {{ device.deviceName }} · {{ device.status === 'ONLINE' ? '在线' : '离线' }}
              </el-tag>
            </div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button type="primary" @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>
