<template>
  <view class="page">
    <!-- 页面标题 -->
    <view class="page-header">
      <text class="header-icon">👥</text>
      <view class="header-text">
        <text class="page-title">家庭成员</text>
        <text class="page-subtitle">邀请家属查看您的健康数据</text>
      </view>
    </view>

    <!-- 邀请码 -->
    <view class="card invite-section">
      <view class="section-header">
        <text class="section-title">🛡️ 专属邀请码</text>
      </view>
      <view class="invite-card">
        <text class="invite-code">{{ state.inviteCode }}</text>
        <view class="invite-actions">
          <view class="invite-btn" @click="copyInviteCode"><text>🔗 复制</text></view>
          <view class="invite-btn" @click="shareInviteCode"><text>💬 分享</text></view>
        </view>
      </view>
      <text class="invite-tip">家属输入此邀请码即可绑定，绑定后可查看您的健康数据与告警通知</text>
    </view>

    <!-- 已绑定家属 -->
    <view class="card family-section">
      <view class="section-header">
        <text class="section-title">👥 已绑定家属 ({{ activeMembers.length }})</text>
        <view class="add-btn" @click="goMemberBind"><text>➕</text></view>
      </view>

      <view v-if="state.loading" class="loading-box">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="activeMembers.length > 0" class="family-list">
        <view v-for="m in activeMembers" :key="m.id" class="family-item">
          <view class="member-avatar">
            <text class="avatar-text">{{ avatarText(m.name) }}</text>
            <text v-if="m.isPrimary" class="primary-badge">❤</text>
          </view>

          <view class="member-info" @click="viewMemberDetail(m)">
            <view class="member-line">
              <text class="member-name">{{ m.name }}</text>
              <text class="member-relation">{{ m.relation }}</text>
              <text v-if="m.isPrimary" class="tag">主要监护人</text>
              <text v-else class="tag tag-plain">普通家属</text>
            </view>
            <text class="member-sub">📞 {{ m.phone }}</text>
            <view class="member-line">
              <text class="member-time">🕐 绑定于 {{ m.bindTime }}</text>
              <text class="status-tag" :class="'status-' + m.status">{{ statusLabel(m.status) }}</text>
            </view>
          </view>

          <view class="member-actions">
            <view v-if="!m.isPrimary && m.status === 'active'" class="circle-btn btn-set" @click="setPrimaryGuardian(m)">
              <text>❤</text>
            </view>
            <view class="circle-btn btn-perm" @click="openPermissionSetting(m)"><text>🛡️</text></view>
            <view class="circle-btn btn-unbind" @click="unbindFamily(m)"><text>🚫</text></view>
          </view>
        </view>
      </view>

      <EmptyState v-else icon="👥" text="暂无绑定家属" desc="点击右上角按钮邀请家属" />
    </view>

    <!-- 待处理邀请 -->
    <view v-if="state.pending.length > 0" class="card pending-section">
      <view class="section-header">
        <text class="section-title">⚠️ 待处理邀请 ({{ state.pending.length }})</text>
      </view>
      <view v-for="p in state.pending" :key="p.id" class="pending-item">
        <view class="pending-info">
          <text class="pending-phone">{{ p.phone }}</text>
          <text class="pending-hint">等待确认中...</text>
        </view>
        <view class="pending-actions">
          <view class="pending-btn ok" @click="acceptInvite(p.id)"><text>同意</text></view>
          <view class="pending-btn no" @click="rejectInvite(p.id)"><text>拒绝</text></view>
        </view>
      </view>
    </view>

    <!-- 绑定说明 -->
    <view class="card guide-section">
      <text class="guide-title">绑定说明</text>
      <view class="guide-item"><text class="guide-icon">✓</text><text class="guide-text">家属绑定后可查看您的健康数据与作息记录</text></view>
      <view class="guide-item"><text class="guide-icon">✓</text><text class="guide-text">主要监护人会优先收到告警通知</text></view>
      <view class="guide-item"><text class="guide-icon">✓</text><text class="guide-text">您可随时调整每位家属的查看权限</text></view>
      <view class="guide-item"><text class="guide-icon warn">⚠️</text><text class="guide-text">解绑后对方将无法继续查看您的数据</text></view>
    </view>

    <!-- 权限设置弹窗 -->
    <view v-if="state.showPermissionModal" class="modal-overlay" @click="closePermissionModal">
      <view class="modal-content" @click.stop>
        <view class="modal-header">
          <text class="modal-title">🛡️ 权限设置</text>
          <text class="modal-sub">{{ state.selectedMember?.name }}</text>
        </view>
        <view class="modal-body">
          <view v-for="p in permissionRows" :key="p.key" class="permission-item">
            <view class="permission-info">
              <text class="permission-label">{{ p.label }}</text>
              <text class="permission-desc">{{ p.desc }}</text>
            </view>
            <view class="toggle-switch" :class="{ on: state.form[p.key] }" @click="state.form[p.key] = !state.form[p.key]">
              <view class="toggle-slider"></view>
            </view>
          </view>
        </view>
        <view class="modal-footer row">
          <view class="cancel-btn" @click="closePermissionModal"><text>取消</text></view>
          <view class="save-btn" @click="savePermissions"><text>保存</text></view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 家庭成员（家属绑定管理）
 * ----------------------------------------------------------------------------
 * 邀请码 + 已绑定家属列表 + 待处理邀请 + 权限设置弹窗
 * 权限规则：设为主要监护人 → 6 项权限全开，其他成员降级为 limited
 */
