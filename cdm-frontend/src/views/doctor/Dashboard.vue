<template>
  <div class="dashboard-view" v-loading="loading">
    <div class="dashboard-commandbar">
      <div class="dashboard-commandbar__title">
        <span class="dashboard-commandbar__eyebrow">DOCTOR WORKSPACE</span>
        <strong>慢病管理工作台</strong>
      </div>
      <div class="dashboard-commandbar__meta">
        <div class="command-pill">
          <span>消息通道</span>
          <strong>{{ notificationStore.connected ? '已连接' : '未连接' }}</strong>
        </div>
        <div class="command-pill">
          <span>运行区域</span>
          <strong>{{ dashboardAreaLabel }}</strong>
        </div>
      </div>
    </div>

    <div class="dashboard-header">
      <div>
        <span class="dashboard-header__eyebrow">CARE OVERVIEW</span>
        <h2>当前重点工作</h2>
        <p>围绕患者风险、随访执行、病历处理和健康宣教，集中展示当前最需要医生关注的事项。</p>
      </div>
      <div class="dashboard-actions">
        <div class="dashboard-clock">
          <span class="dashboard-clock__label">系统时间</span>
          <strong>{{ currentTimeText }}</strong>
          <small>{{ currentDateText }}</small>
        </div>
        <el-tag class="dashboard-actions__tag" type="info" effect="plain">上次刷新：{{ lastUpdatedText }}</el-tag>
        <el-button class="dashboard-actions__refresh" :loading="refreshing" @click="loadDashboard(true)">刷新数据</el-button>
      </div>
    </div>

    <div class="dashboard-marquee">
      <div class="dashboard-marquee__label">实时告警</div>
      <div class="dashboard-marquee__viewport">
        <div class="dashboard-marquee__track">
          <span
            v-for="(item, index) in marqueeItems"
            :key="`primary-${index}`"
            class="dashboard-marquee__item"
          >
            {{ item }}
          </span>
          <span
            v-for="(item, index) in marqueeItems"
            :key="`clone-${index}`"
            class="dashboard-marquee__item"
            aria-hidden="true"
          >
            {{ item }}
          </span>
        </div>
      </div>
    </div>

    <div class="dashboard-status-strip">
      <div class="status-chip">
        <span>数据更新</span>
        <strong>{{ dataSyncStatus }}</strong>
        <small>总览每 60 秒自动刷新一次</small>
      </div>
      <div class="status-chip">
        <span>高风险响应</span>
        <strong>{{ dashboard.kpis.highRiskPatients }}</strong>
        <small>待医生确认处置</small>
      </div>
      <div class="status-chip">
        <span>病历处理</span>
        <strong>{{ dashboard.kpis.paperCaseAttentionPatients }}</strong>
        <small>有待审核或归档异常患者</small>
      </div>
      <div class="status-chip">
        <span>宣教转化</span>
        <strong>{{ percentText(dashboard.videos.summary.completionRate) }}</strong>
        <small>当前平均完成率</small>
      </div>
    </div>

    <div class="kpi-grid">
      <el-card class="kpi-card kpi-blue" shadow="hover">
        <div class="kpi-label">管理患者</div>
        <div class="kpi-value">{{ dashboard.kpis.managedPatients }}</div>
        <div class="kpi-meta">当前归属患者总量</div>
      </el-card>
      <el-card class="kpi-card kpi-red" shadow="hover" @click="goToAssessments()">
        <div class="kpi-label">高风险患者</div>
        <div class="kpi-value">{{ dashboard.kpis.highRiskPatients }}</div>
        <div class="kpi-meta">点击进入风险评估页</div>
      </el-card>
      <el-card class="kpi-card kpi-orange" shadow="hover" @click="goToFollowUps()">
        <div class="kpi-label">待随访</div>
        <div class="kpi-value">{{ dashboard.kpis.pendingFollowUps }}</div>
        <div class="kpi-meta">逾期 {{ dashboard.kpis.overdueFollowUps }}</div>
      </el-card>
      <el-card class="kpi-card kpi-green" shadow="hover">
        <div class="kpi-label">今日预警</div>
        <div class="kpi-value">{{ dashboard.kpis.alertsToday }}</div>
        <div class="kpi-meta">含高风险提醒与系统通知</div>
      </el-card>
      <el-card class="kpi-card kpi-purple" shadow="hover">
        <div class="kpi-label">今日报告</div>
        <div class="kpi-value">{{ dashboard.kpis.reportsToday }}</div>
        <div class="kpi-meta">系统自动生成报告数</div>
      </el-card>
      <el-card class="kpi-card kpi-cyan" shadow="hover" @click="goToChat()">
        <div class="kpi-label">今日 AI 会话</div>
        <div class="kpi-value">{{ dashboard.kpis.doctorChatsToday }}</div>
        <div class="kpi-meta">点击进入 AI 健康顾问</div>
      </el-card>
        <el-card class="kpi-card kpi-amber" shadow="hover" @click="goToPaperCaseReview({ focus: 'attention' })">
        <div class="kpi-label">待处理病历</div>
        <div class="kpi-value">{{ dashboard.kpis.paperCasePendingReviews + dashboard.kpis.paperCaseFailedSyncs }}</div>
        <div class="kpi-meta">待审核 {{ dashboard.kpis.paperCasePendingReviews }} / 失败 {{ dashboard.kpis.paperCaseFailedSyncs }}</div>
      </el-card>
    </div>

    <div class="dashboard-screen-grid">
      <div class="dashboard-screen-column dashboard-screen-column--left">
        <div class="dashboard-column-head">
          <span class="dashboard-column-head__index">01</span>
          <div>
            <strong>风险研判区</strong>
            <small>风险与重点患者</small>
          </div>
        </div>
        <el-card class="dashboard-card dashboard-card--framed" shadow="never">
          <template #header>
            <div class="card-header">
              <div>
                <strong>患者风险总览</strong>
                <span>查看当前风险等级结构和近 7 天高风险识别趋势</span>
              </div>
              <el-button type="primary" link @click="goToAssessments()">风险评估详情</el-button>
            </div>
          </template>

          <div class="chart-grid chart-grid-risk">
            <div ref="riskDistributionRef" class="chart-box"></div>
            <div ref="riskTrendRef" class="chart-box"></div>
          </div>
          <div ref="diseaseRiskRef" class="chart-box chart-wide"></div>

          <div v-if="topRiskPatient" class="risk-spotlight">
            <div class="risk-spotlight__badge">重点患者</div>
            <div class="risk-spotlight__main">
              <div>
                <strong>{{ topRiskPatient.patientName || '待确认患者' }}</strong>
                <p>
                  {{ topRiskPatient.diseaseLabel || '慢病管理' }}
                  · {{ topRiskPatient.followUpLabel || '待随访' }}
                </p>
              </div>
              <el-tag :type="riskTagType(topRiskPatient.riskLevel)" size="large">{{ topRiskPatient.riskLabel || '高风险' }}</el-tag>
            </div>
            <div class="risk-spotlight__meta">
              <span>下次随访：{{ topRiskPatient.nextFollowDate || '--' }}</span>
              <el-button link type="primary" @click="goToPatient(topRiskPatient.patientId)">打开患者详情</el-button>
            </div>
          </div>

          <div class="table-section">
            <div class="section-title">重点患者</div>
            <el-table :data="dashboard.risk.highRiskPatients" size="small" stripe>
              <el-table-column prop="patientName" label="患者" min-width="110" />
              <el-table-column prop="diseaseLabel" label="病种" min-width="100" />
              <el-table-column prop="riskLabel" label="风险等级" min-width="90">
                <template #default="{ row }">
                  <el-tag :type="riskTagType(row.riskLevel)" size="small">{{ row.riskLabel }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="followUpLabel" label="随访状态" min-width="100" />
              <el-table-column prop="nextFollowDate" label="下次随访" min-width="110" />
              <el-table-column label="操作" width="90" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="goToPatient(row.patientId)">查看</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </div>

      <div class="dashboard-screen-column dashboard-screen-column--center">
        <div class="dashboard-column-head">
          <span class="dashboard-column-head__index">02</span>
          <div>
            <strong>执行协同区</strong>
            <small>随访与处理进度</small>
          </div>
        </div>
        <el-card class="dashboard-card dashboard-card--framed" shadow="never">
          <template #header>
            <div class="card-header">
              <div>
                <strong>随访任务总览</strong>
                <span>聚焦待执行、已完成和逾期任务</span>
              </div>
              <el-button type="primary" link @click="goToFollowUps()">随访管理</el-button>
            </div>
          </template>

          <div class="followup-stats">
            <div v-for="item in dashboard.followUp.statusCounts" :key="item.status" class="followup-stat">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
          <div ref="followUpStatusRef" class="chart-box"></div>
          <div ref="followUpTrendRef" class="chart-box"></div>

          <div class="table-section">
            <div class="section-title">逾期任务</div>
            <el-table :data="dashboard.followUp.overdueTasks" size="small" stripe>
              <el-table-column prop="patientName" label="患者" min-width="110" />
              <el-table-column prop="diseaseLabel" label="病种" min-width="100" />
              <el-table-column prop="nextFollowDate" label="计划日期" min-width="110" />
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button link type="primary" @click="goToFollowUps()">处理</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>

        <el-card class="dashboard-card dashboard-card--framed" shadow="never">
          <template #header>
            <div class="card-header">
              <div>
                <strong>智能服务动态</strong>
                <span>汇总智能问答、报告生成和预警处理的最新情况</span>
              </div>
              <el-button type="primary" link @click="goToChat()">查看问答</el-button>
            </div>
          </template>

          <div class="activity-summary">
            <div class="activity-pill">
              <span>今日会话</span>
              <strong>{{ dashboard.agentActivity.summary.doctorChatsToday }}</strong>
            </div>
            <div class="activity-pill">
              <span>辅助分析</span>
              <strong>{{ dashboard.agentActivity.summary.toolCallsToday }}</strong>
            </div>
            <div class="activity-pill">
              <span>今日报告</span>
              <strong>{{ dashboard.agentActivity.summary.reportsToday }}</strong>
            </div>
            <div class="activity-pill">
              <span>需补充处理</span>
              <strong>{{ dashboard.agentActivity.summary.degradedResponsesToday }}</strong>
            </div>
          </div>

          <div ref="agentTrendRef" class="chart-box chart-wide"></div>

          <div class="table-section">
            <div class="section-title">最近活动流</div>
            <div class="event-stream">
              <div
                v-for="(event, index) in dashboard.agentActivity.recentEvents"
                :key="`${event.type}-${index}`"
                class="event-stream__item"
              >
                <div class="event-stream__rail">
                  <span class="event-stream__dot" :class="`event-stream__dot--${event.type || 'default'}`"></span>
                  <span v-if="index !== dashboard.agentActivity.recentEvents.length - 1" class="event-stream__line"></span>
                </div>
                <div class="event-stream__content">
                  <div class="event-stream__top">
                    <span class="event-stream__time">{{ formatDateTime(event.time) }}</span>
                    <el-tag size="small" :type="eventTagType(event.type)" effect="plain">{{ eventTypeLabel(event.type) }}</el-tag>
                  </div>
                  <strong class="event-stream__title">{{ event.title }}</strong>
                  <div class="event-stream__summary">{{ event.summary || '系统完成了一项处理。' }}</div>
                  <div v-if="event.patientName" class="event-stream__patient">关联患者：{{ event.patientName }}</div>
                </div>
              </div>
            </div>
          </div>
        </el-card>

        <el-card class="dashboard-card dashboard-card--framed" shadow="never">
          <template #header>
            <div class="card-header">
              <div>
                <strong>纸质病历运营</strong>
                <span>集中展示待审核病历、归档异常和当前医生范围内需要继续处理的事项</span>
              </div>
              <el-button type="primary" link @click="goToPaperCaseReview({ focus: 'attention' })">病历运营台</el-button>
            </div>
          </template>

          <div class="activity-summary">
            <div class="activity-pill">
              <span>关注患者</span>
              <strong>{{ dashboard.paperCaseOps.attentionPatientCount }}</strong>
            </div>
            <div class="activity-pill">
              <span>待审核标准编码</span>
              <strong>{{ dashboard.paperCaseOps.pendingReviewCount }}</strong>
            </div>
            <div class="activity-pill">
              <span>归档异常</span>
              <strong>{{ dashboard.paperCaseOps.failedSyncCount }}</strong>
            </div>
            <div class="activity-pill">
              <span>病历总量</span>
              <strong>{{ dashboard.paperCaseOps.totalCount }}</strong>
            </div>
          </div>

          <div class="activity-summary clinical-job-summary">
            <div class="activity-pill">
              <span>待审核作业</span>
              <strong>{{ dashboard.clinicalJobOps.reviewPendingCount }}</strong>
            </div>
            <div class="activity-pill">
              <span>失败作业</span>
              <strong>{{ dashboard.clinicalJobOps.failedCount }}</strong>
            </div>
            <div class="activity-pill">
              <span>异常积压</span>
              <strong>{{ dashboard.clinicalJobOps.deadLetterCount }}</strong>
            </div>
            <div class="activity-pill">
              <span>运行超时</span>
              <strong>{{ dashboard.clinicalJobOps.runningTimeoutCount }}</strong>
            </div>
          </div>

          <div class="paper-case-op-tags paper-case-op-tags--panel">
            <el-tag
              v-if="dashboard.clinicalJobOps.reviewPendingCount > 0"
              size="small"
              type="warning"
              effect="plain"
              class="paper-case-op-tag paper-case-op-tag--clickable"
              @click="goToPaperCaseReview({ focus: 'attention', jobStatus: 'review_pending' })"
            >
              待审核 {{ dashboard.clinicalJobOps.reviewPendingCount }}
            </el-tag>
            <el-tag
              v-if="dashboard.clinicalJobOps.failedCount > 0"
              size="small"
              type="danger"
              effect="plain"
              class="paper-case-op-tag paper-case-op-tag--clickable"
              @click="goToPaperCaseReview({ focus: 'attention', jobStatus: 'failed' })"
            >
              失败 {{ dashboard.clinicalJobOps.failedCount }}
            </el-tag>
            <el-tag
              v-if="dashboard.clinicalJobOps.deadLetterCount > 0"
              size="small"
              type="danger"
              effect="plain"
              class="paper-case-op-tag paper-case-op-tag--clickable"
              @click="goToPaperCaseReview({ focus: 'attention', jobStatus: 'dead_letter' })"
            >
              异常积压 {{ dashboard.clinicalJobOps.deadLetterCount }}
            </el-tag>
            <el-tag
              v-if="dashboard.clinicalJobOps.runningTimeoutCount > 0"
              size="small"
              type="warning"
              effect="plain"
            >
              处理超时 {{ dashboard.clinicalJobOps.runningTimeoutCount }}
            </el-tag>
            <el-tag v-if="!hasClinicalJobAttention" size="small" type="success" effect="plain">
              医生范围内临床作业正常
            </el-tag>
          </div>

          <div class="table-section">
            <div class="section-title">最近需处理患者</div>
            <el-table :data="dashboard.paperCaseOps.recentAttentionPatients" size="small" stripe>
              <el-table-column prop="patientName" label="患者" min-width="110" />
              <el-table-column label="待处理" min-width="170">
                <template #default="{ row }">
                  <div class="paper-case-op-tags">
                    <el-tag v-if="row.pendingReviewCount > 0" size="small" type="warning" effect="plain">
                      待审核 {{ row.pendingReviewCount }}
                    </el-tag>
                    <el-tag v-if="row.failedSyncCount > 0" size="small" type="danger" effect="plain">
                      归档异常 {{ row.failedSyncCount }}
                    </el-tag>
                    <el-tag size="small" effect="plain">关注 {{ row.attentionCount }}</el-tag>
                  </div>
                </template>
              </el-table-column>
              <el-table-column prop="latestRecordDate" label="最近病历日期" min-width="120" />
              <el-table-column prop="latestDiagnosis" label="最近诊断" min-width="180" show-overflow-tooltip />
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button link type="primary" @click="goToPaperCaseReview({ patientId: row.patientId, focus: 'attention' })">处理</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="table-section">
            <div class="section-title">临床作业重点类型</div>
            <el-table :data="dashboard.clinicalJobOps.jobTypeSummaries" size="small" stripe>
              <el-table-column label="作业类型" min-width="140">
                <template #default="{ row }">
                  {{ formatClinicalJobType(row.jobType) }}
                </template>
              </el-table-column>
              <el-table-column prop="reviewPendingCount" label="待审核" min-width="80" />
              <el-table-column prop="failedCount" label="失败" min-width="80" />
              <el-table-column prop="deadLetterCount" label="死信" min-width="80" />
              <el-table-column prop="runningTimeoutCount" label="超时" min-width="80" />
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button
                    v-if="row.jobType === 'paper_case_ingest'"
                    link
                    type="primary"
                    @click="goToPaperCaseReview({ focus: 'attention', jobType: row.jobType })"
                  >
                    去处理
                  </el-button>
                  <span v-else class="dashboard-inline-note">关注患者详情</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </div>

      <div class="dashboard-screen-column dashboard-screen-column--right">
        <div class="dashboard-column-head">
          <span class="dashboard-column-head__index">03</span>
          <div>
            <strong>宣教转化区</strong>
            <small>Education Conversion Zone</small>
          </div>
        </div>
        <el-card class="dashboard-card dashboard-card--framed" shadow="never">
          <template #header>
            <div class="card-header">
              <div>
                <strong>宣教视频热度</strong>
                <span>基于真实播放会话统计推荐、播放、完成与来源转化</span>
              </div>
              <el-button type="primary" link @click="goToPatients()">患者管理</el-button>
            </div>
          </template>

          <div class="video-summary">
            <div class="video-stat">
              <span>推荐命中</span>
              <strong>{{ dashboard.videos.summary.recommendedVideoCount }}</strong>
            </div>
            <div class="video-stat">
              <span>播放次数</span>
              <strong>{{ dashboard.videos.summary.playCount }}</strong>
            </div>
            <div class="video-stat">
              <span>独立观看</span>
              <strong>{{ dashboard.videos.summary.uniqueViewerCount }}</strong>
            </div>
            <div class="video-stat">
              <span>平均完成率</span>
              <strong>{{ percentText(dashboard.videos.summary.completionRate) }}</strong>
            </div>
            <div class="video-stat">
              <span>平均观看时长</span>
              <strong>{{ secondsText(dashboard.videos.summary.avgWatchSeconds) }}</strong>
            </div>
            <div class="video-stat">
              <span>转写覆盖率</span>
              <strong>{{ percentText(dashboard.videos.summary.transcriptCoverage) }}</strong>
            </div>
          </div>

          <el-alert
            :title="dashboard.videos.summary.completionRateNote"
            type="warning"
            :closable="false"
            show-icon
            class="video-alert"
          />

          <div ref="videoTopRef" class="chart-box"></div>
          <div ref="videoSourceRef" class="chart-box chart-compact"></div>
          <div ref="videoDiseaseRef" class="chart-box"></div>

          <div class="table-section">
            <div class="section-title">来源转化详情</div>
            <el-table :data="dashboard.videos.sourceConversion" size="small" stripe>
              <el-table-column prop="label" label="来源" min-width="110" />
              <el-table-column prop="recommendedCount" label="推荐" min-width="80" />
              <el-table-column prop="playCount" label="播放" min-width="80" />
              <el-table-column prop="completedCount" label="完成" min-width="80" />
              <el-table-column prop="uniqueViewerCount" label="观看人数" min-width="90" />
              <el-table-column label="完成率" min-width="90">
                <template #default="{ row }">
                  {{ percentText(row.completionRate) }}
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { clinicalDataJobApi, doctorDashboardApi } from '../../api'
import { useNotificationStore } from '../../stores/notification'
import { useUserStore } from '../../stores/user'
import { echarts } from '../../utils/echarts-basic'

const router = useRouter()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

const loading = ref(false)
const refreshing = ref(false)
const lastUpdatedText = ref('--')
const currentTimeText = ref(dayjs().format('HH:mm:ss'))
const currentDateText = ref(dayjs().format('YYYY-MM-DD dddd'))
const dashboard = ref(createEmptyDashboard())

const riskDistributionRef = ref(null)
const riskTrendRef = ref(null)
const diseaseRiskRef = ref(null)
const followUpStatusRef = ref(null)
const followUpTrendRef = ref(null)
const agentTrendRef = ref(null)
const videoTopRef = ref(null)
const videoSourceRef = ref(null)
const videoDiseaseRef = ref(null)

let refreshTimer = null
let clockTimer = null
let presenceTimer = null
const chartMap = new Map()

const marqueeItems = computed(() => {
  const items = []
  const highRisk = Number(dashboard.value.kpis.highRiskPatients || 0)
  const overdue = Number(dashboard.value.kpis.overdueFollowUps || 0)
  const alerts = Number(dashboard.value.kpis.alertsToday || 0)
  const reports = Number(dashboard.value.kpis.reportsToday || 0)
  const completionRate = percentText(dashboard.value.videos.summary.completionRate)

  items.push(`今日高风险患者 ${highRisk} 人`)
  items.push(`逾期随访 ${overdue} 项`)
  items.push(`今日预警 ${alerts} 条`)
  items.push(`今日报告 ${reports} 份`)
  items.push(`宣教平均完成率 ${completionRate}`)

  if (dashboard.value.agentActivity.recentEvents.length) {
    const latestEvent = dashboard.value.agentActivity.recentEvents[0]
    items.push(`最新服务动态：${latestEvent.title || '系统事件'} ${latestEvent.patientName ? `- ${latestEvent.patientName}` : ''}`)
  }

  return items
})

const topRiskPatient = computed(() => dashboard.value.risk.highRiskPatients?.[0] || null)
const dashboardAreaLabel = computed(() => (userStore.username ? `${userStore.username} / 医生工作台` : '医生工作台'))
const dataSyncStatus = computed(() => (lastUpdatedText.value === '--' ? '待更新' : '已更新'))
const hasClinicalJobAttention = computed(() => (
  Number(dashboard.value.clinicalJobOps.reviewPendingCount || 0) > 0
    || Number(dashboard.value.clinicalJobOps.failedCount || 0) > 0
    || Number(dashboard.value.clinicalJobOps.deadLetterCount || 0) > 0
    || Number(dashboard.value.clinicalJobOps.runningTimeoutCount || 0) > 0
))

function createEmptyDashboard() {
  return {
    kpis: {
      managedPatients: 0,
      highRiskPatients: 0,
      alertsToday: 0,
      pendingFollowUps: 0,
      overdueFollowUps: 0,
      reportsToday: 0,
      doctorChatsToday: 0,
      paperCaseAttentionPatients: 0,
      paperCasePendingReviews: 0,
      paperCaseFailedSyncs: 0
    },
    risk: {
      distribution: [],
      highRiskTrend: [],
      diseaseRiskMatrix: [],
      highRiskPatients: []
    },
    followUp: {
      statusCounts: [],
      completionTrend: [],
      overdueTasks: []
    },
    paperCaseOps: {
      totalCount: 0,
      attentionCount: 0,
      attentionPatientCount: 0,
      pendingReviewCount: 0,
      failedSyncCount: 0,
      recentAttentionPatients: []
    },
    clinicalJobOps: {
      reviewPendingCount: 0,
      failedCount: 0,
      deadLetterCount: 0,
      runningTimeoutCount: 0,
      retryableCount: 0,
      jobTypeSummaries: []
    },
    agentActivity: {
      summary: {
        doctorChatsToday: 0,
        reportsToday: 0,
        alertsToday: 0,
        toolCallsToday: 0,
        degradedResponsesToday: 0
      },
      hourlyTrend: [],
      recentEvents: []
    },
    videos: {
      summary: {
        recommendedVideoCount: 0,
        playCount: 0,
        uniqueViewerCount: 0,
        activeVideoCount: 0,
        transcriptCoverage: 0,
        avgWatchSeconds: 0,
        completionRate: 0,
        completionRateNote: '当前暂无真实播放数据。'
      },
      topVideos: [],
      sourceConversion: [],
      diseaseHotspots: []
    },
    onlinePresence: {
      totalUsers: 0,
      totalSessions: 0,
      doctorUsers: 0,
      patientUsers: 0,
      adminUsers: 0,
      anonymousUsers: 0,
      messageChannelConnected: false
    }
  }
}

async function loadDashboard(fromManual = false) {
  if (fromManual) {
    refreshing.value = true
  } else {
    loading.value = true
  }
  try {
    const [data, clinicalJobSummary] = await Promise.all([
      doctorDashboardApi.getOverview(userStore.doctorId),
      clinicalDataJobApi.getSummary().catch(() => null)
    ])
    dashboard.value = createEmptyDashboard()
    dashboard.value = {
      ...dashboard.value,
      ...data,
      kpis: { ...dashboard.value.kpis, ...(data?.kpis || {}) },
      risk: { ...dashboard.value.risk, ...(data?.risk || {}) },
      followUp: { ...dashboard.value.followUp, ...(data?.followUp || {}) },
      paperCaseOps: { ...dashboard.value.paperCaseOps, ...(data?.paperCaseOps || {}) },
      clinicalJobOps: {
        ...dashboard.value.clinicalJobOps,
        ...normalizeClinicalJobOps(clinicalJobSummary)
      },
      agentActivity: {
        ...dashboard.value.agentActivity,
        ...(data?.agentActivity || {}),
        summary: {
          ...dashboard.value.agentActivity.summary,
          ...(data?.agentActivity?.summary || {})
        }
      },
      videos: {
        ...dashboard.value.videos,
        ...(data?.videos || {}),
        summary: {
          ...dashboard.value.videos.summary,
          ...(data?.videos?.summary || {})
        }
      },
      onlinePresence: {
        ...dashboard.value.onlinePresence,
        ...(data?.onlinePresence || {})
      }
    }
    lastUpdatedText.value = dayjs().format('MM-DD HH:mm:ss')
    await nextTick()
    renderCharts()
  } catch (error) {
    console.error('Failed to load doctor dashboard:', error)
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function refreshPresence() {
  try {
    const presence = await doctorDashboardApi.getPresence()
    dashboard.value = {
      ...dashboard.value,
      onlinePresence: {
        ...dashboard.value.onlinePresence,
        ...(presence || {})
      }
    }
  } catch (error) {
    console.error('Failed to refresh doctor presence:', error)
  }
}

function renderCharts() {
  setChart('riskDistribution', riskDistributionRef.value, {
    color: ['#7ec7ff', '#f6c85f', '#f28b82', '#7a1f1f'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, icon: 'circle', textStyle: { color: 'rgba(210, 231, 255, 0.72)' } },
    series: [{
      name: '风险分布',
      type: 'pie',
      radius: ['45%', '72%'],
      center: ['50%', '45%'],
      label: { color: '#dff3ff', formatter: '{b}\n{c}' },
      data: dashboard.value.risk.distribution.map(item => ({
        name: item.label,
        value: item.value
      }))
    }]
  })

  setChart('riskTrend', riskTrendRef.value, {
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: dashboard.value.risk.highRiskTrend.map(item => item.date),
      boundaryGap: false,
      axisLine: { lineStyle: { color: 'rgba(115, 160, 204, 0.4)' } },
      axisLabel: { color: 'rgba(198, 223, 247, 0.74)' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: 'rgba(198, 223, 247, 0.7)' },
      splitLine: { lineStyle: { color: 'rgba(67, 108, 151, 0.18)' } }
    },
    series: [{
      name: '高风险识别',
      type: 'line',
      smooth: true,
      areaStyle: { color: 'rgba(245,108,108,0.12)' },
      lineStyle: { color: '#f56c6c', width: 3 },
      itemStyle: { color: '#f56c6c' },
      data: dashboard.value.risk.highRiskTrend.map(item => item.count)
    }]
  })

  setChart('diseaseRisk', diseaseRiskRef.value, {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, textStyle: { color: 'rgba(206, 226, 248, 0.74)' } },
    grid: { left: 48, right: 20, top: 36, bottom: 24 },
    xAxis: {
      type: 'category',
      data: dashboard.value.risk.diseaseRiskMatrix.map(item => item.label),
      axisLine: { lineStyle: { color: 'rgba(115, 160, 204, 0.4)' } },
      axisLabel: { color: 'rgba(198, 223, 247, 0.74)' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: 'rgba(198, 223, 247, 0.7)' },
      splitLine: { lineStyle: { color: 'rgba(67, 108, 151, 0.18)' } }
    },
    series: [
      stackedSeries('低风险', '#7ec7ff', 'low'),
      stackedSeries('中风险', '#f6c85f', 'medium'),
      stackedSeries('高风险', '#f56c6c', 'high'),
      stackedSeries('危急', '#7a1f1f', 'critical')
    ]
  })

  setChart('followUpStatus', followUpStatusRef.value, {
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      label: { color: '#dff3ff', formatter: '{b}\n{c}' },
      data: dashboard.value.followUp.statusCounts.map(item => ({
        name: item.label,
        value: item.value
      }))
    }]
  })

  setChart('followUpTrend', followUpTrendRef.value, {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: 'rgba(206, 226, 248, 0.74)' } },
    grid: { left: 36, right: 12, top: 32, bottom: 18 },
    xAxis: {
      type: 'category',
      data: dashboard.value.followUp.completionTrend.map(item => item.date),
      axisLine: { lineStyle: { color: 'rgba(115, 160, 204, 0.4)' } },
      axisLabel: { color: 'rgba(198, 223, 247, 0.74)' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: 'rgba(198, 223, 247, 0.7)' },
      splitLine: { lineStyle: { color: 'rgba(67, 108, 151, 0.18)' } }
    },
    series: [
      {
        name: '已完成',
        type: 'bar',
        itemStyle: { color: '#67c23a' },
        data: dashboard.value.followUp.completionTrend.map(item => item.completed)
      },
      {
        name: '当日到期',
        type: 'line',
        smooth: true,
        itemStyle: { color: '#e6a23c' },
        data: dashboard.value.followUp.completionTrend.map(item => item.due)
      }
    ]
  })

  setChart('agentTrend', agentTrendRef.value, {
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: 'rgba(206, 226, 248, 0.74)' } },
    grid: { left: 40, right: 12, top: 36, bottom: 20 },
    xAxis: {
      type: 'category',
      data: dashboard.value.agentActivity.hourlyTrend.map(item => item.hour),
      axisLine: { lineStyle: { color: 'rgba(115, 160, 204, 0.4)' } },
      axisLabel: { color: 'rgba(198, 223, 247, 0.74)' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: 'rgba(198, 223, 247, 0.7)' },
      splitLine: { lineStyle: { color: 'rgba(67, 108, 151, 0.18)' } }
    },
    series: [
      {
        name: 'AI 会话',
        type: 'line',
        smooth: true,
        itemStyle: { color: '#409eff' },
        data: dashboard.value.agentActivity.hourlyTrend.map(item => item.chatSessions)
      },
      {
        name: '报告生成',
        type: 'bar',
        itemStyle: { color: '#67c23a' },
        data: dashboard.value.agentActivity.hourlyTrend.map(item => item.reports)
      },
      {
        name: '预警触发',
        type: 'bar',
        itemStyle: { color: '#f56c6c' },
        data: dashboard.value.agentActivity.hourlyTrend.map(item => item.alerts)
      }
    ]
  })

  setChart('videoTop', videoTopRef.value, {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 110, right: 20, top: 16, bottom: 16 },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: 'rgba(198, 223, 247, 0.7)' },
      splitLine: { lineStyle: { color: 'rgba(67, 108, 151, 0.18)' } }
    },
    yAxis: {
      type: 'category',
      data: dashboard.value.videos.topVideos.map(item => item.title),
      axisLabel: {
        color: 'rgba(198, 223, 247, 0.74)',
        width: 90,
        overflow: 'truncate'
      }
    },
    series: [{
      type: 'bar',
      itemStyle: { color: '#5b8ff9' },
      data: dashboard.value.videos.topVideos.map(item => item.playCount)
    }]
  })

  setChart('videoSource', videoSourceRef.value, {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { top: 0, textStyle: { color: 'rgba(206, 226, 248, 0.74)' } },
    grid: { left: 42, right: 12, top: 36, bottom: 20 },
    xAxis: {
      type: 'category',
      data: dashboard.value.videos.sourceConversion.map(item => item.label),
      axisLine: { lineStyle: { color: 'rgba(115, 160, 204, 0.4)' } },
      axisLabel: { color: 'rgba(198, 223, 247, 0.74)' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: 'rgba(198, 223, 247, 0.7)' },
      splitLine: { lineStyle: { color: 'rgba(67, 108, 151, 0.18)' } }
    },
    series: [
      {
        name: '推荐命中',
        type: 'bar',
        itemStyle: { color: '#91caff' },
        data: dashboard.value.videos.sourceConversion.map(item => item.recommendedCount)
      },
      {
        name: '播放次数',
        type: 'bar',
        itemStyle: { color: '#36cfc9' },
        data: dashboard.value.videos.sourceConversion.map(item => item.playCount)
      },
      {
        name: '完成次数',
        type: 'bar',
        itemStyle: { color: '#73d13d' },
        data: dashboard.value.videos.sourceConversion.map(item => item.completedCount)
      }
    ]
  })

  setChart('videoDisease', videoDiseaseRef.value, {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: 36, right: 12, top: 24, bottom: 16 },
    xAxis: {
      type: 'category',
      data: dashboard.value.videos.diseaseHotspots.map(item => item.label),
      axisLine: { lineStyle: { color: 'rgba(115, 160, 204, 0.4)' } },
      axisLabel: { color: 'rgba(198, 223, 247, 0.74)' }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: 'rgba(198, 223, 247, 0.7)' },
      splitLine: { lineStyle: { color: 'rgba(67, 108, 151, 0.18)' } }
    },
    series: [{
      type: 'bar',
      itemStyle: { color: '#8b5cf6' },
      data: dashboard.value.videos.diseaseHotspots.map(item => item.count)
    }]
  })
}

