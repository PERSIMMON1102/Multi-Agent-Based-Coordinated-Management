<template>
  <div class="messaging-page" :class="themeClass">
    <section class="messaging-hero">
      <div class="messaging-hero__content">
        <span class="messaging-hero__eyebrow">{{ userStore.isDoctor ? 'Care Communication' : 'Medical Support' }}</span>
        <h1>{{ pageTitle }}</h1>
        <p>{{ pageSubtitle }}</p>
        <div class="messaging-hero__chips">
          <el-tag effect="plain" round>{{ roleLabel }}</el-tag>
          <el-tag effect="plain" round type="info">联系人 {{ contacts.length }}</el-tag>
          <el-tag effect="plain" round type="warning">未读 {{ unreadCount }}</el-tag>
          <el-tag effect="plain" round type="success">当前会话 {{ selectedContactName }}</el-tag>
        </div>
      </div>
    </section>

    <section class="messaging-stat-grid">
      <article class="messaging-stat-card">
        <span>联系人总数</span>
        <strong>{{ contacts.length }}</strong>
        <p>{{ userStore.isDoctor ? '当前显示你负责管理的患者联系人。' : '当前显示与你沟通的医生联系人。' }}</p>
      </article>
      <article class="messaging-stat-card">
        <span>当前会话消息</span>
        <strong>{{ conversation.length }}</strong>
        <p>{{ selectedContact ? '当前选中联系人会话已载入。' : '请选择联系人开始查看消息。' }}</p>
      </article>
      <article class="messaging-stat-card">
        <span>未读消息</span>
        <strong>{{ unreadCount }}</strong>
        <p>切换联系人后会自动清空该联系人的未读计数。</p>
      </article>
      <article class="messaging-stat-card">
        <span>当前联系人</span>
        <strong>{{ selectedContactName }}</strong>
        <p>{{ selectedContact ? '可直接发送文本消息并实时查看回复。' : '等待你选择一个联系人。' }}</p>
      </article>
    </section>

    <section class="messaging-workspace">
      <aside class="contact-rail">
        <div class="contact-rail__head">
          <div>
            <span class="contact-rail__eyebrow">Contact List</span>
            <h2>{{ userStore.isDoctor ? '我的患者' : '我的医生' }}</h2>
          </div>
        </div>

        <el-input v-model="searchText" placeholder="搜索联系人" clearable size="small" class="contact-rail__search" />

        <el-scrollbar height="100%">
          <el-empty v-if="filteredContacts.length === 0" description="暂无联系人" :image-size="72" />
          <div
            v-for="c in filteredContacts"
            :key="c.id"
            :class="['contact-item', { active: selectedContact?.id === c.id }]"
            @click="selectContact(c)"
          >
            <el-avatar :size="40" :src="resolveAvatar(c.avatar, c.role, c.name || c.id)" :class="['contact-item__avatar', c.role === 'doctor' ? 'contact-item__avatar--doctor' : 'contact-item__avatar--patient']">
              {{ (c.name || c.id).charAt(0) }}
            </el-avatar>
            <div class="contact-item__content">
              <div class="contact-item__top">
                <span class="contact-item__name">{{ c.name || c.id }}</span>
                <el-tag v-if="c.role === 'doctor'" size="small" type="primary" effect="plain">医生</el-tag>
                <el-tag v-else size="small" type="success" effect="plain">患者</el-tag>
              </div>
              <div class="contact-item__preview">
                {{ c.lastMessage || '暂无消息' }}
              </div>
            </div>
            <div v-if="c.unread" class="contact-item__badge">
              <el-badge :value="c.unread" :max="99" />
            </div>
          </div>
        </el-scrollbar>
      </aside>

      <div class="conversation-panel">
        <template v-if="selectedContact">
          <div class="conversation-panel__header">
            <div class="conversation-panel__identity">
              <el-avatar :size="34" :src="resolveAvatar(selectedContact.avatar, selectedContact.role, selectedContact.name || selectedContact.id)" :class="['contact-item__avatar', selectedContact.role === 'doctor' ? 'contact-item__avatar--doctor' : 'contact-item__avatar--patient']">
                {{ (selectedContact.name || '').charAt(0) }}
              </el-avatar>
              <div>
                <div class="conversation-panel__name">{{ selectedContact.name || selectedContact.id }}</div>
                <div class="conversation-panel__meta">{{ selectedContact.role === 'doctor' ? '医生联系人' : '患者联系人' }}</div>
              </div>
            </div>
            <el-tag size="small" type="info" effect="plain">消息 {{ conversation.length }}</el-tag>
          </div>

          <div ref="msgBox" class="conversation-panel__messages">
            <div v-for="msg in conversation" :key="msg.id" :class="['msg-row', msg.fromId === myId ? 'mine' : 'theirs']">
              <div class="msg-bubble-wrap">
                <div class="msg-sender">{{ msg.fromName || msg.fromId }}</div>
                <div class="msg-bubble">{{ msg.content }}</div>
                <div class="msg-time">{{ formatTime(msg.timestamp) }}</div>
              </div>
            </div>
            <el-empty v-if="conversation.length === 0" description="开始对话吧" :image-size="72" />
          </div>

          <div class="conversation-panel__input">
            <el-input
              v-model="msgInput"
              placeholder="输入消息..."
              @keyup.enter="sendMsg"
              size="large"
            >
              <template #append>
                <el-button type="primary" @click="sendMsg" :disabled="!msgInput.trim()">发送</el-button>
              </template>
            </el-input>
          </div>
        </template>

        <div v-else class="conversation-panel__empty">
          <el-empty description="请选择一位联系人开始对话" :image-size="90" />
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { messagingApi, patientApi, profileApi } from '../../api'
import { useUserStore } from '../../stores/user'
import SockJS from 'sockjs-client/dist/sockjs'
import { Client } from '@stomp/stompjs'
import { resolveAvatar } from '../../utils/avatar'
import dayjs from 'dayjs'

