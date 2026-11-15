<template>
  <view class="page">
    <!-- 标题 -->
    <view class="page-header">
      <text class="header-icon">📞</text>
      <view class="header-text">
        <text class="page-title">紧急联系人</text>
        <text class="page-subtitle">SOS 求助时会第一时间通知这些人</text>
      </view>
    </view>

    <!-- 联系人列表 -->
    <view class="card">
      <view class="section-header">
        <text class="card-title">已添加联系人 ({{ state.list.length }})</text>
        <view class="add-btn" @click="openForm()"><text>＋ 添加</text></view>
      </view>

      <view v-if="state.loading" class="loading-box"><text class="loading-text">加载中...</text></view>

      <view v-else-if="state.list.length > 0">
        <view v-for="c in state.list" :key="c.id" class="contact-item">
          <view class="contact-avatar"><text>{{ avatarText(c.name) }}</text></view>
          <view class="contact-info">
            <view class="contact-line">
              <text class="contact-name">{{ c.name }}</text>
              <text class="contact-relation">{{ c.relation }}</text>
              <text v-if="c.isPrimary" class="tag">首选</text>
            </view>
            <text class="contact-phone">{{ c.phone }}</text>
          </view>
          <view class="contact-actions">
            <view class="circle-btn call" @click="callContact(c)"><text>📞</text></view>
            <view class="circle-btn edit" @click="openForm(c)"><text>✏️</text></view>
            <view class="circle-btn del" @click="removeContact(c)"><text>🗑️</text></view>
          </view>
        </view>
      </view>

      <EmptyState
        v-else
        icon="📞"
        text="暂无紧急联系人"
        desc="建议至少添加 2 位联系人，确保求助时能及时响应"
        action-text="立即添加"
        @action="openForm()"
      />
    </view>

    <!-- 说明 -->
    <view class="card">
      <text class="card-title">💡 说明</text>
      <view class="guide-item"><text class="guide-dot">•</text><text class="guide-text">首位联系人（首选）会在 SOS 求助时最先被通知</text></view>
      <view class="guide-item"><text class="guide-dot">•</text><text class="guide-text">建议添加同住家属、附近亲属与社区医生各一位</text></view>
      <view class="guide-item"><text class="guide-dot">•</text><text class="guide-text">联系人信息仅用于紧急求助，不会对外公开</text></view>
    </view>

    <!-- 添加/编辑弹窗 -->
    <view v-if="state.showForm" class="modal-overlay" @click="closeForm">
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text class="modal-title">{{ state.editingId ? '编辑联系人' : '添加联系人' }}</text>
        </view>
        <view class="modal-body">
          <view class="form-row">
            <text class="form-label">姓名</text>
            <input v-model="state.form.name" class="form-input" type="text" placeholder="请输入姓名" placeholder-class="ph" />
          </view>
          <view class="form-row">
            <text class="form-label">关系</text>
            <picker mode="selector" :range="relationOptions" :value="relationIndex" @change="onRelationChange">
              <view class="picker-value">
                <text>{{ state.form.relation }}</text>
                <text class="arrow">›</text>
              </view>
            </picker>
          </view>
          <view class="form-row">
            <text class="form-label">手机号</text>
            <input v-model="state.form.phone" class="form-input" type="number" maxlength="11" placeholder="请输入手机号" placeholder-class="ph" />
          </view>
          <view class="form-row no-border">
            <text class="form-label">设为首选</text>
            <view class="toggle-switch" :class="{ on: state.form.isPrimary }" @click="state.form.isPrimary = !state.form.isPrimary">
              <view class="toggle-slider"></view>
            </view>
          </view>
        </view>
        <view class="modal-footer">
          <view class="cancel-btn" @click="closeForm"><text>取消</text></view>
          <view class="save-btn" @click="saveContact"><text>保存</text></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 紧急联系人
 * ----------------------------------------------------------------------------
 * 列表 + 增删改（弹窗表单）
 * 本地存储 + 调 authApi.updateProfile 接口
 */
import { reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { authApi } from '@/services/api'
import EmptyState from '@/components/EmptyState.vue'

/** 联系人结构 */
interface Contact {
  id: string
  name: string
  relation: string
  phone: string
  isPrimary: boolean
}

/** 本地存储键 */
const STORAGE_KEY = 'guardian_emergency_contacts'

/** 关系选项 */
const relationOptions = ['儿子', '女儿', '配偶', '兄弟姐妹', '亲属', '邻居', '社区医生', '其他']

/** ★ 兜底数据 */
const FALLBACK_CONTACTS: Contact[] = [
  { id: 'c-1', name: '李小明', relation: '儿子', phone: '13800138000', isPrimary: true },
  { id: 'c-2', name: '王丽', relation: '女儿', phone: '13900139000', isPrimary: false },
  { id: 'c-3', name: '张医生', relation: '社区医生', phone: '13700137000', isPrimary: false }
]

const state = reactive({
  loading: true,
  list: [] as Contact[],
  showForm: false,
  editingId: '' as string,
  form: {
    name: '',
    relation: relationOptions[0],
    phone: '',
    isPrimary: false
  }
})

/** 关系 picker 当前索引 */
const relationIndex = computed(() => {
  const i = relationOptions.indexOf(state.form.relation)
  return i < 0 ? 0 : i
})

/** 首字头像 */
function avatarText(name: string): string {
  return name ? name.slice(0, 1) : '联'
}

function onRelationChange(e: { detail: { value: number | string } }) {
  state.form.relation = relationOptions[Number(e.detail.value)]
}

/** 本地读取 */
function readLocal(): Contact[] | null {
  const raw = uni.getStorageSync(STORAGE_KEY)
  if (raw && Array.isArray(raw) && raw.length > 0) return raw as Contact[]
  return null
}

/** 加载联系人 */
async function loadContacts() {
  state.loading = true
  const local = readLocal()

  try {
    await authApi.me()
    // 接口可用：本地优先（用户可能刚改过），否则用兜底
    state.list = local || JSON.parse(JSON.stringify(FALLBACK_CONTACTS)) as Contact[]
  } catch {
    // ★ 兜底
    state.list = local || JSON.parse(JSON.stringify(FALLBACK_CONTACTS)) as Contact[]
  } finally {
    state.loading = false
  }
}

/** 持久化（本地 + 接口） */
async function persist() {
  uni.setStorageSync(STORAGE_KEY, state.list)
  // 首选联系人也同步到用户资料
  const primary = state.list.find((c) => c.isPrimary)
  if (primary) {
    try {
      await authApi.updateProfile({ emergencyPhone: primary.phone })
    } catch {
      // ★ 离线降级：本地已保存
    }
  }
}

/** 打开表单（不传则为新增） */
function openForm(c?: Contact) {
  if (c) {
    state.editingId = c.id
    state.form = { name: c.name, relation: c.relation, phone: c.phone, isPrimary: c.isPrimary }
  } else {
    state.editingId = ''
    state.form = { name: '', relation: relationOptions[0], phone: '', isPrimary: state.list.length === 0 }
  }
  state.showForm = true
}

/** 关闭表单 */
function closeForm() {
  state.showForm = false
  state.editingId = ''
}

/** 保存联系人 */
async function saveContact() {
  const { name, relation, phone, isPrimary } = state.form
  if (!name.trim()) {
    uni.showToast({ title: '请输入姓名', icon: 'none' })
    return
  }
  if (!/^\d{6,11}$/.test(phone)) {
    uni.showToast({ title: '请输入有效手机号', icon: 'none' })
    return
  }

  // 设为首选时，其他联系人取消首选
  if (isPrimary) {
    state.list.forEach((c) => {
      c.isPrimary = false
    })
  }

  if (state.editingId) {
    const target = state.list.find((c) => c.id === state.editingId)
    if (target) {
      target.name = name.trim()
      target.relation = relation
      target.phone = phone
      target.isPrimary = isPrimary
    }
  } else {
    state.list.push({
      id: `c-${Date.now()}`,
      name: name.trim(),
      relation,
      phone,
      isPrimary
    })
  }

  await persist()
  closeForm()
  uni.showToast({ title: '已保存', icon: 'success' })
}

/** 删除联系人 */
function removeContact(c: Contact) {
  uni.showModal({
    title: '删除联系人',
    content: `确认删除 ${c.name}（${c.relation}）？`,
    success: async (res) => {
      if (!res.confirm) return
      state.list = state.list.filter((x) => x.id !== c.id)
      // 删掉首选后自动指定第一位为首选
      if (c.isPrimary && state.list.length > 0) state.list[0].isPrimary = true
      await persist()
      uni.showToast({ title: '已删除', icon: 'none' })
    }
  })
}

/** 拨打电话 */
function callContact(c: Contact) {
  uni.makePhoneCall({
    phoneNumber: c.phone,
    fail: () => uni.showToast({ title: '拨号已取消', icon: 'none' })
  })
}

onShow(() => {
  loadContacts()
})
</script>

<style lang="scss" scoped>
.page { padding: 32rpx 24rpx calc(120rpx + env(safe-area-inset-bottom)); }

.page-header { display: flex; align-items: center; gap: 16rpx; margin-bottom: 32rpx; }
.header-icon { font-size: 48rpx; }
.header-text { display: flex; flex-direction: column; }
.page-title { font-size: 44rpx; font-weight: 700; color: var(--text-1); }
.page-subtitle { font-size: 26rpx; color: var(--text-2); margin-top: 6rpx; }

.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8rpx; }
.add-btn {
  padding: 10rpx 24rpx; border-radius: var(--radius-full);
  background: var(--primary-l); color: var(--primary-d); font-size: 24rpx;
}

