<template>
  <div class="patient-shell">
    <div class="patient-shell__glow patient-shell__glow--left"></div>
    <div class="patient-shell__glow patient-shell__glow--right"></div>

    <el-container class="patient-shell__frame">
      <el-aside width="248px" class="patient-shell__aside">
        <div class="patient-brand">
          <span class="patient-brand__pill">慢病协同管理</span>
          <h1>我的健康工作台</h1>
          <p>把评估、随访、宣教与日常管理放在同一条健康旅程里。</p>
        </div>

        <div class="patient-profile-card">
          <div class="patient-profile-card__top">
            <el-avatar :size="44" :src="resolveAvatar(userStore.avatar, userStore.role, userStore.username)">
              {{ (userStore.username || '患者').charAt(0) }}
            </el-avatar>
            <div>
              <div class="patient-profile-card__name">{{ userStore.username || '患者' }}</div>
              <div class="patient-profile-card__meta">今天是 {{ todayLabel }}</div>
            </div>
          </div>
          <div class="patient-profile-card__stats">
            <div class="patient-mini-stat">
              <span class="patient-mini-stat__label">未读通知</span>
              <strong>{{ patientUnreadCount }}</strong>
            </div>
            <div class="patient-mini-stat">
              <span class="patient-mini-stat__label">同步状态</span>
              <strong>{{ notificationStore.connected ? '在线' : '同步中' }}</strong>
            </div>
          </div>
        </div>

        <div class="patient-menu-section">
          <div class="patient-menu-section__label">核心服务</div>
          <el-menu :default-active="route.path" router class="patient-menu">
            <el-menu-item index="/patient">
              <el-icon><House /></el-icon>
              <span>首页</span>
            </el-menu-item>
            <el-menu-item index="/patient/daily-record">
              <el-icon><EditPen /></el-icon>
              <span>每日健康数据</span>
            </el-menu-item>
            <el-menu-item index="/patient/reports">
              <el-icon><Document /></el-icon>
              <span>健康报告</span>
            </el-menu-item>
            <el-menu-item index="/patient/reminders">
              <el-icon><Bell /></el-icon>
              <span>随访提醒</span>
            </el-menu-item>
            <el-menu-item index="/patient/health-trend">
              <el-icon><TrendCharts /></el-icon>
              <span>健康趋势</span>
            </el-menu-item>
            <el-menu-item index="/patient/chat">
              <el-icon><ChatDotRound /></el-icon>
              <span>健康顾问</span>
            </el-menu-item>
            <el-menu-item index="/patient/education">
              <el-icon><Reading /></el-icon>
              <span>健康宣教</span>
            </el-menu-item>
          </el-menu>
        </div>

        <div class="patient-menu-section">
          <div class="patient-menu-section__label">互动与设置</div>
          <el-menu :default-active="route.path" router class="patient-menu patient-menu--secondary">
            <el-menu-item index="/patient/videos">
              <el-icon><VideoPlay /></el-icon>
              <span>健康视频</span>
            </el-menu-item>
            <el-menu-item index="/patient/messaging">
              <el-icon><ChatLineSquare /></el-icon>
              <span>医患沟通</span>
            </el-menu-item>
            <el-menu-item index="/patient/forum">
              <el-icon><Comment /></el-icon>
              <span>社区论坛</span>
            </el-menu-item>
            <el-menu-item index="/patient/profile">
              <el-icon><UserFilled /></el-icon>
              <span>个人中心</span>
            </el-menu-item>
          </el-menu>
        </div>
      </el-aside>

      <el-container class="patient-shell__main" direction="vertical">
        <el-header class="patient-shell__header">
          <div class="patient-shell__header-copy">
            <span class="patient-shell__eyebrow">个人健康工作台</span>
            <h2>欢迎回来</h2>
            <p>继续查看风险评估、随访提醒和最新健康建议。</p>
          </div>

          <div class="patient-shell__actions">
            <el-button class="patient-notification-trigger" @click="showNotif = true">
              <el-badge :value="patientUnreadCount" :max="99" :hidden="patientUnreadCount === 0">
                <el-icon><Bell /></el-icon>
              </el-badge>
              <span>通知中心</span>
            </el-button>

            <el-tag class="patient-shell__sync-tag" size="small" :type="notificationStore.connected ? 'success' : 'info'">
              {{ notificationStore.connected ? '消息已同步' : '同步中' }}
            </el-tag>

            <el-dropdown @command="handleCommand">
              <el-button class="patient-account-button">
                <el-avatar :size="24" :src="resolveAvatar(userStore.avatar, userStore.role, userStore.username)">
                  {{ (userStore.username || '患').charAt(0) }}
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

        <el-main class="patient-shell__content">
          <div class="patient-shell__content-inner">
            <router-view v-slot="{ Component }">
              <keep-alive include="ChatView">
                <component :is="Component" />
              </keep-alive>
            </router-view>
          </div>
        </el-main>
      </el-container>
    </el-container>

    <el-drawer v-model="showNotif" class="patient-notification-drawer" title="消息通知" size="420px">
      <div class="patient-notification-toolbar">
        <el-tag size="small" :type="notificationStore.connected ? 'success' : 'info'">
          {{ notificationStore.connected ? '消息已同步' : '同步中' }}
        </el-tag>
        <div class="patient-notification-toolbar__actions">
          <el-button size="small" @click="refreshNotifications">刷新</el-button>
          <el-button
            size="small"
            type="primary"
            plain
            :disabled="patientUnreadCount === 0"
            @click="markAllNotifications"
          >
            全部标为已读
          </el-button>
        </div>
      </div>

      <el-segmented v-model="notificationFilter" :options="filterOptions" class="patient-notification-filter" />

      <el-timeline>
        <el-timeline-item
          v-for="(n, i) in filteredNotifications"
          :key="n.id || i"
          :timestamp="formatTime(n.timestamp)"
          :type="getNotificationType(n.type)"
        >
          <div class="patient-notification-card">
            <div class="patient-notification-card__top">
              <div>
                <strong>{{ n.title }}</strong>
                <p class="patient-notification-card__message">{{ n.message }}</p>
                <div v-if="hasHistoryAlerts(n)" class="notification-history-tags">
                  <el-tag
                    v-for="(alert, index) in n.historyAlerts"
                    :key="`patient-notification-history-${n.id || i}-${index}`"
                    size="small"
                    type="warning"
                    effect="plain"
                  >
                    {{ alert }}
                  </el-tag>
                </div>
              </div>
              <el-tag v-if="!n.read" size="small" type="danger" effect="plain">未读</el-tag>
              <el-tag v-else size="small" type="success" effect="plain">已读</el-tag>
            </div>
            <div class="patient-notification-card__actions">
              <el-button v-if="!n.read" type="primary" text size="small" @click.stop="markNotification(n.id)">标为已读</el-button>
              <el-button v-if="canOpenNotification(n)" type="primary" text size="small" @click.stop="openNotification(n)">查看</el-button>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="filteredNotifications.length === 0" :description="emptyDescription" />
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
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
const showNotif = ref(false)
const notificationFilter = ref('unread')
const visibleNotificationTypes = new Set(['FOLLOW_UP_REMINDER', 'AI_FOLLOW_UP', 'ASSESSMENT_RESULT'])

