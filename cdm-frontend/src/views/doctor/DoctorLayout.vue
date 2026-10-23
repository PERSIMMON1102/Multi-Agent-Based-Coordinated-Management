<template>
  <div class="doctor-shell">
    <div class="doctor-shell__glow doctor-shell__glow--left"></div>
    <div class="doctor-shell__glow doctor-shell__glow--right"></div>

    <el-container class="doctor-shell__frame">
      <el-aside :width="asideWidth" class="doctor-shell__aside" :class="{ 'doctor-shell__aside--collapsed': isSidebarCollapsed }">
        <div class="doctor-shell__aside-toolbar">
          <span v-if="!isSidebarCollapsed" class="doctor-shell__aside-toolbar-label">导航</span>
          <el-button class="doctor-shell__aside-toggle" @click="toggleSidebar">
            <el-icon>
              <Expand v-if="isSidebarCollapsed" />
              <Fold v-else />
            </el-icon>
          </el-button>
        </div>

        <div class="doctor-brand">
          <span class="doctor-brand__pill">Doctor Workspace</span>
          <h1>慢病协同管理平台</h1>
        </div>

        <div class="doctor-profile-card">
          <div class="doctor-profile-card__top">
            <el-avatar :size="44" :src="resolveAvatar(userStore.avatar, userStore.role, userStore.username)">
              {{ (userStore.username || '医生').charAt(0) }}
            </el-avatar>
            <div>
              <div class="doctor-profile-card__name">{{ userStore.username || '医生' }}</div>
              <div class="doctor-profile-card__meta">当前工作台已接入患者管理、随访与健康服务</div>
            </div>
          </div>
          <div class="doctor-profile-card__stats">
            <div class="doctor-mini-stat">
              <span>未读通知</span>
              <strong>{{ notificationStore.unreadCount }}</strong>
            </div>
            <div class="doctor-mini-stat">
              <span>连接状态</span>
              <strong>{{ notificationStore.connected ? '在线' : '离线' }}</strong>
            </div>
          </div>
        </div>

        <div class="doctor-menu-section">
          <div class="doctor-menu-section__label">日常工作</div>
          <el-menu :default-active="route.path" router class="doctor-menu">
            <el-menu-item index="/doctor" :title="sidebarTitle('工作台')">
              <el-icon><DataAnalysis /></el-icon>
              <span>工作台</span>
            </el-menu-item>
            <el-menu-item index="/doctor/patients" :title="sidebarTitle('患者管理')">
              <el-icon><User /></el-icon>
              <span>患者管理</span>
            </el-menu-item>
            <el-menu-item index="/doctor/health-record" :title="sidebarTitle('健康数据录入')">
              <el-icon><EditPen /></el-icon>
              <span>健康数据录入</span>
            </el-menu-item>
            <el-menu-item index="/doctor/health-trend" :title="sidebarTitle('健康趋势')">
              <el-icon><TrendCharts /></el-icon>
              <span>健康趋势</span>
            </el-menu-item>
            <el-menu-item index="/doctor/assessments" :title="sidebarTitle('风险评估')">
              <el-icon><Warning /></el-icon>
              <span>风险评估</span>
            </el-menu-item>
            <el-menu-item index="/doctor/follow-ups" :title="sidebarTitle('随访管理')">
              <el-icon><Calendar /></el-icon>
              <span>随访管理</span>
            </el-menu-item>
          </el-menu>
        </div>

        <div class="doctor-menu-section">
          <div class="doctor-menu-section__label">辅助工具</div>
          <el-menu :default-active="route.path" router class="doctor-menu doctor-menu--secondary">
            <el-menu-item index="/doctor/chat" :title="sidebarTitle('AI 健康顾问')">
              <el-icon><ChatDotRound /></el-icon>
              <span>AI 健康顾问</span>
            </el-menu-item>
            <el-menu-item index="/doctor/knowledge-graph" :title="sidebarTitle('知识中心')">
              <el-icon><Share /></el-icon>
              <span>知识中心</span>
            </el-menu-item>
            <el-menu-item index="/doctor/messaging" :title="sidebarTitle('医患沟通')">
              <el-icon><ChatLineSquare /></el-icon>
              <span>医患沟通</span>
            </el-menu-item>
            <el-menu-item index="/doctor/forum" :title="sidebarTitle('社区论坛')">
              <el-icon><Comment /></el-icon>
              <span>社区论坛</span>
            </el-menu-item>
            <el-menu-item index="/doctor/profile" :title="sidebarTitle('个人中心')">
              <el-icon><UserFilled /></el-icon>
              <span>个人中心</span>
            </el-menu-item>
          </el-menu>
        </div>
      </el-aside>

      <el-container class="doctor-shell__main" direction="vertical">
        <el-header
          class="doctor-shell__header"
          :class="{
            'doctor-shell__header--dashboard': isDashboardRoute,
            'doctor-shell__header--dashboard-wide': isDashboardRoute && isSidebarCollapsed
          }"
        >
          <div class="doctor-shell__header-copy">
            <span class="doctor-shell__eyebrow">临床工作台</span>
            <h2>{{ currentPageTitle }}</h2>
          </div>

          <div class="doctor-shell__actions">
            <el-button class="doctor-notification-trigger" @click="showNotifications = true">
              <el-badge :value="notificationStore.unreadCount" :max="99" :hidden="notificationStore.unreadCount === 0">
                <el-icon><Bell /></el-icon>
              </el-badge>
              <span>通知中心</span>
            </el-button>

            <el-tag class="doctor-shell__sync-tag" size="small" :type="notificationStore.connected ? 'success' : 'danger'">
              {{ notificationStore.connected ? '服务已连接' : '连接中断' }}
            </el-tag>

            <el-dropdown @command="handleCommand">
              <el-button class="doctor-account-button">
                <el-avatar :size="24" :src="resolveAvatar(userStore.avatar, userStore.role, userStore.username)">
                  {{ (userStore.username || '医').charAt(0) }}
                </el-avatar>
                <span>{{ userStore.username }}</span>
                <el-icon class="el-icon--right"><ArrowDown /></el-icon>
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>

        <el-main
          class="doctor-shell__content"
          :class="{
            'doctor-shell__content--dashboard': isDashboardRoute,
            'doctor-shell__content--dashboard-wide': isDashboardRoute && isSidebarCollapsed
          }"
        >
          <div
            class="doctor-shell__content-inner"
            :class="{
              'doctor-shell__content-inner--dashboard': isDashboardRoute,
              'doctor-shell__content-inner--dashboard-wide': isDashboardRoute && isSidebarCollapsed
            }"
          >
            <router-view v-slot="{ Component }">
              <keep-alive include="ChatView">
                <component :is="Component" />
              </keep-alive>
            </router-view>
          </div>
        </el-main>
      </el-container>

      <el-drawer v-model="showNotifications" class="doctor-notification-drawer" title="系统通知" size="420px">
        <div class="doctor-notification-toolbar">
          <el-tag size="small" :type="notificationStore.unreadCount > 0 ? 'danger' : 'info'">
            未读 {{ notificationStore.unreadCount }}
          </el-tag>
          <div class="doctor-notification-toolbar__actions">
            <el-button size="small" @click="refresh">刷新</el-button>
            <el-button size="small" type="primary" plain :disabled="notificationStore.unreadCount === 0" @click="markAll">
              全部标为已读
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :disabled="!hasReadNotifications"
              @click="removeRead"
            >
              清空已读
            </el-button>
          </div>
        </div>
        <el-timeline>
          <el-timeline-item
            v-for="(n, i) in notificationStore.notifications"
            :key="n.id || i"
            :type="getNotificationType(n.type)"
            :timestamp="formatTime(n.timestamp)"
          >
            <div class="doctor-notification-card">
              <div class="doctor-notification-card__top">
                <div>
                  <strong>{{ n.title }}</strong>
                  <p class="doctor-notification-card__message">{{ n.message }}</p>
                  <div v-if="hasHistoryAlerts(n)" class="notification-history-tags">
                    <el-tag
                      v-for="(alert, index) in n.historyAlerts"
                      :key="`doctor-notification-history-${n.id || i}-${index}`"
                      size="small"
                      type="warning"
                      effect="plain"
                    >
                      {{ alert }}
                    </el-tag>
                  </div>
                </div>
                <el-tag v-if="!n.read" size="small" type="danger" effect="plain">未读</el-tag>
                <el-button v-else type="success" text size="small" disabled>已读</el-button>
              </div>
              <div class="doctor-notification-card__actions">
                <el-button v-if="!n.read" type="primary" text size="small" @click.stop="markOne(n.id)">标为已读</el-button>
                <el-button v-if="canOpenNotification(n)" type="primary" text size="small" @click.stop="openNotification(n)">查看</el-button>
                <el-button type="danger" text size="small" @click.stop="removeOne(n.id)">删除</el-button>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="notificationStore.notifications.length === 0" description="暂无通知" />
      </el-drawer>
    </el-container>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useNotificationStore } from '../../stores/notification'
