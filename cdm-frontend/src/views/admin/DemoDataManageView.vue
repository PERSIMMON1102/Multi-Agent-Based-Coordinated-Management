<template>
  <div class="admin-page demo-data-page">
    <section class="admin-hero">
      <div class="admin-hero__content">
        <span class="admin-hero__eyebrow">Patient Access Control</span>
        <h1 class="admin-hero__title">患者接入与云端验证</h1>
        <p class="admin-hero__desc">
          管理正式患者的云端映射、责任医生绑定与资料可用状态，并验证云端患者资料与健康指标是否已经进入正式业务流程。
        </p>
        <div class="admin-hero__chips">
          <el-tag effect="plain" round>正式患者 {{ integrationOverview.registeredPatients || 0 }}</el-tag>
          <el-tag effect="plain" round type="info">筛选结果 {{ integrationOverview.filteredPatients || 0 }}</el-tag>
          <el-tag effect="plain" round type="success">服务可用 {{ integrationOverview.agentReadyPatients || 0 }}</el-tag>
          <el-tag effect="plain" round type="warning">云端映射 {{ integrationOverview.medplumMappedPatients || 0 }}</el-tag>
        </div>
      </div>

      <div class="admin-hero__actions">
        <el-button class="admin-hero__ghost" :loading="integrationLoading" @click="loadIntegrationOverview">刷新患者接入</el-button>
        <el-button class="admin-hero__ghost" :loading="medplumStatusLoading" @click="loadMedplumStatus">刷新云端状态</el-button>
      </div>
    </section>

    <el-alert
      v-if="errorMessage"
      type="error"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
      :title="errorMessage"
    />

    <section class="admin-stat-grid admin-stat-grid--four">
      <article class="admin-stat-card admin-stat-card--blue">
        <span>正式患者</span>
        <strong>{{ integrationOverview.registeredPatients || 0 }}</strong>
        <p>已注册的正式业务患者。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--green">
        <span>已绑定医生</span>
        <strong>{{ integrationOverview.assignedPatients || 0 }}</strong>
        <p>已满足医生端患者级对话前提。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--orange">
        <span>云端已映射</span>
        <strong>{{ integrationOverview.medplumMappedPatients || 0 }}</strong>
        <p>云端可读的 Patient 映射数。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--red">
        <span>服务已就绪</span>
        <strong>{{ integrationOverview.agentReadyPatients || 0 }}</strong>
        <p>本地上下文可用，医生端可直接发起 AI 对话。</p>
      </article>
    </section>

    <el-card shadow="never" class="admin-card">
      <template #header>
        <div class="admin-section-head">
          <div>
            <span class="admin-section-head__eyebrow">Access Status</span>
            <h2>正式患者接入</h2>
            <p>展示已注册患者的医生绑定、云端映射与资料可用性。</p>
          </div>
          <el-tag :type="medplumStatus?.authenticated ? 'success' : 'warning'" effect="plain">
            云端 {{ medplumStatus?.authenticated ? '已连通' : '未连通' }}
          </el-tag>
        </div>
      </template>

      <div class="integration-summary">
        <div class="integration-summary__item">
          <span>云端接入</span>
          <strong>{{ integrationOverview.medplum?.message || '状态待加载' }}</strong>
        </div>
        <div class="integration-summary__item">
          <span>本地健康记录</span>
          <strong>{{ integrationOverview.localHealthRecords || 0 }}</strong>
        </div>
        <div class="integration-summary__item">
          <span>档案已完善</span>
          <strong>{{ integrationOverview.profileCompletedPatients || 0 }}</strong>
        </div>
        <div class="integration-summary__item">
          <span>当前筛选结果</span>
          <strong>{{ integrationOverview.filteredPatients || 0 }}</strong>
        </div>
      </div>

      <div class="integration-toolbar">
        <el-input
          v-model="integrationQuery.keyword"
          clearable
          placeholder="搜索已注册患者：姓名 / 用户名 / 手机 / 云端编号"
          @keyup.enter="loadIntegrationOverview"
          @clear="loadIntegrationOverview"
        />
        <el-select v-model="integrationQuery.readiness" @change="loadIntegrationOverview">
          <el-option label="全部患者" value="all" />
          <el-option label="未绑定医生" value="unassigned" />
          <el-option label="档案未完善" value="profile_incomplete" />
          <el-option label="未映射云端" value="unmapped" />
          <el-option label="服务已就绪" value="agent_ready" />
        </el-select>
        <el-button type="primary" plain :loading="integrationLoading" @click="loadIntegrationOverview">查询患者</el-button>
      </div>

      <el-table :data="integrationPatients" size="small" border stripe>
        <el-table-column prop="name" label="患者" min-width="160">
          <template #default="{ row }">
            <div class="cell-title">{{ row.name }}</div>
            <div class="cell-subtitle">{{ row.username || '未配置账号' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="责任医生" min-width="150">
          <template #default="{ row }">
            <div class="cell-title">{{ row.assignedDoctorName || '未绑定' }}</div>
            <div class="cell-subtitle">ID {{ row.assignedDoctorId || '--' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="绑定调整" min-width="220">
          <template #default="{ row }">
            <el-select
              v-model="doctorBindings[row.patientId]"
              filterable
              clearable
              placeholder="选择责任医生"
              class="doctor-binding-select"
            >
              <el-option
                v-for="doctor in doctorOptions"
                :key="doctor.id"
                :label="`${doctor.name}${doctor.department ? ` · ${doctor.department}` : ''}`"
                :value="doctor.id"
              />
            </el-select>
            <el-button
              link
              type="warning"
              :loading="bindingPatientId === row.patientId"
              @click="bindDoctor(row.patientId)"
            >
              保存绑定
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="本地上下文" min-width="160">
          <template #default="{ row }">
            <el-tag size="small" :type="row.patientContextReady ? 'success' : 'warning'" effect="plain">
              {{ row.patientContextReady ? '上下文可读' : '上下文不足' }}
            </el-tag>
            <el-tag size="small" type="info" effect="plain" class="tag-gap">
              记录 {{ row.localHealthRecordCount || 0 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="云端上下文" min-width="170">
          <template #default="{ row }">
            <el-tag size="small" :type="row.medplumContextReady ? 'success' : 'warning'" effect="plain">
              {{ row.medplumContextReady ? '云端已映射' : '云端未映射' }}
            </el-tag>
            <div class="cell-subtitle">健康指标 {{ row.medplumObservationCount || 0 }}</div>
          </template>
        </el-table-column>
        <el-table-column label="AI 就绪" min-width="160">
          <template #default="{ row }">
            <el-tag size="small" :type="row.agentContextReady ? 'success' : 'warning'" effect="dark">
              {{ row.agentContextReady ? '已就绪' : '降级运行' }}
            </el-tag>
            <div class="cell-subtitle">{{ row.degradeReason || row.agentContextMessage }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="190" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="selectPatient(row.patientId)">查看详情</el-button>
            <el-button
              link
              type="success"
              :loading="syncingPatientId === row.patientId"
              @click="syncBusinessPatient(row.patientId)"
            >
              同步云端档案
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="selectedPatientStatus?.found" shadow="never" class="admin-card detail-card">
      <template #header>
        <div class="admin-section-head">
          <div>
            <span class="admin-section-head__eyebrow">Service Readiness</span>
            <h2>{{ selectedPatientStatus.name }} 的接入详情</h2>
            <p>{{ selectedPatientStatus.agentContextMessage }}</p>
          </div>
          <el-tag :type="selectedPatientStatus.agentContextReady ? 'success' : 'warning'" effect="dark">
            {{ selectedPatientStatus.agentContextReady ? '服务已就绪' : '服务降级运行' }}
          </el-tag>
        </div>
      </template>

      <div class="detail-grid">
        <div class="detail-item">
          <span>患者 ID</span>
          <strong>{{ selectedPatientStatus.patientId }}</strong>
        </div>
        <div class="detail-item">
          <span>云端编号</span>
          <strong>{{ selectedPatientStatus.fhirId || '--' }}</strong>
        </div>
        <div class="detail-item">
          <span>云端患者编号</span>
          <strong>{{ selectedPatientStatus.medplumPatientId || '--' }}</strong>
        </div>
        <div class="detail-item">
          <span>上下文来源</span>
          <strong>{{ (selectedPatientStatus.contextSources || []).join(' + ') || '--' }}</strong>
        </div>
      </div>

      <el-row :gutter="16" class="detail-panels">
        <el-col :md="12" :sm="24">
          <div class="detail-panel">
            <div class="detail-panel__head detail-panel__head--summary">
              <div class="detail-panel__title">本地上下文摘要</div>
              <el-button
                v-if="hasLongLocalSummary"
                link
                type="primary"
                @click="localSummaryExpanded = !localSummaryExpanded"
              >
                {{ localSummaryExpanded ? '收起详情' : '查看详情' }}
              </el-button>
            </div>
            <p>{{ displayedLocalSummary }}</p>
          </div>
        </el-col>
        <el-col :md="12" :sm="24">
          <div class="detail-panel">
            <div class="detail-panel__title">云端摘要</div>
            <p>{{ selectedPatientStatus.medplumPatientSummary || '暂无云端患者摘要。' }}</p>
            <p class="detail-panel__muted">{{ selectedPatientStatus.medplumObservationSummary || '' }}</p>
          </div>
        </el-col>
      </el-row>

      <div class="detail-panel detail-panel--cloud">
        <div class="detail-panel__head">
          <div>
            <div class="detail-panel__title">云端资料视图</div>
            <p class="detail-panel__muted">
              直接展示云端患者资料与最近一段历史健康指标，用于验证系统是否已接入本地之外的数据来源。
            </p>
          </div>
          <el-button
            size="small"
            :loading="cloudLoading"
            @click="loadSelectedPatientCloudData"
          >
            刷新云端资料
          </el-button>
        </div>

        <div class="detail-grid detail-grid--three">
          <div class="detail-item">
            <span>云端患者</span>
            <strong>{{ maskedCloudPatientName }}</strong>
          </div>
          <div class="detail-item">
            <span>云端档案</span>
            <strong>{{ cloudPatientData.profileCompleted === true ? '已完善' : cloudPatientData.profileCompleted === false ? '未完善' : '--' }}</strong>
          </div>
          <div class="detail-item">
            <span>健康指标数</span>
            <strong>{{ cloudObservations.length }}</strong>
          </div>
          <div class="detail-item">
            <span>纸质病历资源</span>
            <strong>{{ cloudPaperCaseResourceCount }}</strong>
          </div>
        </div>

        <el-alert
          v-if="cloudMessage"
          :type="cloudPatientData.found || cloudObservationMeta.found ? 'success' : 'warning'"
          :closable="false"
          show-icon
          style="margin-top: 12px"
          :title="cloudMessage"
        />

        <div class="detail-panel__subsection">
          <div class="detail-panel__head">
            <div class="detail-panel__subtitle">云端病史摘要</div>
            <el-switch
              v-model="showCloudSensitiveDetails"
              inline-prompt
              active-text="明文"
              inactive-text="脱敏"
            />
          </div>
          <p>{{ displayedCloudHistorySummary }}</p>
        </div>

        <div class="detail-panel__subsection">
          <div class="detail-panel__subtitle">本地 / 云端差异提示</div>
          <div v-if="cloudOnlySignals.length" class="signal-list">
            <el-tag
              v-for="signal in cloudOnlySignals"
              :key="signal"
              class="list-tag"
              type="danger"
              effect="plain"
            >
              {{ signal }}
            </el-tag>
          </div>
          <p v-else class="detail-panel__muted">当前未识别到明显的云端独有字段，或患者尚未完成云端同步。</p>
        </div>

        <div class="detail-panel__subsection">
          <div class="detail-panel__subtitle">最近健康指标记录</div>
          <el-table :data="cloudObservations" size="small" border stripe empty-text="暂无云端健康指标">
            <el-table-column prop="effectiveDateTime" label="时间" min-width="180" />
            <el-table-column prop="code" label="指标" min-width="180" />
            <el-table-column prop="value" label="值" min-width="160" />
          </el-table>
        </div>

        <div class="detail-panel__subsection">
          <div class="detail-panel__subtitle">纸质病历标准资源</div>
          <div class="paper-case-resource-grid">
            <div class="result-item">
              <span>就诊记录</span>
              <strong>{{ cloudEncounters.length }}</strong>
            </div>
            <div class="result-item">
              <span>诊断记录</span>
              <strong>{{ cloudConditions.length }}</strong>
            </div>
            <div class="result-item">
              <span>病历文档</span>
              <strong>{{ cloudDocumentReferences.length }}</strong>
            </div>
          </div>
          <el-table :data="cloudPaperCaseRows" size="small" border stripe empty-text="暂无云端纸质病历标准资源">
            <el-table-column prop="resourceType" label="资源类型" min-width="150" />
            <el-table-column prop="display" label="摘要" min-width="260" show-overflow-tooltip />
            <el-table-column prop="date" label="时间" min-width="180" />
            <el-table-column prop="id" label="资源 ID" min-width="180" show-overflow-tooltip />
          </el-table>
        </div>
      </div>

      <div class="detail-panel detail-panel--validation">
        <div class="detail-panel__head">
          <div>
            <div class="detail-panel__title">接入验证结论</div>
            <p class="detail-panel__muted">
              该结论仅基于管理员可见的接入状态、云端患者资料与健康指标差异，不进入医生端会话，不模拟医生身份。
            </p>
          </div>
          <el-button
            size="small"
            type="primary"
            plain
            :disabled="!selectedPatientStatus?.found"
            @click="exportValidationReport"
          >
            导出验证报告
          </el-button>
        </div>

        <div class="validation-grid">
          <div class="validation-item">
            <span>本地正式患者</span>
            <strong>{{ selectedPatientStatus.username?.startsWith('demo-patient-') ? '否' : '是' }}</strong>
          </div>
          <div class="validation-item">
            <span>云端已映射</span>
            <strong>{{ selectedPatientStatus.medplumContextReady ? '是' : '否' }}</strong>
          </div>
          <div class="validation-item">
            <span>云端健康指标</span>
            <strong>{{ cloudObservations.length > 0 ? `是（${cloudObservations.length} 条）` : '否' }}</strong>
          </div>
          <div class="validation-item">
            <span>云端病历资源</span>
            <strong>{{ cloudPaperCaseResourceCount > 0 ? `是（${cloudPaperCaseResourceCount} 条）` : '否' }}</strong>
          </div>
          <div class="validation-item">
            <span>发现云端独有信号</span>
            <strong>{{ cloudOnlySignals.length > 0 ? `是（${cloudOnlySignals.length} 项）` : '否' }}</strong>
          </div>
        </div>

        <el-alert
          :type="validationConclusion.type"
          :closable="false"
          show-icon
          style="margin-top: 14px"
          :title="validationConclusion.title"
          :description="validationConclusion.description"
        />

        <div class="detail-panel__subsection">
          <div class="detail-panel__subtitle">报告预览</div>
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="导出范围">管理员侧患者接入验证</el-descriptions-item>
            <el-descriptions-item label="患者">{{ selectedPatientStatus.name || '--' }}</el-descriptions-item>
            <el-descriptions-item label="正式患者">{{ selectedPatientStatus.username?.startsWith('demo-patient-') ? '否' : '是' }}</el-descriptions-item>
            <el-descriptions-item label="云端映射">{{ selectedPatientStatus.medplumContextReady ? '已建立' : '未建立' }}</el-descriptions-item>
            <el-descriptions-item label="云端健康指标">{{ cloudObservations.length }} 条</el-descriptions-item>
            <el-descriptions-item label="云端病历资源">{{ cloudPaperCaseResourceCount }} 条</el-descriptions-item>
            <el-descriptions-item label="云端独有信号">{{ cloudOnlySignals.length }} 项</el-descriptions-item>
            <el-descriptions-item label="云端取数标记">聊天页单条回复可显示 `usedCloudPatientData`</el-descriptions-item>
            <el-descriptions-item label="导出格式">JSON</el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from '../../utils/element-plus-service'
import { adminApi, medplumApi } from '../../api'

const integrationOverview = ref({})
const selectedPatientStatus = ref(null)
const integrationLoading = ref(false)
const medplumStatusLoading = ref(false)
const syncingPatientId = ref(null)
const bindingPatientId = ref(null)
const errorMessage = ref('')
const medplumStatus = ref(null)
const doctorOptions = ref([])
const doctorBindings = ref({})
const cloudLoading = ref(false)
const cloudPatientData = ref({})
const cloudObservationMeta = ref({})
const cloudPaperCaseMeta = ref({})
const showCloudSensitiveDetails = ref(false)
const localSummaryExpanded = ref(false)
const integrationQuery = ref({
  keyword: '',
  readiness: 'all'
})

const integrationPatients = computed(() => integrationOverview.value.patients || [])
const cloudObservations = computed(() => cloudObservationMeta.value.observations || [])
const cloudEncounters = computed(() => cloudPaperCaseMeta.value.encounters || [])
const cloudConditions = computed(() => cloudPaperCaseMeta.value.conditions || [])
const cloudDocumentReferences = computed(() => cloudPaperCaseMeta.value.documentReferences || [])
const cloudPaperCaseResourceCount = computed(() => (
  cloudEncounters.value.length + cloudConditions.value.length + cloudDocumentReferences.value.length
))
const cloudPaperCaseRows = computed(() => {
  const encounterRows = cloudEncounters.value.map(item => ({
    resourceType: 'Encounter',
    id: item?.id || '',
    display: item?.display || '纸质病历就诊',
    date: item?.date || ''
  }))
  const conditionRows = cloudConditions.value.map(item => ({
    resourceType: 'Condition',
    id: item?.id || '',
    display: item?.display || '未命名诊断',
    date: item?.date || ''
  }))
  const documentRows = cloudDocumentReferences.value.map(item => ({
    resourceType: 'DocumentReference',
    id: item?.id || '',
    display: item?.display || '纸质病历文书',
    date: item?.date || ''
  }))
  return [...encounterRows, ...conditionRows, ...documentRows]
})
const localSummaryText = computed(() => {
  return String(selectedPatientStatus.value?.patientContextSummary || '').trim()
})
const hasLongLocalSummary = computed(() => localSummaryText.value.length > 120)
const displayedLocalSummary = computed(() => {
  if (!localSummaryText.value) {
    return '暂无本地上下文摘要。'
  }
  if (localSummaryExpanded.value || !hasLongLocalSummary.value) {
    return localSummaryText.value
  }
  return `${localSummaryText.value.slice(0, 120)}...`
})
const maskedCloudPatientName = computed(() => {
  const name = String(cloudPatientData.value?.name || '').trim()
  if (!name) return '--'
  if (showCloudSensitiveDetails.value) return name
  if (name.length <= 1) return `${name}*`
  if (name.length === 2) return `${name[0]}*`
  return `${name[0]}${'*'.repeat(Math.max(1, name.length - 2))}${name[name.length - 1]}`
})
const displayedCloudHistorySummary = computed(() => {
  const raw = String(cloudPatientData.value?.medicalHistorySummary || '').trim()
  if (!raw) return '云端未返回额外病史摘要。'
  if (showCloudSensitiveDetails.value) return raw
  return raw
    .replace(/\b1[3-9]\d{9}\b/g, '1**********')
    .replace(/\b\d{15,18}[0-9Xx]?\b/g, '**************')
    .replace(/责任医生ID\s*\d+/g, '责任医生ID **')
})
const cloudMessage = computed(() => {
  return cloudObservationMeta.value.message || cloudPaperCaseMeta.value.message || cloudPatientData.value.message || ''
})
const cloudOnlySignals = computed(() => {
  const signals = []
  const localSummary = selectedPatientStatus.value?.patientContextSummary || ''
  const localDoctorId = selectedPatientStatus.value?.assignedDoctorId
  const cloudDoctorId = cloudPatientData.value?.assignedDoctorId
  const cloudHistorySummary = cloudPatientData.value?.medicalHistorySummary || ''
  const latestLocalRecordDate = selectedPatientStatus.value?.latestRecordDate || null

  if (!localSummary && cloudHistorySummary) {
    signals.push('云端存在病史摘要，本地上下文为空')
  } else if (cloudHistorySummary && !localSummary.includes(cloudHistorySummary.split('\n')[0])) {
    signals.push('云端病史摘要与本地上下文不一致')
  }

  if (!localDoctorId && cloudDoctorId) {
    signals.push('云端存在责任医生绑定信息，本地未绑定')
  }

  if (cloudObservations.value.length > 0 && !latestLocalRecordDate) {
    signals.push(`云端已有 ${cloudObservations.value.length} 条健康指标，本地无健康记录`)
  }

  if (cloudPaperCaseResourceCount.value > 0) {
    signals.push(`云端已存在 ${cloudPaperCaseResourceCount.value} 条纸质病历标准资源`)
  }

  const unseenObservationCodes = cloudObservations.value
    .map(item => item?.code)
    .filter(code => code && !localSummary.includes(code))

  if (unseenObservationCodes.length > 0) {
    signals.push(`云端额外指标：${[...new Set(unseenObservationCodes)].slice(0, 3).join('、')}`)
  }

  return signals
})
const validationConclusion = computed(() => {
  const isBusinessPatient = selectedPatientStatus.value?.username && !selectedPatientStatus.value.username.startsWith('demo-patient-')
  const medplumReady = Boolean(selectedPatientStatus.value?.medplumContextReady)
  const cloudHasObservation = cloudObservations.value.length > 0
  const hasCloudOnlySignals = cloudOnlySignals.value.length > 0

  if (isBusinessPatient && medplumReady && (cloudHasObservation || hasCloudOnlySignals)) {
    return {
      type: 'success',
      title: '已满足管理员侧接入验证条件',
      description: '该正式患者已建立云端映射，且管理员侧已观测到云端患者资料、健康指标或云端独有信号。无需进入医生端，即可证明外部数据源已接入本系统。'
    }
  }

  if (isBusinessPatient && medplumReady) {
    return {
      type: 'warning',
      title: '已完成云端映射，但差异证据仍不足',
      description: '当前能证明该正式患者已接入云端，但尚未形成足够明确的“云端有、本地无”取证信号。可继续补充云端健康指标后再验证。'
    }
  }

  return {
    type: 'info',
    title: '当前尚未形成接入验证证据',
    description: '请先完成正式患者绑定与同步，确保云端患者资料和健康指标已可读，再观察云端差异提示。'
  }
})

onMounted(async () => {
  await Promise.all([
    loadIntegrationOverview(),
    loadMedplumStatus(),
    loadDoctors()
  ])
})

async function loadIntegrationOverview() {
  integrationLoading.value = true
  errorMessage.value = ''
  try {
    integrationOverview.value = await adminApi.getPatientIntegrationOverview({
      keyword: integrationQuery.value.keyword?.trim() || undefined,
      readiness: integrationQuery.value.readiness || undefined
    })
    doctorBindings.value = Object.fromEntries(
      integrationPatients.value.map(row => [row.patientId, row.assignedDoctorId || null])
    )
    const selectedPatientStillVisible = integrationPatients.value.some(
      row => row.patientId === selectedPatientStatus.value?.patientId
    )
    if (integrationPatients.value.length === 0) {
      selectedPatientStatus.value = null
      cloudPatientData.value = {}
      cloudObservationMeta.value = {}
      cloudPaperCaseMeta.value = {}
    } else if (!selectedPatientStatus.value || !selectedPatientStillVisible) {
      await selectPatient(integrationPatients.value[0].patientId)
    } else if (selectedPatientStatus.value?.patientId) {
      await selectPatient(selectedPatientStatus.value.patientId)
    }
  } catch (error) {
    integrationOverview.value = {}
    errorMessage.value = error.response?.data?.message || '加载患者接入概览失败'
  } finally {
    integrationLoading.value = false
  }
}

async function loadDoctors() {
  try {
    doctorOptions.value = await adminApi.getDoctors()
  } catch (error) {
    doctorOptions.value = []
  }
}

async function loadMedplumStatus() {
  medplumStatusLoading.value = true
  try {
    medplumStatus.value = await medplumApi.getStatus(true)
  } catch (error) {
    medplumStatus.value = { authenticated: false }
  } finally {
    medplumStatusLoading.value = false
  }
}

async function selectPatient(patientId) {
  if (!patientId) return
  try {
    showCloudSensitiveDetails.value = false
    localSummaryExpanded.value = false
    selectedPatientStatus.value = await adminApi.getPatientIntegrationStatus(patientId)
    await loadSelectedPatientCloudData()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '加载患者接入详情失败'
  }
}

async function syncBusinessPatient(patientId) {
  syncingPatientId.value = patientId
  errorMessage.value = ''
  try {
    const result = await adminApi.syncPatientIntegration(patientId)
    ElMessage.success(result.message || '患者同步完成')
    await loadIntegrationOverview()
    if (selectedPatientStatus.value?.patientId === patientId) {
      await loadSelectedPatientCloudData()
    }
  } catch (error) {
    const message = error.response?.data?.message || '同步正式患者到云端失败'
    errorMessage.value = message
    ElMessage.error(message)
  } finally {
    syncingPatientId.value = null
  }
}

async function bindDoctor(patientId) {
  bindingPatientId.value = patientId
  errorMessage.value = ''
  try {
    const result = await adminApi.bindPatientIntegrationDoctor(patientId, doctorBindings.value[patientId] || null)
    ElMessage.success(result.message || '责任医生绑定已更新')
    await loadIntegrationOverview()
    await selectPatient(patientId)
  } catch (error) {
    const message = error.response?.data?.message || '更新责任医生绑定失败'
    errorMessage.value = message
    ElMessage.error(message)
  } finally {
    bindingPatientId.value = null
  }
}

async function loadSelectedPatientCloudData() {
  const patientId = selectedPatientStatus.value?.patientId
  if (!patientId) {
    showCloudSensitiveDetails.value = false
    cloudPatientData.value = {}
    cloudObservationMeta.value = {}
    cloudPaperCaseMeta.value = {}
    return
  }

  cloudLoading.value = true
  try {
    const [patient, observations, paperCases] = await Promise.all([
      medplumApi.getPatient(patientId),
      medplumApi.getObservations(patientId, 120),
      medplumApi.getPaperCaseResources(patientId, 120)
    ])
    cloudPatientData.value = patient || {}
    cloudObservationMeta.value = observations || {}
    cloudPaperCaseMeta.value = paperCases || {}
  } catch (error) {
    cloudPatientData.value = {}
    cloudObservationMeta.value = {
      observations: [],
      message: error.response?.data?.message || '读取云端资料失败'
    }
    cloudPaperCaseMeta.value = {
      encounters: [],
      conditions: [],
      documentReferences: [],
      message: error.response?.data?.message || '读取云端病历资源失败'
    }
  } finally {
    cloudLoading.value = false
  }
}

function buildValidationReport() {
  const patient = selectedPatientStatus.value || {}
  const reportTime = new Date().toISOString()
  return {
    exportedAt: reportTime,
    scope: 'admin-patient-integration-validation',
    note: '该报告仅基于管理员可见接入状态与云端取证生成，不包含医生端会话越权验证。',
    patient: {
      patientId: patient.patientId || null,
      name: patient.name || '',
      username: patient.username || '',
      fhirId: patient.fhirId || '',
      assignedDoctorId: patient.assignedDoctorId || null,
      assignedDoctorName: patient.assignedDoctorName || '',
      profileCompleted: Boolean(patient.profileCompleted),
      agentContextReady: Boolean(patient.agentContextReady),
      medplumContextReady: Boolean(patient.medplumContextReady)
    },
    localContext: {
      summary: patient.patientContextSummary || '',
      latestRecordDate: patient.latestRecordDate || null,
      localHealthRecordCount: patient.localHealthRecordCount || 0,
      contextSources: patient.contextSources || []
    },
    medplumContext: {
      patient: {
        found: Boolean(cloudPatientData.value?.found),
        localPatientId: cloudPatientData.value?.localPatientId || null,
        medplumPatientId: cloudPatientData.value?.medplumPatientId || '',
        name: maskedCloudPatientName.value,
        profileCompleted: cloudPatientData.value?.profileCompleted ?? null,
        medicalHistorySummary: displayedCloudHistorySummary.value
      },
      observationCount: cloudObservations.value.length,
      observations: cloudObservations.value,
      paperCaseResources: {
        encounterCount: cloudEncounters.value.length,
        conditionCount: cloudConditions.value.length,
        documentReferenceCount: cloudDocumentReferences.value.length,
        totalCount: cloudPaperCaseResourceCount.value,
        rows: cloudPaperCaseRows.value
      }
    },
    cloudOnlySignals: cloudOnlySignals.value,
    conclusion: validationConclusion.value
  }
}

function exportValidationReport() {
  if (!selectedPatientStatus.value?.found) {
    ElMessage.warning('请先选择一位正式患者')
    return
  }

  const report = buildValidationReport()
  const blob = new Blob([JSON.stringify(report, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const patientLabel = selectedPatientStatus.value?.username || selectedPatientStatus.value?.patientId || 'patient'
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `patient-integration-validation-${patientLabel}.json`
  document.body.appendChild(anchor)
  anchor.click()
  document.body.removeChild(anchor)
  URL.revokeObjectURL(url)
  ElMessage.success('验证报告已导出')
}
</script>

<style scoped>
.integration-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.integration-toolbar {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 180px 120px;
  gap: 12px;
  margin-bottom: 16px;
}

.integration-summary__item,
.detail-item,
.detail-panel,
.result-item {
  padding: 14px 12px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
}

.integration-summary__item span,
.detail-item span,
.result-item span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.integration-summary__item strong,
.detail-item strong,
.result-item strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 18px;
}

.cell-title {
  color: #0f172a;
  font-weight: 600;
}

.cell-subtitle {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.tag-gap {
  margin-left: 8px;
}

.doctor-binding-select {
  width: 100%;
}

.detail-card,
.mock-import-card {
  margin-top: 16px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.detail-panels {
  margin-top: 16px;
}

.detail-panel__title {
  margin-bottom: 8px;
  color: #0f172a;
  font-weight: 600;
}

.detail-panel__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.detail-panel__subtitle {
  margin-bottom: 8px;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
}

.detail-panel__muted {
  color: #64748b;
  font-size: 13px;
}

.detail-panel__head--summary {
  margin-bottom: 8px;
}

.detail-panel--cloud {
  margin-top: 16px;
}

.detail-panel--validation {
  margin-top: 16px;
}

.detail-panel__subsection {
  margin-top: 16px;
}

.detail-grid--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.paper-case-resource-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}

.validation-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
}

.validation-item {
  padding: 14px 12px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: linear-gradient(180deg, #fffdf7 0%, #f8fafc 100%);
}

.validation-item span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.validation-item strong {
  display: block;
  margin-top: 6px;
  color: #0f172a;
  font-size: 18px;
}

@media (max-width: 1100px) {
  .integration-summary,
  .integration-toolbar,
  .detail-grid,
  .detail-grid--three,
  .paper-case-resource-grid,
  .validation-grid {
    grid-template-columns: 1fr;
  }

  .tag-gap {
    margin-left: 0;
    margin-top: 8px;
  }
}
</style>