function stackedSeries(name, color, field) {
  return {
    name,
    type: 'bar',
    stack: 'risk',
    itemStyle: { color },
    data: dashboard.value.risk.diseaseRiskMatrix.map(item => item[field] || 0)
  }
}

function setChart(key, el, option) {
  if (!el) return
  let chart = chartMap.get(key)
  if (!chart) {
    chart = echarts.init(el)
    chartMap.set(key, chart)
  }
  chart.setOption(option, true)
}

function resizeCharts() {
  chartMap.forEach(chart => chart.resize())
}

function goToAssessments() {
  router.push('/doctor/assessments')
}

function goToFollowUps() {
  router.push('/doctor/follow-ups')
}

function goToChat() {
  router.push('/doctor/chat')
}

function goToPaperCaseReview(options = {}) {
  const normalized = (typeof options === 'object' && options !== null)
    ? options
    : { patientId: options }
  const query = {}
  if (normalized.patientId) {
    query.patientId = String(normalized.patientId)
  }
  if (normalized.paperCaseId) {
    query.paperCaseId = String(normalized.paperCaseId)
  }
  if (normalized.focus) {
    query.focus = normalized.focus
  }
  if (normalized.jobStatus) {
    query.jobStatus = normalized.jobStatus
  }
  if (normalized.jobType) {
    query.jobType = normalized.jobType
  }
  router.push({ name: 'DoctorPaperCaseReview', query })
}