const filterOptions = [
  { label: '未读', value: 'unread' },
  { label: '全部', value: 'all' },
  { label: '已读', value: 'read' }
]

const todayLabel = computed(() => dayjs().format('YYYY年M月D日'))

const patientNotifications = computed(() => {
  return notificationStore.notifications.filter(item => visibleNotificationTypes.has(item.type))
})

const patientUnreadCount = computed(() => {
  return patientNotifications.value.filter(item => !item.read).length
})

const filteredNotifications = computed(() => {
  if (notificationFilter.value === 'unread') {
    return patientNotifications.value.filter(item => !item.read)
  }
  if (notificationFilter.value === 'read') {
    return patientNotifications.value.filter(item => item.read)
  }
  return patientNotifications.value
})

const emptyDescription = computed(() => {
  if (notificationFilter.value === 'unread') return '暂无未读通知'
  if (notificationFilter.value === 'read') return '暂无已读通知'
  return '暂无通知'
})

onMounted(() => {
  notificationStore.connect({ scope: 'patient', patientId: userStore.patientId })
})

onUnmounted(() => {
  notificationStore.disconnect()
})

function handleCommand(cmd) {
  if (cmd === 'profile') {
    router.push('/patient/profile')
  } else if (cmd === 'logout') {
    userStore.logout()
    router.replace('/login')
    ElMessage.success('已退出登录')
  }
}

