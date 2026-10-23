<template>
  <div class="daily-record-page">
    <section class="daily-record-hero">
      <div class="daily-record-hero__content">
        <span class="daily-record-hero__eyebrow">Daily Health Log</span>
        <h1>每日健康数据填报</h1>
        <p>按天记录关键生命体征、血糖、生活方式和服药情况，让趋势分析、随访和健康建议都有连续的数据基础。</p>
        <div class="daily-record-hero__chips">
          <el-tag effect="plain" round>记录总数 {{ records.length }}</el-tag>
          <el-tag effect="plain" round type="info">最近日期 {{ latestRecord?.recordDate || '暂无' }}</el-tag>
          <el-tag effect="plain" round type="success">当前 BMI {{ bmiDisplay || '--' }}</el-tag>
        </div>
      </div>
      <div class="daily-record-hero__aside">
        <strong>{{ latestRecord?.recordDate || '--' }}</strong>
        <span>最近一次记录日期</span>
      </div>
    </section>

    <section class="daily-record-metric-grid">
      <article class="daily-record-metric-card">
        <span>最近血压</span>
        <strong>{{ latestBloodPressure }}</strong>
        <p>收缩压 / 舒张压，便于快速判断近期控制情况。</p>
      </article>
      <article class="daily-record-metric-card">
        <span>最近空腹血糖</span>
        <strong>{{ latestGlucose }}</strong>
        <p>用于观察近期血糖变化，辅助后续评估和宣教。</p>
      </article>
      <article class="daily-record-metric-card">
        <span>最近 BMI</span>
        <strong>{{ latestBmi }}</strong>
        <p>身高体重会自动计算 BMI，避免重复手工换算。</p>
      </article>
    </section>

    <section class="daily-record-layout">
      <div class="daily-record-main">
        <div class="daily-record-panel">
          <div class="daily-record-panel__head">
            <div>
              <span class="daily-record-panel__eyebrow">Record Entry</span>
              <h2>填写今日数据</h2>
              <p>录入完成后会进入个人健康记录，用于趋势分析和后续随访。</p>
            </div>
          </div>

          <el-form :model="form" label-width="110px" class="daily-record-form">
            <div class="daily-record-form__group">
              <div class="daily-record-form__group-title">基础生命体征</div>
              <el-row :gutter="18">
                <el-col :md="12" :sm="24">
                  <el-form-item label="记录日期">
                    <el-date-picker v-model="form.recordDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :md="12" :sm="24">
                  <el-form-item label="静息心率">
                    <el-input-number v-model="form.restingHeartRate" :min="30" :max="220" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="18">
                <el-col :md="12" :sm="24">
                  <el-form-item label="收缩压(mmHg)">
                    <el-input-number v-model="form.systolicBp" :min="50" :max="260" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :md="12" :sm="24">
                  <el-form-item label="舒张压(mmHg)">
                    <el-input-number v-model="form.diastolicBp" :min="30" :max="180" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="18">
                <el-col :md="8" :sm="24">
                  <el-form-item label="身高(cm)">
                    <el-input-number v-model="form.height" :precision="1" :min="80" :max="250" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :md="8" :sm="24">
                  <el-form-item label="体重(kg)">
                    <el-input-number v-model="form.weight" :precision="1" :min="20" :max="300" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :md="8" :sm="24">
                  <el-form-item label="BMI">
                    <el-input :model-value="bmiDisplay" readonly placeholder="自动计算" />
                  </el-form-item>
                </el-col>
              </el-row>
            </div>

            <div class="daily-record-form__group">
              <div class="daily-record-form__group-title">代谢指标</div>
              <el-row :gutter="18">
                <el-col :md="8" :sm="24">
                  <el-form-item label="空腹血糖">
                    <el-input-number v-model="form.fastingGlucose" :precision="1" :step="0.1" :min="1" :max="40" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :md="8" :sm="24">
                  <el-form-item label="餐后血糖">
                    <el-input-number v-model="form.postprandialGlucose" :precision="1" :step="0.1" :min="1" :max="40" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :md="8" :sm="24">
                  <el-form-item label="HbA1c">
                    <el-input-number v-model="form.hba1c" :precision="1" :step="0.1" :min="3" :max="20" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>
            </div>

            <div class="daily-record-form__group">
              <div class="daily-record-form__group-title">生活方式与依从性</div>
              <el-row :gutter="18">
                <el-col :md="8" :sm="24">
                  <el-form-item label="每周运动次数">
                    <el-input-number v-model="form.exerciseFrequency" :min="0" :max="14" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :md="8" :sm="24">
                  <el-form-item label="盐摄入评分">
                    <el-input-number v-model="form.saltIntake" :min="1" :max="5" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :md="8" :sm="24">
                  <el-form-item label="饮酒评分">
                    <el-input-number v-model="form.alcoholIntake" :min="0" :max="5" style="width: 100%" />
                  </el-form-item>
                </el-col>
              </el-row>

              <el-row :gutter="18">
                <el-col :md="12" :sm="24">
                  <el-form-item label="漏服率(%)">
                    <el-input-number v-model="form.medicationMissRate" :min="0" :max="100" style="width: 100%" />
                  </el-form-item>
                </el-col>
                <el-col :md="12" :sm="24">
                  <el-form-item label="规律服药">
                    <el-switch v-model="form.onRegularMedication" active-text="是" inactive-text="否" />
                  </el-form-item>
                </el-col>
              </el-row>
            </div>

            <el-form-item class="daily-record-form__actions">
              <el-button type="primary" :loading="submitting" @click="submitRecord">提交今日记录</el-button>
              <el-button @click="resetForm">重置</el-button>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <aside class="daily-record-side">
        <div class="daily-record-side-card">
          <span class="daily-record-side-card__eyebrow">Device Data Import</span>
          <h3>导入设备数据</h3>
          <p class="daily-record-side-copy">
            上传设备导出的 JSON，自动识别血压、心率、血糖、体重等指标，并写入你的健康记录。
          </p>
          <el-upload
            class="daily-record-upload"
            drag
            :show-file-list="false"
            :auto-upload="false"
            :on-change="handleHuaweiJsonFileChange"
            accept=".json"
          >
            <div class="daily-record-upload__title">{{ huaweiJsonFile ? huaweiJsonFile.name : '拖拽或点击上传设备 JSON' }}</div>
            <div class="daily-record-upload__hint">支持标准设备数据 JSON 文件。</div>
          </el-upload>
          <el-button type="primary" class="daily-record-upload__button" :loading="huaweiImportSubmitting" @click="handleHuaweiJsonImport">
            导入设备数据
          </el-button>
          <p class="daily-record-upload__status" :class="{ 'daily-record-upload__status--ready': Boolean(huaweiJsonFile) }">
            {{ huaweiJsonFile ? '文件已就绪，点击按钮开始导入。' : '请选择一个设备 JSON 文件。' }}
          </p>
          <div v-if="deviceImportResult" class="daily-record-result">
            <div class="daily-record-result__item">
              <span>导入结果</span>
              <strong>{{ deviceImportResult.importedRecords }} / {{ deviceImportResult.totalRecords }} 条已写入</strong>
            </div>
            <div class="daily-record-result__item" v-if="deviceImportResult.sourceDeviceId">
              <span>设备 ID</span>
              <strong>{{ deviceImportResult.sourceDeviceId }}</strong>
            </div>
            <div class="daily-record-result__item">
              <span>处理状态</span>
              <strong>{{ formatDeviceImportStatus(deviceImportResult) }}</strong>
            </div>
            <div v-if="deviceImportResult.medplumSyncMessage" class="daily-record-result__block">
              <span>处理说明</span>
              <p>{{ deviceImportResult.medplumSyncMessage }}</p>
            </div>
          </div>
        </div>

        <div class="daily-record-side-card">
          <span class="daily-record-side-card__eyebrow">Medical Record Upload</span>
          <h3>上传病历资料</h3>
          <p class="daily-record-side-copy">
            可上传检查单、出院记录或门诊病历图片/PDF。系统会先识别内容，再交由医生审核确认。
          </p>
          <el-upload
            class="daily-record-upload"
            drag
            :show-file-list="false"
            :auto-upload="false"
            :on-change="handlePaperCaseFileChange"
            accept=".png,.jpg,.jpeg,.pdf"
          >
            <div class="daily-record-upload__title">{{ paperCaseFile ? paperCaseFile.name : '拖拽或点击上传病历文件' }}</div>
            <div class="daily-record-upload__hint">支持 PNG / JPG / PDF，上传后医生可进一步审核 ICD 结果。</div>
          </el-upload>
          <el-button type="primary" class="daily-record-upload__button" :loading="paperCaseSubmitting" @click="handlePaperCaseSubmit">
            上传并识别病历
          </el-button>
          <p class="daily-record-upload__status" :class="{ 'daily-record-upload__status--ready': Boolean(paperCaseFile) }">
            {{ paperCaseFile ? '文件已就绪，点击按钮开始上传。' : '请选择一份病历图片或 PDF。' }}
          </p>
          <div v-if="paperCaseResult" class="daily-record-result">
            <div class="daily-record-result__item">
              <span>患者信息</span>
              <strong>{{ formatPatientInfo(paperCaseResult.patientInfo) }}</strong>
            </div>
            <div class="daily-record-result__item" v-if="paperCaseResult.job?.jobStatus">
              <span>处理作业</span>
              <strong>{{ formatPaperCaseJobStatus(paperCaseResult.job?.jobStatus) }}</strong>
            </div>
            <div class="daily-record-result__item">
              <span>初步诊断</span>
              <strong>{{ paperCaseResult.preliminaryDiagnosis || '待识别' }}</strong>
            </div>
            <div class="daily-record-result__item">
              <span>审核状态</span>
              <strong>{{ formatPaperCaseStatus(paperCaseResult) }}</strong>
            </div>
            <div class="daily-record-result__item" v-if="paperCaseResult.icdCode">
              <span>ICD-11</span>
              <strong>{{ formatIcd(paperCaseResult) }}</strong>
            </div>
            <div v-if="paperCaseResult.chiefComplaint" class="daily-record-result__block">
              <span>主诉</span>
              <p>{{ paperCaseResult.chiefComplaint }}</p>
            </div>
            <div v-if="paperCaseResult.job?.lastError" class="daily-record-result__block">
              <span>失败原因</span>
              <p>{{ paperCaseResult.job.lastError }}</p>
            </div>
          </div>
        </div>

        <div class="daily-record-side-card">
          <span class="daily-record-side-card__eyebrow">Entry Preview</span>
          <h3>当前填写摘要</h3>
          <div class="daily-record-preview-grid">
            <div>
              <span>血压</span>
              <strong>{{ form.systolicBp || '--' }}/{{ form.diastolicBp || '--' }}</strong>
            </div>
            <div>
              <span>血糖</span>
              <strong>{{ form.fastingGlucose ?? '--' }}</strong>
            </div>
            <div>
              <span>BMI</span>
              <strong>{{ bmiDisplay || '--' }}</strong>
            </div>
            <div>
              <span>漏服率</span>
              <strong>{{ form.medicationMissRate ?? 0 }}%</strong>
            </div>
          </div>
        </div>

        <div class="daily-record-side-card">
          <span class="daily-record-side-card__eyebrow">Latest Snapshot</span>
          <h3>最近记录</h3>
          <ul class="daily-record-side-list">
            <li>记录日期：{{ latestRecord?.recordDate || '暂无' }}</li>
            <li>最近血压：{{ latestBloodPressure }}</li>
            <li>最近血糖：{{ latestGlucose }}</li>
            <li>最近 BMI：{{ latestBmi }}</li>
          </ul>
        </div>

        <div class="daily-record-side-card">
          <span class="daily-record-side-card__eyebrow">Tips</span>
          <h3>填写建议</h3>
          <ul class="daily-record-side-list">
            <li>同一时间段记录更利于后续趋势对比。</li>
            <li>血压建议静坐休息后测量，避免运动后立即填写。</li>
            <li>若近期症状明显波动，除了填报也应及时联系医生。</li>
          </ul>
        </div>
      </aside>
    </section>

    <section class="daily-record-history">
      <div class="daily-record-panel daily-record-panel--history">
        <div class="daily-record-panel__head">
          <div>
            <span class="daily-record-panel__eyebrow">Recent History</span>
            <h2>最近记录</h2>
            <p>按日期倒序展示，便于快速回看近期变化和依从性情况。</p>
          </div>
          <el-button plain @click="loadRecords">刷新</el-button>
        </div>

        <el-table :data="records" v-loading="loading" stripe class="daily-record-table">
          <el-table-column prop="recordDate" label="日期" width="120" />
          <el-table-column label="血压" width="130">
            <template #default="{ row }">
              {{ row.systolicBp || '--' }}/{{ row.diastolicBp || '--' }}
            </template>
          </el-table-column>
          <el-table-column prop="restingHeartRate" label="心率" width="90" />
          <el-table-column prop="weight" label="体重(kg)" width="100" />
          <el-table-column prop="bmi" label="BMI" width="90" />
          <el-table-column prop="fastingGlucose" label="空腹血糖" width="110" />
          <el-table-column prop="exerciseFrequency" label="每周运动" width="100" />
          <el-table-column prop="medicationMissRate" label="漏服率(%)" width="110" />
        </el-table>

        <el-empty v-if="!loading && records.length === 0" description="还没有健康记录，先填写今天的数据吧" :image-size="88" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from '../../utils/element-plus-service'
