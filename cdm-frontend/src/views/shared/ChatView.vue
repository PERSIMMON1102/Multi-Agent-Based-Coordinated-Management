<template>
  <div class="chat-page" :class="chatThemeClass">
    <section class="chat-hero">
      <div class="chat-hero__content">
        <span class="chat-hero__eyebrow">{{ userStore.isDoctor ? 'Clinical Co-Pilot' : 'Health Companion' }}</span>
        <h1>{{ pageTitle }}</h1>
        <p>{{ pageSubtitle }}</p>
        <div class="chat-hero__chips">
          <el-tag effect="plain" round :type="statusType">{{ statusText }}</el-tag>
          <el-tag effect="plain" round type="info">{{ sessionMetricText }}</el-tag>
          <el-tag effect="plain" round :type="contextChipType">{{ contextModeText }}</el-tag>
        </div>
      </div>

      <div class="chat-hero__controls">
        <div class="chat-control-card">
          <div class="chat-control-card__label">会话控制</div>
          <div class="chat-control-card__body">
            <el-segmented
              v-if="userStore.isDoctor"
              v-model="doctorChatMode"
              :options="chatModeOptions"
              size="small"
              @change="handleModeChange"
            />
            <el-select
              v-if="userStore.isDoctor && doctorChatMode === 'patient'"
              v-model="selectedPatientId"
              placeholder="选择患者上下文"
              clearable
              filterable
              class="chat-control-card__select"
              @change="handlePatientChange"
            >
              <el-option v-for="p in patients" :key="p.id" :label="`${p.name} (${p.phone || p.idCard || ''})`" :value="p.id" />
            </el-select>
            <div class="chat-control-card__hint">{{ controlHint }}</div>
            <el-button class="chat-control-card__button" @click="clearChat" :disabled="messages.length === 0">清除对话</el-button>
          </div>
        </div>
      </div>
    </section>

    <section class="chat-workspace">
      <aside class="session-rail">
        <div class="session-rail__head">
          <div>
            <div class="session-rail__label">Session Archive</div>
            <h2>{{ sessionListTitle }}</h2>
          </div>
          <el-button size="small" type="primary" link @click="createSession">新建</el-button>
        </div>
        <p class="session-rail__hint">{{ sessionRailHint }}</p>

        <el-scrollbar height="100%">
          <el-empty v-if="sessions.length === 0" :description="sessionEmptyText" :image-size="80" />
          <div
            v-for="s in sessions"
            :key="s.id"
            :class="['session-item', { active: s.id === sessionId }]"
            @click="selectSession(s.id)"
          >
            <div class="session-title">{{ s.title || '未命名对话' }}</div>
            <div class="session-time">{{ formatTime(s.lastMessageAt) }}</div>
          </div>
        </el-scrollbar>
      </aside>

      <div class="conversation-panel">
        <div class="conversation-panel__scroll" ref="chatBox">
          <div v-if="messages.length === 0" class="chat-welcome">
            <div class="chat-welcome__icon">
              <el-icon :size="42"><ChatDotRound /></el-icon>
            </div>
            <span class="chat-welcome__eyebrow">{{ userStore.isDoctor ? 'Doctor Workflow' : 'Personal Guidance' }}</span>
            <h3>{{ welcomeTitle }}</h3>
            <p>{{ welcomeSubtitle }}</p>
            <div class="chat-context-notice" :class="`chat-context-notice--${contextNoticeTone}`">
              {{ contextNoticeText }}
            </div>
            <div v-if="showLatestAssistantOrchestration" class="chat-orchestration-overview">
              <div class="chat-orchestration-overview__head">
                <span class="chat-orchestration-overview__eyebrow">Response Process</span>
                <strong>多步骤整理已启用</strong>
              </div>
              <div class="chat-orchestration-overview__metrics">
                <div class="chat-orchestration-overview__metric">
                  <span>处理步骤</span>
                  <strong>{{ latestAssistantSubAgentCount }}</strong>
                </div>
                <div class="chat-orchestration-overview__metric">
                  <span>底层能力</span>
                  <strong>{{ latestAssistantToolCount }}</strong>
                </div>
                <div class="chat-orchestration-overview__metric">
                  <span>执行状态</span>
                  <strong>{{ latestAssistantDegraded ? '含降级' : '正常' }}</strong>
                </div>
              </div>
              <div class="chat-orchestration-overview__agents">
                参与环节：{{ latestAssistantSubAgentLabels }}
              </div>
            </div>
            <div class="quick-questions">
              <el-button v-for="q in quickQuestions" :key="q" size="small" round @click="sendQuickQuestion(q)">
                {{ q }}
              </el-button>
            </div>
          </div>

          <div v-for="(msg, idx) in messages" :key="idx" :class="['chat-msg', msg.role]">
            <div class="msg-avatar">
              <el-avatar :size="38" :class="['msg-avatar__badge', msg.role === 'user' ? 'msg-avatar__badge--user' : 'msg-avatar__badge--assistant']">
                {{ msg.role === 'user' ? '我' : 'AI' }}
              </el-avatar>
            </div>
            <div class="msg-content">
              <div class="msg-name">{{ msg.role === 'user' ? '我' : '慢病管家' }}</div>
              <div v-if="msg.role === 'assistant'" class="msg-bubble msg-bubble--structured">
                <div v-if="shouldShowDetailedProcess(msg)" class="msg-orchestration-banner">
                  <div class="msg-orchestration-banner__head">
                    <div>
                      <div class="msg-orchestration-banner__eyebrow">Response Process</div>
                      <strong>当前回答由系统分步骤整理后生成</strong>
                    </div>
                    <span :class="['msg-orchestration-banner__status', { 'msg-orchestration-banner__status--degraded': hasDegradation(msg) }]">
                      {{ hasDegradation(msg) ? '含降级处理' : '处理正常' }}
                    </span>
                  </div>
                  <div class="msg-orchestration-banner__metrics">
                    <div class="msg-orchestration-banner__metric">
                      <span>处理步骤</span>
                      <strong>{{ collaborationSubAgents(msg).length }}</strong>
                    </div>
                    <div class="msg-orchestration-banner__metric">
                      <span>底层能力</span>
                      <strong>{{ collaborationToolLabels(msg).length }}</strong>
                    </div>
                    <div class="msg-orchestration-banner__metric">
                      <span>当前阶段</span>
                      <strong>{{ collaborationStageText(msg) }}</strong>
                    </div>
                  </div>
                  <div class="msg-orchestration-banner__agents">
                    参与环节：{{ collaborationSubAgentText(msg) }}
                  </div>
                  <div
                    v-if="msg.goal || msg.progressSummary || msg.nextAction || msg.blockers.length > 0"
                    class="msg-orchestration-banner__planning"
                  >
                    <div v-if="msg.goal" class="msg-orchestration-banner__planning-item">
                      <span>当前目标</span>
                      <strong>{{ msg.goal }}</strong>
                    </div>
                    <div v-if="msg.progressSummary" class="msg-orchestration-banner__planning-item">
                      <span>当前进展</span>
                      <strong>{{ msg.progressSummary }}</strong>
                    </div>
                    <div v-if="msg.nextAction" class="msg-orchestration-banner__planning-item">
                      <span>下一步</span>
                      <strong>{{ msg.nextAction }}</strong>
                    </div>
                    <div v-if="msg.blockers.length > 0" class="msg-orchestration-banner__planning-item msg-orchestration-banner__planning-item--warning">
                      <span>阻塞项</span>
                      <strong>{{ msg.blockers.join('；') }}</strong>
                    </div>
                  </div>
                </div>
                <div v-if="shouldShowThoughtPanel(msg)" class="msg-thought-shell">
                  <button
                    type="button"
                    class="msg-thought-toggle"
                    @click="toggleThought(msg)"
                  >
                    <span class="msg-thought-toggle__label">分析过程</span>
                    <span class="msg-thought-toggle__hint">{{ msg.thoughtExpanded ? '收起' : '展开' }}</span>
                  </button>
                  <div v-if="msg.thoughtExpanded" class="msg-thought">
                    <div v-if="hasCollaborationChain(msg)" class="msg-collaboration">
                      <div class="msg-collaboration__title">多步骤整理过程</div>
                      <div class="msg-collaboration__row">
                        <span class="msg-collaboration__label">主流程</span>
                        <span class="msg-collaboration__value">{{ collaborationCoordinatorLabel() }}</span>
                      </div>
                      <div class="msg-collaboration__row">
                        <span class="msg-collaboration__label">处理步骤</span>
                        <span class="msg-collaboration__value">{{ collaborationSubAgentText(msg) }}</span>
                      </div>
                      <div v-if="collaborationToolText(msg)" class="msg-collaboration__row">
                        <span class="msg-collaboration__label">底层能力</span>
                        <span class="msg-collaboration__value">{{ collaborationToolText(msg) }}</span>
                      </div>
                    </div>
                    <RichTextBlock :content="assistantThinkingText(msg)" compact />
                  </div>
                </div>
                <div class="msg-output">
                  <div v-if="shouldShowPatientContextBrief(msg)" class="msg-patient-context-brief">
                    <div class="msg-patient-context-brief__head">
                      <div>
                        <div class="msg-patient-context-brief__eyebrow">Patient Context</div>
                        <strong>已结合患者上下文</strong>
                      </div>
                      <span class="msg-patient-context-brief__status">定向建议</span>
                    </div>
                    <p class="msg-patient-context-brief__text">{{ patientContextBriefText(msg) }}</p>
                    <div class="msg-patient-context-brief__chips">
                      <span v-for="chip in patientContextBriefChips(msg)" :key="chip" class="msg-patient-context-brief__chip">
                        {{ chip }}
                      </span>
                    </div>
                  </div>
                  <div v-if="!shouldRenderSimplePatientAnswer(msg)" class="msg-output__head">
                    <div class="msg-section-title">输出</div>
                    <span
                      v-if="assistantResponseStatusText(msg)"
                      :class="['msg-output__status', { 'msg-output__status--done': msg.streamPhase === 'done' }]"
                    >
                      {{ assistantResponseStatusText(msg) }}
                    </span>
                  </div>
                  <div v-if="isRetryNoticeMessage(msg)" class="msg-retry-card msg-retry-card--notice">
                    <div class="msg-retry-card__eyebrow">Admin Trigger</div>
                    <div class="msg-retry-card__title">后台重新执行已触发</div>
                    <p class="msg-retry-card__hint">这是一条流程提示，不是新的医学结论。系统完成重跑后，会在下方追加一条“重跑补充结果”。</p>
                    <RichTextBlock :content="msg.content" compact />
                  </div>
                  <div v-if="isRetryResultMessage(msg)" class="msg-retry-card msg-retry-card--result">
                    <div class="msg-retry-card__eyebrow">Retry Result</div>
                    <div class="msg-retry-card__title">管理员重跑后的补充结果</div>
                    <p class="msg-retry-card__hint">{{ retryResultHint(msg, idx) }}</p>
                    <div v-if="hasRetryComparison(msg, idx)" class="msg-retry-compare">
                      <div class="msg-retry-compare__item">
                        <span class="msg-retry-compare__label">重跑前</span>
                        <p>{{ retryComparisonBefore(msg, idx) }}</p>
                      </div>
                      <div class="msg-retry-compare__item">
                        <span class="msg-retry-compare__label">重跑后</span>
                        <p>{{ retryComparisonAfter(msg) }}</p>
                      </div>
                    </div>
                    <RichTextBlock :content="msg.content" compact />
                  </div>
                  <div v-if="shouldRenderSimplePatientAnswer(msg)" class="msg-output-stage msg-output-stage--simple">
                    <RichTextBlock :content="simplePatientAnswerText(msg)" compact />
                  </div>
                  <div v-else-if="shouldShowPreviewAnswer(msg)" class="msg-output-stage">
                    <div class="msg-output-stage__title">快速判断</div>
                    <RichTextBlock :content="previewAnswerText(msg)" compact />
                  </div>
                  <div v-if="shouldShowFinalAnswer(msg)" class="msg-output-stage">
                    <div class="msg-output-stage__title">补充分析</div>
                    <RichTextBlock :content="finalAnswerText(msg)" compact />
                  </div>
                  <div v-if="!shouldShowPreviewAnswer(msg) && !shouldShowFinalAnswer(msg)" class="msg-output-placeholder">
                    {{ assistantPlaceholderText(msg) }}
                  </div>
                </div>
              </div>
              <div v-else class="msg-bubble">
                <RichTextBlock :content="msg.content" compact />
              </div>

              <div
                v-if="shouldShowCloudUsageBadge(msg)"
                class="cloud-usage-badge"
              >
                本次回答已使用云端健康档案
              </div>

              <AgentTraceFlow
                v-if="shouldShowTracePanel(msg)"
                class="agent-trace"
                eyebrow="Process Flow"
                title="回答整理过程"
                :summary="msg.knowledgeGraphSummary"
                :steps="msg.steps"
                :tools="traceToolsForMessage(msg)"
                :active-step-index="msg.activeTraceIndex"
                :phase="msg.streamPhase"
                coordinator-label="慢病顾问主流程"
                :flags="msg.safetyFlags.map(formatSafetyFlag)"
                :references="msg.knowledgeGraphItems"
                :format-step-label="formatTraceStepLabel"
                compact
                @select-reference="openReferenceDetail"
              />

              <div v-if="shouldShowReferenceCards(msg)" class="msg-references">
                <div class="msg-references-title">本次回答参考了以下资料</div>
                <div class="reference-list">
                  <button
                    v-for="(reference, refIdx) in msg.references"
                    :key="`${idx}-${refIdx}`"
                    type="button"
                    class="reference-card reference-card--button"
                    @click="openReferenceDetail(reference)"
                  >
                    <div class="reference-top">
                      <span class="reference-name">{{ reference.title || '知识片段' }}</span>
                      <span v-if="reference.sourceType" class="reference-type">{{ formatSourceType(reference.sourceType) }}</span>
                    </div>
                    <div class="reference-meta">
                      {{ formatReferenceMeta(reference) }}
                    </div>
                    <div v-if="reference.contentSnippet" class="reference-snippet">{{ reference.contentSnippet }}</div>
                    <div class="reference-action">查看引用详情</div>
                  </button>
                </div>
              </div>
            </div>
          </div>

          <div v-if="loading && streamingAssistantIndex < 0" class="chat-msg assistant">
            <div class="msg-avatar">
              <el-avatar :size="38" class="msg-avatar__badge msg-avatar__badge--assistant">AI</el-avatar>
            </div>
            <div class="msg-content">
              <div class="msg-name">慢病管家</div>
              <div class="msg-bubble typing">
                <span class="dot"></span><span class="dot"></span><span class="dot"></span>
              </div>
              <div class="msg-thinking-live">{{ liveThinkingText }}</div>
              <div v-if="loadingTraceSteps.length > 0 && !isDoctorPatientMode" class="agent-loading">
                <div class="agent-trace-title">系统处理中</div>
                <div class="agent-loading-list">
                  <div
                    v-for="(step, stepIdx) in loadingTraceSteps"
                    :key="`loading-${stepIdx}`"
                    :class="['agent-loading-item', { active: stepIdx === loadingTraceIndex }]"
                  >
                    {{ step }}
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="inputText"
            :placeholder="chatInputPlaceholder"
            :disabled="loading"
            @keyup.enter="sendMessage"
            size="large"
          >
            <template #append>
              <el-button :icon="Promotion" :loading="loading" @click="sendMessage" type="primary">
                发送
              </el-button>
            </template>
          </el-input>
          <div class="chat-input__hint">
            AI 回答仅供参考，不能替代专业医疗建议。如有紧急情况，请立即就医。
          </div>
        </div>
      </div>
    </section>

    <ReferenceDetailDrawer
      v-model="referenceDrawerVisible"
      :reference="selectedReference"
    />
  </div>