function goToPatient(patientId) {
  if (!patientId) return
  router.push(`/doctor/patients/${patientId}`)
}

function goToPatients() {
  router.push('/doctor/patients')
}

function formatClinicalJobType(jobType) {
  if (!jobType) return '--'
  if (jobType === 'paper_case_ingest') return '纸质病历导入'
  if (jobType === 'huawei_iot_import') return '设备数据导入'
  return jobType
}

function riskTagType(riskLevel) {
  return {
    LOW: 'success',
    MEDIUM: 'warning',
    HIGH: 'danger',
    CRITICAL: 'danger'
  }[riskLevel] || 'info'
}

function eventTagType(type) {
  return {
    chat: 'primary',
    report: 'success',
    alert: 'danger'
  }[type] || 'info'
}

function eventType(type) {
  return {
    chat: 'primary',
    report: 'success',
    alert: 'danger'
  }[type] || 'info'
}

function eventTypeLabel(type) {
  return {
    chat: 'AI 会话',
    report: '报告生成',
    alert: '预警提醒'
  }[type] || '事件'
}

function formatDateTime(value) {
  return value ? dayjs(value).format('MM-DD HH:mm') : '--'
}

function percentText(value) {
  if (value == null) return '--'
  return `${value}%`
}

function secondsText(value) {
  const seconds = Number(value)
  if (!seconds) return '--'
  if (seconds < 60) return `${seconds.toFixed(0)}秒`
  const minutes = Math.floor(seconds / 60)
  const remain = Math.round(seconds % 60)
  return `${minutes}分${remain.toString().padStart(2, '0')}秒`
}