import { useUserStore } from '../../stores/user'
import { ElMessage } from '../../utils/element-plus-service'
import { resolveAvatar } from '../../utils/avatar'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()
const notificationStore = useNotificationStore()
const userStore = useUserStore()
const showNotifications = ref(false)
const isSidebarCollapsed = ref(false)

const SIDEBAR_STORAGE_KEY = 'doctor-sidebar-collapsed'

const titleMap = {
  '/doctor': '医生运营驾驶舱',
  '/doctor/patients': '患者管理',
  '/doctor/health-record': '健康数据录入',
  '/doctor/health-trend': '健康趋势',
  '/doctor/assessments': '风险评估',
  '/doctor/follow-ups': '随访管理',
  '/doctor/chat': 'AI 健康顾问',
  '/doctor/knowledge-graph': '知识中心',
  '/doctor/messaging': '医患沟通',
  '/doctor/forum': '社区论坛',
  '/doctor/profile': '个人中心'
}

const currentPageTitle = computed(() => {
  if (route.path.startsWith('/doctor/patients/') && route.path.includes('/reports/')) {
    return '医生报告详情'
  }
  if (route.path.startsWith('/doctor/patients/')) {
    return '患者详情'
  }
  return titleMap[route.path] || '医生工作台'
})

const isDashboardRoute = computed(() => route.path === '/doctor')
const asideWidth = computed(() => (isSidebarCollapsed.value ? '88px' : '252px'))
const hasReadNotifications = computed(() => notificationStore.notifications.some(item => item.read))