const userStore = useUserStore()
const searchText = ref('')
const selectedContact = ref(null)
const msgInput = ref('')
const conversation = ref([])
const contacts = ref([])
const msgBox = ref(null)
let stompClient = null
let pollTimer = null

const knownDoctors = [
  { loginName: 'doctor_zhang', displayName: '张医生' },
  { loginName: 'doctor_li', displayName: '李医生' }
]

const myId = computed(() => {
  if (userStore.isDoctor) {
    return 'doctor_' + userStore.loginName
  }
  return 'patient_' + userStore.patientId
})

const myName = computed(() => userStore.username)
const themeClass = computed(() => userStore.isDoctor ? 'messaging-page--doctor' : 'messaging-page--patient')
const roleLabel = computed(() => userStore.isDoctor ? '医生工作台' : '患者工作台')
const pageTitle = computed(() => userStore.isDoctor ? '医患沟通中心' : '与医生沟通')
const pageSubtitle = computed(() => {
  if (userStore.isDoctor) {
    return '集中处理患者消息、复诊沟通与管理提醒，让沟通流程和随访执行保持一致。'
  }
  return '与医生保持连续沟通，及时反馈指标变化、症状波动和日常问题。'
})
const unreadCount = computed(() => contacts.value.reduce((sum, item) => sum + (item.unread || 0), 0))
const selectedContactName = computed(() => selectedContact.value?.name || '未选择')

const filteredContacts = computed(() => {
  if (!searchText.value) return contacts.value
  return contacts.value.filter(c => (c.name || c.id).includes(searchText.value))
})

onMounted(async () => {
  if (userStore.isDoctor) {
    await loadDoctorContacts()
  } else {
    await loadPatientContacts()
  }

  try {
    const serverContacts = await messagingApi.getContacts(myId.value)
    for (const sc of serverContacts) {
      const existing = contacts.value.find(c => c.id === sc.id)
      if (existing) {
        existing.lastMessage = sc.lastMessage || ''
        continue
      }
      contacts.value.push({
        id: sc.id,
        name: sc.fromName || sc.id,
        role: sc.id?.startsWith('doctor_') ? 'doctor' : 'patient',
        lastMessage: sc.lastMessage || '',
        unread: 0
      })
    }
  } catch (e) { /* ignore */ }

  if (contacts.value.length > 0) {
    const withMsg = contacts.value.find(c => c.lastMessage)
    await selectContact(withMsg || contacts.value[0])
  }

  connectWs()

  pollTimer = setInterval(async () => {
    if (selectedContact.value) {
      try {
        const msgs = await messagingApi.getConversation(myId.value, selectedContact.value.id)
        if (msgs.length > conversation.value.length) {
          conversation.value = msgs
          scrollToBottom()
        }
      } catch (e) { /* ignore */ }
    }
  }, 5000)
})

onUnmounted(() => {
  if (stompClient) stompClient.deactivate()
  if (pollTimer) clearInterval(pollTimer)
})

function connectWs() {
  try {
    stompClient = new Client({
      webSocketFactory: () => new SockJS('/ws/notifications'),
      connectHeaders: userStore.token ? { Authorization: `Bearer ${userStore.token}` } : {},
      reconnectDelay: 5000,
      onConnect: () => {
        stompClient.subscribe('/topic/chat/' + myId.value, (message) => {
          const msg = JSON.parse(message.body)
          const fromContact = contacts.value.find(c => c.id === msg.fromId)
          if (fromContact) {
            fromContact.lastMessage = msg.content
            if (!selectedContact.value || selectedContact.value.id !== msg.fromId) {
              fromContact.unread = (fromContact.unread || 0) + 1
            }
          }
          const toContact = contacts.value.find(c => c.id === msg.toId)
          if (toContact) toContact.lastMessage = msg.content

          if (selectedContact.value) {
            const contactId = selectedContact.value.id
            if (msg.fromId === contactId || msg.toId === contactId) {
              if (!conversation.value.find(m => m.id === msg.id)) {
                conversation.value.push(msg)
                scrollToBottom()
              }
            }
          }
        })
      },
      onStompError: () => {},
      onWebSocketError: () => {}
    })
    stompClient.activate()
  } catch (e) {
    // fallback to polling
  }
}