function formatTime(ts) {
  return ts ? dayjs(ts).format('MM-DD HH:mm') : ''
}

function hasHistoryAlerts(notification) {
  return Array.isArray(notification?.historyAlerts) && notification.historyAlerts.length > 0
}

function getNotificationTarget(notification) {
  if (!notification) {
    return null
  }
  if (notification.type === 'ASSESSMENT_RESULT') {
    return { name: 'PatientReports' }
  }
  if (notification.type === 'FOLLOW_UP_REMINDER' || notification.type === 'AI_FOLLOW_UP') {
    return { name: 'PatientReminders' }
  }
  return null
}

function canOpenNotification(notification) {
  return getNotificationTarget(notification) != null
}

function getNotificationType(type) {
  const map = { FOLLOW_UP_REMINDER: 'primary', AI_FOLLOW_UP: 'warning', ASSESSMENT_RESULT: 'danger' }
  return map[type] || 'info'
}

async function markNotification(id) {
  await notificationStore.markRead(id)
}

async function openNotification(notification) {
  const target = getNotificationTarget(notification)
  if (!target) {
    return
  }
  if (!notification.read && notification.id) {
    await markNotification(notification.id)
  }
  const resolvedTarget = router.resolve(target)
  if (resolvedTarget.fullPath === route.fullPath) {
    ElMessage.info('当前已经在对应页面，可以直接查看当前内容')
    return
  }
  await router.push(target)
  showNotif.value = false
}

async function markAllNotifications() {
  await notificationStore.markAllRead(userStore.patientId)
}

async function refreshNotifications() {
  await notificationStore.refresh()
}
</script>

<style scoped>
.patient-shell {
  position: relative;
  width: 100%;
  min-width: 0;
  min-height: 100vh;
  padding: var(--app-shell-padding);
  overflow: hidden;
}

.patient-shell__glow {
  position: absolute;
  border-radius: 999px;
  filter: blur(18px);
  opacity: 0.75;
  pointer-events: none;
}

.patient-shell__glow--left {
  top: -60px;
  left: -40px;
  width: 280px;
  height: 280px;
  background: rgba(189, 217, 204, 0.8);
}

.patient-shell__glow--right {
  right: -40px;
  top: 40px;
  width: 260px;
  height: 260px;
  background: rgba(231, 215, 196, 0.78);
}

.patient-shell__frame {
  position: relative;
  width: 100%;
  max-width: var(--app-shell-max-width);
  margin: 0 auto;
  min-width: 0;
  min-height: calc(100vh - (var(--app-shell-padding) * 2));
  border: 1px solid var(--patient-border);
  border-radius: 30px;
  background: rgba(250, 248, 243, 0.72);
  box-shadow: var(--patient-shadow);
  backdrop-filter: blur(24px);
  overflow: hidden;
}

.patient-shell__aside {
  padding: 22px 18px;
  background:
    linear-gradient(180deg, rgba(255, 252, 246, 0.9) 0%, rgba(246, 249, 246, 0.9) 100%);
  border-right: 1px solid rgba(38, 64, 75, 0.08);
}

.patient-brand {
  margin-bottom: 18px;
  padding: 18px;
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(45, 94, 119, 0.94), rgba(79, 126, 142, 0.88));
  color: #fff;
  box-shadow: 0 20px 42px rgba(40, 74, 92, 0.25);
}

.patient-brand__pill {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.patient-brand h1 {
  margin: 14px 0 8px;
  font-size: 24px;
  line-height: 1.2;
}

.patient-brand p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(255, 255, 255, 0.8);
}

.patient-profile-card {
  margin-bottom: 18px;
  padding: 16px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  border-radius: 22px;
  background: var(--patient-surface-strong);
  box-shadow: var(--patient-shadow-soft);
}

.patient-profile-card__top {
  display: flex;
  align-items: center;
  gap: 12px;
}

.patient-profile-card__avatar-fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(43, 108, 143, 0.14), rgba(43, 108, 143, 0.3));
  color: var(--patient-primary);
}

.patient-profile-card__name {
  font-size: 15px;
  font-weight: 700;
  color: var(--patient-ink);
}

