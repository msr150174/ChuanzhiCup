<script setup lang="ts">
import { Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onMounted, reactive, ref } from 'vue'
import { feedbackApi } from '@/api'
import type { FeedbackItem, FeedbackQuery, Paged } from '@/types'
import { formatDateTime } from '@/utils/format'

const loading = ref(false)
const tableData = ref<FeedbackItem[]>([])
const total = ref(0)
const dialogVisible = ref(false)
const submitting = ref(false)
const currentFeedback = ref<FeedbackItem | null>(null)
const reply = ref('')

const filters = reactive({
  handled: '' as FeedbackQuery['handled'],
})

const query = reactive<FeedbackQuery>({
  handled: '',
  page: 1,
  pageSize: 10,
})

const ratingText = computed(() => ['很不满意', '不满意', '一般', '满意', '非常满意'])

async function loadFeedback() {
  loading.value = true
  try {
    const result: Paged<FeedbackItem> = await feedbackApi.list(query)
    tableData.value = result.records
    total.value = result.total
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  query.handled = filters.handled
  query.page = 1
  loadFeedback()
}

function resetFilters() {
  filters.handled = ''
  applyFilters()
}

function openReply(row: FeedbackItem) {
  currentFeedback.value = row
  reply.value = row.reply || ''
  dialogVisible.value = true
}

async function submitReply() {
  if (!currentFeedback.value) return
  if (!reply.value.trim()) {
    ElMessage.warning('请填写回复内容')
    return
  }

  submitting.value = true
  try {
    await ElMessageBox.confirm('确认提交该反馈回复吗？提交后将标记为已处理。', '回复反馈', {
      confirmButtonText: '确认回复',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await feedbackApi.reply(currentFeedback.value.id, reply.value.trim())
    ElMessage.success('回复成功')
    dialogVisible.value = false
    await loadFeedback()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      // 业务错误由响应拦截器提示
    }
  } finally {
    submitting.value = false
  }
}

function ratingLabel(rating: number): string {
  return ratingText.value[rating - 1] || `${rating} 分`
}

function handlePageChange(page: number) {
  query.page = page
  loadFeedback()
}

function handleSizeChange(size: number) {
  query.pageSize = size
  query.page = 1
  loadFeedback()
}

onMounted(loadFeedback)
</script>

<template>
  <div class="feedback-page">
    <section class="card filter-card">
      <el-form :inline="true" class="filter-form" @submit.prevent>
        <el-form-item label="状态">
          <el-select v-model="filters.handled" placeholder="全部状态" clearable class="filter-select">
            <el-option label="未回复" value="false" />
            <el-option label="已回复" value="true" />
          </el-select>
        </el-form-item>
        <div class="filter-actions">
          <el-button type="primary" :icon="Search" @click="applyFilters">查询</el-button>
          <el-button :icon="Refresh" @click="resetFilters">重置</el-button>
        </div>
      </el-form>
    </section>

    <section class="card table-card">
      <el-table v-loading="loading" :data="tableData" class="warm-table">
        <el-table-column prop="userName" label="用户" min-width="100" />
        <el-table-column prop="userPhone" label="手机号" min-width="130" />
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="评分" width="120">
          <template #default="{ row }: { row: FeedbackItem }">
            <div class="rating-cell">
              <el-rate :model-value="row.severity" disabled />
              <small>{{ ratingLabel(row.severity) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" min-width="170">
          <template #default="{ row }: { row: FeedbackItem }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }: { row: FeedbackItem }">
            <el-tag :type="row.handled ? 'success' : 'warning'" effect="light">
              {{ row.handled ? '已回复' : '待回复' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="90">
          <template #default="{ row }: { row: FeedbackItem }">
            <el-button link type="primary" @click="openReply(row)">{{ row.handled ? '查看' : '回复' }}</el-button>
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

    <el-dialog v-model="dialogVisible" :title="currentFeedback?.handled ? '反馈详情' : '回复反馈'" width="600px" destroy-on-close>
      <div v-if="currentFeedback" class="reply-dialog">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="用户">
            {{ currentFeedback.userName }}（{{ currentFeedback.userPhone }}）
          </el-descriptions-item>
          <el-descriptions-item label="标题">{{ currentFeedback.title }}</el-descriptions-item>
          <el-descriptions-item label="内容">{{ currentFeedback.content }}</el-descriptions-item>
          <el-descriptions-item label="评分">
            <el-rate :model-value="currentFeedback.severity" disabled />
          </el-descriptions-item>
          <el-descriptions-item v-if="currentFeedback.reply" label="历史回复">{{ currentFeedback.reply }}</el-descriptions-item>
        </el-descriptions>
        <el-input
          v-model="reply"
          class="reply-input"
          type="textarea"
          :rows="4"
          maxlength="300"
          show-word-limit
          placeholder="请输入回复内容"
        />
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitReply">确认回复</el-button>
      </template>
    </el-dialog>
  </div>
</template>