onMounted(() => {
  initializeSidebar()
  notificationStore.connect({ scope: 'all' })
})

onUnmounted(() => {
  notificationStore.disconnect()
})

watch(isDashboardRoute, value => {
  const hasPreference = window.localStorage.getItem(SIDEBAR_STORAGE_KEY) != null
  if (!hasPreference) {
    isSidebarCollapsed.value = value
  }
})

function handleCommand(cmd) {
  if (cmd === 'profile') {
    router.push('/doctor/profile')
  } else if (cmd === 'logout') {
    userStore.logout()
    router.replace('/login')
    ElMessage.success('已退出登录')
  }
}

function getNotificationType(type) {
  const map = { ASSESSMENT_RESULT: 'warning', FOLLOW_UP_REMINDER: 'primary', RESOURCE_RECOMMENDATION: 'danger', AI_FOLLOW_UP: 'warning' }
  return map[type] || 'info'
}

function formatTime(ts) {
  return ts ? dayjs(ts).format('MM-DD HH:mm:ss') : ''
}

function hasHistoryAlerts(notification) {
  return Array.isArray(notification?.historyAlerts) && notification.historyAlerts.length > 0
}

function getNotificationTarget(notification) {
  if (!notification) {
    return null
  }
  if (notification.type === 'AI_FOLLOW_UP' && notification.patientId != null) {
    return { name: 'FollowUps', query: { tab: 'smart', patientId: String(notification.patientId) } }
  }
  if (notification.patientId == null) {
    return null
  }
  if (notification.type === 'ASSESSMENT_RESULT') {
    return { name: 'PatientDetail', params: { id: String(notification.patientId) }, query: { tab: 'assessments' } }
  }
  if (notification.type === 'FOLLOW_UP_REMINDER') {
    return { name: 'PatientDetail', params: { id: String(notification.patientId) }, query: { tab: 'followups' } }
  }
  if (notification.type === 'RESOURCE_RECOMMENDATION') {
    return { name: 'PatientDetail', params: { id: String(notification.patientId) }, query: { tab: 'assessments' } }
  }
  return null
}

function canOpenNotification(notification) {
  return getNotificationTarget(notification) != null
}

