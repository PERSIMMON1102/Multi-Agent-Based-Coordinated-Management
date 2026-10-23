<template>
  <div class="admin-page clinical-job-page">
    <section class="admin-hero">
      <div class="admin-hero__content">
        <span class="admin-hero__eyebrow">DATA OPERATIONS</span>
        <h1 class="admin-hero__title">临床数据作业队列</h1>
        <p class="admin-hero__desc">
          统一查看纸质病历导入、设备数据接入与云端归档相关异步作业的失败、运行、待审核、可重试和死信状态，并支持手动重新入队或取消。
        </p>
        <div class="admin-hero__chips">
          <el-tag effect="plain" round>当前筛选 {{ selectedStatusLabel }}</el-tag>
          <el-tag effect="plain" round type="warning">总作业 {{ summary.totalJobs || 0 }}</el-tag>
          <el-tag effect="plain" round type="success">可重试 {{ retryableJobs.length }}</el-tag>
          <el-tag effect="plain" round type="info">可重放 {{ replayableJobs.length }}</el-tag>
          <el-tag effect="plain" round type="danger">超时待处理 {{ summary.runningTimeoutCount || 0 }}</el-tag>
        </div>
      </div>

      <div class="admin-hero__actions">
        <el-button class="admin-hero__ghost" :loading="loading" @click="loadAll">刷新队列</el-button>
        <el-button type="primary" class="admin-hero__primary" :loading="requeueDueLoading" @click="requeueDueJobs">
          批量重新入队
        </el-button>
        <el-button type="success" class="admin-hero__primary" :loading="replayAllLoading" :disabled="replayableJobs.length === 0" @click="replayFailedJobsBatch">
          批量重试归档
        </el-button>
      </div>
    </section>

    <el-alert
      v-if="errorMessage"
      class="clinical-job-alert"
      type="error"
      :closable="false"
      :title="errorMessage"
      show-icon
    />

    <section class="admin-stat-grid admin-stat-grid--five">
      <article class="admin-stat-card admin-stat-card--red">
        <span>失败作业</span>
        <strong>{{ stats.failed }}</strong>
        <p>仍会自动重试或等待人工恢复的失败作业。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--orange">
        <span>死信作业</span>
        <strong>{{ stats.deadLetter }}</strong>
        <p>已达到最大重试次数，需要人工介入处理。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--green">
        <span>待审核作业</span>
        <strong>{{ stats.reviewPending }}</strong>
        <p>通常是等待 ICD 人工确认后才能继续归档的病历作业。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--blue">
        <span>可重试作业</span>
        <strong>{{ stats.retryable }}</strong>
        <p>已到重试时间窗口，可立即重新入队执行。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--teal">
        <span>平均恢复耗时</span>
        <strong>{{ stats.averageRecoveryMinutes }} 分钟</strong>
        <p>统计从首次失败到最终恢复处理的平均耗时。</p>
      </article>
    </section>

    <section class="admin-grid admin-grid--sidebar clinical-job-grid">
      <el-card shadow="never" class="admin-card">
        <template #header>
          <div class="admin-section-head">
            <div>
              <span class="admin-section-head__eyebrow">Filters</span>
              <h2>状态筛选</h2>
              <p>优先关注 failed、dead_letter 和 review_pending，快速定位需要人工干预的作业。</p>
            </div>
          </div>
        </template>

        <div class="admin-toolbar clinical-job-toolbar">
          <el-select v-model="selectedStatus" @change="loadJobsByStatus">
            <el-option
              v-for="item in statusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-button :loading="loading" @click="loadJobsByStatus">刷新当前状态</el-button>
        </div>

        <div class="admin-toolbar clinical-job-toolbar clinical-job-toolbar--search">
          <el-input
            v-model="correlationQuery"
            clearable
            placeholder="按 correlationId 查询完整作业链"
            @keyup.enter="loadJobsByCorrelation"
            @clear="resetCorrelationFilter"
          />
          <el-button :loading="correlationLoading" @click="loadJobsByCorrelation">按记录查询</el-button>
          <el-button text @click="resetCorrelationFilter">清空</el-button>
        </div>

        <el-table class="admin-data-table" :data="jobs" stripe border v-loading="loading">
          <el-table-column prop="id" label="作业 ID" width="96" />
          <el-table-column label="类型" min-width="150">
            <template #default="{ row }">
              {{ row.jobTypeLabel || formatJobType(row.jobType) }}
            </template>
          </el-table-column>
          <el-table-column prop="correlationId" label="记录 ID" min-width="240" show-overflow-tooltip />
          <el-table-column prop="idempotencyKey" label="幂等键" min-width="240" show-overflow-tooltip />
          <el-table-column label="来源" min-width="110">
            <template #default="{ row }">
              {{ row.vendorLabel || row.vendor || '--' }}
            </template>
          </el-table-column>
          <el-table-column prop="jobStatus" label="状态" min-width="120">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="statusTagType(row.jobStatus)">
                {{ row.jobStatus || '--' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lastStage" label="最近阶段" min-width="170" />
          <el-table-column prop="retryCount" label="重试" min-width="90">
            <template #default="{ row }">
              {{ row.retryCount || 0 }} / {{ row.maxRetries || 0 }}
            </template>
          </el-table-column>
          <el-table-column prop="firstFailedAt" label="首次失败" min-width="176">
            <template #default="{ row }">
              {{ formatTime(row.firstFailedAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="nextRetryAt" label="下次重试" min-width="176">
            <template #default="{ row }">
              {{ formatTime(row.nextRetryAt) }}
            </template>
          </el-table-column>
          <el-table-column prop="lastError" label="错误摘要" min-width="220" show-overflow-tooltip />
          <el-table-column label="操作" width="356" fixed="right">
            <template #default="{ row }">
              <el-button
                v-if="canReplayJob(row)"
                link
                type="success"
                :loading="replayingJobId === row.id"
                @click="replayJob(row.id)"
              >
                重试归档
              </el-button>
              <el-button
                v-if="canRequeueJob(row)"
                link
                type="primary"
                :loading="requeueingJobId === row.id"
                @click="requeueJob(row.id)"
              >
                重新入队
              </el-button>
              <el-button
                v-if="canCancelJob(row)"
                link
                type="danger"
                :loading="cancellingJobId === row.id"
                @click="cancelJob(row)"
              >
                取消作业
              </el-button>
              <el-button
                v-if="canReviewPaperCase(row)"
                link
                type="warning"
                @click="openPaperCaseReview(row)"
              >
                去审核病历
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="!loading && jobs.length === 0" class="admin-empty-block">
          <el-empty description="当前状态下暂无作业" />
        </div>
      </el-card>

      <div class="admin-aside-stack">
        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Record Scope</span>
                <h2>记录定位</h2>
                <p>按一次纸质病历上传或一次设备导入追踪完整处理记录。</p>
              </div>
            </div>
          </template>
          <div v-if="activeCorrelationId" class="admin-status-stack">
            <div class="admin-status-item clinical-job-item">
              <strong>当前记录</strong>
              <span>{{ activeCorrelationId }}</span>
              <span>关联作业 {{ jobs.length }}</span>
            </div>
          </div>
          <el-empty v-else description="当前未按记录过滤" :image-size="72" />
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Dead Letter Insight</span>
                <h2>死信诊断摘要</h2>
                <p>快速定位堆积最多的作业类型、阶段和错误模式。</p>
              </div>
            </div>
          </template>
          <div class="clinical-job-summary-grid">
            <div class="clinical-job-summary-block">
              <strong>按类型</strong>
              <div class="admin-status-stack">
                <div
                  v-for="item in summary.deadLetterByJobType"
                  :key="`type-${item.key}`"
                  class="admin-status-item clinical-job-item"
                >
                  <strong>{{ item.label || item.key }}</strong>
                  <span>{{ item.count }} 条死信作业</span>
                </div>
                <el-empty v-if="summary.deadLetterByJobType.length === 0" description="暂无死信类型堆积" :image-size="64" />
              </div>
            </div>
            <div class="clinical-job-summary-block">
              <strong>按阶段</strong>
              <div class="admin-status-stack">
                <div
                  v-for="item in summary.deadLetterByStage"
                  :key="`stage-${item.key}`"
                  class="admin-status-item clinical-job-item"
                >
                  <strong>{{ item.key }}</strong>
                  <span>{{ item.count }} 次终止于该阶段</span>
                </div>
                <el-empty v-if="summary.deadLetterByStage.length === 0" description="暂无阶段堆积" :image-size="64" />
              </div>
            </div>
            <div class="clinical-job-summary-block">
              <strong>错误摘要</strong>
              <div class="admin-status-stack">
                <div
                  v-for="item in summary.topDeadLetterErrors"
                  :key="`error-${item.key}`"
                  class="admin-status-item clinical-job-item"
                >
                  <strong>{{ item.key }}</strong>
                  <span>{{ item.count }} 次重复</span>
                </div>
                <el-empty v-if="summary.topDeadLetterErrors.length === 0" description="暂无错误摘要" :image-size="64" />
              </div>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Retryable Queue</span>
                <h2>到期可重试作业</h2>
                <p>这里显示已进入重试窗口的作业，便于人工恢复处理。</p>
              </div>
            </div>
          </template>
          <div class="admin-status-stack">
            <div v-for="job in retryableJobs" :key="job.id" class="admin-status-item clinical-job-item">
              <strong>#{{ job.id }} · {{ job.jobTypeLabel || formatJobType(job.jobType) }}</strong>
              <span>状态 {{ job.jobStatus || '--' }}</span>
              <span>下次重试 {{ formatTime(job.nextRetryAt) }}</span>
            </div>
            <el-empty v-if="retryableJobs.length === 0" description="暂无到期可重试作业" :image-size="72" />
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Timeout Insight</span>
                <h2>超时作业摘要</h2>
                <p>这里展示近期 `running_timeout` 的作业分布，方便定位执行卡死的来源。</p>
              </div>
            </div>
          </template>
          <div class="clinical-job-summary-grid">
            <div class="clinical-job-summary-block">
              <strong>按类型</strong>
              <div class="admin-status-stack">
                <div
                  v-for="item in summary.runningTimeoutByJobType"
                  :key="`timeout-type-${item.key}`"
                  class="admin-status-item clinical-job-item"
                >
                  <strong>{{ item.label || item.key }}</strong>
                  <span>{{ item.count }} 条超时作业</span>
                </div>
                <el-empty v-if="summary.runningTimeoutByJobType.length === 0" description="暂无超时类型堆积" :image-size="64" />
              </div>
            </div>
            <div class="clinical-job-summary-block">
              <strong>按来源</strong>
              <div class="admin-status-stack">
                <div
                  v-for="item in summary.runningTimeoutByVendor"
                  :key="`timeout-vendor-${item.key}`"
                  class="admin-status-item clinical-job-item"
                >
                  <strong>{{ item.label || item.key }}</strong>
                  <span>{{ item.count }} 条超时作业</span>
                </div>
                <el-empty v-if="summary.runningTimeoutByVendor.length === 0" description="暂无超时来源堆积" :image-size="64" />
              </div>
            </div>
            <div class="clinical-job-summary-block">
              <strong>最近超时作业</strong>
              <div class="admin-status-stack">
                <div
                  v-for="job in summary.recentRunningTimeouts"
                  :key="`timeout-${job.id}`"
                  class="admin-status-item clinical-job-item"
                >
                  <strong>#{{ job.id }} · {{ job.jobTypeLabel || formatJobType(job.jobType) }}</strong>
                  <span>阶段 {{ job.lastStage || '--' }}</span>
                  <span>错误 {{ job.lastError || '--' }}</span>
                </div>
                <el-empty v-if="summary.recentRunningTimeouts.length === 0" description="暂无近期超时作业" :image-size="64" />
              </div>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Recovery Insight</span>
                <h2>恢复效率摘要</h2>
                <p>追踪从首次失败到恢复成功的作业，帮助判断自动重试和人工介入的恢复效率。</p>
              </div>
            </div>
          </template>
          <div class="admin-status-stack">
            <div class="admin-status-item clinical-job-item">
              <strong>已恢复作业</strong>
              <span>{{ summary.recoveredCount || 0 }} 条</span>
              <span>平均恢复 {{ summary.averageRecoveryDurationMinutes || 0 }} 分钟</span>
            </div>
            <div
              v-for="job in summary.recentRecoveredJobs"
              :key="`recovered-${job.id}`"
              class="admin-status-item clinical-job-item"
            >
              <strong>#{{ job.id }} · {{ job.jobTypeLabel || formatJobType(job.jobType) }}</strong>
              <span>首次失败 {{ formatTime(job.firstFailedAt) }}</span>
              <span>恢复完成 {{ formatTime(job.finishedAt) }}</span>
            </div>
            <el-empty v-if="summary.recentRecoveredJobs.length === 0" description="暂无近期恢复作业" :image-size="72" />
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Job Type Insight</span>
                <h2>分来源作业摘要</h2>
                <p>按纸质病历导入和设备数据接入拆开看积压、恢复和超时信号，避免全局平均值掩盖问题。</p>
              </div>
            </div>
          </template>
          <div class="admin-status-stack">
            <div
              v-for="item in summary.jobTypeSummaries"
              :key="`job-type-${item.jobType}`"
              class="admin-status-item clinical-job-item"
            >
              <strong>{{ item.jobTypeLabel || formatJobType(item.jobType) }}</strong>
              <span>总作业 {{ item.totalCount || 0 }} · 失败 {{ item.failedCount || 0 }} · 死信 {{ item.deadLetterCount || 0 }}</span>
              <span>运行中 {{ item.runningCount || 0 }} · 待审核 {{ item.reviewPendingCount || 0 }} · 可重试 {{ item.retryableCount || 0 }}</span>
              <span>运行超时 {{ item.runningTimeoutCount || 0 }} · 已恢复 {{ item.recoveredCount || 0 }} · 平均恢复 {{ item.averageRecoveryDurationMinutes || 0 }} 分钟</span>
            </div>
            <el-empty v-if="summary.jobTypeSummaries.length === 0" description="暂无分来源统计" :image-size="72" />
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Clinical Alerts</span>
                <h2>最近临床告警</h2>
                <p>展示近期死信和执行超时告警，便于快速回溯人工干预轨迹。</p>
              </div>
            </div>
          </template>
          <div class="clinical-job-summary-grid">
            <div class="clinical-job-summary-block">
              <strong>告警类型分布</strong>
              <div class="admin-status-stack">
                <div
                  v-for="(count, type) in summary.recentAlertCounts"
                  :key="`alert-count-${type}`"
                  class="admin-status-item clinical-job-item"
                >
                  <strong>{{ type }}</strong>
                  <span>{{ count }} 条近期告警</span>
                </div>
                <el-empty v-if="Object.keys(summary.recentAlertCounts).length === 0" description="暂无近期告警" :image-size="64" />
              </div>
            </div>
            <div class="clinical-job-summary-block">
              <strong>最近告警</strong>
              <div class="admin-status-stack">
                <div
                  v-for="alert in summary.recentAlerts"
                  :key="`alert-${alert.id}`"
                  class="admin-status-item clinical-job-item"
                >
                  <strong>{{ alert.type }}</strong>
                  <span>{{ alert.title || '--' }}</span>
                  <span>{{ alert.message || '--' }}</span>
                  <span>{{ formatTime(alert.createdAt) }}</span>
                </div>
                <el-empty v-if="summary.recentAlerts.length === 0" description="暂无近期告警" :image-size="64" />
              </div>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Replayable Archive</span>
                <h2>可重试归档作业</h2>
                <p>仅展示纸质病历和设备导入中可重新触发云端归档的作业。</p>
              </div>
            </div>
          </template>
          <div class="admin-status-stack">
            <div v-for="job in replayableJobs" :key="`replay-${job.id}`" class="admin-status-item clinical-job-item">
              <strong>#{{ job.id }} · {{ job.jobTypeLabel || formatJobType(job.jobType) }}</strong>
              <span>来源 {{ job.sourceKind || '--' }}</span>
              <span>状态 {{ job.jobStatus || '--' }}</span>
              <span>阶段 {{ job.lastStage || '--' }}</span>
              <span>错误 {{ job.lastError || '--' }}</span>
            </div>
            <el-empty v-if="replayableJobs.length === 0" description="暂无可重试归档作业" :image-size="72" />
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Recent Dead Letters</span>
                <h2>最近死信作业</h2>
                <p>这里保留最近进入死信队列的作业，用于快速跳到问题排查。</p>
              </div>
            </div>
          </template>
          <div class="admin-status-stack">
            <div v-for="job in summary.recentDeadLetters" :key="`dead-${job.id}`" class="admin-status-item clinical-job-item">
              <strong>#{{ job.id }} · {{ job.jobTypeLabel || formatJobType(job.jobType) }}</strong>
              <span>阶段 {{ job.lastStage || '--' }}</span>
              <span>错误 {{ job.lastError || '--' }}</span>
            </div>
            <el-empty v-if="summary.recentDeadLetters.length === 0" description="暂无近期死信作业" :image-size="72" />
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Guide</span>
                <h2>处理建议</h2>
              </div>
            </div>
          </template>
          <ul class="admin-tips-list">
            <li>`running/received`：说明作业正在执行或已入队待执行，必要时可人工取消，避免重复识别或重复归档。</li>
            <li>`failed`：优先观察 `nextRetryAt` 与 `lastStage`，可等待自动重试，也可立即手动重新入队。</li>
            <li>`dead_letter`：说明自动重试已耗尽，通常需要检查源文件、识别质量或云端配置；纸质病历和设备导入作业可额外尝试 `重试归档`。</li>
            <li>`review_pending`：多见于纸质病历等待 ICD 人工确认，这类作业不应该盲目重试。</li>
          </ul>
        </el-card>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clinicalDataJobApi, medplumApi } from '../../api'
import { ElMessage, ElMessageBox } from '../../utils/element-plus-service'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const correlationLoading = ref(false)
const requeueDueLoading = ref(false)
const replayAllLoading = ref(false)
const requeueingJobId = ref(null)
const replayingJobId = ref(null)
const cancellingJobId = ref(null)
const errorMessage = ref('')
const summary = ref(createEmptySummary())
const selectedStatus = ref('failed')
const correlationQuery = ref('')
const activeCorrelationId = ref('')
const jobs = ref([])
const retryableJobs = ref([])
const replayableJobs = ref([])

const statusOptions = [
  { value: 'failed', label: '失败作业' },
  { value: 'running', label: '运行中作业' },
  { value: 'dead_letter', label: '死信作业' },
  { value: 'review_pending', label: '待审核作业' },
  { value: 'cancelled', label: '已取消作业' },
  { value: 'synced', label: '已归档作业' }
]

const selectedStatusLabel = computed(() => {
  if (activeCorrelationId.value) {
    return `记录 ${activeCorrelationId.value}`
  }
  return statusOptions.find(item => item.value === selectedStatus.value)?.label || selectedStatus.value
})

const stats = computed(() => ({
  failed: Number(summary.value.statusCounts?.failed || 0),
  deadLetter: Number(summary.value.statusCounts?.dead_letter || 0),
  reviewPending: Number(summary.value.statusCounts?.review_pending || 0),
  retryable: Number(summary.value.retryableCount || 0),
  averageRecoveryMinutes: Number(summary.value.averageRecoveryDurationMinutes || 0)
}))

const replayableJobIds = computed(() => new Set(replayableJobs.value.map(job => job.id)))

onMounted(() => {
  if (typeof route.query.correlationId === 'string' && route.query.correlationId.trim()) {
    correlationQuery.value = route.query.correlationId.trim()
    loadCorrelationView()
    return
  }
  loadAll()
})

async function loadAll() {
  await Promise.all([loadSummary(), loadJobsByStatus(), loadRetryableJobs(), loadReplayableJobs()])
}

async function loadSummary() {
  try {
    summary.value = normalizeSummary(await clinicalDataJobApi.getSummary())
  } catch (_error) {
    summary.value = createEmptySummary()
  }
}

async function loadJobsByStatus() {
  loading.value = true
  errorMessage.value = ''
  try {
    activeCorrelationId.value = ''
    jobs.value = normalizeJobs(await clinicalDataJobApi.listByStatus(selectedStatus.value))
  } catch (error) {
    jobs.value = []
    errorMessage.value = error.response?.data?.message || '临床数据作业列表加载失败'
  } finally {
    loading.value = false
  }
}

async function loadJobsByCorrelation() {
  if (!correlationQuery.value.trim()) {
    ElMessage.warning('请输入 correlationId')
    return
  }
  await loadCorrelationView()
}

async function loadCorrelationView() {
  correlationLoading.value = true
  loading.value = true
  errorMessage.value = ''
  try {
    const correlationId = correlationQuery.value.trim()
    jobs.value = normalizeJobs(await clinicalDataJobApi.listByCorrelationId(correlationId))
    activeCorrelationId.value = correlationId
    if (jobs.value.length === 0) {
      ElMessage.warning('未找到对应记录作业')
    }
  } catch (error) {
    jobs.value = []
    activeCorrelationId.value = ''
    errorMessage.value = error.response?.data?.message || '按记录查询作业失败'
  } finally {
    correlationLoading.value = false
    loading.value = false
  }
}

async function loadRetryableJobs() {
  try {
    retryableJobs.value = normalizeJobs(await clinicalDataJobApi.listRetryable())
  } catch (_error) {
    retryableJobs.value = []
  }
}

async function loadReplayableJobs() {
  try {
    replayableJobs.value = normalizeJobs(await medplumApi.listReplayableJobs())
  } catch (_error) {
    replayableJobs.value = []
  }
}

async function requeueJob(jobId) {
  requeueingJobId.value = jobId
  try {
    const result = await clinicalDataJobApi.requeue(jobId)
    ElMessage.success(result.message || '作业已重新入队')
    await loadAll()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '作业重新入队失败')
  } finally {
    requeueingJobId.value = null
  }
}

async function replayJob(jobId) {
  replayingJobId.value = jobId
  try {
    const result = await medplumApi.replayJobSync(jobId)
    if (result.success) {
      ElMessage.success(result.message || '作业已重试归档')
    } else {
      ElMessage.warning(result.message || '作业已执行重试，但归档仍未成功')
    }
    await loadAll()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '作业重试归档失败')
  } finally {
    replayingJobId.value = null
  }
}

async function cancelJob(row) {
  try {
    const { value } = await ElMessageBox.prompt('可选填写取消原因，默认会记录为“运维手动取消”。', `取消作业 #${row.id}`, {
      inputPlaceholder: '例如：重复提交、外部服务异常、人工终止',
      confirmButtonText: '确认取消',
      cancelButtonText: '放弃',
      distinguishCancelAndClose: true
    })
    cancellingJobId.value = row.id
    const result = await clinicalDataJobApi.cancel(row.id, value?.trim() || '运维手动取消')
    ElMessage.success(result.message || '作业已取消')
    await loadAll()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    ElMessage.error(error.response?.data?.message || '取消作业失败')
  } finally {
    cancellingJobId.value = null
  }
}

async function requeueDueJobs() {
  requeueDueLoading.value = true
  try {
    const result = await clinicalDataJobApi.requeueDue()
    ElMessage.success(result.message || '已批量重新入队')
    await loadAll()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '批量重新入队失败')
  } finally {
    requeueDueLoading.value = false
  }
}

async function replayFailedJobsBatch() {
  if (replayableJobs.value.length === 0) {
    ElMessage.info('当前没有可重试归档的作业')
    return
  }
  replayAllLoading.value = true
  try {
    const result = await medplumApi.replayFailedJobs(replayableJobs.value.length)
    if (result.success) {
      ElMessage.success(result.message || '已批量重试归档')
    } else {
      ElMessage.warning(result.message || '批量重放已执行，但存在失败作业')
    }
    await loadAll()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '批量重试归档失败')
  } finally {
    replayAllLoading.value = false
  }
}