.patient-profile-card__meta {
  margin-top: 4px;
  font-size: 12px;
  color: var(--patient-muted);
}

.patient-profile-card__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.patient-mini-stat {
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(247, 243, 236, 0.92);
}

.patient-mini-stat__label {
  display: block;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--patient-muted);
}

.patient-mini-stat strong {
  font-size: 14px;
  color: var(--patient-ink);
}

.patient-menu-section {
  margin-bottom: 16px;
}

.patient-menu-section__label {
  margin: 0 10px 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: rgba(31, 51, 64, 0.5);
}

.patient-menu {
  border: 0;
  background: transparent;
}

.patient-menu :deep(.el-menu-item) {
  height: 48px;
  margin-bottom: 6px;
  border-radius: 16px;
  color: var(--patient-ink);
}

.patient-menu :deep(.el-menu-item .el-icon) {
  color: rgba(31, 51, 64, 0.58);
}

.patient-menu :deep(.el-menu-item:hover) {
  background: rgba(43, 108, 143, 0.08);
}

.patient-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, rgba(43, 108, 143, 0.14), rgba(43, 108, 143, 0.05));
  color: var(--patient-primary);
}

.patient-menu :deep(.el-menu-item.is-active .el-icon) {
  color: var(--patient-primary);
}

.patient-shell__main {
  min-width: 0;
  background: linear-gradient(180deg, rgba(251, 250, 246, 0.4) 0%, rgba(255, 255, 255, 0.55) 100%);
}

.patient-shell__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  min-height: 96px;
  padding: 22px var(--app-content-padding-x) 16px;
  border-bottom: 1px solid rgba(31, 51, 64, 0.06);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.84) 0%, rgba(255, 252, 246, 0.6) 100%);
}

.patient-shell__header-copy h2 {
  margin: 8px 0 6px;
  font-size: 27px;
  line-height: 1.1;
  color: var(--patient-ink);
}

.patient-shell__header-copy p {
  margin: 0;
  font-size: 14px;
  color: var(--patient-muted);
}

.patient-shell__eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.1em;
  color: rgba(43, 108, 143, 0.72);
}

.patient-shell__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.patient-notification-trigger,
.patient-account-button {
  min-height: 42px;
  border-radius: 999px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  background: rgba(255, 255, 255, 0.74);
  color: var(--patient-ink);
  box-shadow: 0 10px 24px rgba(35, 52, 62, 0.08);
}

.patient-notification-trigger {
  padding: 0 16px;
}

.patient-account-button {
  padding: 0 12px;
}

.patient-shell__sync-tag {
  border-radius: 999px;
}

.patient-shell__content {
  min-width: 0;
  padding: var(--app-content-padding-y) var(--app-content-padding-x);
  background: transparent;
  overflow-x: hidden;
}

.patient-shell__content-inner {
  width: 100%;
  max-width: var(--app-content-max-width);
  margin: 0 auto;
}

.patient-notification-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.patient-notification-toolbar__actions {
  display: flex;
  gap: 8px;
}

.patient-notification-filter {
  margin-bottom: 14px;
}

.patient-notification-card {
  padding: 14px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 10px 24px rgba(35, 52, 62, 0.06);
}

.patient-notification-card__top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.patient-notification-card__message {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #5f6f78;
}

.patient-notification-card__actions {
  margin-top: 8px;
}

.notification-history-tags {
  margin-top: 8px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

@media (max-width: 1100px) {
  .patient-shell {
    padding: 12px;
  }

  .patient-shell__frame {
    min-height: calc(100vh - 24px);
  }

  .patient-shell__header {
    padding: 20px 22px 16px;
  }
}

@media (max-width: 960px) {
  .patient-shell__frame {
    display: block;
  }

  .patient-shell__aside {
    width: auto !important;
    border-right: 0;
    border-bottom: 1px solid rgba(31, 51, 64, 0.08);
  }

  .patient-shell__header {
    flex-direction: column;
    align-items: flex-start;
  }

  .patient-shell__actions {
    width: 100%;
    flex-wrap: wrap;
  }
}

@media (max-width: 640px) {
  .patient-shell__header-copy h2 {
    font-size: 24px;
  }

  .patient-profile-card__stats {
    grid-template-columns: 1fr;
  }

  .patient-notification-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .patient-notification-toolbar__actions {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