function normalizeClinicalJobOps(summary) {
  const jobTypeSummaries = Array.isArray(summary?.jobTypeSummaries) ? summary.jobTypeSummaries : []
  return {
    reviewPendingCount: Number(summary?.statusCounts?.review_pending || 0),
    failedCount: Number(summary?.statusCounts?.failed || 0),
    deadLetterCount: Number(summary?.statusCounts?.dead_letter || 0),
    runningTimeoutCount: Number(summary?.runningTimeoutCount || 0),
    retryableCount: Number(summary?.retryableCount || 0),
    jobTypeSummaries: jobTypeSummaries
      .filter(item =>
        Number(item?.reviewPendingCount || 0) > 0
          || Number(item?.failedCount || 0) > 0
          || Number(item?.deadLetterCount || 0) > 0
          || Number(item?.runningTimeoutCount || 0) > 0
      )
      .slice(0, 4)
  }
}

function updateClock() {
  currentTimeText.value = dayjs().format('HH:mm:ss')
  currentDateText.value = dayjs().format('YYYY-MM-DD dddd')
}

onMounted(async () => {
  updateClock()
  await loadDashboard()
  await refreshPresence()
  refreshTimer = window.setInterval(() => loadDashboard(), 60 * 1000)
  presenceTimer = window.setInterval(() => refreshPresence(), 10 * 1000)
  clockTimer = window.setInterval(updateClock, 1000)
  window.addEventListener('resize', resizeCharts)
})

