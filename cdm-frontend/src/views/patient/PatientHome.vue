<template>
  <div class="patient-home">
    <section class="patient-home__hero">
      <div class="patient-home__hero-copy">
        <span class="patient-home__eyebrow">今日健康概览</span>
        <h1>{{ greetingText }}，{{ userStore.username || '患者' }}</h1>
        <p>{{ heroSummary }}</p>
        <div class="patient-home__hero-actions">
          <el-button type="primary" round @click="router.push('/patient/reports')">查看健康报告</el-button>
          <el-button round @click="router.push('/patient/reminders')">查看随访提醒</el-button>
          <el-button text class="patient-home__hero-link" @click="router.push('/patient/chat')">
            咨询健康顾问
          </el-button>
        </div>
      </div>

      <div class="patient-home__hero-panel" :class="`patient-home__hero-panel--${overallRiskTone}`">
        <span class="patient-home__hero-panel-label">综合风险</span>
        <strong>{{ overallRiskLabel }}</strong>
        <p>{{ latestAssessmentTimeText }}</p>
        <div class="patient-home__hero-panel-grid">
          <div>
            <span>活跃随访</span>
            <strong>{{ activeFollowUpCount }}</strong>
          </div>
          <div>
            <span>病史提醒</span>
            <strong>{{ historyAlertCount }}</strong>
          </div>
        </div>
      </div>
    </section>

    <section class="patient-home__metrics">
      <button type="button" class="patient-metric-card patient-metric-card--reports" @click="router.push('/patient/reports')">
        <div class="patient-metric-card__icon">
          <el-icon><Document /></el-icon>
        </div>
        <div class="patient-metric-card__body">
          <span>评估报告</span>
          <strong>{{ assessments.length }}</strong>
          <p>查看最近风险评估和病史提醒</p>
        </div>
      </button>

      <button type="button" class="patient-metric-card patient-metric-card--followups" @click="router.push('/patient/reminders')">
        <div class="patient-metric-card__icon">
          <el-icon><Calendar /></el-icon>
        </div>
        <div class="patient-metric-card__body">
          <span>随访计划</span>
          <strong>{{ activeFollowUpCount }}</strong>
          <p>跟进待完成的计划和提醒</p>
        </div>
      </button>

      <button type="button" class="patient-metric-card patient-metric-card--education" @click="router.push('/patient/education')">
        <div class="patient-metric-card__icon">
          <el-icon><Reading /></el-icon>
        </div>
        <div class="patient-metric-card__body">
          <span>健康宣教</span>
          <strong>{{ latestAssessments.length }}</strong>
          <p>结合当前病种继续查看个性化建议</p>
        </div>
      </button>
    </section>

    <section class="patient-home__content-grid">
      <el-card shadow="never" class="patient-surface-card patient-home__summary-card">
        <template #header>
          <div class="patient-home__section-head">
            <div>
              <span class="patient-home__section-eyebrow">最近变化</span>
              <h2>评估摘要</h2>
            </div>
            <el-button type="primary" plain round @click="router.push('/patient/reports')">查看全部</el-button>
          </div>
        </template>

        <div v-if="latestAssessments.length > 0" class="patient-assessment-list">
          <article
            v-for="a in latestAssessments"
            :key="`${a.diseaseType}-${a.createdAt}`"
            class="patient-assessment-item"
            @click="router.push('/patient/reports')"
          >
            <div class="patient-assessment-item__top">
              <div class="patient-assessment-item__title">
                <el-tag :type="riskTagType(a.riskLevel)" size="small" effect="dark">{{ riskLabel(a.riskLevel) }}</el-tag>
                <span>{{ diseaseLabel(a.diseaseType) }}</span>
              </div>
              <div class="patient-assessment-item__score">
                <span>评分</span>
                <strong>{{ a.score != null ? a.score.toFixed(1) : '--' }}</strong>
              </div>
            </div>

            <p v-if="a.assessmentDetail" class="patient-assessment-item__detail">
              {{ a.assessmentDetail }}
            </p>

            <div class="patient-assessment-item__footer">
              <span>{{ formatDateTime(a.createdAt) }}</span>
              <div v-if="hasHistoryAlerts(a)" class="patient-assessment-item__alerts">
                <el-tag
                  v-for="(alert, index) in a.historyAlerts.slice(0, 2)"
                  :key="`${a.diseaseType}-history-${index}`"
                  type="warning"
                  effect="plain"
                  size="small"
                >
                  {{ alert }}
                </el-tag>
              </div>
            </div>
          </article>
        </div>
        <el-empty v-else description="暂无评估数据" />
      </el-card>

      <div class="patient-home__side-stack">
        <el-card shadow="never" class="patient-surface-card patient-focus-card">
          <template #header>
            <div class="patient-home__section-head patient-home__section-head--compact">
              <div>
                <span class="patient-home__section-eyebrow">本周重点</span>
                <h2>当前关注事项</h2>
              </div>
            </div>
          </template>

          <div class="patient-focus-list">
            <div v-for="(item, index) in careFocus" :key="`focus-${index}`" class="patient-focus-item">
              <span class="patient-focus-item__index">0{{ index + 1 }}</span>
              <div>
                <strong>{{ item.title }}</strong>
                <p>{{ item.description }}</p>
              </div>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="patient-surface-card patient-quick-card">
          <template #header>
            <div class="patient-home__section-head patient-home__section-head--compact">
              <div>
                <span class="patient-home__section-eyebrow">快捷入口</span>
                <h2>继续下一步</h2>
              </div>
            </div>
          </template>

          <div class="patient-quick-grid">
            <button type="button" class="patient-quick-entry" @click="router.push('/patient/daily-record')">
              <span>记录今日指标</span>
              <el-icon><EditPen /></el-icon>
            </button>
            <button type="button" class="patient-quick-entry" @click="router.push('/patient/chat')">
              <span>进入健康咨询</span>
              <el-icon><ChatDotRound /></el-icon>
            </button>
            <button type="button" class="patient-quick-entry" @click="router.push('/patient/health-trend')">
              <span>查看趋势图</span>
              <el-icon><TrendCharts /></el-icon>
            </button>
            <button type="button" class="patient-quick-entry" @click="router.push('/patient/profile')">
              <span>完善个人档案</span>
              <el-icon><UserFilled /></el-icon>
            </button>
          </div>
        </el-card>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { assessmentApi, followUpApi } from '../../api'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()