import { dailyRecordApi, deviceImportApi, paperCaseApi } from '../../api'
import { useUserStore } from '../../stores/user'

const userStore = useUserStore()
const PAPER_CASE_TERMINAL_JOB_STATUSES = ['synced', 'review_pending', 'failed', 'dead_letter', 'cancelled']
const loading = ref(false)
const submitting = ref(false)
const huaweiImportSubmitting = ref(false)
const paperCaseSubmitting = ref(false)
const records = ref([])
const huaweiJsonFile = ref(null)
const paperCaseFile = ref(null)
const deviceImportResult = ref(null)
const paperCaseResult = ref(null)

const form = reactive({
  recordDate: new Date().toISOString().slice(0, 10),
  systolicBp: null,
  diastolicBp: null,
  restingHeartRate: null,
  height: null,
  weight: null,
  fastingGlucose: null,
  postprandialGlucose: null,
  hba1c: null,
  exerciseFrequency: null,
  saltIntake: 3,
  alcoholIntake: 0,
  medicationMissRate: 0,
  onRegularMedication: true
})

const bmiDisplay = computed(() => {
  if (!form.height || !form.weight) return ''
  const meters = form.height / 100
  if (!meters) return ''
  return (form.weight / (meters * meters)).toFixed(1)
})

const latestRecord = computed(() => records.value[0] || null)
const latestBloodPressure = computed(() => {
  if (!latestRecord.value) return '--'
  return `${latestRecord.value.systolicBp || '--'}/${latestRecord.value.diastolicBp || '--'}`
})
const latestGlucose = computed(() => latestRecord.value?.fastingGlucose ?? '--')
const latestBmi = computed(() => latestRecord.value?.bmi ?? '--')