function resetCorrelationFilter() {
  correlationQuery.value = ''
  activeCorrelationId.value = ''
  loadJobsByStatus()
}

function normalizeJobs(payload) {
  return Array.isArray(payload) ? payload : []
}

function normalizeSummary(payload) {
  return {
    totalJobs: Number(payload?.totalJobs || 0),
    retryableCount: Number(payload?.retryableCount || 0),
    runningTimeoutCount: Number(payload?.runningTimeoutCount || 0),
    recoveredCount: Number(payload?.recoveredCount || 0),
    averageRecoveryDurationMinutes: Number(payload?.averageRecoveryDurationMinutes || 0),
    statusCounts: payload?.statusCounts || {},
    recentAlertCounts: payload?.recentAlertCounts || {},
    deadLetterByJobType: Array.isArray(payload?.deadLetterByJobType) ? payload.deadLetterByJobType : [],
    deadLetterByStage: Array.isArray(payload?.deadLetterByStage) ? payload.deadLetterByStage : [],
    deadLetterByVendor: Array.isArray(payload?.deadLetterByVendor) ? payload.deadLetterByVendor : [],
    runningTimeoutByJobType: Array.isArray(payload?.runningTimeoutByJobType) ? payload.runningTimeoutByJobType : [],
    runningTimeoutByVendor: Array.isArray(payload?.runningTimeoutByVendor) ? payload.runningTimeoutByVendor : [],
    topDeadLetterErrors: Array.isArray(payload?.topDeadLetterErrors) ? payload.topDeadLetterErrors : [],
    recentDeadLetters: Array.isArray(payload?.recentDeadLetters) ? payload.recentDeadLetters : [],
    recentRunningTimeouts: Array.isArray(payload?.recentRunningTimeouts) ? payload.recentRunningTimeouts : [],
    recentRecoveredJobs: Array.isArray(payload?.recentRecoveredJobs) ? payload.recentRecoveredJobs : [],
    recentAlerts: Array.isArray(payload?.recentAlerts) ? payload.recentAlerts : [],
    jobTypeSummaries: Array.isArray(payload?.jobTypeSummaries) ? payload.jobTypeSummaries : []
  }
}