const assessments = ref([])
const followUps = ref([])
let refreshTimer = null

const diseaseMap = {
  DIABETES: '2型糖尿病',
  HYPERTENSION: '高血压',
  COPD: '慢阻肺',
  CKD: '慢性肾病',
  CHD: '冠心病',
  STROKE: '脑卒中'
}

const riskMap = { LOW: '低', MEDIUM: '中', HIGH: '高', CRITICAL: '危急' }
const riskWeight = { LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4 }

function diseaseLabel(type) {
  return diseaseMap[type] || type
}

function riskLabel(level) {
  return riskMap[level] || level || '暂无'
}

function riskTagType(level) {
  return { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }[level] || 'info'
}

function hasHistoryAlerts(row) {
  return Array.isArray(row?.historyAlerts) && row.historyAlerts.length > 0
}

function formatDateTime(value) {
  return value ? dayjs(value).format('MM-DD HH:mm') : '暂无时间'
}

const sortedAssessments = computed(() => {
  return [...assessments.value].sort((a, b) => dayjs(b.createdAt).valueOf() - dayjs(a.createdAt).valueOf())
})

const latestAssessments = computed(() => {
  const map = new Map()
  sortedAssessments.value.forEach(item => {
    if (!map.has(item.diseaseType)) {
      map.set(item.diseaseType, item)
    }
  })
  return Array.from(map.values())
})

const overallRiskLevel = computed(() => {
  const maxWeight = sortedAssessments.value.reduce((max, item) => {
    return Math.max(max, riskWeight[item.riskLevel] || 0)
  }, 0)
  return Object.keys(riskWeight).find(key => riskWeight[key] === maxWeight) || null
})

const overallRiskLabel = computed(() => {
  if (!overallRiskLevel.value) {
    return '暂无'
  }
  return riskLabel(overallRiskLevel.value)
})

const overallRiskTone = computed(() => {
  return {
    LOW: 'low',
    MEDIUM: 'medium',
    HIGH: 'high',
    CRITICAL: 'critical'
  }[overallRiskLevel.value] || 'neutral'
})

const activeFollowUpCount = computed(() => {
  return followUps.value.filter(item => !['COMPLETED', 'CANCELLED'].includes(item.status)).length
})

const historyAlertCount = computed(() => {
  return latestAssessments.value.reduce((count, item) => {
    return count + (Array.isArray(item.historyAlerts) ? item.historyAlerts.length : 0)
  }, 0)
})

const latestAssessmentTimeText = computed(() => {
  const latest = sortedAssessments.value[0]
  return latest?.createdAt ? `最近评估于 ${dayjs(latest.createdAt).format('M月D日 HH:mm')}` : '暂时还没有评估记录'
})

const greetingText = computed(() => {
  const hour = dayjs().hour()
  if (hour < 11) return '早上好'
  if (hour < 14) return '中午好'
  if (hour < 19) return '下午好'
  return '晚上好'
})