async function markOne(id) {
  await notificationStore.markRead(id)
}

async function openNotification(notification) {
  const target = getNotificationTarget(notification)
  if (!target) {
    return
  }
  if (!notification.read && notification.id) {
    await markOne(notification.id)
  }
  const resolvedTarget = router.resolve(target)
  if (resolvedTarget.fullPath === route.fullPath) {
    ElMessage.info('当前已经在对应页面，可以直接查看当前内容')
    return
  }
  await router.push(target)
  showNotifications.value = false
}

async function markAll() {
  await notificationStore.markAllRead()
}

async function removeOne(id) {
  await notificationStore.deleteOne(id)
}

async function removeRead() {
  await notificationStore.deleteRead()
}

async function refresh() {
  await notificationStore.refresh()
}

function initializeSidebar() {
  const stored = window.localStorage.getItem(SIDEBAR_STORAGE_KEY)
  if (stored != null) {
    isSidebarCollapsed.value = stored === 'true'
    return
  }
  isSidebarCollapsed.value = isDashboardRoute.value
}

function toggleSidebar() {
  isSidebarCollapsed.value = !isSidebarCollapsed.value
  window.localStorage.setItem(SIDEBAR_STORAGE_KEY, String(isSidebarCollapsed.value))
}

function sidebarTitle(label) {
  return isSidebarCollapsed.value ? label : ''
}
</script>

<style scoped>
.doctor-shell {
  position: relative;
  width: 100%;
  min-width: 0;
  min-height: 100vh;
  padding: var(--app-shell-padding);
  overflow: hidden;
}

.doctor-shell__glow {
  position: absolute;
  border-radius: 999px;
  filter: blur(20px);
  pointer-events: none;
  opacity: 0.8;
}

.doctor-shell__glow--left {
  top: -80px;
  left: -60px;
  width: 320px;
  height: 320px;
  background: rgba(54, 77, 94, 0.55);
}

.doctor-shell__glow--right {
  top: 30px;
  right: -70px;
  width: 300px;
  height: 300px;
  background: rgba(102, 124, 148, 0.28);
}

.doctor-shell__frame {
  position: relative;
  width: 100%;
  max-width: var(--app-shell-max-width);
  margin: 0 auto;
  min-width: 0;
  min-height: calc(100vh - (var(--app-shell-padding) * 2));
  border: 1px solid rgba(27, 40, 52, 0.12);
  border-radius: 30px;
  overflow: hidden;
  background: rgba(239, 242, 244, 0.68);
  box-shadow: 0 28px 64px rgba(17, 28, 38, 0.16);
  backdrop-filter: blur(20px);
}

.doctor-shell__aside {
  padding: 20px 18px;
  background: linear-gradient(180deg, #1e2d38 0%, #263845 100%);
  border-right: 1px solid rgba(255, 255, 255, 0.06);
  color: #f5f7fb;
  transition: width 0.24s ease, padding 0.24s ease;
  overflow: hidden;
}

.doctor-shell__aside-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}

.doctor-shell__aside-toolbar-label {
  color: rgba(220, 230, 236, 0.56);
  font-size: 12px;
  letter-spacing: 0.12em;
}

.doctor-shell__aside-toggle {
  width: 34px;
  min-width: 34px;
  min-height: 34px;
  padding: 0;
  border-radius: 50%;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.06);
  color: #eef7fc;
}

.doctor-shell__aside--collapsed {
  padding-left: 10px;
  padding-right: 10px;
}

.doctor-shell__aside--collapsed .doctor-shell__aside-toolbar {
  justify-content: center;
}

.doctor-shell__aside--collapsed .doctor-brand {
  padding: 12px 10px;
  text-align: center;
}

.doctor-shell__aside--collapsed .doctor-brand__pill,
.doctor-shell__aside--collapsed .doctor-brand h1,
.doctor-shell__aside--collapsed .doctor-brand p,
.doctor-shell__aside--collapsed .doctor-profile-card__top > div,
.doctor-shell__aside--collapsed .doctor-profile-card__stats,
.doctor-shell__aside--collapsed .doctor-menu-section__label,
.doctor-shell__aside--collapsed .doctor-menu :deep(.el-menu-item span),
.doctor-shell__aside--collapsed .doctor-shell__aside-toolbar-label {
  display: none;
}