function createEmptySummary() {
  return {
    totalJobs: 0,
    retryableCount: 0,
    runningTimeoutCount: 0,
    recoveredCount: 0,
    averageRecoveryDurationMinutes: 0,
    statusCounts: {},
    recentAlertCounts: {},
    deadLetterByJobType: [],
    deadLetterByStage: [],
    deadLetterByVendor: [],
    runningTimeoutByJobType: [],
    runningTimeoutByVendor: [],
    topDeadLetterErrors: [],
    recentDeadLetters: [],
    recentRunningTimeouts: [],
    recentRecoveredJobs: [],
    recentAlerts: [],
    jobTypeSummaries: []
  }
}

function statusTagType(status) {
  if (!status) return 'info'
  if (status === 'synced') return 'success'
  if (status === 'running' || status === 'received') return 'primary'
  if (status === 'review_pending') return 'warning'
  if (status === 'cancelled') return 'info'
  if (status === 'dead_letter') return 'danger'
  if (status === 'failed') return 'danger'
  return 'info'
}

function canRequeueJob(job) {
  return ['failed', 'dead_letter'].includes(job?.jobStatus)
}

function canReplayJob(job) {
  return replayableJobIds.value.has(job?.id)
}

function canCancelJob(job) {
  return ['received', 'running', 'failed', 'review_pending'].includes(job?.jobStatus)
}