onMounted(() => {
  loadRecords()
})

function resetForm() {
  form.recordDate = new Date().toISOString().slice(0, 10)
  form.systolicBp = null
  form.diastolicBp = null
  form.restingHeartRate = null
  form.height = null
  form.weight = null
  form.fastingGlucose = null
  form.postprandialGlucose = null
  form.hba1c = null
  form.exerciseFrequency = null
  form.saltIntake = 3
  form.alcoholIntake = 0
  form.medicationMissRate = 0
  form.onRegularMedication = true
}

function handleHuaweiJsonFileChange(file) {
  huaweiJsonFile.value = file?.raw || null
}

function handlePaperCaseFileChange(file) {
  paperCaseFile.value = file?.raw || null
}

async function loadRecords() {
  if (!userStore.patientId) return
  loading.value = true
  try {
    records.value = await dailyRecordApi.getByPatient(userStore.patientId)
  } catch (e) {
    records.value = []
    ElMessage.error(e.response?.data?.message || '加载健康记录失败')
  } finally {
    loading.value = false
  }
}

async function submitRecord() {
  if (!userStore.patientId) {
    ElMessage.warning('未获取到患者身份，请重新登录')
    return
  }
  submitting.value = true
  try {
    await dailyRecordApi.create({
      patientId: userStore.patientId,
      ...form,
      bmi: bmiDisplay.value ? Number(bmiDisplay.value) : null
    })
    ElMessage.success('健康记录已提交')
    resetForm()
    await loadRecords()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '提交失败')
  } finally {
    submitting.value = false
  }
}

