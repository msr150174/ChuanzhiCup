import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/Login.vue'),
    meta: { public: true, title: '管理员登录' },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '数据总览', subtitle: '查看用户、告警与今日守护概况' },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/Users.vue'),
        meta: { title: '用户管理', subtitle: '维护用户身份、角色与账号状态' },
      },
      {
        path: 'alerts',
        name: 'alerts',
        component: () => import('@/views/Alerts.vue'),
        meta: { title: '告警处置', subtitle: '及时跟进每一条健康风险提醒' },
      },
      {
        path: 'feedback',
        name: 'feedback',
        component: () => import('@/views/Feedback.vue'),
        meta: { title: '反馈处理', subtitle: '倾听用户建议并完成回复闭环' },
      },
      {
        path: 'devices',
        name: 'devices',
        component: () => import('@/views/Devices.vue'),
        meta: { title: '设备管理', subtitle: '查看无感设备绑定与在线状态' },
      },
      {
        path: 'system',
        name: 'system',
        component: () => import('@/views/System.vue'),
        meta: { title: '系统监控', subtitle: '关注数据库、缓存与运行时健康状态' },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  const userStore = useUserStore()

  if (to.meta.public) {
    if (to.path === '/login' && userStore.isAdmin) {
      return '/dashboard'
    }
    return true
  }

  if (!userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  if (!userStore.isAdmin) {
    userStore.logout()
    return '/login'
  }

  return true
})

export default router