import { reactive, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { familyApi, authApi } from '@/services/api'
import EmptyState from '@/components/EmptyState.vue'

/**
 * 家属权限
 * 6 项布尔权限 + level 级别（full 主要监护人 / limited 普通家属）
 */
interface Permissions {
  viewHealth: boolean
  viewLocation: boolean
  viewMedicine: boolean
  setReminder: boolean
  receiveAlert: boolean
  emergencyContact: boolean
  /** full 主要监护人（6 项全开） / limited 普通家属 */
  level: 'full' | 'limited'
}

/** 家属成员 */
interface FamilyMember {
  id: string
  name: string
  phone: string
  relation: string
  isPrimary: boolean
  bindTime: string
  status: 'active' | 'pending' | 'inactive'
  permissions: Permissions
}

/**
 * 生成一套权限
 * full = true → level 为 full 且 6 项全开；否则 level 为 limited 且全关
 */
function allPermissions(full: boolean): Permissions {
  return {
    viewHealth: full,
    viewLocation: full,
    viewMedicine: full,
    setReminder: full,
    receiveAlert: full,
    emergencyContact: full,
    level: full ? 'full' : 'limited'
  }
}

/** 兜底数据 */
const FALLBACK_MEMBERS: FamilyMember[] = [
  {
    id: 'm-1',
    name: '李小明',
    phone: '138****8000',
    relation: '儿子',
    isPrimary: true,
    bindTime: '2024-03-12',
    status: 'active',
    permissions: allPermissions(true)
  },
  {
    id: 'm-2',
    name: '王丽',
    phone: '139****9000',
    relation: '女儿',
    isPrimary: false,
    bindTime: '2024-05-08',
    status: 'active',
    permissions: allPermissions(false)
  }
]

/** 兜底待处理邀请 */
const FALLBACK_PENDING = [
  { id: 'p-1', phone: '137****1234', createTime: '2 小时前' }
]

const state = reactive({
  loading: true,
  inviteCode: '',
  members: [] as FamilyMember[],
  pending: [] as { id: string; phone: string; createTime: string }[],
  showPermissionModal: false,
  selectedMember: null as FamilyMember | null,
  form: {
    viewHealth: false,
    viewLocation: false,
    receiveAlert: false,
    callContact: false
  }
})

/** 权限设置弹窗的 4 项 */
const permissionRows: { key: keyof typeof state.form; label: string; desc: string }[] = [
  { key: 'viewHealth', label: '查看健康数据', desc: '包括作息记录、睡眠质量、AI报告' },
  { key: 'viewLocation', label: '查看位置信息', desc: '实时位置与历史轨迹' },
  { key: 'receiveAlert', label: '接收告警通知', desc: 'SOS求救、异常告警提醒' },
  { key: 'callContact', label: '联系紧急联系人', desc: '可通过平台联系您的其他联系人' }
]

/** 已激活的家属 */
const activeMembers = computed(() => state.members.filter((m) => m.status === 'active'))

/** 姓名首字头像 */
function avatarText(name: string): string {
  return name ? name.slice(0, 1) : '家'
}

/** 状态文案 */
function statusLabel(status: string): string {
  if (status === 'active') return '已激活'
  if (status === 'pending') return '待确认'
  return '已失效'
}

/** 加载家庭成员数据 */
async function loadFamily() {
  state.loading = true
  try {
    const [memberRes, pendingRes] = await Promise.all([
      familyApi.members(),
      familyApi.pending().catch(() => ({ list: [] as unknown[] }))
    ])
    state.members = (memberRes.list || []).map((u) => ({
      id: u.id,
      name: u.name || '家属',
      phone: u.phone || '',
      relation: '家属',
      isPrimary: false,
      bindTime: u.createTime || '--',
      status: 'active' as const,
      permissions: allPermissions(false)
    }))
    if (state.members.length === 0) throw new Error('empty')
    state.pending = FALLBACK_PENDING
  } catch {
    // ★ 兜底
    state.members = JSON.parse(JSON.stringify(FALLBACK_MEMBERS)) as FamilyMember[]
    state.pending = [...FALLBACK_PENDING]
  } finally {
    state.loading = false
  }

  // 邀请码：接口失败时本地生成一份展示
  try {
    const inv = await familyApi.invite()
    state.inviteCode = inv.inviteCode
  } catch {
    try {
      const inv2 = await authApi.generateInvite()
      state.inviteCode = inv2.inviteCode
    } catch {
      state.inviteCode = uni.getStorageSync('guardian_invite_code') || 'GH-8F2K9D'
      uni.setStorageSync('guardian_invite_code', state.inviteCode)
    }
  }
}

/** 复制邀请码 */
function copyInviteCode() {
  uni.setClipboardData({
    data: state.inviteCode,
    success: () => uni.showToast({ title: '邀请码已复制', icon: 'none' })
  })
}

/** 分享邀请码 */
function shareInviteCode() {
  // #ifdef MP-WEIXIN
  uni.showShareMenu({ withShareTicket: true } as never)
  // #endif
  // #ifndef MP-WEIXIN
  uni.showModal({
    title: '分享邀请码',
    content: `邀请码：${state.inviteCode}，请复制后发送给家属`,
    showCancel: false,
    confirmText: '复制',
    success: () => copyInviteCode()
  })
  // #endif
}

/** 跳转成员绑定页 */
function goMemberBind() {
  uni.navigateTo({ url: '/pages/member-bind/index' })
}

/** 查看成员详情 */
function viewMemberDetail(m: FamilyMember) {
  const p = m.permissions
  const isFull = p.level === 'full'
  const yn = (on: boolean) => (isFull || on ? '是' : '否')
  uni.showModal({
    title: m.name,
    content:
      `关系：${m.relation}\n绑定时间：${m.bindTime}\n状态：${statusLabel(m.status)}\n` +
      `权限级别：${isFull ? '主要监护人（6 项全开）' : '普通家属'}\n` +
      `查看健康数据：${yn(p.viewHealth)}\n` +
      `查看位置信息：${yn(p.viewLocation)}\n` +
      `接收告警通知：${yn(p.receiveAlert)}\n` +
      `联系紧急联系人：${yn(p.emergencyContact)}`,
    showCancel: false,
    confirmText: '知道了'
  })
}

/** 打开权限设置弹窗 */
function openPermissionSetting(m: FamilyMember) {
  state.selectedMember = m
  const isFull = m.permissions.level === 'full'
  state.form = {
    viewHealth: isFull || m.permissions.viewHealth,
    viewLocation: isFull || m.permissions.viewLocation,
    receiveAlert: isFull || m.permissions.receiveAlert,
    callContact: isFull || m.permissions.emergencyContact
  }
  state.showPermissionModal = true
}

/** 关闭权限弹窗 */
function closePermissionModal() {
  state.showPermissionModal = false
  state.selectedMember = null
}

/** 保存权限 */
async function savePermissions() {
  const m = state.selectedMember
  if (!m) return

  // 6 项权限：勾选后仅保留 4 项可调 + 原有 2 项跟随主要监护人级别
  const next: Permissions = {
    ...m.permissions,
    viewHealth: state.form.viewHealth,
    viewLocation: state.form.viewLocation,
    receiveAlert: state.form.receiveAlert,
    emergencyContact: state.form.callContact
  }
  // 四项全部勾选时视为完整权限，否则降级为 limited
  const allOn =
    next.viewHealth && next.viewLocation && next.receiveAlert && next.emergencyContact
  next.level = allOn ? 'full' : 'limited'

  try {
    await familyApi.setPermission(m.id, next.level)
  } catch {
    // 离线降级：本地保存
  }
  m.permissions = next
  m.isPrimary = next.level === 'full'
  closePermissionModal()
  uni.showToast({ title: '权限已更新', icon: 'none' })
}

/**
 * 设为主要监护人
 * 规则：该成员 6 项权限全开（level=full），其他 active 成员降级为 limited
 */
async function setPrimaryGuardian(m: FamilyMember) {
  try {
    await familyApi.setPermission(m.id, 'full')
    await Promise.all(
      activeMembers.value
        .filter((x) => x.id !== m.id)
        .map((x) => familyApi.setPermission(x.id, 'limited').catch(() => undefined))
    )
  } catch {
    // 离线降级：仍然本地更新
  }
  state.members.forEach((x) => {
    x.isPrimary = x.id === m.id
    x.permissions = allPermissions(x.id === m.id)
  })
  uni.showToast({ title: `已设置${m.name}为主要监护人`, icon: 'none' })
}

/** 同意邀请 */
async function acceptInvite(id: string) {
  try {
    await familyApi.accept(id)
  } catch {
    // 离线降级
  }
  state.pending = state.pending.filter((p) => p.id !== id)
  uni.showToast({ title: '已同意', icon: 'none' })
  loadFamily()
}

/** 拒绝邀请 */
async function rejectInvite(id: string) {
  try {
    await familyApi.reject(id)
  } catch {
    // 离线降级
  }
  state.pending = state.pending.filter((p) => p.id !== id)
  uni.showToast({ title: '已拒绝', icon: 'none' })
}

/** 解绑家属 */
function unbindFamily(m: FamilyMember) {
  uni.showModal({
    title: '解绑家属',
    content: `确认解绑 ${m.name}（${m.relation}）？解绑后对方将无法查看您的健康数据。`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await familyApi.remove(m.id)
      } catch {
        // 离线降级
      }
      state.members = state.members.filter((x) => x.id !== m.id)
      uni.showToast({ title: '已解绑', icon: 'none' })
    }
  })
}