async function handleHuaweiJsonImport() {
  if (!userStore.patientId) {
    ElMessage.warning('未获取到患者身份，请重新登录')
    return
  }
  if (!huaweiJsonFile.value) {
    ElMessage.warning('请先选择设备 JSON 文件')
    return
  }
  huaweiImportSubmitting.value = true
  try {
    const result = await deviceImportApi.importHuaweiJson(userStore.patientId, huaweiJsonFile.value)
    deviceImportResult.value = result
    applyImportedMetrics(result?.records?.[0])
    await loadRecords()
    if (result?.medplumSyncStatus === 'duplicate') {
      ElMessage.warning('检测到重复导入，系统已复用最近一次作业')
    } else if ((result?.importedRecords || 0) > 0) {
      ElMessage.success(`设备数据导入成功，已写入 ${result.importedRecords} 条记录`)
    } else {
      ElMessage.warning(result?.medplumSyncMessage || '导入已结束，但没有写入新的记录')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '设备数据导入失败')
  } finally {
    huaweiImportSubmitting.value = false
  }
}

async function handlePaperCaseSubmit() {
  if (!userStore.patientId) {
    ElMessage.warning('未获取到患者身份，请重新登录')
    return
  }
  if (!paperCaseFile.value) {
    ElMessage.warning('请先选择病历文件')
    return
  }
  paperCaseSubmitting.value = true
  try {
    const result = await paperCaseApi.upload(userStore.patientId, paperCaseFile.value, form.recordDate)
    paperCaseResult.value = result
    const resolved = await waitForPaperCaseResult(result?.id)
    paperCaseResult.value = resolved || result
    applyImportedMetrics((resolved || result)?.structuredMetrics)
    await loadRecords()
    if ((resolved || result)?.job?.jobStatus === 'failed' || (resolved || result)?.job?.jobStatus === 'dead_letter') {
      ElMessage.error((resolved || result)?.job?.lastError || '病历识别失败')
    } else if ((resolved || result)?.icdCodingStatus === 'pending_review') {
      ElMessage.success('病历已上传，系统已识别并等待医生审核')
    } else {
      ElMessage.success('病历上传成功，识别结果已同步到你的记录')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '病历上传失败')
  } finally {
    paperCaseSubmitting.value = false
  }
}

