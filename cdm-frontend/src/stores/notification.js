import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { notificationApi } from '../api'
import SockJS from 'sockjs-client/dist/sockjs'
import { Client } from '@stomp/stompjs'
import { useUserStore } from './user'

export const useNotificationStore = defineStore('notification', () => {
  const userStore = useUserStore()
  const notifications = ref([])
  const connected = ref(false)
  const scope = ref('all')
  const currentPatientId = ref(null)
  let stompClient = null

  const unreadCount = computed(() => notifications.value.filter(n => !n.read).length)

  function normalizeNotification(notification) {
    return {
      id: notification.id,
      type: notification.type,
      patientId: notification.patientId ?? null,
      title: notification.title,
      message: notification.message,
      historyAlerts: Array.isArray(notification.historyAlerts) ? notification.historyAlerts : [],
      read: Boolean(notification.read),
      timestamp: new Date(notification.createdAt || notification.timestamp || Date.now()).getTime()
    }
  }

  function upsertNotification(notification) {
    const normalized = normalizeNotification(notification)
    if (scope.value === 'patient' && currentPatientId.value != null && normalized.patientId !== currentPatientId.value) {
      return
    }

    notifications.value = [
      normalized,
      ...notifications.value.filter(item => item.id !== normalized.id)
    ].slice(0, 100)
  }

  async function loadPersistedNotifications() {
    try {
      const list = scope.value === 'patient' && currentPatientId.value != null
        ? await notificationApi.getByPatient(currentPatientId.value)
        : await notificationApi.getAll()
      notifications.value = list.map(normalizeNotification)
    } catch (e) {
      notifications.value = []
    }
  }

  async function connect(options = {}) {
    scope.value = options.scope || scope.value || 'all'
    currentPatientId.value = scope.value === 'patient'
      ? (options.patientId ?? currentPatientId.value)
      : null

    await loadPersistedNotifications()

    if (stompClient) {
      await stompClient.deactivate()
      stompClient = null
    }
    connected.value = false

    stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws/notifications'),
      connectHeaders: userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {},
      reconnectDelay: 5000,
      onConnect: () => {
        connected.value = true
        if (scope.value === 'patient' && currentPatientId.value != null) {
          stompClient.subscribe(`/topic/patient/${currentPatientId.value}`, (message) => {
            upsertNotification(JSON.parse(message.body))
          })
          return
        }

        stompClient.subscribe('/topic/notifications', (message) => {
          upsertNotification(JSON.parse(message.body))
        })
      },
      onDisconnect: () => {
        connected.value = false
      },
      onStompError: () => {},
      onWebSocketError: () => {}
  })
  stompClient.activate()
  }

  function disconnect() {
    if (stompClient) {
      stompClient.deactivate()
      stompClient = null
    }
    connected.value = false
  }

  function clearNotifications() {
    notifications.value = []
  }

  async function refresh() {
    await loadPersistedNotifications()
  }

  async function markRead(id) {
    try {
      await notificationApi.markRead(id)
      const n = notifications.value.find(n => n.id === id)
      if (n) n.read = true
    } catch (e) { /* ignore */ }
  }

  async function markAllRead(patientId) {
    try {
      if (scope.value === 'patient') {
        const targetPatientId = patientId ?? currentPatientId.value
        if (targetPatientId == null) return
        await notificationApi.markAllRead(targetPatientId)
      } else {
        await notificationApi.markAllGlobal()
      }
      notifications.value.forEach(n => { n.read = true })
    } catch (e) { /* ignore */ }
  }

  async function deleteOne(id) {
    try {
      await notificationApi.deleteOne(id)
      notifications.value = notifications.value.filter(n => n.id !== id)
    } catch (e) { /* ignore */ }
  }

  async function deleteRead(patientId) {
    try {
      if (scope.value === 'patient') {
        const targetPatientId = patientId ?? currentPatientId.value
        if (targetPatientId == null) return
        await notificationApi.deleteRead(targetPatientId)
        notifications.value = notifications.value.filter(n => !n.read || n.patientId !== targetPatientId)
      } else {
        await notificationApi.deleteReadGlobal()
        notifications.value = notifications.value.filter(n => !n.read)
      }
    } catch (e) { /* ignore */ }
  }

  return {
    notifications,
    connected,
    scope,
    currentPatientId,
    unreadCount,
    connect,
    refresh,
    markRead,
    markAllRead,
    deleteOne,
    deleteRead,
    disconnect,
    clearNotifications
  }
})