</template>

<script setup>
defineOptions({
  name: 'ChatView'
})

import { ref, onMounted, nextTick, computed, onUnmounted, onActivated, onDeactivated, watch } from 'vue'
import { chatApi, patientApi } from '../../api/index'
import { useUserStore } from '../../stores/user'
import { ElMessage } from '../../utils/element-plus-service'
import dayjs from 'dayjs'
import ReferenceDetailDrawer from '../../components/ReferenceDetailDrawer.vue'
import RichTextBlock from '../../components/RichTextBlock.vue'
import AgentTraceFlow from '../../components/AgentTraceFlow.vue'

const userStore = useUserStore()
const CHAT_REMOTE_SYNC_INTERVAL_MS = 8000
const chatBox = ref(null)
const inputText = ref('')
const loading = ref(false)
const messages = ref([])
const sessionId = ref(null)
const llmAvailable = ref(false)
const patients = ref([])
const selectedPatientId = ref(null)
const sessions = ref([])
const referenceDrawerVisible = ref(false)
const selectedReference = ref(null)
const doctorChatMode = ref('general')
const loadingTraceSteps = ref([])
const loadingTraceIndex = ref(0)
const streamingAssistantIndex = ref(-1)
let loadingTraceTimer = null
let remoteSyncTimer = null
let sessionSyncing = false
let chatInitializing = true
let chatArchiveReady = false
let streamRequestActive = false
const DRAFT_SESSION_PREFIX = 'draft-'
const CHAT_DEBUG = true

const generalQuickQuestions = [
  '高血压患者饮食需要注意什么？',
  '糖尿病如何控制血糖？',
  '基层医生如何解读慢病评估报告？',
  '什么情况下需要紧急就医？'
]
const patientQuickQuestions = [
  '这位患者的评估报告怎么解读？',
  '这位患者当前最大的慢病风险是什么？',
  '这位患者下一步随访重点应该放在哪里？',
  '这位患者出现哪些情况需要尽快线下就医？'
]
const chatModeOptions = [
  { label: '通用咨询', value: 'general' },
  { label: '患者咨询', value: 'patient' }
]

const statusType = computed(() => llmAvailable.value ? 'success' : 'warning')
const statusText = computed(() => llmAvailable.value ? 'AI已就绪' : 'AI未配置')
const selectedPatientStorageKey = computed(() => `cdm_chat_selected_patient_${userStore.doctorId || userStore.username || 'doctor'}`)
const doctorChatModeStorageKey = computed(() => `cdm_chat_mode_${userStore.doctorId || userStore.username || 'doctor'}`)
const chatArchiveStorageKey = computed(() => {
  const ownerRole = currentOwnerRole.value || 'unknown'
  const ownerUserId = currentOwnerUserId.value || 'anonymous'
  const patientId = currentPatientId.value == null ? 'general' : String(currentPatientId.value)
  return `cdm_chat_archive_${ownerRole}_${ownerUserId}_${patientId}`
})

const selectedPatientName = computed(() => {
  if (!selectedPatientId.value || doctorChatMode.value !== 'patient') return ''
  const p = patients.value.find(p => p.id === selectedPatientId.value)
  return p ? p.name : ''
})

const currentOwnerRole = computed(() => userStore.isDoctor ? 'doctor' : 'patient')
const currentOwnerUserId = computed(() => userStore.isDoctor ? userStore.doctorId : userStore.patientId)
const currentPatientId = computed(() => {
  if (userStore.isDoctor) {
    return doctorChatMode.value === 'patient' ? selectedPatientId.value : null
  }
  return userStore.patientId || null
})
const isDoctorPatientMode = computed(() => userStore.isDoctor && doctorChatMode.value === 'patient')
const chatThemeClass = computed(() => userStore.isDoctor ? 'chat-page--doctor' : 'chat-page--patient')
const pageTitle = computed(() => userStore.isDoctor ? 'AI 临床顾问' : 'AI 健康顾问')
const pageSubtitle = computed(() => {
  if (userStore.isDoctor) {
    return '围绕患者上下文、风险评估和慢病依据，快速得到可解释的临床辅助建议。'
  }
  return '结合你的健康记录、宣教内容和知识库证据，给出更连贯的慢病管理建议。'
})
const sessionListTitle = computed(() => {
  if (!userStore.isDoctor) return '历史会话'
  if (doctorChatMode.value === 'general') return '通用会话'
  return selectedPatientName.value ? `${selectedPatientName.value} 的会话` : '患者会话'
})
const sessionEmptyText = computed(() => {
  if (!userStore.isDoctor) return '暂无会话'
  if (doctorChatMode.value === 'general') return '暂无通用会话'
  return selectedPatientId.value ? '暂无该患者会话' : '请选择患者后查看会话'
})
const sessionMetricText = computed(() => `会话 ${sessions.value.length}`)
const contextModeText = computed(() => {
  if (!userStore.isDoctor) return '个人健康咨询'
  if (doctorChatMode.value === 'general') return '通用咨询模式'
  return selectedPatientName.value ? `患者上下文：${selectedPatientName.value}` : '待选择患者上下文'
})
const contextChipType = computed(() => {
  if (!userStore.isDoctor) return 'success'
  if (doctorChatMode.value === 'general') return 'info'
  return selectedPatientName.value ? 'success' : 'warning'
})
const controlHint = computed(() => {
  if (!userStore.isDoctor) return '当前会话默认带入你的健康档案和近期待办信息。'
  if (doctorChatMode.value === 'general') return '通用咨询模式下，回答不会带入任何患者病历数据。'
  return selectedPatientName.value ? `当前已加载 ${selectedPatientName.value} 的上下文。` : '请选择一位患者，AI 将自动带入该患者的上下文。'
})
const sessionRailHint = computed(() => {
  if (!userStore.isDoctor) return '可回看历史提问、引用依据和回答整理过程。'
  if (doctorChatMode.value === 'general') return '这里保存通用慢病咨询，不带患者上下文。'
  return selectedPatientId.value ? '当前列表仅展示该患者的独立会话历史。' : '请选择患者后查看对应的独立会话。'
})
const welcomeTitle = computed(() => userStore.isDoctor ? '你好，这里是你的慢病临床助手' : '你好，我是你的慢病健康管家')
const welcomeSubtitle = computed(() => {
  if (userStore.isDoctor) {
    return '你可以做通用慢病咨询，也可以切到患者上下文模式，结合病历和评估结果做针对性分析。'
  }
  return '你可以询问饮食、运动、指标解读或报告内容，系统会结合你的个人上下文进行回答。'
})
const contextNoticeTone = computed(() => {
  if (!userStore.isDoctor) return 'success'
  if (doctorChatMode.value === 'general') return 'info'
  return selectedPatientName.value ? 'success' : 'warning'
})
const contextNoticeText = computed(() => {
  if (!userStore.isDoctor) {
    return '当前回答会结合你的健康记录、评估结果和知识库证据。'
  }
  if (doctorChatMode.value === 'general') {
    return '当前为通用咨询模式，回答不会带入任何患者病历数据。'
  }
  if (!selectedPatientId.value) {
    return '请选择一位患者，AI 将自动加载该患者的历史症状、评估和随访数据。'
  }
  return `当前患者上下文：${selectedPatientName.value}`
})
const chatInputPlaceholder = computed(() => {
  if (!userStore.isDoctor) return '输入你的健康问题...'
  if (doctorChatMode.value === 'general') return '输入临床咨询或慢病管理问题...'
  return selectedPatientId.value ? '输入针对当前患者的问题...' : '请先选择患者，再输入问题...'
})
const liveThinkingText = computed(() => {
  if (isDoctorPatientMode.value) {
    return '正在结合患者资料生成建议...'
  }
  return '正在深度思考并组织回答...'
})
const latestAssistantMessage = computed(() => [...messages.value].reverse().find(msg => msg?.role === 'assistant') || null)
const latestAssistantHasMultiAgentFlow = computed(() => hasCollaborationChain(latestAssistantMessage.value || {}))
const showLatestAssistantOrchestration = computed(() => latestAssistantHasMultiAgentFlow.value && !isDoctorPatientMode.value)
const latestAssistantSubAgentCount = computed(() => collaborationSubAgents(latestAssistantMessage.value || {}).length)
const latestAssistantToolCount = computed(() => collaborationToolLabels(latestAssistantMessage.value || {}).length)
const latestAssistantDegraded = computed(() => {
  const notes = latestAssistantMessage.value?.degradationNotes
  return Array.isArray(notes) && notes.length > 0
})
const latestAssistantSubAgentLabels = computed(() => {
  const labels = collaborationSubAgents(latestAssistantMessage.value || {}).map(formatToolName).filter(Boolean)
  return labels.length > 0 ? labels.join('、') : '暂无'
})
const quickQuestions = computed(() => {
  if (!userStore.isDoctor) return generalQuickQuestions
  return doctorChatMode.value === 'patient' ? patientQuickQuestions : generalQuickQuestions
})
onMounted(async () => {
  await initializeChatPage()
  startRemoteSessionSync()
})