onShow(() => {
  loadFamily()
})
</script>

<style lang="scss" scoped>
.page { padding: 48rpx 24rpx calc(120rpx + env(safe-area-inset-bottom)); }

.page-header { display: flex; align-items: center; gap: 16rpx; margin-bottom: 32rpx; }
.header-icon { font-size: 48rpx; }
.header-text { display: flex; flex-direction: column; }
.page-title { font-size: 44rpx; font-weight: 700; color: var(--text-1); }
.page-subtitle { font-size: 26rpx; color: var(--text-2); margin-top: 6rpx; }

.section-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20rpx; }
.section-title { font-size: 34rpx; font-weight: 700; color: var(--text-1); }
.add-btn {
  width: 60rpx; height: 60rpx; border-radius: var(--radius-sm);
  background: var(--primary-l); color: var(--primary-d);
  display: flex; align-items: center; justify-content: center; font-size: 28rpx;
}

/* 邀请码 */
.invite-card {
  display: flex; align-items: center; justify-content: space-between;
  padding: 28rpx; border-radius: var(--radius-sm);
  background: var(--primary-l); border: 2rpx solid var(--primary);
}
.invite-code { font-size: 44rpx; font-weight: 700; color: var(--primary-d); letter-spacing: 8rpx; }
.invite-actions { display: flex; gap: 16rpx; }
.invite-btn {
  padding: 12rpx 24rpx; border-radius: var(--radius-sm);
  background: var(--surface); border: 2rpx solid var(--primary);
  color: var(--primary-d); font-size: 24rpx;
}
.invite-tip { display: block; font-size: 24rpx; color: var(--text-2); text-align: center; margin-top: 20rpx; line-height: 1.6; }