async function waitForPaperCaseResult(recordId, maxAttempts = 12, delayMs = 1500) {
  if (!recordId || !userStore.patientId) {
    return null
  }
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    if (attempt > 0) {
      await sleep(delayMs)
    }
    const records = await paperCaseApi.getByPatient(userStore.patientId)
    const matched = Array.isArray(records) ? records.find(item => item.id === recordId) : null
    if (!matched) {
      continue
    }
    paperCaseResult.value = matched
    if (PAPER_CASE_TERMINAL_JOB_STATUSES.includes(matched?.job?.jobStatus)) {
      return matched
    }
  }
  return paperCaseResult.value
}

function sleep(ms) {
  return new Promise(resolve => window.setTimeout(resolve, ms))
}

function applyImportedMetrics(metrics) {
  if (!metrics) return
  const fieldMap = {
    recordDate: 'recordDate',
    systolicBp: 'systolicBp',
    diastolicBp: 'diastolicBp',
    restingHeartRate: 'restingHeartRate',
    height: 'height',
    weight: 'weight',
    fastingGlucose: 'fastingGlucose',
    postprandialGlucose: 'postprandialGlucose',
    hba1c: 'hba1c',
    exerciseFrequency: 'exerciseFrequency',
    saltIntake: 'saltIntake',
    alcoholIntake: 'alcoholIntake',
    medicationMissRate: 'medicationMissRate',
    onRegularMedication: 'onRegularMedication'
  }
  Object.entries(fieldMap).forEach(([source, target]) => {
    const value = metrics[source]
    if (value !== null && value !== undefined && value !== '') {
      form[target] = value
    }
  })
}