.doctor-shell__aside--collapsed .doctor-profile-card {
  padding: 12px 8px;
}

.doctor-shell__aside--collapsed .doctor-profile-card__top {
  justify-content: center;
}

.doctor-shell__aside--collapsed .doctor-menu-section {
  margin-bottom: 12px;
}

.doctor-shell__aside--collapsed .doctor-menu :deep(.el-menu-item) {
  justify-content: center;
  padding: 0;
}

.doctor-shell__aside--collapsed .doctor-menu :deep(.el-menu-item .el-icon) {
  margin-right: 0;
  font-size: 18px;
}

.doctor-brand {
  margin-bottom: 18px;
  padding: 18px;
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.08), rgba(99, 149, 181, 0.18));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.doctor-brand__pill {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.doctor-brand h1 {
  margin: 14px 0 8px;
  font-size: 24px;
  line-height: 1.2;
}

.doctor-brand p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(234, 241, 247, 0.76);
}

.doctor-profile-card {
  margin-bottom: 18px;
  padding: 16px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.doctor-profile-card__top {
  display: flex;
  align-items: center;
  gap: 12px;
}

.doctor-profile-card__avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.12);
}

.doctor-profile-card__name {
  font-size: 15px;
  font-weight: 700;
  color: #fff;
}

.doctor-profile-card__meta {
  margin-top: 4px;
  font-size: 12px;
  color: rgba(226, 235, 241, 0.7);
}

.doctor-profile-card__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.doctor-mini-stat {
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(10, 19, 27, 0.22);
}

.doctor-mini-stat span {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  color: rgba(226, 235, 241, 0.66);
}

.doctor-mini-stat strong {
  font-size: 14px;
  color: #fff;
}

.doctor-menu-section {
  margin-bottom: 16px;
}

.doctor-menu-section__label {
  margin: 0 10px 10px;
  font-size: 12px;
  letter-spacing: 0.08em;
  font-weight: 700;
  color: rgba(220, 230, 236, 0.48);
}

.doctor-menu {
  border: 0;
  background: transparent;
}

.doctor-menu :deep(.el-menu-item) {
  height: 48px;
  margin-bottom: 6px;
  border-radius: 16px;
  color: rgba(234, 241, 247, 0.82);
  background: transparent;
}

.doctor-menu :deep(.el-menu-item .el-icon) {
  color: rgba(226, 235, 241, 0.64);
}

.doctor-menu :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.08);
}

.doctor-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(125, 177, 207, 0.2), rgba(125, 177, 207, 0.08));
  color: #bde2ff;
}

.doctor-menu :deep(.el-menu-item.is-active .el-icon) {
  color: #bde2ff;
}

.doctor-shell__main {
  min-width: 0;
  background: linear-gradient(180deg, rgba(249, 250, 251, 0.7) 0%, rgba(242, 245, 247, 0.82) 100%);
}

.doctor-shell__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 96px;
  padding: 22px var(--app-content-padding-x) 16px;
  border-bottom: 1px solid rgba(31, 51, 64, 0.08);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.82) 0%, rgba(244, 246, 248, 0.72) 100%);
}

.doctor-shell__eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: rgba(43, 108, 143, 0.72);
}

.doctor-shell__header-copy h2 {
  margin: 8px 0 6px;
  font-size: 27px;
  line-height: 1.1;
  color: #1f3340;
}

.doctor-shell__header-copy p {
  margin: 0;
  color: #66757f;
  line-height: 1.7;
}

.doctor-shell__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doctor-notification-trigger,
.doctor-account-button {
  min-height: 42px;
  border-radius: 999px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  background: rgba(255, 255, 255, 0.78);
  color: #1f3340;
  box-shadow: 0 10px 24px rgba(17, 28, 38, 0.08);
}

.doctor-notification-trigger {
  padding: 0 16px;
}

.doctor-account-button {
  padding: 0 12px;
}

.doctor-shell__content {
  min-width: 0;
  padding: var(--app-content-padding-y) var(--app-content-padding-x);
  background: transparent;
  overflow-x: hidden;
}

.doctor-shell__content-inner {
  width: 100%;
  max-width: var(--app-content-max-width);
  margin: 0 auto;
}