const heroSummary = computed(() => {
  if (sortedAssessments.value.length === 0) {
    return '你还没有生成评估记录，可以先录入健康数据，系统会据此生成风险评估和后续建议。'
  }
  if (overallRiskLevel.value === 'CRITICAL' || overallRiskLevel.value === 'HIGH') {
    return '当前存在较高风险项目，建议优先查看报告中的重点提示，并尽快完成待处理随访。'
  }
  if (activeFollowUpCount.value > 0) {
    return '你目前有待跟进的随访计划，建议先查看提醒，再结合健康咨询确认本周重点。'
  }
  return '你的主要评估结果已经同步，可以继续查看报告、趋势或个性化宣教内容。'
})

const careFocus = computed(() => {
  const items = []

  if (overallRiskLevel.value === 'CRITICAL' || overallRiskLevel.value === 'HIGH') {
    items.push({
      title: '优先关注高风险结果',
      description: '先进入健康报告查看高风险病种的建议，再决定本周要优先落实的检查和复诊。'
    })
  }

  if (activeFollowUpCount.value > 0) {
    items.push({
      title: '完成待处理随访',
      description: `当前还有 ${activeFollowUpCount.value} 条活跃随访计划，建议尽快查看提醒内容并按计划反馈。`
    })
  }

  if (historyAlertCount.value > 0) {
    items.push({
      title: '留意病史相关提醒',
      description: '近期评估中已经识别出与既往病史或过敏信息相关的提醒，查看报告时优先关注这些标签。'
    })
  }

  items.push({
    title: '保持连续记录',
    description: '每天补充血压、血糖或其他关键指标，系统给出的风险趋势和宣教建议会更准确。'
  })

  return items.slice(0, 3)
})

async function loadDashboardData() {
  const patientId = userStore.patientId
  if (!patientId) {
    return
  }
  const [assessmentList, followUpList] = await Promise.all([
    assessmentApi.getByPatient(patientId),
    followUpApi.getByPatient(patientId)
  ])
  assessments.value = assessmentList
  followUps.value = followUpList
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void loadDashboardData()
  }
}

onMounted(async () => {
  await loadDashboardData()
  refreshTimer = window.setInterval(() => {
    void loadDashboardData()
  }, 30000)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.patient-home {
  display: grid;
  gap: 24px;
}

.patient-home__hero {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 0.9fr);
  gap: 18px;
}

.patient-home__hero-copy,
.patient-home__hero-panel {
  border: 1px solid rgba(31, 51, 64, 0.08);
  border-radius: 28px;
  box-shadow: var(--patient-shadow-soft);
}

.patient-home__hero-copy {
  padding: 30px 32px;
  background:
    radial-gradient(circle at top right, rgba(214, 233, 223, 0.85), transparent 32%),
    linear-gradient(135deg, rgba(255, 252, 246, 0.92), rgba(246, 250, 248, 0.92));
}

.patient-home__eyebrow {
  display: inline-block;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: rgba(43, 108, 143, 0.72);
}

.patient-home__hero-copy h1 {
  margin: 12px 0 10px;
  font-size: 34px;
  line-height: 1.1;
  color: var(--patient-ink);
}

.patient-home__hero-copy p {
  max-width: 620px;
  margin: 0;
  font-size: 15px;
  line-height: 1.8;
  color: var(--patient-muted);
}

.patient-home__hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 24px;
}

.patient-home__hero-link {
  font-weight: 600;
}

.patient-home__hero-panel {
  padding: 24px;
  background: rgba(255, 255, 255, 0.82);
}

.patient-home__hero-panel-label {
  display: inline-flex;
  margin-bottom: 16px;
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(43, 108, 143, 0.1);
  font-size: 12px;
  font-weight: 700;
  color: var(--patient-primary);
}

.patient-home__hero-panel strong {
  display: block;
  font-size: 42px;
  line-height: 1;
  color: var(--patient-ink);
}

.patient-home__hero-panel p {
  margin: 12px 0 0;
  color: var(--patient-muted);
  line-height: 1.7;
}

.patient-home__hero-panel-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 20px;
}

.patient-home__hero-panel-grid div {
  padding: 14px;
  border-radius: 18px;
  background: rgba(247, 243, 236, 0.85);
}

.patient-home__hero-panel-grid span {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--patient-muted);
}

.patient-home__hero-panel-grid strong {
  font-size: 20px;
}

.patient-home__hero-panel--low {
  background: linear-gradient(180deg, rgba(227, 244, 233, 0.86), rgba(255, 255, 255, 0.92));
}

.patient-home__hero-panel--medium {
  background: linear-gradient(180deg, rgba(255, 243, 217, 0.88), rgba(255, 255, 255, 0.92));
}

.patient-home__hero-panel--high,
.patient-home__hero-panel--critical {
  background: linear-gradient(180deg, rgba(248, 224, 224, 0.88), rgba(255, 255, 255, 0.92));
}