function formatDeviceImportStatus(result) {
  const medplumStatusMap = {
    synced: '导入成功 / 云端已同步',
    skipped: '导入成功 / 云端同步已跳过',
    failed: '导入成功 / 云端同步失败',
    duplicate: '重复导入 / 已复用最近作业',
    cancelled: '作业已取消'
  }
  if (result?.medplumSyncStatus && medplumStatusMap[result.medplumSyncStatus]) {
    return medplumStatusMap[result.medplumSyncStatus]
  }
  return result?.job?.jobStatus || '未知状态'
}

function formatPatientInfo(info) {
  if (!info) return '识别中'
  return [info.name, info.gender, info.age ? `${info.age}岁` : null].filter(Boolean).join(' / ') || '识别中'
}

function formatPaperCaseStatus(record) {
  if (record?.job?.jobStatus === 'failed' || record?.job?.jobStatus === 'dead_letter') {
    return '识别失败'
  }
  if (record?.job?.jobStatus === 'running' || record?.job?.jobStatus === 'received') {
    return '识别处理中'
  }
  const mapping = {
    pending_review: '待医生审核',
    auto_coded: '已自动编码',
    manually_confirmed: '已医生确认',
    not_coded: '未完成编码'
  }
  return mapping[record?.icdCodingStatus] || '处理中'
}

function formatPaperCaseJobStatus(status) {
  const mapping = {
    received: '已接收',
    running: '识别中',
    parsed: '病历已识别',
    mapped: '结构化完成',
    review_pending: '待医生审核',
    synced: '已完成',
    failed: '处理失败',
    dead_letter: '多次失败',
    cancelled: '已取消'
  }
  return mapping[status] || status || '处理中'
}

function formatIcd(record) {
  if (!record?.icdCode) return '未匹配'
  const confidence = typeof record.icdConfidence === 'number'
    ? ` (${Math.round(record.icdConfidence * 100)}%)`
    : ''
  return `${record.icdCode} ${record.icdTitle || ''}${confidence}`.trim()
}
</script>

<style scoped>
.daily-record-page {
  display: grid;
  gap: 22px;
}

.daily-record-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  border-radius: 28px;
  color: #f7fbff;
  box-shadow: 0 28px 60px rgba(19, 33, 43, 0.16);
  background:
    radial-gradient(circle at top right, rgba(150, 198, 214, 0.2), transparent 34%),
    linear-gradient(135deg, #1f4f63 0%, #2f6f8b 52%, #4c91a5 100%);
}

.daily-record-hero__content {
  max-width: 760px;
}

.daily-record-hero__eyebrow,
.daily-record-panel__eyebrow,
.daily-record-side-card__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: 0.1em;
}

.daily-record-hero__eyebrow {
  background: rgba(255, 255, 255, 0.12);
}

.daily-record-hero h1 {
  margin: 18px 0 10px;
  font-size: 34px;
  line-height: 1.05;
}

.daily-record-hero p {
  margin: 0;
  max-width: 640px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(237, 244, 249, 0.82);
}

.daily-record-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.daily-record-hero__aside {
  min-width: 220px;
  display: grid;
  align-content: center;
  justify-items: end;
  gap: 6px;
  text-align: right;
}

.daily-record-hero__aside strong {
  font-size: 34px;
  line-height: 1;
}

.daily-record-hero__aside span {
  font-size: 13px;
  color: rgba(237, 244, 249, 0.82);
}

.daily-record-metric-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.daily-record-metric-card,
.daily-record-panel,
.daily-record-side-card {
  border-radius: 24px;
  border: 1px solid rgba(39, 81, 104, 0.08);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 18px 38px rgba(17, 28, 38, 0.08);
}