.loading-box { padding: 64rpx 0; text-align: center; }
.loading-text { font-size: 28rpx; color: var(--text-2); }

.contact-item {
  display: flex; align-items: center; gap: 20rpx;
  padding: 28rpx 0; border-bottom: 2rpx solid var(--divider);
}
.contact-item:last-child { border-bottom: none; }
.contact-avatar {
  width: 88rpx; height: 88rpx; border-radius: 50%; flex-shrink: 0;
  background: linear-gradient(135deg, var(--primary), #9db89c);
  color: #fff; font-size: 34rpx; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}
.contact-info { flex: 1; display: flex; flex-direction: column; gap: 8rpx; }
.contact-line { display: flex; align-items: center; gap: 12rpx; }
.contact-name { font-size: 32rpx; font-weight: 600; color: var(--text-1); }
.contact-relation {
  font-size: 22rpx; color: var(--text-2);
  padding: 2rpx 14rpx; border-radius: 8rpx; background: var(--surface-2);
}
.contact-phone { font-size: 26rpx; color: var(--text-2); }

.contact-actions { display: flex; gap: 12rpx; }
.circle-btn {
  width: 64rpx; height: 64rpx; border-radius: 50%; font-size: 26rpx;
  display: flex; align-items: center; justify-content: center;
}
.circle-btn.call { background: var(--primary-l); }
.circle-btn.edit { background: var(--surface-2); }
.circle-btn.del { background: var(--danger-l); }

.guide-item { display: flex; gap: 12rpx; margin-bottom: 14rpx; }
.guide-dot { font-size: 26rpx; color: var(--primary-d); }
.guide-text { flex: 1; font-size: 26rpx; color: var(--text-2); line-height: 1.6; }

/* 弹窗 */
.modal-overlay {
  position: fixed; left: 0; top: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5); z-index: 999;
  display: flex; align-items: center; justify-content: center;
}
.modal-content { width: 86%; background: var(--surface); border-radius: var(--radius); overflow: hidden; }
.modal-header { padding: 32rpx; background: var(--primary-l); }
.modal-title { font-size: 34rpx; font-weight: 700; color: var(--text-1); }
.modal-body { padding: 24rpx 32rpx; }
.form-row {
  display: flex; align-items: center; justify-content: space-between;
  padding: 24rpx 0; border-bottom: 2rpx solid var(--divider);
}
.form-row.no-border { border-bottom: none; }
.form-label { font-size: 28rpx; color: var(--text-2); flex-shrink: 0; }
.form-input { flex: 1; text-align: right; font-size: 30rpx; color: var(--text-1); }
.ph { color: var(--text-3); font-size: 28rpx; }
.picker-value { display: flex; align-items: center; gap: 12rpx; font-size: 30rpx; color: var(--text-1); }
.arrow { font-size: 34rpx; color: var(--text-3); }

.toggle-switch {
  width: 96rpx; height: 56rpx; border-radius: 28rpx;
  background: var(--surface-3); position: relative; transition: background 0.2s; flex-shrink: 0;
}
.toggle-switch.on { background: var(--primary-d); }
.toggle-slider {
  position: absolute; top: 4rpx; left: 4rpx;
  width: 48rpx; height: 48rpx; border-radius: 50%; background: #fff; transition: left 0.2s;
}
.toggle-switch.on .toggle-slider { left: 44rpx; }

.modal-footer { display: flex; gap: 20rpx; padding: 0 32rpx 32rpx; }
.cancel-btn, .save-btn {
  flex: 1; height: 88rpx; border-radius: var(--radius-sm);
  display: flex; align-items: center; justify-content: center; font-size: 30rpx;
}
.cancel-btn { background: var(--surface-2); color: var(--text-2); }
.save-btn { background: var(--primary-d); color: #fff; font-weight: 600; }
</style>
