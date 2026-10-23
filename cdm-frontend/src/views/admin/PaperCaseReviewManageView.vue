<template>
  <div class="admin-page paper-case-review-page">
    <section class="admin-hero">
      <div class="admin-hero__content">
        <span class="admin-hero__eyebrow">Medical Record Review</span>
        <h1 class="admin-hero__title">{{ pageTitle }}</h1>
        <p class="admin-hero__desc">
          {{ pageDescription }}
        </p>
        <div class="admin-hero__chips">
          <el-tag effect="plain" round>当前患者 {{ selectedPatientLabel }}</el-tag>
          <el-tag effect="plain" round type="warning">待确认 {{ pendingReviewCount }}</el-tag>
          <el-tag effect="plain" round type="success">已确认 {{ confirmedCount }}</el-tag>
          <el-tag effect="plain" round type="info">作业异常 {{ failedJobCount }}</el-tag>
          <el-tag v-if="!isDoctorMode" effect="plain" round type="danger">归档失败 {{ failedSyncRecords.length }}</el-tag>
        </div>
      </div>

      <div class="admin-hero__actions">
        <el-button class="admin-hero__ghost" :loading="patientLoading || recordsLoading" @click="loadCurrentPatientRecords">
          刷新病历
        </el-button>
      </div>
    </section>

    <el-alert
      v-if="errorMessage"
      class="paper-case-review-alert"
      type="error"
      :closable="false"
      :title="errorMessage"
      show-icon
    />

    <section class="admin-grid admin-grid--sidebar paper-case-review-grid">
      <el-card shadow="never" class="admin-card admin-sticky-card">
        <template #header>
          <div class="admin-section-head">
            <div>
              <span class="admin-section-head__eyebrow">Patient Locator</span>
              <h2>患者定位</h2>
              <p>{{ patientLocatorDescription }}</p>
            </div>
          </div>
        </template>

        <div v-if="!hasFocusedPatient" class="paper-case-review-search">
          <el-input
            v-model="keyword"
            clearable
            placeholder="按姓名搜索患者"
            @keyup.enter="searchPatients"
            @clear="loadPatients"
          />
          <el-button type="primary" :loading="patientLoading" @click="searchPatients">搜索</el-button>
        </div>

        <el-table
          class="admin-data-table"
          :data="patients"
          stripe
          border
          highlight-current-row
          v-loading="patientLoading"
          @current-change="handlePatientChange"
        >
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="姓名" min-width="110" />
          <el-table-column prop="gender" label="性别" width="80" />
          <el-table-column prop="phone" label="电话" min-width="140" />
        </el-table>

        <div v-if="hasFocusedPatient && selectedPatient" class="admin-status-stack">
          <div class="admin-status-item paper-case-review-item">
            <strong>当前患者</strong>
            <span>{{ selectedPatient.name || '--' }} (#{{ selectedPatient.id || '--' }})</span>
            <span>{{ selectedPatient.gender || '--' }} {{ selectedPatient.phone ? `/ ${selectedPatient.phone}` : '' }}</span>
          </div>
        </div>

        <div v-if="!patientLoading && patients.length === 0" class="admin-empty-block">
          <el-empty description="未找到可审核患者" />
        </div>
      </el-card>

      <div class="admin-aside-stack">
        <el-card v-if="!isDoctorMode" shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head">
              <div>
                <span class="admin-section-head__eyebrow">Global Queue</span>
                <h2>全局待审核病历</h2>
                <p>直接从全局待审核队列切入，快速定位所有待人工确认的病历记录。</p>
              </div>
            </div>
          </template>

          <el-table class="admin-data-table" :data="pendingReviewRecords" stripe border v-loading="pendingQueueLoading">
            <el-table-column prop="patientId" label="患者 ID" width="96" />
            <el-table-column label="患者信息" min-width="180">
              <template #default="{ row }">
                {{ formatPatientInfo(row.patientInfo) }}
              </template>
            </el-table-column>
            <el-table-column label="初步诊断" min-width="220">
              <template #default="{ row }">
                <div class="paper-case-diagnosis-cell">
                  <el-tag
                    v-for="(item, index) in previewDiagnoses(row.preliminaryDiagnosis)"
                    :key="`queue-diagnosis-${row.id}-${index}`"
                    size="small"
                    effect="plain"
                    type="warning"
                  >
                    {{ item }}
                  </el-tag>
                  <span v-if="remainingDiagnosisCount(row.preliminaryDiagnosis) > 0" class="paper-case-diagnosis-more">
                    +{{ remainingDiagnosisCount(row.preliminaryDiagnosis) }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="作业阶段" min-width="160">
              <template #default="{ row }">
                {{ formatJobStage(row.job?.lastStage) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="warning" @click="openPendingReview(row)">打开</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!pendingQueueLoading && pendingReviewRecords.length === 0" class="admin-empty-block">
            <el-empty description="当前没有待审核病历" />
          </div>
        </el-card>

        <el-card v-if="!isDoctorMode" shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head">
              <div>
                <span class="admin-section-head__eyebrow">Archive Recovery</span>
                <h2>归档失败病历</h2>
                <p>集中处理标准疾病编码已确认但云端归档失败的病历，支持直接回到审核上下文或原地重试归档。</p>
              </div>
            </div>
          </template>

          <el-table class="admin-data-table" :data="failedSyncRecords" stripe border v-loading="failedSyncLoading">
            <el-table-column prop="patientId" label="患者 ID" width="96" />
            <el-table-column label="患者信息" min-width="180">
              <template #default="{ row }">
                {{ formatPatientInfo(row.patientInfo) }}
              </template>
            </el-table-column>
            <el-table-column label="初步诊断" min-width="220">
              <template #default="{ row }">
                <div class="paper-case-diagnosis-cell">
                  <el-tag
                    v-for="(item, index) in previewDiagnoses(row.preliminaryDiagnosis)"
                    :key="`failed-diagnosis-${row.id}-${index}`"
                    size="small"
                    effect="plain"
                    type="warning"
                  >
                    {{ item }}
                  </el-tag>
                  <span v-if="remainingDiagnosisCount(row.preliminaryDiagnosis) > 0" class="paper-case-diagnosis-more">
                    +{{ remainingDiagnosisCount(row.preliminaryDiagnosis) }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="medplumSyncMessage" label="失败原因" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="190" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openPendingReview(row)">打开</el-button>
                <el-button
                  v-if="canReplaySync(row)"
                  link
                  type="danger"
                  :loading="replayingJobId === row.job?.id"
                  @click="replaySync(row)"
                >
                  重试归档
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!failedSyncLoading && failedSyncRecords.length === 0" class="admin-empty-block">
            <el-empty description="当前没有归档失败病历" />
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head">
              <div>
                <span class="admin-section-head__eyebrow">Review Queue</span>
                <h2>病历审核列表</h2>
                <p>{{ reviewQueueDescription }}</p>
              </div>
            </div>
          </template>

          <div v-if="isDoctorMode" class="admin-toolbar paper-case-review-toolbar">
            <el-radio-group v-model="doctorFocusFilter" size="small">
              <el-radio-button label="attention">优先待处理</el-radio-button>
              <el-radio-button label="pending_review">待审核</el-radio-button>
              <el-radio-button label="sync_failed">归档失败</el-radio-button>
              <el-radio-button label="all">全部病历</el-radio-button>
            </el-radio-group>
          </div>

          <el-table class="admin-data-table" :data="displayedPaperCaseRecords" stripe border v-loading="recordsLoading">
            <el-table-column prop="recordDate" label="病历日期" min-width="120" />
            <el-table-column prop="originalFileName" label="原文件" min-width="200" show-overflow-tooltip />
            <el-table-column label="初步诊断" min-width="240">
              <template #default="{ row }">
                <div class="paper-case-diagnosis-cell">
                  <el-tag
                    v-for="(item, index) in previewDiagnoses(row.preliminaryDiagnosis)"
                    :key="`record-diagnosis-${row.id}-${index}`"
                    size="small"
                    effect="plain"
                    type="warning"
                  >
                    {{ item }}
                  </el-tag>
                  <span v-if="remainingDiagnosisCount(row.preliminaryDiagnosis) > 0" class="paper-case-diagnosis-more">
                    +{{ remainingDiagnosisCount(row.preliminaryDiagnosis) }}
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="标准编码状态" min-width="160">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="icdStatusTagType(row.icdCodingStatus)">
                  {{ formatIcdStatus(row) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="作业状态" min-width="160">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="jobStatusTagType(row.job?.jobStatus)">
                  {{ formatJobStatus(row.job?.jobStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="selectRecord(row)">审核标准编码</el-button>
                <el-button
                  v-if="canReplaySync(row)"
                  link
                  type="danger"
                  :loading="replayingJobId === row.job?.id"
                  @click="replaySync(row)"
                >
                  重试归档
                </el-button>
                <el-button
                  v-if="row.job?.correlationId && !isDoctorMode"
                  link
                  type="success"
                  @click="openJobChain(row.job.correlationId)"
                >
                  查看处理记录
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!recordsLoading && displayedPaperCaseRecords.length === 0" class="admin-empty-block">
            <el-empty description="当前患者暂无纸质病历记录" />
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card admin-sticky-card paper-case-review-detail-card">
          <template #header>
            <div class="admin-section-head">
              <div>
                <span class="admin-section-head__eyebrow">Code Review</span>
                <h2>人工确认</h2>
                <p>从标准疾病编码候选中选一个，必要时补充诊断标题，确认后会推动后续云端归档。</p>
              </div>
            </div>
          </template>

            <div v-if="selectedRecord" class="paper-case-review-detail">
              <div class="paper-case-review-summary">
                <div class="admin-status-item paper-case-review-item">
                  <strong>病历</strong>
                  <span>{{ selectedRecord.originalFileName || '--' }}</span>
                  <div class="paper-case-review-diagnosis-list">
                    <el-tag
                      v-for="(item, index) in selectedDiagnosisItems"
                      :key="`selected-diagnosis-${selectedRecord.id}-${index}`"
                      size="small"
                      effect="plain"
                      type="warning"
                    >
                      {{ item }}
                    </el-tag>
                    <span v-if="selectedDiagnosisItems.length === 0" class="paper-case-review-empty">未识别</span>
                  </div>
                </div>
                <div class="admin-status-item paper-case-review-item">
                  <strong>当前作业</strong>
                  <span>{{ formatJobStatus(selectedRecord.job?.jobStatus) }}</span>
                  <span>{{ formatJobStage(selectedRecord.job?.lastStage) }}</span>
                </div>
              </div>

            <div v-if="selectedCandidateGroups.length" class="paper-case-review-group-list">
              <section
                v-for="group in selectedCandidateGroups"
                :key="group.key"
                class="paper-case-review-group"
              >
                <div class="paper-case-review-group__head">
                  <div>
                    <strong>{{ group.label }}</strong>
                    <p>{{ group.description }}</p>
                  </div>
                  <el-tag size="small" effect="plain" type="info">{{ group.items.length }} 个候选</el-tag>
                </div>
                <div class="paper-case-review-candidates">
                  <div
                    v-for="candidate in group.items"
                    :key="`${selectedRecord.id}-${group.key}-${candidate.code}`"
                    class="paper-case-review-candidate"
                    :class="{ 'paper-case-review-candidate--active': selectedIcdCode === candidate.code }"
                    @click="chooseCandidate(candidate)"
                  >
                    <div class="paper-case-review-candidate__meta">
                      <strong>{{ candidate.code }}</strong>
                      <el-tag size="small" effect="plain" :type="confidenceTagType(candidate.confidence)">
                        {{ formatConfidenceShort(candidate.confidence) }}
                      </el-tag>
                    </div>
                    <span>{{ candidate.title || '--' }}</span>
                    <small>{{ formatCandidateSource(candidate) }}</small>
                  </div>
                </div>
              </section>
            </div>
            <el-empty v-else description="当前病历暂无标准疾病编码候选，仍可手工录入。" :image-size="64" />

            <el-form label-position="top" class="paper-case-review-form">
              <el-form-item label="确认标准编码">
                <el-input v-model="selectedIcdCode" placeholder="请输入或选择标准疾病编码（ICD-11）" />
              </el-form-item>
              <el-form-item label="诊断标题">
                <el-input v-model="manualIcdTitle" placeholder="请输入标准诊断名称" />
              </el-form-item>
            </el-form>

            <div class="paper-case-review-actions">
              <el-button @click="resetSelection">清空选择</el-button>
              <el-button
                type="primary"
                :loading="confirming"
                :disabled="!selectedRecord?.id || !selectedIcdCode"
                @click="confirmIcd"
              >
                确认标准编码并推进同步
              </el-button>
            </div>
          </div>
          <el-empty v-else description="请先在上方病历列表中选择一条记录" :image-size="80" />
        </el-card>

        <el-card v-if="isDoctorMode" shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head">
              <div>
                <span class="admin-section-head__eyebrow">Clinical Job Exceptions</span>
                <h2>临床作业异常</h2>
                <p>{{ doctorClinicalJobDescription }}</p>
              </div>
            </div>
          </template>

          <div class="admin-toolbar paper-case-review-toolbar paper-case-review-toolbar--wrap">
            <div class="paper-case-review-tags">
              <el-tag size="small" effect="plain" type="warning">
                待审核作业 {{ doctorClinicalJobSummary.reviewPendingCount }}
              </el-tag>
              <el-tag size="small" effect="plain" type="danger">
                失败作业 {{ doctorClinicalJobSummary.failedCount }}
              </el-tag>
              <el-tag size="small" effect="plain" type="danger">
                死信作业 {{ doctorClinicalJobSummary.deadLetterCount }}
              </el-tag>
              <el-tag v-if="doctorClinicalJobSummary.retryableCount > 0" size="small" effect="plain" type="success">
                可重新入队 {{ doctorClinicalJobSummary.retryableCount }}
              </el-tag>
              <el-tag v-if="selectedClinicalJobStatus" size="small" effect="plain" type="info">
                已筛选 {{ formatJobStatus(selectedClinicalJobStatus) }}
              </el-tag>
            </div>
            <el-button size="small" :loading="clinicalJobExceptionLoading" @click="loadDoctorClinicalJobs">刷新异常作业</el-button>
          </div>

          <el-table
            class="admin-data-table"
            :data="doctorClinicalJobs"
            stripe
            border
            v-loading="clinicalJobExceptionLoading"
          >
            <el-table-column label="状态" width="112">
              <template #default="{ row }">
                <el-tag size="small" effect="plain" :type="jobStatusTagType(row.jobStatus)">
                  {{ formatJobStatus(row.jobStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="作业类型" min-width="140">
              <template #default="{ row }">
                {{ formatClinicalJobType(row.jobType) }}
              </template>
            </el-table-column>
            <el-table-column label="当前阶段" min-width="160">
              <template #default="{ row }">
                {{ formatJobStage(row.lastStage) }}
              </template>
            </el-table-column>
            <el-table-column label="关联病历" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                {{ formatClinicalJobSource(row) }}
              </template>
            </el-table-column>
            <el-table-column label="错误信息" min-width="220" show-overflow-tooltip>
              <template #default="{ row }">
                {{ row.lastError || '--' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canOpenClinicalJob(row)"
                  link
                  type="primary"
                  @click="openClinicalJob(row)"
                >
                  {{ row.jobStatus === 'review_pending' ? '去审核' : '打开病历' }}
                </el-button>
                <el-button
                  v-if="canRequeueClinicalJob(row)"
                  link
                  type="danger"
                  :loading="clinicalJobActionJobId === row.id"
                  @click="requeueClinicalJob(row)"
                >
                  重新入队
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!clinicalJobExceptionLoading && doctorClinicalJobs.length === 0" class="admin-empty-block">
            <el-empty description="当前患者暂无需要处理的临床作业异常" />
          </div>
        </el-card>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from '../../utils/element-plus-service'
import { clinicalDataJobApi, medplumApi, paperCaseApi, patientApi } from '../../api'

const route = useRoute()
const router = useRouter()
const patientLoading = ref(false)
const recordsLoading = ref(false)
const pendingQueueLoading = ref(false)
const failedSyncLoading = ref(false)
const clinicalJobExceptionLoading = ref(false)
const confirming = ref(false)
const replayingJobId = ref(null)
const clinicalJobActionJobId = ref(null)
const errorMessage = ref('')
const keyword = ref('')
const patients = ref([])
const selectedPatient = ref(null)
const paperCaseRecords = ref([])
const pendingReviewRecords = ref([])
const failedSyncRecords = ref([])
const selectedRecord = ref(null)
const selectedIcdCode = ref('')
const manualIcdTitle = ref('')
const doctorFocusFilter = ref('attention')
const patientSummary = ref(null)
const doctorReviewPendingJobs = ref([])
const doctorFailedJobs = ref([])
const doctorDeadLetterJobs = ref([])
const doctorRetryableJobs = ref([])

const isDoctorMode = computed(() => String(route.name || '').startsWith('Doctor'))
const focusedPatientId = computed(() => normalizeId(route.query.patientId))
const selectedClinicalJobStatus = computed(() => normalizeJobStatus(route.query.jobStatus))
const selectedClinicalJobType = computed(() => {
  const value = String(route.query.jobType || '').trim()
  return value || ''
})
const hasFocusedPatient = computed(() => isDoctorMode.value && !!focusedPatientId.value)
const pageTitle = computed(() => (isDoctorMode.value ? '纸质病历审核' : '纸质病历审核台'))
const pageDescription = computed(() => (
  isDoctorMode.value
    ? '面向当前医生负责患者的纸质病历审核与归档恢复入口，聚焦标准疾病编码确认和失败后的继续处理。'
    : '面向纸质病历的人工审核入口。先定位患者，再确认标准疾病编码，并联动查看处理记录，形成从识别到归档的完整闭环。'
))
const patientLocatorDescription = computed(() => (
  hasFocusedPatient.value
    ? '当前页面已绑定到指定患者，聚焦处理该患者的纸质病历审核与恢复。'
    : isDoctorMode.value
    ? '按姓名筛选当前医生负责患者，进入对应纸质病历审核流。'
    : '管理员可按姓名快速筛选患者，进入对应纸质病历审核流。'
))
const reviewQueueDescription = computed(() => (
  isDoctorMode.value
    ? '优先处理当前患者中待审核标准编码和归档失败的病历，减少在全量病历之间来回查找。'
    : '只要某条病历存在待审核标准疾病编码候选，就可以在这里完成人工确认。'
))

const selectedPatientLabel = computed(() => {
  if (!selectedPatient.value) return '未选择'
  return `${selectedPatient.value.name || '--'} (#${selectedPatient.value.id || '--'})`
})
const pendingReviewCount = computed(() => paperCaseRecords.value.filter(item => item?.icdCodingStatus === 'pending_review').length)
const confirmedCount = computed(() => paperCaseRecords.value.filter(item => item?.icdCodingStatus === 'manually_confirmed').length)
const failedJobCount = computed(() => paperCaseRecords.value.filter(item => ['failed', 'dead_letter'].includes(item?.job?.jobStatus)).length)
const doctorClinicalJobs = computed(() => {
  const merged = []
  const seen = new Set()
  ;[
    ...doctorReviewPendingJobs.value,
    ...doctorFailedJobs.value,
    ...doctorDeadLetterJobs.value
  ].forEach(item => {
    if (!item?.id || seen.has(item.id)) return
    seen.add(item.id)
    merged.push(item)
  })
  let items = merged
  if (selectedPatient.value?.id) {
    items = items.filter(item => normalizeId(item.patientId) === selectedPatient.value.id)
  }
  if (selectedClinicalJobStatus.value) {
    items = items.filter(item => item.jobStatus === selectedClinicalJobStatus.value)
  }
  if (selectedClinicalJobType.value) {
    items = items.filter(item => item.jobType === selectedClinicalJobType.value)
  }
  return items.sort((left, right) => sortDateValue(right.updatedAt || right.createdAt) - sortDateValue(left.updatedAt || left.createdAt))
})
const doctorClinicalJobSummary = computed(() => ({
  reviewPendingCount: doctorClinicalJobs.value.filter(item => item.jobStatus === 'review_pending').length,
  failedCount: doctorClinicalJobs.value.filter(item => item.jobStatus === 'failed').length,
  deadLetterCount: doctorClinicalJobs.value.filter(item => item.jobStatus === 'dead_letter').length,
  retryableCount: doctorClinicalJobs.value.filter(item => isRetryableClinicalJob(item)).length
}))
const doctorClinicalJobDescription = computed(() => {
  const patientScopeText = hasFocusedPatient.value
    ? '当前页面已聚焦指定患者的医生范围作业异常。'
    : '围绕当前选中患者，直接处理医生范围内的待审核、失败和死信作业。'
  if (selectedClinicalJobStatus.value) {
    return `${patientScopeText} 当前仅显示 ${formatJobStatus(selectedClinicalJobStatus.value)} 作业。`
  }
  return patientScopeText
})
const displayedPaperCaseRecords = computed(() => {
  if (isDoctorMode.value && patientSummary.value) {
    if (doctorFocusFilter.value === 'pending_review') {
      return patientSummary.value.pendingReviewRecords || []
    }
    if (doctorFocusFilter.value === 'sync_failed') {
      return patientSummary.value.failedSyncRecords || []
    }
    if (doctorFocusFilter.value === 'all') {
      return patientSummary.value.allRecords || []
    }
    return patientSummary.value.attentionRecords || []
  }
  if (!isDoctorMode.value) {
    return paperCaseRecords.value
  }
  if (doctorFocusFilter.value === 'all') {
    return paperCaseRecords.value
  }
  if (doctorFocusFilter.value === 'pending_review') {
    return paperCaseRecords.value.filter(item => item?.icdCodingStatus === 'pending_review')
  }
  if (doctorFocusFilter.value === 'sync_failed') {
    return paperCaseRecords.value.filter(item => item?.medplumSyncStatus === 'failed')
  }
  return paperCaseRecords.value.filter(item =>
    item?.icdCodingStatus === 'pending_review'
      || item?.medplumSyncStatus === 'failed'
      || ['failed', 'dead_letter', 'review_pending'].includes(item?.job?.jobStatus)
  )
})
const selectedDiagnosisItems = computed(() => splitPreliminaryDiagnoses(selectedRecord.value?.preliminaryDiagnosis))
const selectedCandidateGroups = computed(() => groupCandidatesByDiagnosis(selectedRecord.value))

onMounted(async () => {
  syncDoctorFocusFilter()
  if (isDoctorMode.value) {
    await loadPatients()
    await loadDoctorClinicalJobs()
  } else {
    await Promise.all([loadPatients(), loadPendingReviewRecords(), loadFailedSyncRecords()])
  }
})

watch(
  () => route.query.focus,
  () => {
    syncDoctorFocusFilter()
  }
)

watch(
  () => route.query.patientId,
  async (value, oldValue) => {
    if (!isDoctorMode.value || value === oldValue) return
    const patientId = normalizeId(value)
    if (!patientId) return
    const matched = patients.value.find(item => item.id === patientId)
    if (matched) {
      selectedPatient.value = matched
      await loadCurrentPatientRecords()
      return
    }
    await loadPatients()
  }
)

async function loadPatients() {
  patientLoading.value = true
  errorMessage.value = ''
  try {
    const params = keyword.value ? { name: keyword.value } : {}
    patients.value = await patientApi.list(params)
    if (hasFocusedPatient.value) {
      patients.value = patients.value.filter(item => item.id === focusedPatientId.value)
    }
    const routePatientId = normalizeId(route.query.patientId)
    if (patients.value.length > 0) {
      selectedPatient.value = patients.value.find(item => item.id === routePatientId) || patients.value[0]
      await loadCurrentPatientRecords()
    } else {
      selectedPatient.value = null
      paperCaseRecords.value = []
      resetSelection()
    }
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '加载患者列表失败'
    ElMessage.error(errorMessage.value)
  } finally {
    patientLoading.value = false
  }
}

async function searchPatients() {
  await loadPatients()
}

async function handlePatientChange(row) {
  selectedPatient.value = row || null
  await loadCurrentPatientRecords()
}

async function loadCurrentPatientRecords() {
  if (!selectedPatient.value?.id) {
    paperCaseRecords.value = []
    patientSummary.value = null
    resetSelection()
    return
  }
  recordsLoading.value = true
  errorMessage.value = ''
  try {
    if (isDoctorMode.value) {
      patientSummary.value = await paperCaseApi.getPatientSummary(selectedPatient.value.id)
      paperCaseRecords.value = Array.isArray(patientSummary.value?.allRecords) ? patientSummary.value.allRecords : []
    } else {
      patientSummary.value = null
      paperCaseRecords.value = await paperCaseApi.getByPatient(selectedPatient.value.id)
    }
    const routePaperCaseId = normalizeId(route.query.paperCaseId)
    const preferredRecordId = routePaperCaseId || selectedRecord.value?.id
    if (preferredRecordId) {
      const refreshed = paperCaseRecords.value.find(item => item.id === preferredRecordId)
        || displayedPaperCaseRecords.value.find(item => item.id === preferredRecordId)
      if (refreshed) {
        selectRecord(refreshed)
      } else {
        resetSelection()
      }
    } else if (displayedPaperCaseRecords.value.length > 0) {
      selectRecord(displayedPaperCaseRecords.value[0])
    } else {
      resetSelection()
    }
  } catch (error) {
    paperCaseRecords.value = []
    patientSummary.value = null
    resetSelection()
    errorMessage.value = error.response?.data?.message || '加载纸质病历记录失败'
    ElMessage.error(errorMessage.value)
  } finally {
    recordsLoading.value = false
  }
}

async function loadDoctorClinicalJobs() {
  if (!isDoctorMode.value) return
  clinicalJobExceptionLoading.value = true
  try {
    const [reviewPending, failed, deadLetter, retryable] = await Promise.all([
      clinicalDataJobApi.listByStatus('review_pending'),
      clinicalDataJobApi.listByStatus('failed'),
      clinicalDataJobApi.listByStatus('dead_letter'),
      clinicalDataJobApi.listRetryable().catch(() => [])
    ])
    doctorReviewPendingJobs.value = Array.isArray(reviewPending) ? reviewPending : []
    doctorFailedJobs.value = Array.isArray(failed) ? failed : []
    doctorDeadLetterJobs.value = Array.isArray(deadLetter) ? deadLetter : []
    doctorRetryableJobs.value = Array.isArray(retryable) ? retryable : []
  } catch (error) {
    doctorReviewPendingJobs.value = []
    doctorFailedJobs.value = []
    doctorDeadLetterJobs.value = []
    doctorRetryableJobs.value = []
    ElMessage.error(error.response?.data?.message || '加载临床作业异常失败')
  } finally {
    clinicalJobExceptionLoading.value = false
  }
}

async function loadPendingReviewRecords() {
  pendingQueueLoading.value = true
  try {
    pendingReviewRecords.value = await paperCaseApi.getPendingReview()
  } catch (error) {
    pendingReviewRecords.value = []
    ElMessage.error(error.response?.data?.message || '加载全局待审核病历失败')
  } finally {
    pendingQueueLoading.value = false
  }
}

async function loadFailedSyncRecords() {
  failedSyncLoading.value = true
  try {
    failedSyncRecords.value = await paperCaseApi.getFailedSync()
  } catch (error) {
    failedSyncRecords.value = []
    ElMessage.error(error.response?.data?.message || '加载归档失败病历失败')
  } finally {
    failedSyncLoading.value = false
  }
}

function selectRecord(row) {
  selectedRecord.value = row
  selectedIcdCode.value = row?.icdCode || row?.icdCandidates?.[0]?.code || ''
  manualIcdTitle.value = row?.icdTitle || row?.icdCandidates?.find(item => item.code === selectedIcdCode.value)?.title || ''
}

function chooseCandidate(candidate) {
  if (!candidate) return
  selectedIcdCode.value = candidate.code || ''
  manualIcdTitle.value = candidate.title || ''
}

function resetSelection() {
  selectedRecord.value = null
  selectedIcdCode.value = ''
  manualIcdTitle.value = ''
}

async function confirmIcd() {
  if (!selectedRecord.value?.id) {
    ElMessage.warning('请先选择一条病历记录')
    return
  }
  if (!selectedIcdCode.value) {
    ElMessage.warning('请先填写标准疾病编码')
    return
  }
  const candidate = selectedRecord.value.icdCandidates?.find(item => item.code === selectedIcdCode.value)
  confirming.value = true
  try {
    await paperCaseApi.confirmIcd(selectedRecord.value.id, {
      code: selectedIcdCode.value,
      title: manualIcdTitle.value || candidate?.title || selectedRecord.value.icdTitle || '',
      confidence: candidate?.confidence ?? selectedRecord.value.icdConfidence ?? null
    })
    ElMessage.success('标准疾病编码已确认，后续归档流程将继续推进')
    await refreshOperationalData()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '标准疾病编码确认失败')
  } finally {
    confirming.value = false
  }
}

function canReplaySync(row) {
  return ['failed', 'dead_letter'].includes(row?.job?.jobStatus) && !!row?.job?.id
}

async function replaySync(row) {
  if (!canReplaySync(row)) return
  replayingJobId.value = row.job.id
  try {
    const result = await medplumApi.replayJobSync(row.job.id)
    if (result.success) {
      ElMessage.success(result.message || '病历同步已重放成功')
    } else {
      ElMessage.warning(result.message || '病历已执行重放，但同步仍未成功')
    }
    await refreshOperationalData()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '病历同步重放失败')
  } finally {
    replayingJobId.value = null
  }
}

function canOpenClinicalJob(job) {
  return !!findPaperCaseRecordForJob(job) || !!normalizeId(job?.patientId)
}

async function openClinicalJob(job) {
  if (!job?.id) return
  const targetPatientId = normalizeId(job.patientId)
  if (targetPatientId && selectedPatient.value?.id !== targetPatientId) {
    const matchedPatient = patients.value.find(item => item.id === targetPatientId)
    if (matchedPatient) {
      selectedPatient.value = matchedPatient
    } else {
      selectedPatient.value = {
        id: targetPatientId,
        name: '关联患者'
      }
    }
    await loadCurrentPatientRecords()
  }
  const matchedRecord = findPaperCaseRecordForJob(job)
  if (matchedRecord) {
    selectRecord(matchedRecord)
    return
  }
  if (targetPatientId) {
    await router.push({
      name: 'PatientDetail',
      params: { id: String(targetPatientId) },
      query: {
        tab: 'clinicaljobs',
        jobId: String(job.id),
        jobStatus: job.jobStatus || '',
        jobType: job.jobType || ''
      }
    })
  }
}

function canRequeueClinicalJob(job) {
  return ['failed', 'dead_letter'].includes(job?.jobStatus) && isRetryableClinicalJob(job)
}

async function requeueClinicalJob(job) {
  if (!canRequeueClinicalJob(job)) return
  clinicalJobActionJobId.value = job.id
  try {
    const result = await clinicalDataJobApi.requeue(job.id)
    if (result?.success) {
      ElMessage.success(result.message || '临床作业已重新入队')
    } else {
      ElMessage.warning(result?.message || '临床作业已尝试重新入队，请稍后刷新确认')
    }
    await refreshOperationalData()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '临床作业重新入队失败')
  } finally {
    clinicalJobActionJobId.value = null
  }
}

function openJobChain(correlationId) {
  if (!correlationId || isDoctorMode.value) return
  router.push({ name: 'AdminClinicalDataJobs', query: { correlationId } })
}

async function openPendingReview(row) {
  if (!row?.patientId || !row?.id) return
  selectedPatient.value = patients.value.find(item => item.id === row.patientId) || selectedPatient.value
  if (!selectedPatient.value || selectedPatient.value.id !== row.patientId) {
    selectedPatient.value = { id: row.patientId, name: row.patientInfo?.name || row.patientName || '待审核患者' }
  }
  await loadCurrentPatientRecords()
  const matched = paperCaseRecords.value.find(item => item.id === row.id)
  if (matched) {
    selectRecord(matched)
  }
}

function normalizeId(value) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

function normalizeJobStatus(value) {
  const normalized = String(value || '').trim()
  return ['review_pending', 'failed', 'dead_letter'].includes(normalized) ? normalized : ''
}

function syncDoctorFocusFilter() {
  if (!isDoctorMode.value) return
  const focus = String(route.query.focus || '').trim()
  if (['attention', 'pending_review', 'sync_failed', 'all'].includes(focus)) {
    doctorFocusFilter.value = focus
    return
  }
  doctorFocusFilter.value = 'attention'
}

async function refreshOperationalData() {
  if (isDoctorMode.value) {
    await Promise.all([loadCurrentPatientRecords(), loadDoctorClinicalJobs()])
    return
  }
  await Promise.all([loadCurrentPatientRecords(), loadPendingReviewRecords(), loadFailedSyncRecords()])
}

function formatPatientInfo(info) {
  if (!info) return '--'
  return [info.name, info.gender, info.age ? `${info.age}岁` : null].filter(Boolean).join(' / ') || '--'
}

function normalizeDiagnosisText(value) {
  return String(value || '')
    .replace(/\s+/g, '')
    .replace(/[()（）]/g, '')
    .replace(/[，,；;。·、:：]/g, '')
    .replace(/第?[一二三四五六七八九十0-9]+级/g, '')
    .trim()
}

function splitPreliminaryDiagnoses(value) {
  const text = String(value || '').trim()
  if (!text) return []
  const normalized = text.replace(/\r/g, '').replace(/\n+/g, ' ').trim()
  const numbered = [...normalized.matchAll(/(?:^|\s)(\d+\s*[.．、]\s*.*?)(?=(?:\s+\d+\s*[.．、]\s*)|$)/g)]
    .map(match => match[1].replace(/^\d+\s*[.．、]\s*/, '').trim())
    .filter(Boolean)
  if (numbered.length > 0) {
    return numbered
  }
  return normalized
    .split(/[；;、\n]+/)
    .map(item => item.replace(/^\d+\s*[.．、]\s*/, '').trim())
    .filter(Boolean)
}

function previewDiagnoses(value, limit = 2) {
  return splitPreliminaryDiagnoses(value).slice(0, limit)
}

function remainingDiagnosisCount(value, limit = 2) {
  const items = splitPreliminaryDiagnoses(value)
  return Math.max(items.length - limit, 0)
}

function isDiagnosisMatched(diagnosis, candidateTitle) {
  const left = normalizeDiagnosisText(diagnosis)
  const right = normalizeDiagnosisText(candidateTitle)
  if (!left || !right) return false
  return left.includes(right) || right.includes(left)
}

function groupCandidatesByDiagnosis(record) {
  const candidates = Array.isArray(record?.icdCandidates) ? [...record.icdCandidates] : []
  if (candidates.length === 0) return []
  const diagnoses = splitPreliminaryDiagnoses(record?.preliminaryDiagnosis)
  const sortedCandidates = candidates.sort((left, right) => (right?.confidence || 0) - (left?.confidence || 0))
  const usedCodes = new Set()
  const groups = diagnoses.map((diagnosis, index) => {
    const items = sortedCandidates.filter(candidate => {
      if (usedCodes.has(candidate.code)) return false
      if (!isDiagnosisMatched(diagnosis, candidate?.title || '')) return false
      usedCodes.add(candidate.code)
      return true
    })
    return {
      key: `diagnosis-${index}`,
      label: `对应诊断 ${index + 1}`,
      description: diagnosis,
      items
    }
  }).filter(group => group.items.length > 0)

  const remaining = sortedCandidates.filter(candidate => !usedCodes.has(candidate.code))
  if (remaining.length > 0) {
    groups.push({
      key: 'other',
      label: '其他相关候选',
      description: diagnoses.length > 0 ? '未与单条诊断直接匹配，但仍建议医生复核。' : '当前没有可拆分的诊断条目，展示全部候选。',
      items: remaining
    })
  }

  if (groups.length === 0) {
    groups.push({
      key: 'all',
      label: '全部候选',
      description: '当前没有可拆分的诊断条目，展示全部候选。',
      items: sortedCandidates
    })
  }
  return groups
}

function formatIcdStatus(record) {
  const mapping = {
    pending_review: '待人工确认',
    auto_coded: '自动编码',
    manually_confirmed: '已人工确认',
    not_coded: '未编码'
  }
  return mapping[record?.icdCodingStatus] || record?.icdCodingStatus || '未知'
}

function icdStatusTagType(status) {
  if (status === 'manually_confirmed' || status === 'auto_coded') return 'success'
  if (status === 'pending_review') return 'warning'
  return 'info'
}

function formatJobStatus(status) {
  const mapping = {
    received: '已接收',
    parsed: '已解析',
    mapped: '已映射',
    review_pending: '待审核',
    running: '执行中',
    synced: '已同步',
    failed: '失败',
    dead_letter: '死信',
    cancelled: '已取消'
  }
  return mapping[status] || status || '未知'
}

function formatJobStage(stage) {
  const mapping = {
    received: '接收病历',
    paper_case_async_dispatch: '异步派发',
    ocr_extract: '病历识别',
    ocr_parsed: '病历识别完成',
    icd_mapping: '标准编码映射',
    structured_mapped: '标准化映射完成',
    medplum_sync: '云端归档',
    medplum_synced: '云端归档完成',
    awaiting_manual_icd_review: '等待标准编码人工确认',
    medplum_sync_failed: '云端归档失败',
    manual_icd_confirmed_medplum_synced: '人工确认后同步完成',
    manual_icd_confirmed_medplum_sync_failed: '人工确认后归档失败',
    failed: '执行失败',
    manual_cancel: '人工取消'
  }
  return mapping[stage] || stage || '阶段未知'
}

function formatClinicalJobType(jobType) {
  if (jobType === 'paper_case_ingest') return '纸质病历导入'
  if (jobType === 'huawei_iot_import') return '设备数据导入'
  return jobType || '未知类型'
}

function formatClinicalJobSource(job) {
  const parts = []
  if (job?.sourceFileName) {
    parts.push(job.sourceFileName)
  }
  const matchedRecord = findPaperCaseRecordForJob(job)
  if (matchedRecord?.recordDate) {
    parts.push(matchedRecord.recordDate)
  }
  if (job?.sourceRefId) {
    parts.push(`#${job.sourceRefId}`)
  }
  return parts.join(' / ') || '--'
}

function jobStatusTagType(status) {
  if (status === 'synced') return 'success'
  if (status === 'review_pending' || status === 'received' || status === 'running' || status === 'parsed' || status === 'mapped') return 'warning'
  if (status === 'failed' || status === 'dead_letter') return 'danger'
  return 'info'
}

function formatConfidence(confidence) {
  if (typeof confidence !== 'number') return '置信度 --'
  return `置信度 ${Math.round(confidence * 100)}%`
}

function formatConfidenceShort(confidence) {
  if (typeof confidence !== 'number') return '置信度 --'
  return `${Math.round(confidence * 100)}%`
}

function confidenceTagType(confidence) {
  if (typeof confidence !== 'number') return 'info'
  if (confidence >= 0.85) return 'success'
  if (confidence >= 0.65) return 'warning'
  return 'info'
}

function formatCandidateSource(candidate) {
  const source = candidate?.source || 'system-mapping'
  return `来源：${source}`
}

function isRetryableClinicalJob(job) {
  return doctorRetryableJobs.value.some(item => item?.id === job?.id)
}

function findPaperCaseRecordForJob(job) {
  if (!job?.id) return null
  return paperCaseRecords.value.find(item => item?.job?.id === job.id)
    || paperCaseRecords.value.find(item => item?.id === job.sourceRefId)
    || null
}

function sortDateValue(value) {
  const timestamp = Date.parse(value || '')
  return Number.isFinite(timestamp) ? timestamp : 0
}
</script>

<style scoped>
.paper-case-review-page {
  gap: 24px;
}

.paper-case-review-alert {
  margin-bottom: 20px;
}

.paper-case-review-grid {
  margin-top: 8px;
  grid-template-columns: minmax(280px, 340px) minmax(0, 1fr);
  gap: 22px;
}

.paper-case-review-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  margin-bottom: 18px;
}

.paper-case-review-toolbar {
  margin-bottom: 18px;
}

.paper-case-review-toolbar--wrap {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.paper-case-review-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.paper-case-review-detail {
  display: grid;
  gap: 18px;
  max-height: calc(100vh - 220px);
  overflow: auto;
  padding-right: 4px;
}

.paper-case-review-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.paper-case-review-item {
  display: grid;
  gap: 4px;
}

.paper-case-diagnosis-cell,
.paper-case-review-diagnosis-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.paper-case-diagnosis-more,
.paper-case-review-empty {
  color: #7b8a97;
  font-size: 13px;
}

.paper-case-review-group-list {
  display: grid;
  gap: 16px;
}

.paper-case-review-group {
  display: grid;
  gap: 12px;
}

.paper-case-review-group__head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
}

.paper-case-review-group__head strong {
  display: block;
  font-size: 15px;
  color: #243746;
}

.paper-case-review-group__head p {
  margin: 4px 0 0;
  color: #607487;
  line-height: 1.6;
}

.paper-case-review-candidates {
  display: grid;
  gap: 10px;
}

.paper-case-review-candidate {
  display: grid;
  gap: 4px;
  padding: 14px 16px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  border-radius: 16px;
  background: rgba(247, 243, 236, 0.55);
  cursor: pointer;
  transition: all 0.2s ease;
}

.paper-case-review-candidate__meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.paper-case-review-candidate small {
  color: #728091;
}

.paper-case-review-candidate:hover,
.paper-case-review-candidate--active {
  border-color: rgba(245, 158, 11, 0.48);
  background: rgba(245, 158, 11, 0.08);
}

.paper-case-review-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.paper-case-review-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

.paper-case-review-detail-card {
  top: 24px;
}

@media (max-width: 1200px) {
  .paper-case-review-grid {
    grid-template-columns: 1fr;
  }

  .paper-case-review-summary,
  .paper-case-review-form {
    grid-template-columns: 1fr;
  }

  .paper-case-review-detail {
    max-height: none;
    overflow: visible;
    padding-right: 0;
  }
}

@media (max-width: 720px) {
  .paper-case-review-search {
    grid-template-columns: 1fr;
  }

  .paper-case-review-toolbar--wrap {
    flex-direction: column;
    align-items: stretch;
  }

  .paper-case-review-actions {
    justify-content: stretch;
    flex-direction: column;
  }
}
</style>