.daily-record-metric-card {
  padding: 18px 20px;
}

.daily-record-metric-card span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #6b7b86;
}

.daily-record-metric-card strong {
  display: block;
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.2;
  color: #1f3340;
}

.daily-record-metric-card p {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #72838d;
}

.daily-record-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
}

.daily-record-panel,
.daily-record-side-card {
  padding: 22px;
}

.daily-record-panel__head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 18px;
}

.daily-record-panel__head h2,
.daily-record-side-card h3 {
  margin: 10px 0 0;
  color: #1f3340;
}

.daily-record-panel__head p,
.daily-record-side-list,
.daily-record-side-card__empty {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.8;
  color: #5d707d;
}

.daily-record-panel__eyebrow,
.daily-record-side-card__eyebrow {
  background: rgba(203, 229, 238, 0.46);
  color: #2e6f8b;
}

.daily-record-form {
  display: grid;
  gap: 18px;
}

.daily-record-form__group {
  padding: 18px 18px 4px;
  border-radius: 22px;
  background: rgba(247, 250, 251, 0.92);
  border: 1px solid rgba(255, 255, 255, 0.8);
}

.daily-record-form__group-title {
  margin-bottom: 14px;
  font-size: 15px;
  font-weight: 700;
  color: #274a5e;
}

.daily-record-form__actions {
  margin-bottom: 0;
}

.daily-record-side {
  display: grid;
  gap: 16px;
}

.daily-record-side-copy {
  margin: 12px 0 14px;
  font-size: 13px;
  line-height: 1.75;
  color: #5d707d;
}

.daily-record-upload {
  margin-top: 4px;
}

.daily-record-upload :deep(.el-upload-dragger) {
  width: 100%;
  padding: 20px 16px;
  border-radius: 18px;
  border: 1px dashed rgba(76, 145, 165, 0.35);
  background: rgba(240, 248, 250, 0.92);
}

.daily-record-upload__title {
  font-size: 14px;
  font-weight: 600;
  color: #295468;
}

.daily-record-upload__hint {
  margin-top: 6px;
  font-size: 12px;
  color: #6b7b86;
}

.daily-record-upload__button {
  width: 100%;
  margin-top: 14px;
}

.daily-record-upload__status {
  margin: 10px 2px 0;
  font-size: 12px;
  line-height: 1.7;
  color: #8a6a2f;
}

.daily-record-upload__status--ready {
  color: #2f7a54;
}

.daily-record-preview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.daily-record-preview-grid div {
  padding: 14px 12px;
  border-radius: 18px;
  background: rgba(203, 229, 238, 0.28);
}

.daily-record-preview-grid span {
  display: block;
  font-size: 12px;
  color: #6b7b86;
}

.daily-record-preview-grid strong {
  display: block;
  margin-top: 6px;
  font-size: 18px;
  color: #1f3340;
}

.daily-record-side-list {
  padding-left: 18px;
}

.daily-record-result {
  display: grid;
  gap: 10px;
  margin-top: 16px;
}

.daily-record-result__item,
.daily-record-result__block {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(247, 250, 251, 0.92);
  border: 1px solid rgba(39, 81, 104, 0.08);
}

.daily-record-result__item span,
.daily-record-result__block span {
  display: block;
  font-size: 12px;
  color: #6b7b86;
}

.daily-record-result__item strong {
  display: block;
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.6;
  color: #1f3340;
}

.daily-record-result__block p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.8;
  color: #425563;
  white-space: pre-wrap;
}

.daily-record-history {
  min-width: 0;
}

.daily-record-panel--history {
  padding-bottom: 10px;
}

.daily-record-table {
  margin-top: 6px;
}

@media (max-width: 1200px) {
  .daily-record-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .daily-record-metric-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .daily-record-hero,
  .daily-record-panel__head {
    flex-direction: column;
    align-items: stretch;
  }

  .daily-record-hero {
    padding: 20px;
  }

  .daily-record-hero__aside {
    justify-items: start;
    text-align: left;
  }

  .daily-record-hero h1 {
    font-size: 28px;
  }

  .daily-record-preview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