onActivated(async () => {
  startRemoteSessionSync()
  reconcileConversationRuntime()
  await syncSessionsWithCurrentContext({ autoSelectFirst: !hasLocalConversation(), preserveActive: true })
  if (loading.value && !streamRequestActive) {
    await refreshActiveSessionFromServer()
  }
  await scrollToBottom()
})

onDeactivated(() => {
  stopRemoteSessionSync()
})

watch([currentOwnerUserId, currentPatientId, doctorChatMode], async () => {
  if (chatInitializing) return
  await syncSessionsWithCurrentContext({ autoSelectFirst: !hasLocalConversation(), preserveActive: true })
})

watch([sessions, messages, sessionId], () => {
  if (!chatArchiveReady) return
  persistChatArchive()
}, { deep: true })

watch(sessionId, (value, oldValue) => {
  debugChat('sessionId changed', { from: oldValue, to: value })
})

watch(messages, (value) => {
  debugChat('messages changed', {
    length: Array.isArray(value) ? value.length : 0,
    roles: Array.isArray(value) ? value.map(item => item?.role) : []
  })
}, { deep: true })

async function initializeChatPage() {
  debugChat('initializeChatPage:start', {
    ownerRole: currentOwnerRole.value,
    ownerUserId: currentOwnerUserId.value,
    patientId: currentPatientId.value,
    doctorMode: doctorChatMode.value
  })
  chatInitializing = true
  chatArchiveReady = false
  restoreDoctorChatMode()
  try {
    const res = await chatApi.getStatus()
    llmAvailable.value = res.available
  } catch (e) {
    llmAvailable.value = false
  }
  if (userStore.isDoctor) {
    try {
      patients.value = await patientApi.list({ doctorId: userStore.doctorId })
      restoreSelectedPatient()
    } catch (e) { /* ignore */ }
  }
  restoreChatArchive()
  reconcileConversationRuntime()
  if (!hasLocalConversation()) {
    resetConversation()
  }
  chatArchiveReady = true
  await hydrateChatFromServer()
  reconcileConversationRuntime()
  chatInitializing = false
  debugChat('initializeChatPage:done', {
    activeSessionId: sessionId.value,
    sessionCount: sessions.value.length,
    messageCount: messages.value.length
  })
}

onUnmounted(() => {
  stopLoadingTrace()
  stopRemoteSessionSync()
})

async function handleModeChange() {
  persistDoctorChatMode()
  resetConversation()
  await syncSessionsWithCurrentContext({ autoSelectFirst: true })
}

async function handlePatientChange() {
  persistSelectedPatient()
  resetConversation()
  await syncSessionsWithCurrentContext({ autoSelectFirst: true })
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || loading.value) return
  if (userStore.isDoctor && doctorChatMode.value === 'patient' && !selectedPatientId.value) {
    ElMessage.warning('请先选择一位患者，再发起与患者相关的咨询')
    return
  }

  messages.value.push({
    role: 'user',
    content: text,
    references: [],
    usedTools: [],
    steps: [],
    safetyFlags: [],
    answerPlan: '',
    thoughtSummary: '',
    goal: '',
    progressSummary: '',
    nextAction: '',
    blockers: [],
    degradationNotes: [],
    knowledgeGraphSummary: '',
    knowledgeGraphItems: [],
    usedCloudPatientData: false
  })
  inputText.value = ''
  loading.value = true
  startLoadingTrace()
  const assistantMessage = normalizeMessage({
    role: 'assistant',
    content: '',
    thoughtSummary: '',
    thoughtExpanded: true
  })
  messages.value.push(assistantMessage)
  streamingAssistantIndex.value = messages.value.length - 1
  await scrollToBottom()

  try {
    streamRequestActive = true
    const res = await sendMessageStream({
      sessionId: isDraftSessionId(sessionId.value) ? null : sessionId.value,
      ownerRole: currentOwnerRole.value,
      ownerUserId: currentOwnerUserId.value,
      patientId: currentPatientId.value,
      message: text
    }, assistantMessage)
    sessionId.value = res.sessionId
    upsertCurrentSession(text, res.timestamp)
    await syncSessionsWithCurrentContext({ autoSelectFirst: false })
  } catch (e) {
    const assistant = messages.value[streamingAssistantIndex.value]
    if (assistant) {
      Object.assign(assistant, normalizeMessage({
        ...assistant,
        role: 'assistant',
        content: assistant.content || '抱歉，请求失败，请稍后重试。'
      }))
    } else {
      messages.value.push(normalizeMessage({
        role: 'assistant',
        content: '抱歉，请求失败，请稍后重试。'
      }))
    }
    ElMessage.error('发送失败')
  } finally {
    streamRequestActive = false
    streamingAssistantIndex.value = -1
    stopLoadingTrace()
    loading.value = false
    await scrollToBottom()
  }
}

async function sendMessageStream(payload, assistantMessage) {
  const token = localStorage.getItem('cdm_token')
  const response = await fetch('/api/chat/send-stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload)
  })

  if (!response.ok || !response.body) {
    if (response.status === 401) {
      localStorage.removeItem('cdm_token')
      window.location.href = '/login'
      throw new Error('未授权')
    }
    throw new Error(`流式请求失败: ${response.status}`)
  }

  const decoder = new TextDecoder('utf-8')
  const reader = response.body.getReader()
  let buffer = ''
  let finalPayload = null

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const segments = buffer.split('\n\n')
    buffer = segments.pop() || ''
    for (const segment of segments) {
      const parsed = parseSseBlock(segment)
      if (!parsed) continue
      finalPayload = await handleStreamEvent(parsed, assistantMessage, finalPayload)
    }
  }

  if (!finalPayload) {
    throw new Error('未收到完整回复')
  }
  return finalPayload
}

function parseSseBlock(block) {
  const lines = block.split('\n').map(line => line.trim()).filter(Boolean)
  if (lines.length === 0) return null
  let event = 'message'
  const dataLines = []
  lines.forEach(line => {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
      return
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  })
  if (dataLines.length === 0) return null
  try {
    return { event, data: JSON.parse(dataLines.join('\n')) }
  } catch (error) {
    return null
  }
}

async function handleStreamEvent(parsed, assistantMessage, finalPayload) {
  const { event, data } = parsed
  if (event === 'meta' && data.sessionId) {
    sessionId.value = data.sessionId
    return finalPayload
  }
  if (event === 'status' && data.phase) {
    assistantMessage.streamPhase = data.phase
    await scrollToBottom()
    return finalPayload
  }
  if (event === 'trace' && data.step) {
    mergeTraceStep(assistantMessage, data.step, data.stepIndex)
    assistantMessage.streamPhase = 'thinking'
    assistantMessage.thoughtExpanded = true
    await scrollToBottom()
    return finalPayload
  }
  if (event === 'artifacts') {
    mergeStreamArtifacts(assistantMessage, data)
    assistantMessage.thoughtExpanded = true
    await scrollToBottom()
    return finalPayload
  }
  if (event === 'thought' && data.delta) {
    assistantMessage.thoughtSummary += data.delta
    assistantMessage.thoughtExpanded = true
    await scrollToBottom()
    return finalPayload
  }
  if (event === 'answer' && data.delta) {
    assistantMessage.content += data.delta
    assistantMessage.previewContent = assistantMessage.content
    await scrollToBottom()
    return finalPayload
  }
  if (event === 'done') {
    const previewContent = assistantMessage.previewContent || assistantMessage.content
    const finalContent = typeof data.reply === 'string' ? data.reply : assistantMessage.content
    const normalized = normalizeMessage({
      ...assistantMessage,
      ...data,
      content: finalContent,
      previewContent,
      finalContent,
      thoughtSummary: data.thoughtSummary || assistantMessage.thoughtSummary,
      thoughtExpanded: Boolean((data.thoughtSummary || assistantMessage.thoughtSummary)),
      streamPhase: 'done',
      activeTraceIndex: Math.max(
        Array.isArray(data.steps) ? data.steps.length - 1 : assistantMessage.steps.length - 1,
        -1
      )
    })
    Object.assign(assistantMessage, normalized)
    await scrollToBottom()
    return data
  }
  if (event === 'error') {
    throw new Error(data.message || '流式聊天失败')
  }
  return finalPayload
}

function sendQuickQuestion(q) {
  inputText.value = q
  sendMessage()
}

async function clearChat() {
  try {
    if (sessionId.value && !isDraftSessionId(sessionId.value)) await chatApi.clearSession(sessionId.value, buildOwnerParams())
  } catch (e) { /* ignore */ }
  resetConversation()
  await syncSessionsWithCurrentContext({ autoSelectFirst: true })
}

async function scrollToBottom() {
  await nextTick()
  if (chatBox.value) {
    chatBox.value.scrollTop = chatBox.value.scrollHeight
  }
}

async function loadSessions(options = {}) {
  debugChat('loadSessions:start', {
    options,
    ownerUserId: currentOwnerUserId.value,
    patientId: currentPatientId.value,
    doctorMode: doctorChatMode.value
  })
  if (!currentOwnerUserId.value) {
    sessions.value = []
    return
  }
  if (userStore.isDoctor && doctorChatMode.value === 'patient' && !selectedPatientId.value) {
    sessions.value = []
    return
  }
  try {
    const cachedSessions = readArchivedSessions()
    const remoteSessions = normalizeArray(await chatApi.listSessions(buildSessionQueryParams()))
    debugChat('loadSessions:response', {
      remoteCount: remoteSessions.length,
      cachedCount: cachedSessions.length,
      firstRemote: remoteSessions[0]?.id || null
    })
    sessions.value = mergeSessionsWithLocalState(mergeArchivedSessions(remoteSessions, cachedSessions))
    persistChatArchive()

    if (options.autoSelectFirst && !hasLocalConversation() && sessions.value.length > 0) {
      const preferredSessionId = options.preferServerFirst && remoteSessions.length > 0
        ? remoteSessions[0]?.id
        : resolvePreferredSessionId({ preferRealSession: true })
      await selectSession(preferredSessionId || sessions.value[0].id)
      return
    }

    if (!options.preserveActive && sessionId.value && !sessions.value.some(item => item.id === sessionId.value)) {
      resetConversation()
    }
  } catch (e) {
    debugChat('loadSessions:error', { message: e?.message || String(e) })
    sessions.value = mergeSessionsWithLocalState(readArchivedSessions())
  }
}

