<template>
  <view class="page">
    <scroll-view scroll-y class="doc-scroll">
      <view class="doc">
        <text class="doc-title">声纹授权协议</text>
        <text class="doc-meta">版本生效日期：2026 年 1 月 1 日　|　版本号：V1.0</text>

        <view class="doc-highlight">
          <text class="doc-highlight-text">
            声纹信息属于敏感个人信息。在您使用语音健康监测功能前，请务必仔细阅读本协议，特别是关于采集目的、存储方式、使用范围与撤回授权方式的条款。您勾选同意后，本协议即生效。
          </text>
        </view>

        <view class="doc-section">
          <text class="doc-h">一、什么是声纹信息</text>
          <text class="doc-p">
            1.1 声纹信息是指通过分析您说话时的音调、音色、语速、节奏等声学特征所形成的、可用于识别特定自然人身份的信息。本协议中所称"声纹数据"，还包括您在使用语音监测功能时产生的语音片段及其转写的文本内容。
          </text>
          <text class="doc-p">
            1.2 声纹信息属于《个人信息保护法》规定的敏感个人信息，我们将在具有特定目的和充分必要性的前提下，单独征求您的同意后予以处理。
          </text>
        </view>

        <view class="doc-section">
          <text class="doc-h">二、采集目的</text>
          <text class="doc-p">2.1 情绪与状态识别：通过语音内容与声学特征，分析长辈的情绪状态、语速与清晰度变化，辅助判断是否存在焦虑、低落或身体不适。</text>
          <text class="doc-p">2.2 健康风险提示：识别语音中出现的头晕、胸闷、疼痛等主诉，结合近期健康数据生成提醒建议并同步给授权的家属。</text>
          <text class="doc-p">2.3 语音交互：支持语音唤起打卡、查询天气与健康数据等便捷操作，降低长辈的输入门槛。</text>
          <text class="doc-p">2.4 我们不会将声纹数据用于身份核验以外的商业目的，不会用于广告精准投放，也不会用于训练与本服务无关的模型。</text>
        </view>

        <view class="doc-section">
          <text class="doc-h">三、采集方式与范围</text>
          <text class="doc-p">3.1 仅在您主动按住"录音"按钮或主动使用语音交互功能时采集，<text class="doc-strong">不会在后台静默录音</text>。</text>
          <text class="doc-p">3.2 单次录音最长 60 秒；屏幕会明确显示录音状态与计时，您可以随时松手结束或取消录制。</text>
          <text class="doc-p">3.3 您可以拒绝授权麦克风权限，此时语音监测功能不可用，但不影响其他健康功能的使用。</text>
        </view>

        <view class="doc-section">
          <text class="doc-h">四、存储方式与期限</text>
          <text class="doc-p">4.1 语音片段在完成分析后，默认不在服务端长期留存；转写文本与分析结果与您的账号关联存储，用于生成历史记录。</text>
          <text class="doc-p">4.2 所有声纹数据在传输过程中使用 HTTPS 加密，在存储时采用加密或不可逆摘要方式处理。</text>
          <text class="doc-p">4.3 存储期限为自采集之日起 12 个月，或至您主动删除、注销账号之日（以较早者为准）。期限届满后我们将删除或匿名化处理。</text>
        </view>

        <view class="doc-section">
          <text class="doc-h">五、使用范围与共享</text>
          <text class="doc-p">5.1 声纹数据仅用于本协议第二条约定的目的，由本服务的技术团队在最小必要权限内处理。</text>
          <text class="doc-p">5.2 经您授权绑定的家属可以查看到语音分析的文字结论，但<text class="doc-strong">无法直接回放您的原始录音</text>。您可以在"隐私安全"页面调整数据可见范围。</text>
          <text class="doc-p">5.3 我们不会向任何第三方出售、出租声纹数据；因法律法规或司法、行政机关强制要求提供时，我们会审查请求的合法性并留存记录。</text>
        </view>

        <view class="doc-section">
          <text class="doc-h">六、撤回授权与删除</text>
          <text class="doc-p">6.1 撤回方式一：在"隐私安全"页面关闭麦克风权限，或在系统设置中撤销本应用的录音权限，撤回后本服务将立即停止采集声纹数据。</text>
          <text class="doc-p">6.2 撤回方式二：在"语音监测"页面点击"清空"删除全部历史语音分析记录。</text>
          <text class="doc-p">6.3 撤回方式三：通过客服渠道（400-000-0000 / support@guardian-demo.com）提交书面撤回申请，我们将在 15 个工作日内完成处理并删除相关声纹数据。</text>
          <text class="doc-p">6.4 撤回授权不影响撤回前基于您的同意已进行的处理活动的效力。撤回后，语音健康监测功能将不可用，其他功能不受影响。</text>
        </view>

        <view class="doc-section">
          <text class="doc-h">七、您的权利与我们的责任</text>
          <text class="doc-p">
            您有权查询、复制、更正、删除您的声纹数据，有权要求我们解释处理规则。若您认为我们违反了本协议或相关法律规定，可以向监管部门投诉举报。我们承诺按照《个人信息保护法》《信息安全技术 声纹识别数据安全要求》等法律法规处理您的声纹信息。
          </text>
        </view>

        <view class="doc-section">
          <text class="doc-h">八、协议的更新与联系</text>
          <text class="doc-p">
            本协议如有更新，我们将在应用内以弹窗等显著方式提示，并重新征求您的同意。如有疑问，请联系客服电话 400-000-0000（工作日 9:00-18:00）或客服邮箱 support@guardian-demo.com。
          </text>
        </view>

        <view class="doc-foot">
          <text class="doc-foot-text">AI 节律守护运营团队</text>
          <text class="doc-foot-text">2026 年 1 月 1 日</text>
        </view>

        <!-- 底部留白，避免内容被固定按钮遮挡 -->
        <view class="foot-space"></view>
      </view>
    </scroll-view>

    <!-- 底部同意按钮 -->
    <view class="agree-bar">
      <view class="agree-check" @click="toggleCheck">
        <view class="checkbox" :class="{ checked }">
          <text v-if="checked">✓</text>
        </view>
        <text class="agree-text">我已阅读并同意《声纹授权协议》</text>
      </view>
      <view class="btn-primary agree-btn" :class="{ disabled: !checked }" @click="handleAgree">
        <text>同意并继续</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
