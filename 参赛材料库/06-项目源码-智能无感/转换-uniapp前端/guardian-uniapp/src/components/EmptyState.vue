<template>
  <view class="empty">
    <text class="empty-icon">{{ icon }}</text>
    <text class="empty-text">{{ text }}</text>
    <text v-if="desc" class="empty-desc">{{ desc }}</text>
    <view v-if="actionText" class="empty-action" @click="$emit('action')">
      <text>{{ actionText }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 空状态
 */
withDefaults(
  defineProps<{
    icon?: string
    text?: string
    desc?: string
    actionText?: string
  }>(),
  {
    icon: '📭',
    text: '暂无数据',
    desc: '',
    actionText: ''
  }
)

defineEmits<{ action: [] }>()
</script>

<style lang="scss" scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 96rpx 0;
}

.empty-icon {
  font-size: 96rpx;
  opacity: 0.5;
}

.empty-text {
  margin-top: 24rpx;
  font-size: 28rpx;
  color: var(--text-2);
}

.empty-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: var(--text-3);
  text-align: center;
  padding: 0 48rpx;
}

.empty-action {
  margin-top: 40rpx;
  padding: 20rpx 48rpx;
  background: var(--primary);
  color: #ffffff;
  font-size: 28rpx;
  border-radius: var(--radius);
}
</style>