onUnmounted(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
  }
  if (clockTimer) {
    window.clearInterval(clockTimer)
  }
  if (presenceTimer) {
    window.clearInterval(presenceTimer)
  }
  window.removeEventListener('resize', resizeCharts)
  chartMap.forEach(chart => chart.dispose())
  chartMap.clear()
})
</script>

<style scoped>
.dashboard-view {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 18px;
  color: #dff3ff;
}

.dashboard-view::before,
.dashboard-view::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.dashboard-view::before {
  background:
    radial-gradient(circle at top left, rgba(31, 217, 255, 0.18), transparent 22%),
    radial-gradient(circle at 86% 14%, rgba(30, 89, 255, 0.18), transparent 20%),
    linear-gradient(180deg, rgba(6, 20, 41, 0.2), rgba(6, 14, 27, 0.02));
}

.dashboard-view::after {
  opacity: 0.16;
  background-image:
    linear-gradient(rgba(80, 137, 196, 0.2) 1px, transparent 1px),
    linear-gradient(90deg, rgba(80, 137, 196, 0.2) 1px, transparent 1px);
  background-size: 34px 34px;
  mask-image: linear-gradient(180deg, rgba(0, 0, 0, 0.7), transparent 88%);
}

.dashboard-commandbar {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  padding: 16px 20px;
  border: 1px solid rgba(84, 141, 199, 0.22);
  border-radius: 24px;
  background:
    linear-gradient(90deg, rgba(5, 16, 31, 0.96), rgba(10, 24, 44, 0.9)),
    radial-gradient(circle at right, rgba(29, 224, 211, 0.08), transparent 22%);
  box-shadow: 0 18px 38px rgba(0, 0, 0, 0.18);
}

