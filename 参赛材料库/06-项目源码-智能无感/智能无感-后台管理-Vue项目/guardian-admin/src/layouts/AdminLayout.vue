<script setup lang="ts">
import { ArrowDown, Monitor, SwitchButton } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const menus = [
  { path: '/dashboard', label: '数据总览' },
  { path: '/users', label: '用户管理' },
  { path: '/alerts', label: '告警处置' },
  { path: '/feedback', label: '反馈处理' },
  { path: '/devices', label: '设备管理' },
  { path: '/system', label: '系统监控' },
]

const activeMenu = computed(() => route.path)
const pageTitle = computed(() => (route.meta.title as string) || '运营管理后台')
const pageSubtitle = computed(() => (route.meta.subtitle as string) || '')

async function handleLogout() {
  await ElMessageBox.confirm('确定要退出当前管理员账号吗？', '退出登录', {
    confirmButtonText: '退出',
    cancelButtonText: '取消',
    type: 'warning',
  })
  userStore.logout()
  await router.push('/login')
}
</script>

<template>
  <div class="admin-shell">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-icon">🌿</span>
        <div>
          <strong>AI 节律守护</strong>
          <small>运营管理后台</small>
        </div>
      </div>

      <nav class="menu-list" aria-label="后台导航">
        <router-link
          v-for="menu in menus"
          :key="menu.path"
          :to="menu.path"
          class="menu-item"
          :class="{ active: activeMenu === menu.path }"
        >
          <span class="menu-dot" />
          {{ menu.label }}
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="admin-card">
          <div class="admin-avatar">{{ userStore.displayName.slice(0, 1) }}</div>
          <div class="admin-meta">
            <strong>{{ userStore.displayName }}</strong>
            <small>系统管理员</small>
          </div>
        </div>
        <el-button text class="logout-button" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon>
          退出登录
        </el-button>
      </div>
    </aside>

    <section class="main-panel">
      <header class="topbar">
        <div>
          <h1>{{ pageTitle }}</h1>
          <p>{{ pageSubtitle }}</p>
        </div>
        <div class="topbar-admin">
          <el-icon><Monitor /></el-icon>
          <span>{{ userStore.displayName }}</span>
          <el-icon class="caret"><ArrowDown /></el-icon>
        </div>
      </header>

      <main class="page-content">
        <router-view />
      </main>
    </section>
  </div>
</template>