async function selectContact(contact) {
  selectedContact.value = contact
  contact.unread = 0
  try {
    conversation.value = await messagingApi.getConversation(myId.value, contact.id)
  } catch (e) {
    conversation.value = []
  }
  await scrollToBottom()
}

async function sendMsg() {
  const content = msgInput.value.trim()
  if (!content || !selectedContact.value) return

  const msg = {
    fromId: myId.value,
    fromName: myName.value,
    toId: selectedContact.value.id,
    toName: selectedContact.value.name,
    content
  }

  msgInput.value = ''

  try {
    const sent = await messagingApi.send(msg)
    if (!conversation.value.find(m => m.id === sent.id)) {
      conversation.value.push(sent)
      scrollToBottom()
    }
  } catch (e) {
    msgInput.value = content
    return
  }

  const contact = contacts.value.find(c => c.id === selectedContact.value.id)
  if (contact) contact.lastMessage = content
}

async function scrollToBottom() {
  await nextTick()
  if (msgBox.value) msgBox.value.scrollTop = msgBox.value.scrollHeight
}

function formatTime(ts) {
  if (!ts) return ''
  const d = dayjs(ts)
  const today = dayjs()
  if (d.isSame(today, 'day')) return d.format('HH:mm')
  return d.format('MM-DD HH:mm')
}

async function loadDoctorContacts() {
  try {
    const patients = await patientApi.list({ doctorId: userStore.doctorId })
    contacts.value = patients.map(p => ({
      id: 'patient_' + p.id,
      name: p.name,
      role: 'patient',
      lastMessage: '',
      unread: 0
    }))
  } catch (e) {
    contacts.value = []
  }
}

async function loadPatientContacts() {
  const list = []
  if (userStore.assignedDoctorId) {
    try {
      const d = await profileApi.getDoctor(userStore.assignedDoctorId)
      if (d) {
        list.push({
          id: 'doctor_' + (d.username || d.id),
          name: d.name || d.username || '我的医生',
          role: 'doctor',
          lastMessage: '',
          unread: 0
        })
      }
    } catch (e) { /* ignore */ }
  }

  if (list.length === 0) {
    list.push(...knownDoctors.map(d => ({
      id: 'doctor_' + d.loginName,
      name: d.displayName,
      role: 'doctor',
      lastMessage: '',
      unread: 0
    })))
  }

  contacts.value = list
}
</script>

<style scoped>
.messaging-page {
  display: grid;
  gap: 22px;
}

.messaging-page--doctor {
  --msg-accent: #294a60;
  --msg-accent-strong: #1f3849;
  --msg-accent-soft: rgba(198, 220, 232, 0.42);
  --msg-surface: rgba(255, 255, 255, 0.88);
  --msg-surface-soft: rgba(247, 249, 250, 0.9);
  --msg-border: rgba(31, 51, 64, 0.08);
}

.messaging-page--patient {
  --msg-accent: #2e6f8b;
  --msg-accent-strong: #24576d;
  --msg-accent-soft: rgba(203, 229, 238, 0.46);
  --msg-surface: rgba(255, 255, 255, 0.88);
  --msg-surface-soft: rgba(247, 250, 251, 0.92);
  --msg-border: rgba(39, 81, 104, 0.08);
}

.messaging-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  border-radius: 28px;
  color: #f7fbff;
  box-shadow: 0 28px 60px rgba(19, 33, 43, 0.16);
}

.messaging-page--doctor .messaging-hero {
  background:
    radial-gradient(circle at top right, rgba(142, 176, 197, 0.18), transparent 34%),
    linear-gradient(135deg, #22313d 0%, #34495a 52%, #466173 100%);
}

.messaging-page--patient .messaging-hero {
  background:
    radial-gradient(circle at top right, rgba(150, 198, 214, 0.2), transparent 34%),
    linear-gradient(135deg, #1f4f63 0%, #2f6f8b 52%, #4c91a5 100%);
}

.messaging-hero__content {
  max-width: 760px;
}

.messaging-hero__eyebrow,
.contact-rail__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  letter-spacing: 0.1em;
}

.messaging-hero h1 {
  margin: 18px 0 10px;
  font-size: 34px;
  line-height: 1.05;
  color: #ffffff;
}

.messaging-hero p {
  margin: 0;
  max-width: 640px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(237, 244, 249, 0.82);
}

.messaging-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.messaging-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.messaging-stat-card,
.contact-rail,
.conversation-panel {
  border-radius: 24px;
  border: 1px solid var(--msg-border);
  background: var(--msg-surface);
  box-shadow: 0 18px 38px rgba(17, 28, 38, 0.08);
}

.messaging-stat-card {
  padding: 18px 20px;
}

.messaging-stat-card span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #6b7b86;
}

