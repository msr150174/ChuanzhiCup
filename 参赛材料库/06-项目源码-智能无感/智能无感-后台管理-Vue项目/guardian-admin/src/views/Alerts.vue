<script setup lang="ts">
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { alertApi } from '@/api'
import type { AlertItem, AlertQuery, Paged, Severity } from '@/types'
import { alertTypeLabel, formatDateTime, severityLabel, severityTagType } from '@/utils/format'

const loading = ref(false)
const tableData = ref<AlertItem[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitting = ref(false)
const currentAlert = ref<AlertItem | null>(null)
const notes = ref('')

const filters = reactive({
  handled: '' as AlertQuery['handled'],
  severity: '' as AlertQuery['severity'],
})

const query = reactive<AlertQuery>({
  handled: '',
  severity: '',
  page: 1,
  pageSize: 10,
})

async function loadAlerts() {
  loading.value = true
  try {
    const result: Paged<AlertItem> = await alertApi.list(query)
    tableData.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  query.handled = filters.handled
  query.severity = filters.severity
  query.page = 1
  loadAlerts()
}

function resetFilters() {
  filters.handled = ''
  filters.severity = ''
  applyFilters()
}

function openHandle(row: AlertItem) {
  currentAlert.value = row
  notes.value = ''
  dialogVisible.value = true
}

async function submitHandle() {
  if (!currentAlert.value) return
  if (!notes.value.trim()) {
    ElMessage.warning('请填写处理说明')
    return
  }

  submitting.value = true
  try {
    await ElMessageBox.confirm('确认提交处理说明并标记该告警为已处理吗？', '处理告警', {
      confirmButtonText: '确认处理',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await alertApi.handle(currentAlert.value.id, notes.value.trim())
    ElMessage.success('已标记为处理完成')
    dialogVisible.value = false
    await loadAlerts()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      // 业务错误由响应拦截器提示
    }
  } finally {
    submitting.value = false
  }
}

function rowClassName({ row }: { row: AlertItem }): string {
  return !row.handled ? 'pending-row' : ''
}

function handlePageChange(page: number) {
  query.page = page
  loadAlerts()
}

function handleSizeChange(size: number) {
  query.pageSize = size
  query.page = 1
  loadAlerts()
}

onMounted(loadAlerts)
</script>

<template>
  <div class="alerts-page">
    <section class="card filter-card">
      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="filters.handled" placeholder="全部状态" clearable class="filter-select">
            <el-option label="未处理" value="false" />
            <el-option label="已处理" value="true" />
          </el-select>
        </el-form-item>
        <el-form-item label="级别">
          <el-select v-model="filters.severity" placeholder="全部级别" clearable class="filter-select">
            <el-option label="紧急" value="URGENT" />
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>
        <div class="filter-actions">
          <el-button type="primary" :icon="Search" @click="applyFilters">查询</el-button>
          <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
        </div>
      </el-form>
    </section>

    <section class="card table-card">
      <el-table v-loading="loading" :data="tableData" :row-class-name="rowClassName" class="warm-table">
        <el-table-column label="级别" width="90">
          <template #default="{ row }: { row: AlertItem }">
            <el-tag :type="severityTagType(row.severity as Severity)" effect="dark" round>
              {{ severityLabel(row.severity) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="110">
          <template #default="{ row }: { row: AlertItem }">{{ alertTypeLabel(row.alertType) }}</template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="130" />
        <el-table-column prop="description" label="描述" min-width="230" show-overflow-tooltip />
        <el-table-column prop="userName" label="用户" min-width="100" />
        <el-table-column prop="userPhone" label="手机号" min-width="130" />
        <el-table-column label="发生时间" min-width="170">
          <template #default="{ row }: { row: AlertItem }">{{ formatDateTime(row.occurredAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }: { row: AlertItem }">
            <el-tag :type="row.handled ? 'success' : 'danger'" effect="light">
              {{ row.handled ? '已处理' : '未处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="100">
          <template #default="{ row }: { row: AlertItem }">
            <el-button link type="primary" :disabled="row.handled" @click="openHandle(row)">处理</el-button>
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

    <el-dialog v-model="dialogVisible" title="处理告警" width="560px" destroy-on-close>
      <div v-if="currentAlert" class="handle-alert">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="告警标题">{{ currentAlert.title }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ currentAlert.userName }}（{{ currentAlert.userPhone }}）</el-descriptions-item>
          <el-descriptions-item label="告警描述">{{ currentAlert.description }}</el-descriptions-item>
        </el-descriptions>
        <el-input
          v-model="notes"
          class="notes-input"
          type="textarea"
          :rows="4"
          maxlength="200"
          show-word-limit
          placeholder="请填写处理说明，例如：已电话确认，用户身体无碍。"
        />
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitHandle">确认处理</el-button>
      </template>
    </el-dialog>
  </div>
</template>