.dashboard-commandbar__title {
  min-width: 0;
}

.dashboard-commandbar__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: rgba(114, 246, 255, 0.78);
  font-size: 11px;
  letter-spacing: 0.16em;
}

.dashboard-commandbar__eyebrow::before {
  content: '';
  width: 20px;
  height: 1px;
  background: currentColor;
}

.dashboard-commandbar__title strong {
  display: block;
  margin-top: 8px;
  color: #f4fbff;
  font-size: 26px;
  line-height: 1.1;
  letter-spacing: 0.04em;
}

.dashboard-commandbar__meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.command-pill {
  min-width: 120px;
  padding: 10px 14px;
  border-radius: 18px;
  border: 1px solid rgba(84, 141, 199, 0.18);
  background: linear-gradient(180deg, rgba(11, 27, 49, 0.74), rgba(9, 20, 38, 0.68));
}

.command-pill span {
  display: block;
  color: rgba(180, 208, 233, 0.62);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.command-pill strong {
  display: block;
  margin-top: 6px;
  color: #dffaff;
  font-size: 18px;
  line-height: 1.1;
}

.dashboard-header {
  position: relative;
  z-index: 1;
  padding: 28px 30px;
  border: 1px solid rgba(73, 125, 177, 0.24);
  border-radius: 30px;
  background:
    radial-gradient(circle at top left, rgba(35, 165, 255, 0.24), transparent 34%),
    linear-gradient(135deg, rgba(8, 22, 46, 0.94), rgba(8, 16, 32, 0.9));
  box-shadow: 0 28px 60px rgba(0, 0, 0, 0.24), inset 0 1px 0 rgba(255, 255, 255, 0.05);
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.dashboard-header__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #72f6ff;
  font-size: 11px;
  letter-spacing: 0.18em;
}

.dashboard-header__eyebrow::before {
  content: '';
  width: 24px;
  height: 1px;
  background: currentColor;
}

.dashboard-header h2 {
  margin: 14px 0 10px;
  color: #f4fbff;
  font-size: 42px;
  line-height: 1.02;
  letter-spacing: 0.03em;
}

.dashboard-header p {
  margin: 0;
  max-width: 760px;
  color: rgba(201, 225, 247, 0.72);
  font-size: 15px;
  line-height: 1.9;
}

.dashboard-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  position: relative;
  z-index: 1;
}

.dashboard-clock {
  min-width: 184px;
  padding: 12px 16px;
  border-radius: 20px;
  border: 1px solid rgba(111, 199, 255, 0.22);
  background: linear-gradient(180deg, rgba(10, 27, 50, 0.78), rgba(9, 20, 39, 0.68));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
}

.dashboard-clock__label {
  display: block;
  color: rgba(180, 208, 233, 0.6);
  font-size: 11px;
  letter-spacing: 0.16em;
}

.dashboard-clock strong {
  display: block;
  margin-top: 6px;
  color: #8df7db;
  font-size: 28px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 14px rgba(141, 247, 219, 0.2);
}

.dashboard-clock small {
  display: block;
  margin-top: 6px;
  color: rgba(198, 223, 247, 0.7);
  font-size: 12px;
}

.dashboard-actions__tag {
  border-color: rgba(111, 199, 255, 0.2);
  background: rgba(12, 27, 51, 0.72);
  color: #d8eeff;
}

.dashboard-actions__refresh {
  min-height: 42px;
  border-radius: 999px;
  border: 1px solid rgba(111, 199, 255, 0.36);
  background: linear-gradient(90deg, rgba(23, 154, 255, 0.26), rgba(29, 224, 211, 0.24));
  color: #ecfbff;
}

.dashboard-marquee {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 110px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
  padding: 14px 18px;
  border: 1px solid rgba(83, 136, 191, 0.22);
  border-radius: 22px;
  background: linear-gradient(90deg, rgba(25, 14, 28, 0.92), rgba(9, 20, 39, 0.82));
  overflow: hidden;
}

.dashboard-marquee__label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 38px;
  border-radius: 999px;
  background: linear-gradient(90deg, rgba(255, 84, 112, 0.28), rgba(255, 166, 0, 0.22));
  color: #ffe5b8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.dashboard-marquee__viewport {
  min-width: 0;
  overflow: hidden;
  mask-image: linear-gradient(90deg, transparent, #000 6%, #000 94%, transparent);
}

.dashboard-marquee__track {
  display: inline-flex;
  align-items: center;
  gap: 16px;
  white-space: nowrap;
  min-width: max-content;
  animation: marquee-scroll 28s linear infinite;
}

.dashboard-marquee__item {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  color: #f8d6de;
  font-size: 13px;
  letter-spacing: 0.04em;
}

.dashboard-marquee__item::after {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #ff7b72;
  box-shadow: 0 0 10px rgba(255, 123, 114, 0.6);
}

.dashboard-status-strip {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.status-chip {
  padding: 16px 18px;
  border-radius: 22px;
  border: 1px solid rgba(83, 136, 191, 0.22);
  background: linear-gradient(180deg, rgba(10, 27, 50, 0.84), rgba(8, 18, 35, 0.74));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  animation: panel-rise 0.75s ease both;
}

.status-chip span {
  display: block;
  color: rgba(180, 208, 233, 0.62);
  font-size: 12px;
  letter-spacing: 0.08em;
}

.status-chip strong {
  display: block;
  margin-top: 10px;
  color: #f4fbff;
  font-size: 28px;
  line-height: 1;
}

.status-chip small {
  display: block;
  margin-top: 8px;
  color: rgba(198, 223, 247, 0.68);
  line-height: 1.6;
}

.kpi-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
}

.kpi-card {
  border: 1px solid rgba(83, 136, 191, 0.22);
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 22px 40px rgba(2, 8, 20, 0.22);
  cursor: default;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
  background: linear-gradient(180deg, rgba(12, 24, 46, 0.92), rgba(9, 18, 35, 0.82));
  animation: panel-rise 0.8s ease both;
}