.doctor-shell__header--dashboard {
  position: relative;
  overflow: hidden;
  border-bottom-color: rgba(70, 118, 164, 0.16);
  background:
    radial-gradient(circle at 12% 10%, rgba(55, 186, 255, 0.16), transparent 18%),
    linear-gradient(180deg, rgba(10, 22, 39, 0.94) 0%, rgba(11, 18, 31, 0.9) 100%);
}

.doctor-shell__header--dashboard .doctor-shell__eyebrow {
  color: rgba(117, 242, 255, 0.78);
}

.doctor-shell__header--dashboard .doctor-shell__header-copy h2 {
  color: #eefaff;
}

.doctor-shell__header--dashboard .doctor-shell__header-copy p {
  color: rgba(199, 223, 245, 0.7);
}

.doctor-shell__content--dashboard {
  padding-top: 20px;
  background:
    radial-gradient(circle at top, rgba(42, 143, 255, 0.12), transparent 24%),
    linear-gradient(180deg, rgba(4, 11, 22, 0.98) 0%, rgba(8, 15, 28, 0.98) 100%);
}

.doctor-shell__content-inner--dashboard {
  max-width: none;
}

.doctor-shell__header--dashboard-wide {
  padding-left: calc(var(--app-content-padding-x) + 12px);
  padding-right: calc(var(--app-content-padding-x) + 12px);
}

.doctor-shell__content--dashboard-wide {
  padding-left: calc(var(--app-content-padding-x) + 12px);
  padding-right: calc(var(--app-content-padding-x) + 12px);
}

.doctor-shell__content-inner--dashboard-wide {
  max-width: none;
}

.doctor-notification-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.doctor-notification-toolbar__actions {
  display: flex;
  gap: 8px;
}

.doctor-notification-card {
  padding: 14px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 10px 24px rgba(17, 28, 38, 0.06);
}

.doctor-notification-card__top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.doctor-notification-card__message {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #5f6f78;
}

.doctor-notification-card__actions {
  margin-top: 8px;
}

.notification-history-tags {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

@media (max-width: 1100px) {
  .doctor-shell {
    padding: 12px;
  }

  .doctor-shell__frame {
    min-height: calc(100vh - 24px);
  }

  .doctor-shell__header {
    padding: 20px 22px 16px;
  }
}

@media (max-width: 960px) {
  .doctor-shell__frame {
    display: block;
  }

  .doctor-shell__aside {
    width: auto !important;
    padding-left: 18px;
    padding-right: 18px;
    border-right: 0;
    border-bottom: 1px solid rgba(31, 51, 64, 0.08);
  }

  .doctor-shell__aside--collapsed .doctor-brand__pill,
  .doctor-shell__aside--collapsed .doctor-brand h1,
  .doctor-shell__aside--collapsed .doctor-brand p,
  .doctor-shell__aside--collapsed .doctor-profile-card__top > div,
  .doctor-shell__aside--collapsed .doctor-profile-card__stats,
  .doctor-shell__aside--collapsed .doctor-menu-section__label,
  .doctor-shell__aside--collapsed .doctor-menu :deep(.el-menu-item span),
  .doctor-shell__aside--collapsed .doctor-shell__aside-toolbar-label {
    display: revert;
  }

  .doctor-shell__aside--collapsed .doctor-menu :deep(.el-menu-item) {
    justify-content: flex-start;
    padding-left: 20px;
    padding-right: 20px;
  }

  .doctor-shell__aside--collapsed .doctor-menu :deep(.el-menu-item .el-icon) {
    margin-right: 8px;
  }

  .doctor-shell__aside--collapsed .doctor-profile-card__top,
  .doctor-shell__aside--collapsed .doctor-shell__aside-toolbar {
    justify-content: space-between;
  }

  .doctor-shell__aside--collapsed .doctor-profile-card,
  .doctor-shell__aside--collapsed .doctor-brand {
    padding-left: 16px;
    padding-right: 16px;
    text-align: left;
  }

  .doctor-shell__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .doctor-shell__actions {
    width: 100%;
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .doctor-shell__header-copy h2 {
    font-size: 24px;
  }

  .doctor-profile-card__stats {
    grid-template-columns: 1fr;
  }

  .doctor-notification-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .doctor-notification-toolbar__actions {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