async function syncSessionsWithCurrentContext(options = {}) {
  if (sessionSyncing) return
  debugChat('syncSessionsWithCurrentContext:start', { options })
  sessionSyncing = true
  try {
    await loadSessions(options)
  } finally {
    sessionSyncing = false
    debugChat('syncSessionsWithCurrentContext:done', {
      activeSessionId: sessionId.value,
      sessionCount: sessions.value.length,
      messageCount: messages.value.length
    })
  }
}

async function selectSession(id) {
  if (isDraftSessionId(id)) {
    resetConversation(id)
    syncSessionRail()
    persistChatArchive()
    await scrollToBottom()
    return
  }
  sessionId.value = id
  loading.value = true
  try {
    const cachedMessages = readArchivedMessages(id)
    if (cachedMessages.length > 0) {
      messages.value = cachedMessages.map(normalizeMessage)
      reconcileConversationRuntime()
    }
    const list = await chatApi.getHistory(id, buildOwnerParams())
    messages.value = list.map(normalizeMessage)
    reconcileConversationRuntime()
    persistChatArchive()
  } catch (e) {
    messages.value = readArchivedMessages(id).map(normalizeMessage)
    reconcileConversationRuntime()
  } finally {
    loading.value = false
    reconcileConversationRuntime()
    await scrollToBottom()
  }
}

async function hydrateChatFromServer() {
  debugChat('hydrateChatFromServer:start', {
    ownerUserId: currentOwnerUserId.value,
    patientId: currentPatientId.value,
    doctorMode: doctorChatMode.value
  })
  if (!currentOwnerUserId.value) {
    sessions.value = []
    messages.value = []
    return
  }
  if (userStore.isDoctor && doctorChatMode.value === 'patient' && !selectedPatientId.value) {
    sessions.value = []
    messages.value = []
    return
  }
  try {
    const archivedActiveSessionId = sessionId.value
    const archivedMessages = normalizeArray(messages.value).map(normalizeMessage)
    const bootstrap = await chatApi.getBootstrap(buildSessionQueryParams())
    const remoteSessions = normalizeArray(bootstrap?.sessions)
    const mergedSessions = mergeSessionsWithLocalState(mergeArchivedSessions(remoteSessions, readArchivedSessions()))
    const bootstrapMessages = normalizeArray(bootstrap?.messages).map(normalizeMessage)
    const remoteActiveSessionId = bootstrap?.activeSessionId || remoteSessions[0]?.id || null
    const preferredActiveSessionId = remoteActiveSessionId || archivedActiveSessionId || null
    debugChat('hydrateChatFromServer:response', {
      sessionCount: remoteSessions.length,
      activeSessionId: preferredActiveSessionId,
      messageCount: bootstrapMessages.length
    })
    sessions.value = mergedSessions
    sessionId.value = preferredActiveSessionId
    if (loading.value && archivedMessages.length > 0 && archivedActiveSessionId && preferredActiveSessionId === archivedActiveSessionId) {
      messages.value = archivedMessages
    } else if (archivedMessages.length > 0 && archivedActiveSessionId && preferredActiveSessionId === archivedActiveSessionId && !shouldApplyRemoteMessages(bootstrapMessages)) {
      messages.value = archivedMessages
    } else {
      messages.value = bootstrapMessages
    }
    persistChatArchive()
    await scrollToBottom()
  } catch (e) {
    debugChat('hydrateChatFromServer:error', { message: e?.message || String(e) })
    await syncSessionsWithCurrentContext({ autoSelectFirst: true, preserveActive: false, preferServerFirst: true })
  }
}

async function refreshActiveSessionFromServer() {
  if (!sessionId.value || isDraftSessionId(sessionId.value) || streamRequestActive || sessionSyncing) {
    return
  }
  if (typeof document !== 'undefined' && document.visibilityState === 'hidden') {
    return
  }
  try {
    const list = normalizeArray(await chatApi.getHistory(sessionId.value, buildOwnerParams())).map(normalizeMessage)
    if (!shouldApplyRemoteMessages(list)) {
      return
    }
    messages.value = list
    reconcileConversationRuntime()
    const lastTimestamp = list[list.length - 1]?.timestamp || Date.now()
    sessions.value = normalizeArray(sessions.value)
      .map(item => item?.id === sessionId.value ? { ...item, lastMessageAt: lastTimestamp } : item)
      .sort((a, b) => new Date(b?.lastMessageAt || 0).getTime() - new Date(a?.lastMessageAt || 0).getTime())
    persistChatArchive()
    await scrollToBottom()
  } catch (e) {
    debugChat('refreshActiveSessionFromServer:error', { message: e?.message || String(e) })
  }
}

async function createSession() {
  archiveCurrentConversation()
  resetConversation(createDraftSessionId())
  syncSessionRail()
  persistChatArchive()
}

function hasLocalConversation() {
  return loading.value || messages.value.length > 0 || Boolean(sessionId.value)
}

function mergeSessionsWithLocalState(remoteSessions = []) {
  const merged = [...remoteSessions]
  if (!sessionId.value) {
    return merged
  }
  const existing = merged.find(item => item.id === sessionId.value)
  if (existing) {
    if (!existing.lastMessageAt) {
      existing.lastMessageAt = Date.now()
    }
    return merged
  }
  if (!hasLocalConversation()) {
    return merged
  }
  merged.unshift({
    id: sessionId.value,
    title: localSessionTitle(),
    patientId: currentPatientId.value,
    lastMessageAt: Date.now()
  })
  return merged
}

function upsertCurrentSession(seedTitle, ts) {
  if (!sessionId.value) return
  const existing = sessions.value.find(item => item.id === sessionId.value)
  if (existing) {
    existing.lastMessageAt = ts || Date.now()
    if (!existing.title && seedTitle) {
      existing.title = generateSessionTitle(seedTitle)
    }
    persistChatArchive()
    return
  }
  sessions.value.unshift({
    id: sessionId.value,
    title: generateSessionTitle(seedTitle),
    patientId: currentPatientId.value,
    lastMessageAt: ts || Date.now()
  })
  persistChatArchive()
}

function archiveCurrentConversation() {
  if (!hasLocalConversation()) return
  const effectiveSessionId = sessionId.value || createDraftSessionId()
  const existing = sessions.value.find(item => item.id === effectiveSessionId)
  const titleSeed = messages.value.find(item => item.role === 'user')?.content || ''
  const archivedSession = {
    id: effectiveSessionId,
    title: existing?.title || generateSessionTitle(titleSeed),
    patientId: currentPatientId.value,
    lastMessageAt: existing?.lastMessageAt || Date.now()
  }
  sessionId.value = effectiveSessionId
  sessions.value = [
    archivedSession,
    ...normalizeArray(sessions.value).filter(item => item?.id !== effectiveSessionId && !isDraftSessionId(item?.id))
  ]
}

function normalizeArray(value) {
  return Array.isArray(value) ? value : []
}

function generateSessionTitle(text) {
  if (!text) return '未命名对话'
  const normalized = String(text).trim()
  if (!normalized) return '未命名对话'
  return normalized.length > 20 ? `${normalized.slice(0, 20)}...` : normalized
}

function localSessionTitle() {
  if (isDraftSessionId(sessionId.value)) {
    return '新对话'
  }
  return generateSessionTitle(messages.value.find(item => item.role === 'user')?.content || '')
}

function formatTime(ts) {
  if (!ts) return ''
  return dayjs(ts).format('MM-DD HH:mm')
}

function formatTraceStepLabel(step) {
  return formatToolName(step?.actionName || step?.agentName)
}

function normalizeMessage(message = {}) {
  return {
    role: message.role || 'assistant',
    content: message.content || '',
    previewContent: message.previewContent || '',
    finalContent: message.finalContent || '',
    references: Array.isArray(message.references) ? message.references : [],
    usedTools: Array.isArray(message.usedTools) ? message.usedTools : [],
    steps: Array.isArray(message.steps) ? message.steps : [],
    safetyFlags: Array.isArray(message.safetyFlags) ? message.safetyFlags : [],
    answerPlan: message.answerPlan || '',
    thoughtSummary: message.thoughtSummary || '',
    goal: message.goal || '',
    progressSummary: message.progressSummary || '',
    nextAction: message.nextAction || '',
    blockers: Array.isArray(message.blockers) ? message.blockers : [],
    degradationNotes: Array.isArray(message.degradationNotes) ? message.degradationNotes : [],
    knowledgeGraphSummary: message.knowledgeGraphSummary || '',
    knowledgeGraphItems: Array.isArray(message.knowledgeGraphItems) ? message.knowledgeGraphItems : [],
    usedCloudPatientData: Boolean(message.usedCloudPatientData),
    orchestrationTrace: message.orchestrationTrace && typeof message.orchestrationTrace === 'object'
      ? message.orchestrationTrace
      : null,
    timestamp: message.timestamp || null,
    thoughtExpanded: Boolean(message.thoughtExpanded),
    activeTraceIndex: Number.isFinite(Number(message.activeTraceIndex)) ? Number(message.activeTraceIndex) : -1,
    streamPhase: message.streamPhase || ''
  }
}

function resolvePendingAssistantIndex(targetMessages = messages.value) {
  const list = normalizeArray(targetMessages)
  for (let index = list.length - 1; index >= 0; index -= 1) {
    const message = list[index]
    if (message?.role !== 'assistant') continue
    if (message?.streamPhase && message.streamPhase !== 'done') {
      return index
    }
  }
  return -1
}

function hasPendingAssistantMessage(targetMessages = messages.value) {
  return resolvePendingAssistantIndex(targetMessages) >= 0
}

function mergeTraceStep(message = {}, rawStep = {}, stepIndex = -1) {
  if (!rawStep || typeof rawStep !== 'object') return
  if (!Array.isArray(message.steps)) {
    message.steps = []
  }
  const nextKey = `${rawStep.index || message.steps.length + 1}-${rawStep.actionName || rawStep.agentName || ''}`
  const exists = message.steps.some(step => `${step?.index || ''}-${step?.actionName || step?.agentName || ''}` === nextKey)
  if (!exists) {
    message.steps = [...message.steps, rawStep]
  }
  message.activeTraceIndex = Number.isFinite(Number(stepIndex)) && Number(stepIndex) >= 0
    ? Number(stepIndex)
    : Math.max(message.steps.length - 1, 0)
}

function mergeStreamArtifacts(message = {}, payload = {}) {
  message.usedTools = dedupeTextList([...(Array.isArray(message.usedTools) ? message.usedTools : []), ...(Array.isArray(payload.usedTools) ? payload.usedTools : [])])
  message.safetyFlags = dedupeTextList([...(Array.isArray(message.safetyFlags) ? message.safetyFlags : []), ...(Array.isArray(payload.safetyFlags) ? payload.safetyFlags : [])])
  if (payload.goal) {
    message.goal = payload.goal
  }
  if (payload.progressSummary) {
    message.progressSummary = payload.progressSummary
  }
  if (payload.nextAction) {
    message.nextAction = payload.nextAction
  }
  message.blockers = dedupeTextList([...(Array.isArray(message.blockers) ? message.blockers : []), ...(Array.isArray(payload.blockers) ? payload.blockers : [])])
  message.degradationNotes = dedupeTextList([...(Array.isArray(message.degradationNotes) ? message.degradationNotes : []), ...(Array.isArray(payload.degradationNotes) ? payload.degradationNotes : [])])
  if (!message.knowledgeGraphSummary && payload.knowledgeGraphSummary) {
    message.knowledgeGraphSummary = payload.knowledgeGraphSummary
  }
  if ((!Array.isArray(message.knowledgeGraphItems) || message.knowledgeGraphItems.length === 0) && Array.isArray(payload.knowledgeGraphItems)) {
    message.knowledgeGraphItems = payload.knowledgeGraphItems
  }
  if (payload.usedCloudPatientData) {
    message.usedCloudPatientData = true
  }
}