function canReviewPaperCase(job) {
  return job?.jobStatus === 'review_pending'
    && job?.sourceKind === 'paper_case_record'
    && !!job?.patientId
    && !!job?.sourceRefId
}

function openPaperCaseReview(job) {
  if (!canReviewPaperCase(job)) return
  router.push({
    name: 'AdminPaperCaseReview',
    query: {
      patientId: String(job.patientId),
      paperCaseId: String(job.sourceRefId)
    }
  })
}

function formatTime(value) {
  if (!value) return '--'
  return value.replace('T', ' ')
}

function formatJobType(jobType) {
  if (!jobType) return '--'
  if (jobType === 'paper_case_ingest') return '纸质病历导入'
  if (jobType === 'huawei_iot_import') return '设备数据导入'
  return jobType
}
</script>

<style scoped>
.clinical-job-page {
  gap: 24px;
}

.clinical-job-alert {
  margin-bottom: 20px;
}

.clinical-job-grid {
  margin-top: 8px;
}

.clinical-job-toolbar {
  margin-bottom: 18px;
}

.clinical-job-toolbar--search {
  margin-top: -6px;
}

.clinical-job-item {
  display: grid;
  gap: 4px;
}

.clinical-job-summary-grid {
  display: grid;
  gap: 16px;
}

.clinical-job-summary-block {
  display: grid;
  gap: 10px;
}
</style>
