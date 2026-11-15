<template>
  <view class="page">
    <view class="page-inner">
      <!-- ① 品牌区 -->
      <view class="brand">
        <view class="logo">
          <text class="logo-icon">🛡️</text>
        </view>
        <text class="app-name">AI 节律守护</text>
        <text class="app-slogan">让家人安心每一天</text>
        <view class="version-chip">
          <text>v{{ version }}</text>
        </view>
      </view>

      <!-- ② 应用介绍 -->
      <view class="card">
        <text class="card-title">📖 应用介绍</text>
        <text class="intro">
          AI 节律守护是一款面向居家养老场景的智能无感健康守护应用。通过智能手环、血压计等设备，
          自动采集长辈的作息、心率、步数与睡眠数据，结合 AI 分析生成每日健康报告；
          当出现久卧不起、异常告警等情况时，第一时间通知家属，让守护不打扰、不缺席。
        </text>
        <view class="feature-grid">
          <view v-for="f in FEATURES" :key="f.title" class="feature-item">
            <text class="feature-icon">{{ f.icon }}</text>
            <text class="feature-title">{{ f.title }}</text>
            <text class="feature-desc">{{ f.desc }}</text>
          </view>
        </view>
      </view>

      <!-- ③ 联系我们 -->
      <view class="card">
        <text class="card-title">📞 联系我们</text>

        <view class="list-item" @click="callService">
          <view class="list-item-left">
            <view class="list-item-icon">☎️</view>
            <view class="item-text">
              <text class="item-title">客服电话</text>
              <text class="item-desc">{{ SERVICE_PHONE }}（工作日 9:00-18:00）</text>
            </view>
          </view>
          <text class="list-item-arrow">›</text>
        </view>

        <view class="list-item" @click="copyEmail">
          <view class="list-item-left">
            <view class="list-item-icon">✉️</view>
            <view class="item-text">
              <text class="item-title">客服邮箱</text>
              <text class="item-desc">{{ SERVICE_EMAIL }}</text>
            </view>
          </view>
          <text class="list-item-arrow">›</text>
        </view>

        <view class="list-item" @click="copyText(COMPANY.address, '地址已复制')">
          <view class="list-item-left">
            <view class="list-item-icon">🏢</view>
            <view class="item-text">
              <text class="item-title">公司地址</text>
              <text class="item-desc">{{ COMPANY.address }}</text>
            </view>
          </view>
          <text class="list-item-arrow">›</text>
        </view>
      </view>

      <!-- ④ 相关链接 -->
      <view class="card">
        <text class="card-title">🔗 相关条款</text>
        <view class="list-item" @click="goPage('/pages/user-agreement/index')">
          <view class="list-item-left">
            <view class="list-item-icon">📘</view>
            <text class="item-title">用户服务协议</text>
          </view>
          <text class="list-item-arrow">›</text>
        </view>
        <view class="list-item" @click="goPage('/pages/privacy-policy/index')">
          <view class="list-item-left">
            <view class="list-item-icon">🔒</view>
            <text class="item-title">隐私政策</text>
          </view>
          <text class="list-item-arrow">›</text>
        </view>
        <view class="list-item" @click="goPage('/pages/voiceprint-agreement/index')">
          <view class="list-item-left">
            <view class="list-item-icon">🎙️</view>
            <text class="item-title">声纹授权协议</text>
          </view>
          <text class="list-item-arrow">›</text>
        </view>
      </view>

      <!-- ⑤ 备案信息 -->
      <view class="card">
        <text class="card-title">📋 备案信息</text>
        <view class="record-row">
          <text class="record-label">应用名称</text>
          <text class="record-value">AI 节律守护</text>
        </view>
        <view class="record-row">
          <text class="record-label">版本号</text>
          <text class="record-value">v{{ version }}（Build {{ versionCode }}）</text>
        </view>
        <view class="record-row">
          <text class="record-label">ICP 备案号</text>
          <text class="record-value">京ICP备00000000号-1（示例）</text>
        </view>
        <view class="record-row">
          <text class="record-label">公安备案号</text>
          <text class="record-value">京公网安备 00000000000000 号（示例）</text>
        </view>
        <view class="record-row">
          <text class="record-label">软件著作权</text>
          <text class="record-value">2026SR0000000（示例）</text>
        </view>
        <text class="record-tip">备案信息为占位示例，正式上线前请替换为真实编号。</text>
      </view>

      <!-- ⑥ 版权 -->
      <view class="footer">
        <text class="footer-line">{{ COMPANY.copyright }}</text>
        <text class="footer-line">本应用仅供健康参考，不能替代专业医疗诊断</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 关于我们
 * ----------------------------------------------------------------------------
 * 应用信息、团队介绍、联系方式与备案信息占位。
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

/** 从 manifest 读取的版本号（读取失败时用兜底值） */
const version = ref('1.0.0')
const versionCode = ref('100')