/**
 * 声纹授权协议
 * ----------------------------------------------------------------------------
 * 长文本 + 底部"我已阅读并同意"按钮，同意后写入本地授权标记。
 */
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'

/** 本地授权标记的存储键 */
const VOICEPRINT_KEY = 'guardian_voiceprint_agreed'

const checked = ref(false)

/** 切换勾选 */
function toggleCheck() {
  checked.value = !checked.value
}

/** 同意 */
function handleAgree() {
  if (!checked.value) {
    uni.showToast({ title: '请先阅读并勾选同意', icon: 'none' })
    return
  }

  const record = {
    agreed: true,
    agreedAt: new Date().toISOString(),
    version: 'V1.0'
  }
  try {
    uni.setStorageSync(VOICEPRINT_KEY, record)
  } catch {
    // 忽略写入异常
  }

  uni.showToast({ title: '授权成功', icon: 'success' })
  setTimeout(() => {
    uni.navigateBack({
      fail: () => {
        uni.redirectTo({ url: '/pages/settings/index' })
      }
    })
  }, 900)
}

onLoad(() => {
  uni.setNavigationBarTitle({ title: '声纹授权协议' })
  try {
    const saved = uni.getStorageSync(VOICEPRINT_KEY) as { agreed?: boolean } | ''
    if (saved && typeof saved === 'object' && saved.agreed) {
      checked.value = true
    }
  } catch {
    checked.value = false
  }
})
</script>

<style lang="scss" scoped>
.doc-scroll {
  height: calc(100vh - 200rpx);
}

.doc {
  padding: 32rpx 32rpx 32rpx;
  background: var(--bg);
}

.doc-title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: var(--text-1);
  text-align: center;
  line-height: 1.5;
}

.doc-meta {
  display: block;
  margin-top: 16rpx;
  font-size: 22rpx;
  color: var(--text-3);
  text-align: center;
}

.doc-highlight {
  margin-top: 32rpx;
  padding: 24rpx;
  border-radius: var(--radius);
  background: var(--warning-l);
  border-left: 8rpx solid var(--warning);
}

.doc-highlight-text {
  font-size: 26rpx;
  color: var(--text-1);
  line-height: 1.9;
}

.doc-section {
  margin-top: 40rpx;
}

.doc-h {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: var(--primary-d);
  margin-bottom: 16rpx;
}

.doc-p {
  display: block;
  font-size: 27rpx;
  color: var(--text-1);
  line-height: 2;
  margin-bottom: 14rpx;
  text-align: justify;
}

.doc-strong {
  font-weight: 700;
  color: var(--danger);
}

.doc-foot {
  margin-top: 64rpx;
  padding-top: 32rpx;
  border-top: 2rpx solid var(--border);
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.doc-foot-text {
  font-size: 26rpx;
  color: var(--text-2);
  line-height: 1.9;
}

.foot-space {
  height: 40rpx;
}

/* ---------- 底部同意栏 ---------- */
.agree-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 24rpx 32rpx calc(24rpx + env(safe-area-inset-bottom));
  background: var(--surface);
  box-shadow: 0 -4rpx 24rpx rgba(0, 0, 0, 0.06);
}

.agree-check {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.checkbox {
  width: 40rpx;
  height: 40rpx;
  flex-shrink: 0;
  border: 3rpx solid var(--primary);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  color: #ffffff;
}

.checkbox.checked {
  background: var(--primary);
}

.agree-text {
  font-size: 26rpx;
  color: var(--text-2);
}

.agree-btn.disabled {
  opacity: 0.5;
  box-shadow: none;
}
</style>