.messaging-stat-card strong {
  display: block;
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.2;
  color: #1f3340;
}

.messaging-stat-card p {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #72838d;
}

.messaging-workspace {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 18px;
  height: calc(100vh - 250px);
  min-height: 600px;
}

.contact-rail {
  display: flex;
  flex-direction: column;
  padding: 20px 16px;
  overflow: hidden;
}

.contact-rail__head h2 {
  margin: 10px 0 0;
  font-size: 24px;
  color: #1f3340;
}

.contact-rail__eyebrow {
  background: var(--msg-accent-soft);
  color: var(--msg-accent);
}

.contact-rail__search {
  margin: 16px 0;
}

.contact-rail :deep(.el-scrollbar) {
  flex: 1;
}

.contact-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  cursor: pointer;
  margin-bottom: 10px;
  border-radius: 18px;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
  background: rgba(247, 249, 250, 0.88);
  border: 1px solid transparent;
}

.contact-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(17, 28, 38, 0.08);
}

.contact-item.active {
  background: var(--msg-accent-soft);
  border-color: rgba(80, 126, 151, 0.18);
}

.contact-item__avatar {
  flex-shrink: 0;
  box-shadow: 0 12px 24px rgba(17, 28, 38, 0.12);
}

.contact-item__avatar--doctor {
  background: var(--msg-accent-strong);
  color: #ffffff;
}

.contact-item__avatar--patient {
  background: linear-gradient(135deg, #57a882 0%, #79bf9a 100%);
  color: #ffffff;
}

.contact-item__content {
  flex: 1;
  min-width: 0;
}

.contact-item__top {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.contact-item__name {
  font-weight: 700;
  font-size: 14px;
  color: #2b3f4d;
}

.contact-item__preview {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.contact-item__badge {
  flex-shrink: 0;
}

.conversation-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.conversation-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px 20px;
  border-bottom: 1px solid var(--msg-border);
  background: rgba(255, 255, 255, 0.92);
}

.conversation-panel__identity {
  display: flex;
  align-items: center;
  gap: 10px;
}

.conversation-panel__name {
  font-size: 16px;
  font-weight: 700;
  color: #233846;
}

.conversation-panel__meta {
  margin-top: 4px;
  font-size: 12px;
  color: #7d8e98;
}

.conversation-panel__messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: linear-gradient(180deg, rgba(249, 250, 251, 0.5) 0%, rgba(244, 247, 248, 0.82) 100%);
}

.conversation-panel__input {
  padding: 16px 20px;
  border-top: 1px solid var(--msg-border);
  background: rgba(255, 255, 255, 0.92);
}

.conversation-panel__empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.msg-row {
  display: flex;
  margin-bottom: 14px;
}

.msg-row.mine {
  justify-content: flex-end;
}

.msg-bubble-wrap {
  max-width: 70%;
}

.msg-sender {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.msg-row.mine .msg-sender {
  text-align: right;
}

.msg-bubble {
  display: inline-block;
  padding: 10px 14px;
  border-radius: 16px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
  box-shadow: 0 12px 24px rgba(17, 28, 38, 0.08);
}

.msg-row.mine .msg-bubble {
  background: linear-gradient(135deg, var(--msg-accent-strong) 0%, var(--msg-accent) 100%);
  color: #fff;
  border-top-right-radius: 4px;
}

.msg-row.theirs .msg-bubble {
  background: #fff;
  color: #303133;
  border-top-left-radius: 4px;
}

.msg-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 4px;
}

.msg-row.mine .msg-time {
  text-align: right;
}

@media (max-width: 1200px) {
  .messaging-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .messaging-workspace {
    grid-template-columns: 1fr;
    height: auto;
    min-height: 0;
  }

  .contact-rail {
    max-height: 360px;
  }

  .conversation-panel {
    min-height: 520px;
  }
}

@media (max-width: 640px) {
  .messaging-hero,
  .contact-rail,
  .conversation-panel {
    border-radius: 22px;
  }

  .messaging-hero {
    padding: 20px;
  }

  .messaging-stat-grid {
    grid-template-columns: 1fr;
  }

  .messaging-hero h1 {
    font-size: 28px;
  }

  .msg-bubble-wrap {
    max-width: 84%;
  }
}
</style>