const SERVICE_PHONE = '400-000-0000'
const SERVICE_EMAIL = 'support@guardian-demo.com'

/** 公司信息（占位，上线前替换） */
const COMPANY = {
  address: '北京市海淀区中关村大街 1 号 示例大厦 8 层',
  copyright: '© 2026 AI 节律守护团队 保留所有权利'
}

/** 核心功能 */
const FEATURES = [
  { icon: '🌙', title: '无感作息', desc: '自动记录入睡起床' },
  { icon: '💓', title: '健康监测', desc: '心率步数睡眠血氧' },
  { icon: '🆘', title: '紧急求助', desc: '一键 SOS 通知家人' },
  { icon: '🤖', title: 'AI 报告', desc: '每日健康分析建议' }
]

/** 拨打客服电话 */
function callService() {
  uni.showModal({
    title: '联系客服',
    content: `是否拨打客服电话 ${SERVICE_PHONE}？`,
    confirmText: '拨打',
    success: (res) => {
      if (!res.confirm) return
      try {
        uni.makePhoneCall({
          phoneNumber: SERVICE_PHONE.replace(/-/g, ''),
          fail: () => uni.showToast({ title: '当前环境不支持拨号', icon: 'none' })
        })
      } catch {
        uni.showToast({ title: '当前环境不支持拨号', icon: 'none' })
      }
    }
  })
}

/** 复制邮箱 */
function copyEmail() {
  copyText(SERVICE_EMAIL, '邮箱已复制')
}

/** 通用复制 */
function copyText(text: string, tip: string) {
  try {
    uni.setClipboardData({
      data: text,
      success: () => uni.showToast({ title: tip, icon: 'none' })
    })
  } catch {
    uni.showToast({ title: text, icon: 'none' })
  }
}

/** 跳转条款页 */
function goPage(url: string) {
  uni.navigateTo({ url })
}

onLoad(() => {
  try {
    // H5 / 小程序编译后 manifest 信息可能读不到，失败时保留兜底版本号
    const info = uni.getSystemInfoSync() as unknown as { appVersion?: string }
    if (info?.appVersion) version.value = info.appVersion
  } catch {
    version.value = '1.0.0'
  }
})
</script>

<style lang="scss" scoped>
.page-inner {
  padding: 24rpx;
}

/* ---------- 品牌 ---------- */
.brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60rpx 0 48rpx;
}

.logo {
  width: 176rpx;
  height: 176rpx;
  border-radius: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--primary), #a8c4a7);
  box-shadow: 0 12rpx 32rpx rgba(143, 166, 142, 0.28);
}

.logo-icon {
  font-size: 88rpx;
}

.app-name {
  margin-top: 28rpx;
  font-size: 42rpx;
  font-weight: 700;
  color: var(--text-1);
  letter-spacing: 2rpx;
}

.app-slogan {
  margin-top: 10rpx;
  font-size: 26rpx;
  color: var(--text-2);
}

.version-chip {
  margin-top: 20rpx;
  padding: 8rpx 28rpx;
  border-radius: var(--radius-full);
  background: var(--primary-l);
  color: var(--primary-d);
  font-size: 24rpx;
}

/* ---------- 介绍 ---------- */
.intro {
  display: block;
  font-size: 26rpx;
  color: var(--text-2);
  line-height: 1.9;
}

.feature-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 20rpx;
  margin-top: 28rpx;
}

.feature-item {
  width: calc(50% - 10rpx);
  box-sizing: border-box;
  padding: 24rpx;
  border-radius: var(--radius-sm);
  background: var(--surface-2);
  display: flex;
  flex-direction: column;
}

.feature-icon {
  font-size: 40rpx;
}

.feature-title {
  margin-top: 10rpx;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--text-1);
}

.feature-desc {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: var(--text-2);
}

/* ---------- 列表 ---------- */
.item-text {
  display: flex;
  flex-direction: column;
}

.item-title {
  font-size: 30rpx;
  color: var(--text-1);
}

.item-desc {
  margin-top: 4rpx;
  font-size: 22rpx;
  color: var(--text-3);
}

/* ---------- 备案 ---------- */
.record-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
  padding: 20rpx 0;
  border-bottom: 2rpx solid var(--divider);
}

.record-row:last-of-type {
  border-bottom: none;
}

.record-label {
  font-size: 26rpx;
  color: var(--text-2);
  flex-shrink: 0;
}

.record-value {
  flex: 1;
  font-size: 26rpx;
  color: var(--text-1);
  text-align: right;
}

.record-tip {
  display: block;
  margin-top: 16rpx;
  font-size: 22rpx;
  color: var(--text-3);
  line-height: 1.7;
}

/* ---------- 页脚 ---------- */
.footer {
  padding: 40rpx 0 64rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.footer-line {
  font-size: 22rpx;
  color: var(--text-3);
  line-height: 1.8;
}
</style>