function dedupeTextList(items = []) {
  return [...new Set((Array.isArray(items) ? items : []).filter(Boolean))]
}

function previewAnswerText(message = {}) {
  const preview = typeof message.previewContent === 'string' ? message.previewContent.trim() : ''
  if (preview) return preview
  if (message.streamPhase !== 'done') {
    return typeof message.content === 'string' ? message.content.trim() : ''
  }
  return ''
}

function finalAnswerText(message = {}) {
  const finalContent = typeof message.finalContent === 'string' ? message.finalContent.trim() : ''
  if (finalContent) return finalContent
  if (message.streamPhase === 'done') {
    return typeof message.content === 'string' ? message.content.trim() : ''
  }
  return ''
}

function shouldShowPreviewAnswer(message = {}) {
  if (isRetryNoticeMessage(message) || isRetryResultMessage(message)) return false
  return Boolean(previewAnswerText(message))
}

function shouldShowFinalAnswer(message = {}) {
  if (isRetryNoticeMessage(message) || isRetryResultMessage(message)) return false
  if (message.streamPhase !== 'done') return false
  const finalText = finalAnswerText(message)
  if (!finalText) return false
  const previewText = previewAnswerText(message)
  return !previewText || finalText !== previewText
}

function assistantResponseStatusText(message = {}) {
  if (isRetryNoticeMessage(message)) return '等待重跑完成'
  if (isRetryResultMessage(message)) return '重跑结果已返回'
  if (message.streamPhase === 'done') return '已完成完整分析'
  if (message.streamPhase === 'answering') return '正在补充分析'
  if (message.streamPhase === 'thinking') return '正在组织判断'
  return ''
}

function assistantPlaceholderText(message = {}) {
  const blockers = Array.isArray(message.blockers) ? message.blockers.filter(Boolean) : []
  if (isRetryNoticeMessage(message)) {
    return '管理员已触发后台重跑，正在等待新的执行结果...'
  }
  if (isRetryResultMessage(message)) {
    return '后台重跑结果已返回。'
  }
  if (blockers.length > 0) {
    return '当前任务仍被阻塞，请查看阻塞原因或到任务治理页继续处理。'
  }
  if (message.nextAction && message.streamPhase === 'done') {
    return `当前未形成最终补充内容，下一步：${message.nextAction}`
  }
  if (message.thoughtSummary) {
    return '系统正在整理当前分析结论...'
  }
  if (message.streamPhase === 'thinking') {
    return '系统正在组织判断...'
  }
  return '系统正在等待更多执行结果...'
}

function orchestrationControlAction(message = {}) {
  return message?.orchestrationTrace?.controlAction || ''
}

function isRetryNoticeMessage(message = {}) {
  return orchestrationControlAction(message) === 'manual_retry'
}

function isRetryResultMessage(message = {}) {
  return orchestrationControlAction(message) === 'manual_retry_result'
}

function previousComparableAssistantMessage(messageIndex = -1) {
  if (!Number.isInteger(messageIndex) || messageIndex <= 0 || !Array.isArray(messages.value)) return null
  for (let index = messageIndex - 1; index >= 0; index -= 1) {
    const candidate = messages.value[index]
    if (!candidate || candidate.role !== 'assistant') continue
    if (isRetryNoticeMessage(candidate) || isRetryResultMessage(candidate)) continue
    const content = finalAnswerText(candidate) || previewAnswerText(candidate) || candidate.content || ''
    if (typeof content === 'string' && content.trim()) {
      return candidate
    }
  }
  return null
}

function compactTextSnippet(content = '', maxLength = 110) {
  const normalized = typeof content === 'string'
    ? content.replace(/\s+/g, ' ').replace(/\*+/g, '').trim()
    : ''
  if (!normalized) return ''
  if (normalized.length <= maxLength) return normalized
  return `${normalized.slice(0, maxLength).trim()}...`
}

function normalizeRetryResultBody(message = {}) {
  const raw = finalAnswerText(message) || message.content || ''
  return raw.replace(/^管理员触发后台重新执行后，系统补充分析如下：\s*/u, '').trim()
}

function retryResultChanged(message = {}, messageIndex = -1) {
  const previous = previousComparableAssistantMessage(messageIndex)
  if (!previous) return false
  const previousText = compactTextSnippet(finalAnswerText(previous) || previewAnswerText(previous) || previous.content || '', 400)
  const currentText = compactTextSnippet(normalizeRetryResultBody(message), 400)
  return Boolean(previousText && currentText && previousText !== currentText)
}

function retryResultHint(message = {}, messageIndex = -1) {
  if (retryResultChanged(message, messageIndex)) {
    return '这次重跑返回了新的补充结果，下面按“重跑前 / 重跑后”做对照展示。'
  }
  return '这次已经完成重跑；如果前后差异不明显，说明在相同上下文下系统重新执行后结论基本一致。'
}

function hasRetryComparison(message = {}, messageIndex = -1) {
  return Boolean(previousComparableAssistantMessage(messageIndex))
}

function retryComparisonBefore(message = {}, messageIndex = -1) {
  const previous = previousComparableAssistantMessage(messageIndex)
  if (!previous) return ''
  return compactTextSnippet(finalAnswerText(previous) || previewAnswerText(previous) || previous.content || '')
}

function retryComparisonAfter(message = {}) {
  return compactTextSnippet(normalizeRetryResultBody(message))
}

function assistantThinkingText(message = {}) {
  const paragraphs = []
  const collaborationIntro = collaborationNarrative(message)
  if (collaborationIntro) {
    paragraphs.push(collaborationIntro)
  }

  if (message.thoughtSummary) {
    paragraphs.push(message.thoughtSummary)
    return paragraphs.join('\n\n')
  }

  if (message.goal) {
    paragraphs.push(`当前目标：${message.goal}`)
  }

  if (message.progressSummary) {
    paragraphs.push(`当前进展：${message.progressSummary}`)
  }

  if (message.answerPlan) {
    paragraphs.push(`处理目标：${message.answerPlan}`)
  }

  const steps = Array.isArray(message.steps) ? message.steps.slice(0, 3) : []
  if (steps.length > 0) {
    const stepSummary = steps
      .map(step => {
        const label = formatToolName(step?.actionName || step?.agentName)
        const summary = step?.observationSummary || step?.reasoningSummary || '已完成'
        return label && summary ? `${label} -> ${summary}` : (summary || label)
      })
      .filter(Boolean)
      .join('\n')
    if (stepSummary) {
      paragraphs.push(`处理过程：\n${stepSummary}`)
    }
  }

  const degradationNotes = Array.isArray(message.degradationNotes) ? message.degradationNotes : []
  if (degradationNotes.length > 0) {
    paragraphs.push(`降级处理：${degradationNotes.filter(Boolean).join('；')}`)
  }

  const safetyFlags = Array.isArray(message.safetyFlags) ? message.safetyFlags : []
  if (safetyFlags.length > 0) {
    paragraphs.push(`安全检查：${safetyFlags.slice(0, 2).map(formatSafetyFlag).join('、')}`)
  }

  const blockers = Array.isArray(message.blockers) ? message.blockers.filter(Boolean) : []
  if (blockers.length > 0) {
    paragraphs.push(`当前阻塞：${blockers.join('；')}`)
  }

  if (message.nextAction) {
    paragraphs.push(`下一步：${message.nextAction}`)
  }

  return paragraphs.join('\n\n')
}

function hasAssistantThinking(message = {}) {
  return Boolean(assistantThinkingText(message))
}

function shouldShowDetailedProcess(message = {}) {
  return !isDoctorPatientMode.value && hasCollaborationChain(message)
}

function shouldShowThoughtPanel(message = {}) {
  return !isDoctorPatientMode.value && hasAssistantThinking(message)
}

function shouldShowTracePanel(message = {}) {
  if (isDoctorPatientMode.value || message.role !== 'assistant') {
    return false
  }
  return message.usedTools.length > 0
    || message.steps.length > 0
    || message.safetyFlags.length > 0
    || Boolean(message.knowledgeGraphSummary)
    || message.knowledgeGraphItems.length > 0
}

function shouldShowReferenceCards(message = {}) {
  return !isDoctorPatientMode.value && message.role === 'assistant' && message.references.length > 0
}

function shouldShowCloudUsageBadge(message = {}) {
  return !isDoctorPatientMode.value && message.role === 'assistant' && didUseMedplum(message)
}

function shouldRenderSimplePatientAnswer(message = {}) {
  return isDoctorPatientMode.value
    && message.role === 'assistant'
    && !isRetryNoticeMessage(message)
    && !isRetryResultMessage(message)
}

function shouldShowPatientContextBrief(message = {}) {
  if (!isDoctorPatientMode.value || message.role !== 'assistant') {
    return false
  }
  return Boolean(currentPatientId.value || hasCollaborationChain(message) || didUseMedplum(message) || hasAssistantThinking(message))
}

function messageUsesNamedCapability(message = {}, names = []) {
  const normalizedNames = Array.isArray(names)
    ? names.map(item => String(item || '').toLowerCase()).filter(Boolean)
    : []
  if (normalizedNames.length === 0) {
    return false
  }
  const usedTools = Array.isArray(message.usedTools) ? message.usedTools : []
  const steps = Array.isArray(message.steps) ? message.steps : []
  return usedTools.some(tool => normalizedNames.some(name => String(tool || '').toLowerCase().includes(name)))
    || steps.some(step => normalizedNames.some(name => String(step?.actionName || step?.agentName || '').toLowerCase().includes(name)))
}

function patientContextBriefText(message = {}) {
  const patientLabel = selectedPatientName.value || '当前患者'
  const sources = ['基础档案']
  if (didUseMedplum(message)) {
    sources.push('云端健康档案')
  }
  if (messageUsesNamedCapability(message, ['risk_assessment'])) {
    sources.push('风险评估')
  }
  if (messageUsesNamedCapability(message, ['medical_evidence', 'knowledge_graph', 'evidence_support'])) {
    sources.push('慢病依据')
  }
  return `已带入${patientLabel}的${[...new Set(sources)].join('、')}，下面直接给出可用于当前患者沟通、随访和处置判断的建议。`
}

function patientContextBriefChips(message = {}) {
  const chips = []
  if (selectedPatientName.value) {
    chips.push(selectedPatientName.value)
  }
  chips.push('已加载患者资料')
  if (didUseMedplum(message)) {
    chips.push('已结合云端档案')
  }
  if (messageUsesNamedCapability(message, ['risk_assessment'])) {
    chips.push('已结合风险评估')
  }
  if (messageUsesNamedCapability(message, ['medical_evidence', 'knowledge_graph', 'evidence_support'])) {
    chips.push('已结合慢病依据')
  }
  return [...new Set(chips)]
}

function simplePatientAnswerText(message = {}) {
  return finalAnswerText(message)
    || previewAnswerText(message)
    || String(message.content || '').trim()
    || assistantPlaceholderText(message)
}

function toggleThought(message = {}) {
  message.thoughtExpanded = !message.thoughtExpanded
}

function canOpenReferenceDetail(reference = {}) {
  return Boolean(reference.sourceType && reference.refId)
}