.patient-home__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.patient-metric-card {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  width: 100%;
  padding: 22px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.8);
  box-shadow: var(--patient-shadow-soft);
  cursor: pointer;
  text-align: left;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.patient-metric-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 34px rgba(35, 52, 62, 0.1);
}

.patient-metric-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 18px;
  font-size: 24px;
}

.patient-metric-card__body span {
  display: block;
  font-size: 13px;
  color: var(--patient-muted);
}

.patient-metric-card__body strong {
  display: block;
  margin: 10px 0 6px;
  font-size: 28px;
  color: var(--patient-ink);
}

.patient-metric-card__body p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--patient-muted);
}

.patient-metric-card--reports .patient-metric-card__icon {
  background: rgba(43, 108, 143, 0.12);
  color: var(--patient-primary);
}

.patient-metric-card--followups .patient-metric-card__icon {
  background: rgba(230, 162, 60, 0.16);
  color: #ad6800;
}

.patient-metric-card--education .patient-metric-card__icon {
  background: rgba(65, 184, 131, 0.16);
  color: #2d6a4f;
}

.patient-home__content-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(300px, 0.95fr);
  gap: 18px;
}

.patient-home__side-stack {
  display: grid;
  gap: 18px;
}

.patient-surface-card {
  border: 1px solid rgba(31, 51, 64, 0.08);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--patient-shadow-soft);
}

.patient-surface-card :deep(.el-card__header) {
  padding: 22px 24px 18px;
  border-bottom: 1px solid rgba(31, 51, 64, 0.06);
}

.patient-surface-card :deep(.el-card__body) {
  padding: 22px 24px 24px;
}

.patient-home__section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.patient-home__section-head--compact {
  align-items: flex-start;
}

.patient-home__section-eyebrow {
  display: inline-block;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: rgba(43, 108, 143, 0.72);
}

.patient-home__section-head h2 {
  margin: 8px 0 0;
  font-size: 22px;
  color: var(--patient-ink);
}

.patient-assessment-list {
  display: grid;
  gap: 14px;
}

.patient-assessment-item {
  padding: 18px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  border-radius: 22px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(247, 248, 245, 0.9));
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
}

.patient-assessment-item:hover {
  transform: translateY(-2px);
  border-color: rgba(43, 108, 143, 0.18);
  box-shadow: 0 18px 32px rgba(35, 52, 62, 0.08);
}

.patient-assessment-item__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.patient-assessment-item__title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 16px;
  font-weight: 700;
  color: var(--patient-ink);
}

.patient-assessment-item__score {
  text-align: right;
}

.patient-assessment-item__score span {
  display: block;
  font-size: 12px;
  color: var(--patient-muted);
}

.patient-assessment-item__score strong {
  font-size: 22px;
  color: var(--patient-ink);
}

.patient-assessment-item__detail {
  margin: 12px 0 0;
  color: var(--patient-muted);
  line-height: 1.8;
}

.patient-assessment-item__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-top: 14px;
  font-size: 12px;
  color: rgba(31, 51, 64, 0.54);
}

.patient-assessment-item__alerts {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.patient-focus-list {
  display: grid;
  gap: 12px;
}

.patient-focus-item {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 14px;
  padding: 14px;
  border-radius: 20px;
  background: rgba(247, 243, 236, 0.8);
}

.patient-focus-item__index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 16px;
  background: rgba(43, 108, 143, 0.1);
  font-size: 14px;
  font-weight: 700;
  color: var(--patient-primary);
}

.patient-focus-item strong {
  display: block;
  margin-bottom: 6px;
  color: var(--patient-ink);
}

.patient-focus-item p {
  margin: 0;
  line-height: 1.7;
  color: var(--patient-muted);
}

.patient-quick-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.patient-quick-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.78);
  color: var(--patient-ink);
  cursor: pointer;
  transition: background 0.18s ease, transform 0.18s ease, border-color 0.18s ease;
}

.patient-quick-entry:hover {
  transform: translateY(-1px);
  border-color: rgba(43, 108, 143, 0.18);
  background: rgba(242, 248, 252, 0.92);
}

.patient-quick-entry span {
  font-size: 14px;
  font-weight: 600;
}

@media (max-width: 1200px) {
  .patient-home__hero {
    grid-template-columns: 1fr;
  }

  .patient-home__content-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .patient-home__metrics {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .patient-home__hero-copy,
  .patient-home__hero-panel {
    padding: 22px 20px;
  }

  .patient-home__hero-copy h1 {
    font-size: 28px;
  }

  .patient-home__hero-panel-grid,
  .patient-quick-grid {
    grid-template-columns: 1fr;
  }

  .patient-home__section-head,
  .patient-assessment-item__top,
  .patient-assessment-item__footer {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