.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 26px 44px rgba(2, 8, 20, 0.3);
}

.kpi-card :deep(.el-card__body) {
  padding: 20px 22px;
}

.kpi-blue {
  background: linear-gradient(145deg, rgba(8, 32, 66, 0.98), rgba(9, 22, 41, 0.9));
}

.kpi-red {
  background: linear-gradient(145deg, rgba(58, 17, 37, 0.98), rgba(29, 10, 24, 0.92));
  cursor: pointer;
}

.kpi-orange {
  background: linear-gradient(145deg, rgba(59, 33, 6, 0.98), rgba(32, 18, 6, 0.92));
  cursor: pointer;
}

.kpi-green {
  background: linear-gradient(145deg, rgba(5, 55, 57, 0.98), rgba(6, 31, 35, 0.92));
}

.kpi-purple {
  background: linear-gradient(145deg, rgba(31, 25, 70, 0.98), rgba(18, 14, 42, 0.92));
}

.kpi-cyan {
  background: linear-gradient(145deg, rgba(17, 41, 61, 0.98), rgba(9, 22, 34, 0.92));
  cursor: pointer;
}

.kpi-label {
  color: rgba(180, 208, 233, 0.68);
  font-size: 12px;
  letter-spacing: 0.04em;
  margin-bottom: 12px;
}

.kpi-value {
  color: #f3fbff;
  font-size: 36px;
  font-weight: 700;
  line-height: 1;
  margin-bottom: 10px;
  font-variant-numeric: tabular-nums;
  text-shadow: 0 0 18px rgba(114, 246, 255, 0.08);
}

.kpi-meta {
  color: rgba(180, 208, 233, 0.6);
  font-size: 12px;
  line-height: 1.7;
}

.dashboard-row {
  margin-bottom: 0;
}

.dashboard-screen-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1.28fr) minmax(360px, 0.92fr) minmax(340px, 0.88fr);
  gap: 16px;
  align-items: start;
}

.dashboard-screen-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.dashboard-column-head {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 64px;
  padding: 12px 16px;
  border: 1px solid rgba(84, 141, 199, 0.18);
  border-radius: 20px;
  background: linear-gradient(90deg, rgba(7, 18, 34, 0.94), rgba(10, 24, 44, 0.82));
  box-shadow: 0 12px 28px rgba(0, 0, 0, 0.14);
}

.dashboard-column-head__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 42px;
  height: 42px;
  border-radius: 14px;
  border: 1px solid rgba(114, 246, 255, 0.24);
  background: linear-gradient(180deg, rgba(14, 38, 66, 0.88), rgba(9, 22, 40, 0.78));
  color: #72f6ff;
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.dashboard-column-head strong {
  display: block;
  color: #f2fbff;
  font-size: 18px;
  line-height: 1.1;
}

.dashboard-column-head small {
  display: block;
  margin-top: 4px;
  color: rgba(180, 208, 233, 0.62);
  letter-spacing: 0.08em;
}

.dashboard-card {
  position: relative;
  z-index: 1;
  border: 1px solid rgba(83, 136, 191, 0.22);
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgba(8, 20, 38, 0.96), rgba(7, 15, 30, 0.92)),
    linear-gradient(145deg, rgba(34, 165, 255, 0.06), transparent 40%);
  box-shadow: 0 22px 44px rgba(2, 8, 20, 0.24);
}

.dashboard-screen-column--left .dashboard-card,
.dashboard-screen-column--right .dashboard-card {
  flex: 1;
}

.dashboard-screen-column--center .dashboard-card:first-of-type {
  min-height: 660px;
}

.dashboard-screen-column--center .dashboard-card:last-of-type {
  min-height: 720px;
}

.dashboard-card--framed::before,
.dashboard-card--framed::after {
  content: '';
  position: absolute;
  width: 34px;
  height: 34px;
  border-color: rgba(114, 246, 255, 0.34);
  border-style: solid;
  pointer-events: none;
}

.dashboard-card--framed::before {
  top: 12px;
  left: 12px;
  border-width: 1px 0 0 1px;
  border-top-left-radius: 14px;
}

.dashboard-card--framed::after {
  right: 12px;
  bottom: 12px;
  border-width: 0 1px 1px 0;
  border-bottom-right-radius: 14px;
}

.dashboard-card :deep(.el-card__header) {
  padding: 22px 24px 18px;
  border-bottom: 1px solid rgba(67, 108, 151, 0.16);
}

.dashboard-card :deep(.el-card__body) {
  padding: 22px 24px 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.card-header strong {
  display: block;
  color: #f2fbff;
  margin-bottom: 4px;
  font-size: 22px;
  letter-spacing: 0.04em;
}

.card-header span {
  color: rgba(198, 223, 247, 0.68);
  font-size: 13px;
  line-height: 1.7;
}

.card-header > div:first-child {
  position: relative;
  padding-left: 16px;
}

.card-header > div:first-child::before {
  content: '';
  position: absolute;
  left: 0;
  top: 6px;
  width: 6px;
  height: 24px;
  border-radius: 999px;
  background: linear-gradient(180deg, #72f6ff, rgba(114, 246, 255, 0.2));
  box-shadow: 0 0 12px rgba(114, 246, 255, 0.34);
}

.chart-grid {
  display: grid;
  gap: 16px;
}

.chart-grid-risk {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 16px;
}

.chart-box {
  position: relative;
  width: 100%;
  height: 280px;
  border-radius: 22px;
  background:
    radial-gradient(circle at top right, rgba(41, 194, 255, 0.1), transparent 28%),
    linear-gradient(180deg, rgba(9, 24, 46, 0.76), rgba(8, 18, 33, 0.68));
  border: 1px solid rgba(67, 108, 151, 0.16);
}

.chart-box::before,
.chart-box::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.chart-box::before {
  top: 10px;
  left: 10px;
  width: 26px;
  height: 26px;
  border-top: 1px solid rgba(114, 246, 255, 0.3);
  border-left: 1px solid rgba(114, 246, 255, 0.3);
  border-top-left-radius: 10px;
}

.chart-box::after {
  right: 10px;
  bottom: 10px;
  width: 26px;
  height: 26px;
  border-right: 1px solid rgba(114, 246, 255, 0.24);
  border-bottom: 1px solid rgba(114, 246, 255, 0.24);
  border-bottom-right-radius: 10px;
}

.chart-wide {
  height: 320px;
  margin-bottom: 16px;
}

.chart-compact {
  height: 220px;
  margin-bottom: 16px;
}

.risk-spotlight {
  position: relative;
  margin-bottom: 16px;
  padding: 18px 20px;
  border-radius: 22px;
  border: 1px solid rgba(255, 107, 107, 0.28);
  background:
    radial-gradient(circle at left center, rgba(255, 82, 120, 0.16), transparent 30%),
    linear-gradient(135deg, rgba(40, 11, 26, 0.94), rgba(18, 12, 25, 0.88));
  overflow: hidden;
  animation: spotlight-pulse 2.8s ease-in-out infinite;
}

.risk-spotlight::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  border: 1px solid rgba(255, 145, 163, 0.16);
  pointer-events: none;
}

.risk-spotlight__badge {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(255, 84, 112, 0.16);
  color: #ffb3c1;
  font-size: 11px;
  letter-spacing: 0.14em;
}

.risk-spotlight__main {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
  margin-top: 12px;
}

.risk-spotlight__main strong {
  display: block;
  color: #fff3f5;
  font-size: 28px;
  line-height: 1.1;
}

.risk-spotlight__main p {
  margin: 8px 0 0;
  color: rgba(255, 219, 226, 0.76);
  line-height: 1.7;
}

.risk-spotlight__meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
  margin-top: 12px;
  color: rgba(255, 219, 226, 0.72);
  font-size: 13px;
}

.table-section {
  margin-top: 4px;
}