function startLoadingTrace() {
  stopLoadingTrace()
  loadingTraceSteps.value = buildLoadingTraceSteps()
  loadingTraceIndex.value = 0
  ensureLoadingTraceTimer()
}

function buildLoadingTraceSteps() {
  const steps = []
  if (currentPatientId.value) {
    steps.push('正在读取患者资料')
    steps.push('正在读取云端健康档案')
    steps.push('正在分析近期健康记录')
    steps.push('正在运行规则评估')
  } else {
    steps.push('正在理解问题意图')
  }
  if (userStore.isDoctor) {
    steps.push('正在整理知识依据')
  }
  steps.push('正在检索慢病管理知识')
  steps.push('正在生成最终建议')
  return steps
}

function ensureLoadingTraceTimer() {
  if (loadingTraceTimer || loadingTraceSteps.value.length === 0) {
    return
  }
  loadingTraceTimer = window.setInterval(() => {
    loadingTraceIndex.value = (loadingTraceIndex.value + 1) % loadingTraceSteps.value.length
  }, 1200)
}

function stopLoadingTrace() {
  if (loadingTraceTimer) {
    window.clearInterval(loadingTraceTimer)
    loadingTraceTimer = null
  }
  loadingTraceSteps.value = []
  loadingTraceIndex.value = 0
}

function reconcileConversationRuntime() {
  const pendingAssistantIndex = resolvePendingAssistantIndex(messages.value)
  const hasPendingMessage = pendingAssistantIndex >= 0

  if (hasPendingMessage) {
    streamingAssistantIndex.value = pendingAssistantIndex
    loading.value = true
    if (loadingTraceSteps.value.length === 0) {
      loadingTraceSteps.value = buildLoadingTraceSteps()
    }
    ensureLoadingTraceTimer()
    return
  }

  streamingAssistantIndex.value = -1
  loading.value = false
  if (loadingTraceSteps.value.length > 0 || loadingTraceTimer) {
    stopLoadingTrace()
  }
}

function isSubAgentAction(actionName) {
  return typeof actionName === 'string' && actionName.endsWith('_agent')
}

function collaborationCoordinatorLabel() {
  return '慢病顾问主流程'
}

function collaborationSubAgents(message = {}) {
  const steps = Array.isArray(message.steps) ? message.steps : []
  return [...new Set(
    steps
      .map(step => step?.actionName || step?.agentName || '')
      .filter(isSubAgentAction)
  )]
}

function collaborationToolLabels(message = {}) {
  const tools = Array.isArray(message.usedTools) ? message.usedTools : []
  return [...new Set(tools.filter(Boolean))]
}

function collaborationSubAgentText(message = {}) {
  const labels = collaborationSubAgents(message).map(formatToolName).filter(Boolean)
  return labels.length > 0 ? labels.join(' -> ') : '暂无额外步骤'
}

function collaborationToolText(message = {}) {
  const labels = collaborationToolLabels(message).map(formatToolName).filter(Boolean)
  return labels.length > 0 ? labels.join(' -> ') : ''
}

function collaborationNarrative(message = {}) {
  const subAgentLabels = collaborationSubAgents(message).map(formatToolName).filter(Boolean)
  const toolLabels = collaborationToolLabels(message).map(formatToolName).filter(Boolean)
  if (subAgentLabels.length === 0 && toolLabels.length === 0) {
    return ''
  }

  const parts = [`这次回答不是单一步骤直接完成，而是由${collaborationCoordinatorLabel()}先拆解问题。`]
  if (subAgentLabels.length > 0) {
    parts.push(`随后按这些专业环节继续整理：${subAgentLabels.join('、')}。`)
  }
  if (toolLabels.length > 0) {
    parts.push(`本次实际调用的相关能力包括：${toolLabels.join('、')}。`)
  }
  return parts.join('')
}

function hasDegradation(message = {}) {
  return Array.isArray(message.degradationNotes) && message.degradationNotes.length > 0
}

function collaborationStageText(message = {}) {
  const stepCount = Array.isArray(message.steps) ? message.steps.length : 0
  const subAgentCount = collaborationSubAgents(message).length
  const toolCount = collaborationToolLabels(message).length
  const hasAnswer = Boolean(String(message.content || '').trim())
  const hasReplan = Array.isArray(message.steps) && message.steps.some(step => String(step?.actionName || '').toLowerCase() === 'replan')
  if (hasAnswer) return '汇总完成'
  if (hasReplan) return '重规划中'
  if (toolCount > 0) return '能力调用'
  if (subAgentCount > 0) return '任务委派'
  if (stepCount > 0 || String(message.thoughtSummary || '').trim()) return '任务拆解'
  return '待启动'
}

function hasCollaborationChain(message = {}) {
  return collaborationSubAgents(message).length > 0 || collaborationToolLabels(message).length > 0
}

function formatToolName(toolName) {
  const toolNameMap = {
    replan: '自主重规划',
    patient_profile_agent: '患者画像整理',
    patient_context: '患者资料读取',
    patient_context_agent: '患者上下文整理',
    medplum_patient_insight: '云端健康档案',
    medplum_patient_insight_agent: '云端档案整理',
    risk_assessment: '规则风险评估',
    risk_assessment_agent: '风险评估整理',
    evidence_support_agent: '依据整理',
    medical_evidence_search: '慢病证据检索',
    medical_evidence_agent: '慢病依据整理',
    knowledge_graph_support: '知识依据整理',
    knowledge_graph_agent: '知识内容整理'
  }
  return toolNameMap[toolName] || toolName || '未知工具'
}

function traceToolsForMessage(message = {}) {
  const rawTools = Array.isArray(message.usedTools) ? message.usedTools : []
  const tools = rawTools.map(formatToolName).filter(Boolean)
  const hasMedplumTrace = tools.some(tool => String(tool).includes('Medplum'))
  if (didUseMedplum(message) && !hasMedplumTrace) {
    tools.push('云端档案整理')
  }
  return [...new Set(tools)]
}

function formatSafetyFlag(flag) {
  const flagMap = {
    high_risk_detected: '发现高风险信号',
    external_data_degraded: '外部数据已降级',
    guard_added_medical_escalation: '已补充就医提醒',
    guard_replaced_unsupported_patient_assertion: '已拦截无依据患者结论',
    guard_anonymized_patient_name: '已匿名患者姓名',
    medical_escalation_included: '已包含就医提醒'
  }
  return flagMap[flag] || flag
}

function didUseMedplum(message = {}) {
  if (message.usedCloudPatientData === true) {
    return true
  }
  const usedTools = Array.isArray(message.usedTools) ? message.usedTools : []
  const steps = Array.isArray(message.steps) ? message.steps : []
  return usedTools.some(tool => String(tool).toLowerCase().includes('medplum'))
    || steps.some(step => String(step?.actionName || step?.agentName || '').toLowerCase().includes('medplum'))
}

function formatSourceType(type) {
  if (!type) return ''
  const sourceTypeMap = {
    KNOWLEDGE: '知识库',
    GUIDELINE: '指南',
    EDUCATION: '宣教',
    VIDEO_TRANSCRIPT: '视频讲稿'
  }
  return sourceTypeMap[type] || type
}

function formatReferenceMeta(reference = {}) {
  const parts = []
  if (reference.source) parts.push(reference.source)
  if (reference.section) parts.push(reference.section)
  if (reference.publishedAt) parts.push(dayjs(reference.publishedAt).format('YYYY-MM-DD'))
  if (reference.score != null && reference.score !== '') {
    const score = Number(reference.score)
    if (!Number.isNaN(score)) {
      parts.push(`匹配度 ${score.toFixed(2)}`)
    }
  }
  return parts.join(' · ') || '系统知识检索结果'
}

async function openReferenceDetail(reference = {}) {
  if (!canOpenReferenceDetail(reference)) {
    ElMessage.warning('该引用暂不支持查看详情')
    return
  }

  selectedReference.value = reference
  referenceDrawerVisible.value = true
}

function resetConversation(nextSessionId = null) {
  debugChat('resetConversation', {
    nextSessionId,
    currentSessionId: sessionId.value,
    currentMessageCount: messages.value.length
  })
  stopLoadingTrace()
  sessionId.value = nextSessionId
  streamingAssistantIndex.value = -1
  messages.value = []
  inputText.value = ''
}

function startRemoteSessionSync() {
  if (remoteSyncTimer != null) return
  remoteSyncTimer = window.setInterval(() => {
    refreshActiveSessionFromServer()
  }, CHAT_REMOTE_SYNC_INTERVAL_MS)
}

function stopRemoteSessionSync() {
  if (remoteSyncTimer == null) return
  window.clearInterval(remoteSyncTimer)
  remoteSyncTimer = null
}

function debugChat(event, payload = {}) {
  if (!CHAT_DEBUG) return
  console.log(`[ChatDebug] ${event}`, payload)
}

function createDraftSessionId() {
  return `${DRAFT_SESSION_PREFIX}${Date.now()}`
}

function isDraftSessionId(value) {
  return typeof value === 'string' && value.startsWith(DRAFT_SESSION_PREFIX)
}

function syncSessionRail() {
  sessions.value = mergeSessionsWithLocalState(
    normalizeArray(sessions.value).filter(item => !isDraftSessionId(item?.id))
  )
  persistChatArchive()
}

function mergeArchivedSessions(remoteSessions = [], cachedSessions = []) {
  const merged = [...normalizeArray(remoteSessions)]
  normalizeArray(cachedSessions).forEach(item => {
    if (!item?.id || merged.some(session => session?.id === item.id)) {
      return
    }
    merged.push(item)
  })
  return merged.sort((a, b) => new Date(b?.lastMessageAt || 0).getTime() - new Date(a?.lastMessageAt || 0).getTime())
}

function shouldApplyRemoteMessages(remoteMessages = []) {
  const localMessages = normalizeArray(messages.value)
  if (remoteMessages.length !== localMessages.length) {
    return true
  }
  if (remoteMessages.length === 0) {
    return false
  }
  const remoteLast = remoteMessages[remoteMessages.length - 1]
  const localLast = localMessages[localMessages.length - 1]
  return remoteLast?.id !== localLast?.id
    || remoteLast?.timestamp !== localLast?.timestamp
    || remoteLast?.content !== localLast?.content
    || remoteLast?.finalContent !== localLast?.finalContent
    || remoteLast?.previewContent !== localLast?.previewContent
    || remoteLast?.streamPhase !== localLast?.streamPhase
    || remoteLast?.thoughtSummary !== localLast?.thoughtSummary
    || normalizeArray(remoteLast?.steps).length !== normalizeArray(localLast?.steps).length
    || normalizeArray(remoteLast?.references).length !== normalizeArray(localLast?.references).length
}

function persistChatArchive() {
  try {
    const existingArchive = readChatArchive()
    const mergedMessagesBySession = {
      ...(existingArchive?.messagesBySession && typeof existingArchive.messagesBySession === 'object'
        ? existingArchive.messagesBySession
        : {})
    }
    if (sessionId.value && messages.value.length > 0) {
      mergedMessagesBySession[sessionId.value] = messages.value.map(message => normalizeMessage(message))
    }
    const payload = {
      sessions: normalizeArray(sessions.value).map(session => ({
        id: session.id,
        title: session.title || '未命名对话',
        patientId: session.patientId ?? null,
        lastMessageAt: session.lastMessageAt || null
      })),
      activeSessionId: sessionId.value || null,
      messagesBySession: mergedMessagesBySession,
      runtime: {
        inputText: inputText.value || '',
        loading: Boolean(loading.value || hasPendingAssistantMessage()),
        streamingAssistantIndex: Number.isFinite(Number(streamingAssistantIndex.value)) ? Number(streamingAssistantIndex.value) : -1,
        loadingTraceSteps: normalizeArray(loadingTraceSteps.value),
        loadingTraceIndex: Number.isFinite(Number(loadingTraceIndex.value)) ? Number(loadingTraceIndex.value) : 0
      }
    }
    localStorage.setItem(chatArchiveStorageKey.value, JSON.stringify(payload))
  } catch (e) { /* ignore */ }
}