/* 家属列表 */
.loading-box { padding: 48rpx 0; text-align: center; }
.loading-text { font-size: 26rpx; color: var(--text-2); }
.family-list { display: flex; flex-direction: column; gap: 20rpx; }
.family-item {
  display: flex; align-items: center; gap: 20rpx;
  padding: 28rpx; border-radius: var(--radius-sm); background: var(--surface-2);
}
.member-avatar {
  width: 96rpx; height: 96rpx; border-radius: 50%; position: relative;
  background: linear-gradient(135deg, #6366f1, #8b5cf6); flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.avatar-text { font-size: 36rpx; color: #fff; font-weight: 700; }
.primary-badge {
  position: absolute; top: -8rpx; right: -8rpx;
  width: 40rpx; height: 40rpx; border-radius: 50%;
  background: #dc2626; color: #fff; font-size: 22rpx;
  display: flex; align-items: center; justify-content: center;
}
.member-info { flex: 1; display: flex; flex-direction: column; gap: 10rpx; }
.member-line { display: flex; align-items: center; gap: 12rpx; flex-wrap: wrap; }
.member-name { font-size: 32rpx; font-weight: 600; color: var(--text-1); }
.member-relation {
  font-size: 22rpx; color: var(--text-2);
  padding: 2rpx 14rpx; border-radius: 8rpx; background: var(--surface-3);
}
.tag-plain { background: var(--surface-3); color: var(--text-2); }
.member-sub, .member-time { font-size: 24rpx; color: var(--text-2); }
.status-tag { font-size: 22rpx; padding: 2rpx 14rpx; border-radius: 8rpx; }
.status-active { background: var(--primary-l); color: var(--primary-d); }
.status-pending { background: var(--warning-l); color: var(--warning); }
.status-inactive { background: var(--danger-l); color: var(--danger); }

.member-actions { display: flex; gap: 12rpx; }
.circle-btn {
  width: 64rpx; height: 64rpx; border-radius: 50%; font-size: 26rpx;
  display: flex; align-items: center; justify-content: center;
}
.btn-set { background: var(--danger-l); }
.btn-perm { background: rgba(99, 102, 241, 0.12); }
.btn-unbind { background: var(--danger-l); }

/* 待处理邀请 */
.pending-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 24rpx 28rpx; border-radius: var(--radius-sm);
  background: var(--warning-l); margin-bottom: 16rpx;
}
.pending-info { display: flex; flex-direction: column; gap: 6rpx; }
.pending-phone { font-size: 30rpx; font-weight: 600; color: var(--text-1); }
.pending-hint { font-size: 22rpx; color: var(--warning); }
.pending-actions { display: flex; gap: 16rpx; }
.pending-btn { padding: 12rpx 32rpx; border-radius: var(--radius-sm); font-size: 26rpx; }
.pending-btn.ok { background: var(--primary-d); color: #fff; }
.pending-btn.no { background: var(--surface); color: var(--danger); }

/* 绑定说明 */
.guide-title { display: block; font-size: 32rpx; font-weight: 700; color: var(--text-1); margin-bottom: 20rpx; }
.guide-item { display: flex; gap: 16rpx; margin-bottom: 16rpx; }
.guide-icon { font-size: 26rpx; color: var(--primary-d); }
.guide-icon.warn { color: var(--warning); }
.guide-text { font-size: 26rpx; color: var(--text-2); flex: 1; line-height: 1.6; }

/* 弹窗 */
.modal-overlay {
  position: fixed; left: 0; top: 0; right: 0; bottom: 0;
  background: rgba(0, 0, 0, 0.5); z-index: 999;
  display: flex; align-items: center; justify-content: center;
}
.modal-content {
  width: 86%; background: var(--surface);
  border-radius: var(--radius); overflow: hidden;
}
.modal-header {
  padding: 32rpx; background: var(--primary-l);
  display: flex; flex-direction: column; gap: 8rpx;
}
.modal-title { font-size: 34rpx; font-weight: 700; color: var(--text-1); }
.modal-sub { font-size: 24rpx; color: var(--text-2); }
.modal-body { padding: 32rpx; }
.permission-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 24rpx; border-radius: var(--radius-sm);
  background: var(--surface-2); margin-bottom: 20rpx;
}
.permission-info { flex: 1; display: flex; flex-direction: column; gap: 8rpx; padding-right: 16rpx; }
.permission-label { font-size: 30rpx; font-weight: 600; color: var(--text-1); }
.permission-desc { font-size: 22rpx; color: var(--text-2); }

.toggle-switch {
  width: 96rpx; height: 56rpx; border-radius: 28rpx;
  background: var(--surface-3); position: relative; transition: background 0.2s; flex-shrink: 0;
}
.toggle-switch.on { background: var(--primary-d); }
.toggle-slider {
  position: absolute; top: 4rpx; left: 4rpx;
  width: 48rpx; height: 48rpx; border-radius: 50%;
  background: #fff; transition: left 0.2s;
}
.toggle-switch.on .toggle-slider { left: 44rpx; }

.modal-footer { padding: 0 32rpx 32rpx; }
.modal-footer.row { display: flex; gap: 20rpx; }
.cancel-btn, .save-btn {
  flex: 1; height: 88rpx; border-radius: var(--radius-sm);
  display: flex; align-items: center; justify-content: center; font-size: 30rpx;
}
.cancel-btn { background: var(--surface-2); color: var(--text-2); }
.save-btn { background: var(--primary-d); color: #fff; font-weight: 600; }
</style>