.section-title {
  font-size: 14px;
  font-weight: 700;
  color: #ecfbff;
  margin-bottom: 10px;
  letter-spacing: 0.04em;
}

.followup-stats,
.activity-summary,
.video-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 16px;
}

.followup-stat,
.activity-pill,
.video-stat {
  background: linear-gradient(180deg, rgba(14, 33, 60, 0.72), rgba(11, 24, 44, 0.66));
  border-radius: 20px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid rgba(83, 136, 191, 0.2);
}

.followup-stat span,
.activity-pill span,
.video-stat span {
  color: rgba(180, 208, 233, 0.68);
  font-size: 12px;
}

.followup-stat strong,
.activity-pill strong,
.video-stat strong {
  color: #f3fbff;
  font-size: 24px;
  font-weight: 700;
}

.video-alert {
  margin-bottom: 16px;
}

.timeline-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.timeline-summary {
  color: rgba(188, 214, 238, 0.72);
  line-height: 1.7;
}

.timeline-patient {
  color: rgba(180, 208, 233, 0.58);
  font-size: 12px;
  margin-top: 4px;
}

.event-stream {
  position: relative;
  display: grid;
  gap: 14px;
}

.event-stream__item {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr);
  gap: 14px;
  padding: 14px 16px;
  border-radius: 20px;
  border: 1px solid rgba(83, 136, 191, 0.18);
  background: linear-gradient(180deg, rgba(12, 28, 51, 0.78), rgba(10, 20, 38, 0.72));
  animation: panel-rise 0.7s ease both;
}

.event-stream__rail {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.event-stream__dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  margin-top: 4px;
  background: #72f6ff;
  box-shadow: 0 0 12px rgba(114, 246, 255, 0.45);
}

.event-stream__dot--chat {
  background: #3dd9ff;
  box-shadow: 0 0 12px rgba(61, 217, 255, 0.45);
}

.event-stream__dot--report {
  background: #8df7db;
  box-shadow: 0 0 12px rgba(141, 247, 219, 0.45);
}

.event-stream__dot--alert {
  background: #ff7b72;
  box-shadow: 0 0 12px rgba(255, 123, 114, 0.45);
}

.event-stream__line {
  width: 1px;
  flex: 1;
  margin-top: 8px;
  background: linear-gradient(180deg, rgba(114, 246, 255, 0.46), rgba(114, 246, 255, 0.04));
}

.event-stream__content {
  min-width: 0;
}

.event-stream__top {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  align-items: center;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.event-stream__time {
  color: #72f6ff;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.event-stream__title {
  display: block;
  color: #f2fbff;
  font-size: 16px;
  line-height: 1.4;
}

.event-stream__summary {
  margin-top: 6px;
  color: rgba(188, 214, 238, 0.72);
  line-height: 1.7;
}

.event-stream__patient {
  margin-top: 8px;
  color: rgba(180, 208, 233, 0.58);
  font-size: 12px;
}

.paper-case-op-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.paper-case-op-tag--clickable {
  cursor: pointer;
}

.paper-case-op-tags--panel {
  margin-bottom: 16px;
}

.clinical-job-summary {
  margin-top: -4px;
}

.dashboard-inline-note {
  color: rgba(180, 208, 233, 0.62);
  font-size: 12px;
}

.table-section :deep(.el-table) {
  border-radius: 18px;
  overflow: hidden;
  --el-table-border-color: rgba(67, 108, 151, 0.18);
  --el-table-header-bg-color: rgba(15, 31, 54, 0.92);
  --el-table-tr-bg-color: rgba(10, 21, 40, 0.76);
  --el-table-row-hover-bg-color: rgba(22, 49, 82, 0.72);
  --el-table-text-color: #d7edff;
  --el-table-header-text-color: rgba(197, 223, 247, 0.76);
  --el-fill-color-lighter: rgba(12, 23, 42, 0.84);
}

.table-section :deep(.el-table th.el-table__cell) {
  background: transparent;
  color: rgba(197, 223, 247, 0.76);
}

.table-section :deep(.el-table td.el-table__cell) {
  color: #d7edff;
  background: transparent;
}

.table-section :deep(.el-table tr) {
  background: transparent;
}

.dashboard-view :deep(.el-button--primary.is-link) {
  color: #72f6ff;
}

.dashboard-view :deep(.el-tag--info.el-tag--plain) {
  background: rgba(14, 29, 54, 0.82);
  color: #cde8ff;
  border-color: rgba(87, 145, 199, 0.24);
}

.dashboard-view :deep(.el-alert) {
  border-radius: 18px;
  border: 1px solid rgba(255, 191, 73, 0.22);
  background: rgba(64, 38, 6, 0.54);
}

.dashboard-view :deep(.el-alert__title),
.dashboard-view :deep(.el-alert__icon) {
  color: #ffe3a3;
}

@keyframes marquee-scroll {
  from {
    transform: translateX(0);
  }

  to {
    transform: translateX(-50%);
  }
}

@keyframes spotlight-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 rgba(255, 84, 112, 0), inset 0 1px 0 rgba(255, 255, 255, 0.02);
  }

  50% {
    box-shadow: 0 0 26px rgba(255, 84, 112, 0.18), inset 0 1px 0 rgba(255, 255, 255, 0.06);
  }
}

@keyframes panel-rise {
  from {
    opacity: 0;
    transform: translateY(14px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.status-chip:nth-child(2),
.kpi-card:nth-child(2),
.event-stream__item:nth-child(2) {
  animation-delay: 0.06s;
}

.status-chip:nth-child(3),
.kpi-card:nth-child(3),
.event-stream__item:nth-child(3) {
  animation-delay: 0.12s;
}

.status-chip:nth-child(4),
.kpi-card:nth-child(4),
.event-stream__item:nth-child(4) {
  animation-delay: 0.18s;
}

.kpi-card:nth-child(5),
.event-stream__item:nth-child(5) {
  animation-delay: 0.24s;
}

.kpi-card:nth-child(6),
.event-stream__item:nth-child(6) {
  animation-delay: 0.3s;
}

@media (max-width: 1600px) {
  .dashboard-commandbar__meta,
  .dashboard-status-strip,
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .dashboard-screen-grid {
    grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.9fr) minmax(320px, 0.9fr);
  }
}

@media (max-width: 900px) {
  .dashboard-commandbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .dashboard-header {
    flex-direction: column;
  }

  .dashboard-actions {
    width: 100%;
    justify-content: space-between;
  }

  .dashboard-marquee {
    grid-template-columns: 1fr;
  }

  .dashboard-screen-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-screen-column--center .dashboard-card:first-of-type,
  .dashboard-screen-column--center .dashboard-card:last-of-type {
    min-height: auto;
  }

  .dashboard-commandbar__meta,
  .dashboard-status-strip,
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-grid-risk,
  .followup-stats,
  .activity-summary,
  .video-summary {
    grid-template-columns: 1fr;
  }

  .chart-box,
  .chart-wide {
    height: 260px;
  }
}

@media (max-width: 640px) {
  .dashboard-header {
    padding: 20px 18px;
  }

  .dashboard-commandbar {
    padding: 14px 16px;
  }

  .dashboard-commandbar__title strong {
    font-size: 22px;
  }

  .dashboard-header h2 {
    font-size: 24px;
  }

  .dashboard-clock {
    width: 100%;
  }

  .dashboard-card :deep(.el-card__header),
  .dashboard-card :deep(.el-card__body) {
    padding-left: 18px;
    padding-right: 18px;
  }

  .kpi-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-commandbar__meta,
  .dashboard-status-strip {
    grid-template-columns: 1fr;
  }

  .risk-spotlight__main,
  .risk-spotlight__meta,
  .event-stream__top {
    flex-direction: column;
    align-items: flex-start;
  }

  .card-header {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