function restoreChatArchive() {
  const archive = readChatArchive()
  if (!archive) return
  sessions.value = normalizeArray(archive.sessions)
  if (archive.activeSessionId) {
    sessionId.value = archive.activeSessionId
    messages.value = readArchivedMessages(archive.activeSessionId).map(normalizeMessage)
  }
  const runtime = archive.runtime && typeof archive.runtime === 'object' ? archive.runtime : {}
  inputText.value = typeof runtime.inputText === 'string' ? runtime.inputText : ''
  loading.value = Boolean(runtime.loading)
  streamingAssistantIndex.value = Number.isFinite(Number(runtime.streamingAssistantIndex)) ? Number(runtime.streamingAssistantIndex) : -1
  loadingTraceSteps.value = normalizeArray(runtime.loadingTraceSteps)
  loadingTraceIndex.value = Number.isFinite(Number(runtime.loadingTraceIndex)) ? Number(runtime.loadingTraceIndex) : 0
  if (loading.value && loadingTraceSteps.value.length > 0) {
    ensureLoadingTraceTimer()
  }
}

function readChatArchive() {
  try {
    const raw = localStorage.getItem(chatArchiveStorageKey.value)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return parsed && typeof parsed === 'object' ? parsed : null
  } catch (e) {
    return null
  }
}

function readArchivedSessions() {
  return normalizeArray(readChatArchive()?.sessions)
}

function readArchivedMessages(targetSessionId) {
  if (!targetSessionId) return []
  const archive = readChatArchive()
  const messagesBySession = archive?.messagesBySession
  if (!messagesBySession || typeof messagesBySession !== 'object') {
    return []
  }
  return normalizeArray(messagesBySession[targetSessionId])
}

function resolvePreferredSessionId(options = {}) {
  const preferRealSession = options.preferRealSession === true
  const archive = readChatArchive()
  const archivedActiveSessionId = archive?.activeSessionId
  if (archivedActiveSessionId
    && sessions.value.some(session => session?.id === archivedActiveSessionId)
    && (!preferRealSession || !isDraftSessionId(archivedActiveSessionId))) {
    return archivedActiveSessionId
  }
  const candidates = preferRealSession
    ? sessions.value.filter(session => !isDraftSessionId(session?.id))
    : sessions.value
  return candidates[0]?.id || null
}

function buildOwnerParams() {
  return {
    ownerRole: currentOwnerRole.value,
    ownerUserId: currentOwnerUserId.value
  }
}

function buildSessionQueryParams() {
  const params = buildOwnerParams()
  if (currentPatientId.value != null) {
    params.patientId = currentPatientId.value
  }
  return params
}

function persistSelectedPatient() {
  if (!userStore.isDoctor) return
  if (selectedPatientId.value) {
    localStorage.setItem(selectedPatientStorageKey.value, String(selectedPatientId.value))
  } else {
    localStorage.removeItem(selectedPatientStorageKey.value)
  }
}

function persistDoctorChatMode() {
  if (!userStore.isDoctor) return
  localStorage.setItem(doctorChatModeStorageKey.value, doctorChatMode.value)
}

function restoreDoctorChatMode() {
  if (!userStore.isDoctor) return
  const savedValue = localStorage.getItem(doctorChatModeStorageKey.value)
  doctorChatMode.value = savedValue === 'patient' ? 'patient' : 'general'
}

function restoreSelectedPatient() {
  if (!userStore.isDoctor || patients.value.length === 0) return
  const savedValue = localStorage.getItem(selectedPatientStorageKey.value)
  const savedId = savedValue ? Number(savedValue) : null
  if (savedId && patients.value.some(p => p.id === savedId)) {
    selectedPatientId.value = savedId
    return
  }
  if (patients.value.length === 1) {
    selectedPatientId.value = patients.value[0].id
  }
}
</script>

<style scoped>
.chat-page {
  display: grid;
  gap: 22px;
}

.chat-page--doctor {
  --chat-accent: #294a60;
  --chat-accent-strong: #1f3849;
  --chat-accent-soft: rgba(198, 220, 232, 0.42);
  --chat-user-bubble: linear-gradient(135deg, #25465c 0%, #3d6780 100%);
  --chat-assistant-bubble: rgba(255, 255, 255, 0.92);
  --chat-surface: rgba(255, 255, 255, 0.88);
  --chat-surface-soft: rgba(247, 249, 250, 0.9);
  --chat-border: rgba(31, 51, 64, 0.08);
}

.chat-page--patient {
  --chat-accent: #2e6f8b;
  --chat-accent-strong: #24576d;
  --chat-accent-soft: rgba(203, 229, 238, 0.46);
  --chat-user-bubble: linear-gradient(135deg, #2d6882 0%, #4c92a8 100%);
  --chat-assistant-bubble: rgba(255, 255, 255, 0.92);
  --chat-surface: rgba(255, 255, 255, 0.88);
  --chat-surface-soft: rgba(247, 250, 251, 0.94);
  --chat-border: rgba(39, 81, 104, 0.08);
}

.chat-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  border-radius: 28px;
  color: #f7fbff;
  box-shadow: 0 28px 60px rgba(19, 33, 43, 0.16);
}

.chat-page--doctor .chat-hero {
  background:
    radial-gradient(circle at top right, rgba(142, 176, 197, 0.18), transparent 34%),
    linear-gradient(135deg, #22313d 0%, #34495a 52%, #466173 100%);
}

.chat-page--patient .chat-hero {
  background:
    radial-gradient(circle at top right, rgba(150, 198, 214, 0.2), transparent 34%),
    linear-gradient(135deg, #1f4f63 0%, #2f6f8b 52%, #4c91a5 100%);
}

.chat-hero__content {
  max-width: 760px;
}

.chat-hero__eyebrow,
.session-rail__label,
.chat-welcome__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  letter-spacing: 0.1em;
}

.chat-hero h1 {
  margin: 18px 0 10px;
  font-size: 34px;
  line-height: 1.05;
  color: #ffffff;
}

.chat-hero p {
  margin: 0;
  max-width: 640px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(237, 244, 249, 0.82);
}

.chat-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.chat-hero__controls {
  width: 300px;
}

.chat-control-card {
  padding: 18px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.chat-control-card__label {
  font-size: 12px;
  letter-spacing: 0.1em;
  color: rgba(237, 244, 249, 0.7);
}

.chat-control-card__body {
  display: grid;
  gap: 12px;
  margin-top: 12px;
}

.chat-control-card__select,
.chat-control-card__button {
  width: 100%;
}

.chat-control-card__hint {
  font-size: 13px;
  line-height: 1.7;
  color: rgba(237, 244, 249, 0.8);
}

.chat-workspace {
  display: grid;
  grid-template-columns: 290px 1fr;
  gap: 18px;
  height: calc(100vh - 230px);
  min-height: 620px;
}

.session-rail,
.conversation-panel {
  border-radius: 26px;
  border: 1px solid var(--chat-border);
  background: var(--chat-surface);
  box-shadow: 0 20px 44px rgba(17, 28, 38, 0.08);
}

.session-rail {
  display: flex;
  flex-direction: column;
  padding: 20px 16px;
  overflow: hidden;
}

.session-rail__head {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 12px;
}

.session-rail__label,
.chat-welcome__eyebrow {
  background: var(--chat-accent-soft);
  color: var(--chat-accent);
}

.session-rail__head h2 {
  margin: 10px 0 0;
  font-size: 22px;
  color: #1f3340;
}

.session-rail__hint {
  margin: 12px 0 16px;
  font-size: 13px;
  line-height: 1.7;
  color: #6a7c87;
}

.session-rail :deep(.el-scrollbar) {
  flex: 1;
}

.session-item {
  padding: 12px 14px;
  border-radius: 16px;
  cursor: pointer;
  margin-bottom: 10px;
  transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.2s ease;
  background: rgba(247, 249, 250, 0.88);
  border: 1px solid transparent;
}

.session-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 12px 24px rgba(17, 28, 38, 0.08);
}

.session-item.active {
  background: var(--chat-accent-soft);
  border-color: rgba(80, 126, 151, 0.18);
}

.session-title {
  font-weight: 700;
  font-size: 14px;
  color: #2b3f4d;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 12px;
  color: #81929d;
  margin-top: 4px;
}

.conversation-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.conversation-panel__scroll {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
  background: linear-gradient(180deg, rgba(249, 250, 251, 0.5) 0%, rgba(244, 247, 248, 0.82) 100%);
}

.chat-welcome {
  min-height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 20px;
}

.chat-welcome__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 78px;
  height: 78px;
  border-radius: 24px;
  background: var(--chat-accent-soft);
  color: var(--chat-accent);
  margin-bottom: 16px;
}

.chat-welcome h3 {
  margin: 16px 0 10px;
  font-size: 28px;
  color: #1f3340;
}

.chat-welcome p {
  margin: 0;
  max-width: 620px;
  color: #6d7d87;
  line-height: 1.8;
}

.chat-context-notice {
  margin: 16px 0 0;
  padding: 10px 14px;
  border-radius: 16px;
  font-size: 13px;
  line-height: 1.7;
}

.chat-context-notice--info {
  color: #1f5a7a;
  background: rgba(215, 234, 245, 0.7);
}

.chat-context-notice--warning {
  color: #9c6500;
  background: rgba(255, 244, 220, 0.92);
}

.chat-context-notice--success {
  color: #28614d;
  background: rgba(226, 244, 235, 0.92);
}

.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: center;
  max-width: 620px;
  margin-top: 20px;
}

.chat-orchestration-overview {
  width: min(680px, 100%);
  margin-top: 18px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.14);
  border: 1px solid rgba(255, 255, 255, 0.16);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.08);
}

.chat-orchestration-overview__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #f7fbff;
}

.chat-orchestration-overview__head strong {
  font-size: 14px;
}

.chat-orchestration-overview__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.chat-orchestration-overview__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.chat-orchestration-overview__metric {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.12);
}

.chat-orchestration-overview__metric span {
  display: block;
  font-size: 11px;
  color: rgba(237, 244, 249, 0.74);
}

.chat-orchestration-overview__metric strong {
  display: block;
  margin-top: 6px;
  font-size: 18px;
  color: #ffffff;
}

.chat-orchestration-overview__agents {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.7;
  color: rgba(237, 244, 249, 0.82);
}

.chat-msg {
  display: flex;
  gap: 12px;
  margin-bottom: 18px;
}

.chat-msg.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  flex-shrink: 0;
}

.msg-avatar__badge {
  border: 0;
  box-shadow: 0 12px 24px rgba(17, 28, 38, 0.12);
}

.msg-avatar__badge--user {
  background: var(--chat-accent-strong);
  color: #ffffff;
}

.msg-avatar__badge--assistant {
  background: linear-gradient(135deg, #57a882 0%, #79bf9a 100%);
  color: #ffffff;
}

.msg-content {
  display: flex;
  flex-direction: column;
  max-width: min(760px, calc(100% - 52px));
}

.chat-msg.user .msg-content {
  align-items: flex-end;
  margin-left: auto;
}

.msg-name {
  font-size: 12px;
  color: #7f909a;
  margin-bottom: 6px;
}

.msg-bubble {
  display: inline-block;
  padding: 14px 16px;
  border-radius: 18px;
  max-width: 100%;
  line-height: 1.75;
  font-size: 14px;
  word-break: break-word;
  box-shadow: 0 14px 30px rgba(17, 28, 38, 0.08);
}

.chat-msg.user .msg-bubble {
  background: var(--chat-user-bubble);
  color: #fff;
  border-top-right-radius: 6px;
}

.chat-msg.assistant .msg-bubble {
  background: var(--chat-assistant-bubble);
  color: #2d414f;
  border-top-left-radius: 6px;
}

.msg-bubble--structured {
  display: grid;
  gap: 10px;
  min-width: min(560px, 100%);
}

.msg-section-title {
  margin-bottom: 6px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.08em;
  color: #60727d;
}

.msg-thought-shell {
  display: grid;
  gap: 8px;
}

.msg-orchestration-banner {
  display: grid;
  gap: 12px;
  margin-bottom: 12px;
  padding: 14px 15px;
  border-radius: 18px;
  background:
    linear-gradient(135deg, rgba(237, 246, 255, 0.92), rgba(245, 251, 255, 0.96)),
    radial-gradient(circle at top right, rgba(41, 74, 96, 0.12), transparent 42%);
  border: 1px solid rgba(112, 145, 162, 0.22);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.75);
}

.msg-orchestration-banner__head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 10px;
}

.msg-orchestration-banner__eyebrow {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #648090;
}

.msg-orchestration-banner__head strong {
  display: block;
  margin-top: 4px;
  font-size: 14px;
  line-height: 1.5;
  color: #203746;
}

.msg-orchestration-banner__status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(220, 252, 231, 0.88);
  color: #0f766e;
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.msg-orchestration-banner__status--degraded {
  background: rgba(254, 242, 242, 0.94);
  color: #b91c1c;
}

.msg-orchestration-banner__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
}

.msg-orchestration-banner__metric {
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.msg-orchestration-banner__metric span {
  display: block;
  font-size: 11px;
  color: #6a7d88;
}

.msg-orchestration-banner__metric strong {
  display: block;
  margin-top: 6px;
  font-size: 16px;
  color: #183244;
}

.msg-orchestration-banner__agents {
  font-size: 12px;
  line-height: 1.7;
  color: #4b6675;
}

.msg-orchestration-banner__planning {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.msg-orchestration-banner__planning-item {
  display: grid;
  gap: 5px;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid rgba(148, 163, 184, 0.16);
}

.msg-orchestration-banner__planning-item span {
  font-size: 11px;
  color: #70818d;
}

.msg-orchestration-banner__planning-item strong {
  font-size: 13px;
  line-height: 1.6;
  color: #173040;
}

.msg-orchestration-banner__planning-item--warning {
  background: rgba(255, 248, 235, 0.96);
  border-color: rgba(245, 158, 11, 0.2);
}

.msg-thought-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 0;
  background: transparent;
  border: 0;
  color: #6e808b;
  cursor: pointer;
  text-align: left;
}

.msg-thought-toggle__label {
  font-size: 13px;
  font-weight: 700;
  color: #72848f;
}

.msg-thought-toggle__hint {
  font-size: 12px;
  color: #98a8b1;
}

.msg-thought {
  padding: 12px 14px;
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(243, 246, 248, 0.96) 0%, rgba(237, 242, 245, 0.92) 100%);
  border: 1px solid rgba(126, 154, 170, 0.16);
  color: #62747f;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.msg-collaboration {
  display: grid;
  gap: 8px;
  margin-bottom: 10px;
  padding: 11px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.68);
  border: 1px solid rgba(126, 154, 170, 0.18);
}

.msg-collaboration__title {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: #5f7480;
}

.msg-collaboration__row {
  display: grid;
  grid-template-columns: 54px 1fr;
  gap: 10px;
  align-items: start;
}

.msg-collaboration__label {
  font-size: 12px;
  font-weight: 700;
  color: #7a8c96;
}

.msg-collaboration__value {
  font-size: 12px;
  line-height: 1.75;
  color: #4f6571;
}

.msg-output {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid rgba(126, 154, 170, 0.14);
  box-shadow: 0 14px 30px rgba(17, 28, 38, 0.06);
}

.msg-patient-context-brief {
  display: grid;
  gap: 10px;
  margin-bottom: 14px;
  padding: 14px 15px;
  border-radius: 16px;
  background: linear-gradient(135deg, rgba(239, 249, 244, 0.96), rgba(247, 252, 249, 0.98));
  border: 1px solid rgba(125, 183, 149, 0.26);
}

.msg-patient-context-brief__head {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 10px;
}

.msg-patient-context-brief__eyebrow {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #5f8b72;
}

.msg-patient-context-brief__head strong {
  display: block;
  margin-top: 4px;
  font-size: 14px;
  line-height: 1.5;
  color: #234634;
}

.msg-patient-context-brief__status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(220, 252, 231, 0.92);
  color: #1f7a4f;
  font-size: 11px;
  font-weight: 800;
  white-space: nowrap;
}

.msg-patient-context-brief__text {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: #456154;
}

.msg-patient-context-brief__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.msg-patient-context-brief__chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  border: 1px solid rgba(125, 183, 149, 0.22);
  color: #3f6550;
  font-size: 12px;
  font-weight: 600;
}

.msg-output__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.msg-output__status {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 244, 220, 0.92);
  color: #9c6500;
  font-size: 11px;
  font-weight: 700;
  white-space: nowrap;
}

.msg-output__status--done {
  background: rgba(226, 244, 235, 0.92);
  color: #28614d;
}

.msg-output-stage + .msg-output-stage {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid rgba(126, 154, 170, 0.12);
}

.msg-output-stage--simple {
  margin-top: 0;
  padding-top: 0;
  border-top: 0;
}

.msg-output-stage__title {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: #62747f;
}

.msg-retry-card {
  margin-bottom: 12px;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid rgba(126, 154, 170, 0.18);
  background: linear-gradient(180deg, rgba(250, 252, 253, 0.98) 0%, rgba(242, 247, 249, 0.96) 100%);
}

.msg-retry-card--notice {
  border-color: rgba(220, 168, 68, 0.28);
  background: linear-gradient(180deg, rgba(255, 249, 236, 0.98) 0%, rgba(255, 244, 220, 0.94) 100%);
}

.msg-retry-card--result {
  border-color: rgba(55, 129, 97, 0.24);
  background: linear-gradient(180deg, rgba(236, 249, 242, 0.98) 0%, rgba(226, 244, 235, 0.94) 100%);
}

.msg-retry-card__eyebrow {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #6a7c86;
}

.msg-retry-card__title {
  margin-top: 6px;
  font-size: 15px;
  font-weight: 800;
  color: #203541;
}

.msg-retry-card__hint {
  margin: 8px 0 10px;
  font-size: 12px;
  line-height: 1.7;
  color: #5d717c;
}

.msg-retry-compare {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.msg-retry-compare__item {
  padding: 12px 14px;
  border-radius: 14px;
  border: 1px solid rgba(126, 154, 170, 0.18);
  background: rgba(255, 255, 255, 0.68);
}

.msg-retry-compare__label {
  display: inline-block;
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #60737e;
}

.msg-retry-compare__item p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #203541;
}

.msg-output-placeholder {
  font-size: 13px;
  line-height: 1.8;
  color: #8b9aa3;
}

.msg-thinking-live {
  margin-top: 8px;
  font-size: 12px;
  color: #7c8d97;
}

.msg-references,
.agent-trace,
.agent-loading {
  margin-top: 12px;
  max-width: 100%;
}

.cloud-usage-badge {
  margin-top: 10px;
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: #256c57;
  background: rgba(220, 243, 233, 0.92);
  border: 1px solid rgba(73, 157, 118, 0.18);
}

.agent-trace {
  background: var(--chat-surface-soft);
  border: 1px solid rgba(126, 154, 170, 0.12);
  border-radius: 16px;
  padding: 12px 14px;
}

.agent-trace-title,
.msg-references-title {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #62747f;
  margin-bottom: 8px;
}

.agent-step-list,
.reference-list,
.graph-support-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.graph-support-item,
.reference-card {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(126, 154, 170, 0.12);
  border-radius: 14px;
  padding: 10px 12px;
}

.agent-step-top,
.reference-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.agent-step-reason,
.agent-step-observation,
.reference-snippet,
.relation-evidence {
  font-size: 12px;
  line-height: 1.7;
  color: #60727d;
}

.agent-loading-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.agent-loading-item {
  font-size: 12px;
  color: #83949e;
  background: rgba(237, 242, 244, 0.92);
  border-radius: 999px;
  padding: 6px 10px;
  transition: all 0.2s ease;
}

.agent-loading-item.active {
  color: var(--chat-accent);
  background: var(--chat-accent-soft);
}

.graph-support-item--button,
.reference-card--button {
  width: 100%;
  appearance: none;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.graph-support-item--button:hover,
.reference-card--button:hover {
  border-color: rgba(112, 147, 168, 0.25);
  box-shadow: 0 12px 24px rgba(64, 158, 255, 0.08);
  transform: translateY(-1px);
}

.graph-support-item:disabled {
  cursor: default;
  opacity: 1;
}

.graph-support-title,
.reference-name {
  font-size: 13px;
  font-weight: 700;
  color: #2c4150;
}

.graph-support-meta,
.reference-meta {
  margin-top: 6px;
  font-size: 12px;
  color: #8a99a3;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.graph-support-topics {
  margin-top: 6px;
}

.reference-type {
  flex-shrink: 0;
  font-size: 12px;
  color: #4b8d63;
  background: #eef8f2;
  border-radius: 999px;
  padding: 2px 8px;
}

.reference-action {
  margin-top: 8px;
  font-size: 12px;
  color: var(--chat-accent);
  font-weight: 600;
}

.chat-input {
  padding: 18px 20px;
  border-top: 1px solid var(--chat-border);
  background: rgba(255, 255, 255, 0.92);
}

.chat-input__hint {
  margin-top: 8px;
  font-size: 12px;
  color: #a0aab2;
}

.typing {
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 14px 18px;
}

.typing .dot {
  width: 8px;
  height: 8px;
  background: #c0c4cc;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out;
}

.typing .dot:nth-child(1) { animation-delay: -0.32s; }
.typing .dot:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

@media (max-width: 1200px) {
  .chat-workspace {
    grid-template-columns: 260px 1fr;
  }
}

@media (max-width: 960px) {
  .chat-hero,
  .chat-workspace {
    grid-template-columns: 1fr;
    display: grid;
  }

  .chat-hero {
    display: flex;
    flex-direction: column;
  }

  .chat-hero__controls {
    width: 100%;
  }

  .chat-workspace {
    height: auto;
    min-height: 0;
  }

  .session-rail {
    max-height: 360px;
  }

  .conversation-panel {
    min-height: 620px;
  }
}

@media (max-width: 640px) {
  .chat-hero,
  .session-rail,
  .conversation-panel,
  .chat-control-card {
    border-radius: 22px;
  }

  .chat-hero {
    padding: 20px;
  }

  .session-rail,
  .chat-input {
    padding: 16px;
  }

  .conversation-panel__scroll {
    padding: 18px;
  }

  .chat-hero h1 {
    font-size: 28px;
  }

  .chat-orchestration-overview__metrics {
    grid-template-columns: 1fr;
  }

  .msg-content {
    max-width: calc(100% - 48px);
  }

  .msg-orchestration-banner__head {
    flex-direction: column;
  }

  .msg-orchestration-banner__metrics {
    grid-template-columns: 1fr;
  }

  .msg-orchestration-banner__planning {
    grid-template-columns: 1fr;
  }
}
</style>
