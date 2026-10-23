<template>
  <div class="record-page">
    <section class="record-hero">
      <div class="record-hero__content">
        <span class="record-hero__eyebrow">HEALTH RECORD ENTRY</span>
        <h1>健康数据录入工作台</h1>
        <p>
          将体检与实验室指标按结构化方式录入系统，提交后会自动触发风险评估，帮助医生快速完成风险识别和后续干预。
        </p>
        <div class="record-hero__chips">
          <el-tag effect="plain" round>当前患者：{{ selectedPatientName }}</el-tag>
          <el-tag effect="plain" round type="info">体检日期：{{ form.examDate || '--' }}</el-tag>
          <el-tag effect="plain" round type="success">已填写 {{ filledFieldCount }} 项</el-tag>
          <el-tag v-if="bmiDisplay" effect="plain" round :type="bmiTagType">BMI {{ bmiDisplay }}</el-tag>
          <el-tag v-if="routeJobContext" effect="plain" round type="warning">{{ routeJobContext }}</el-tag>
        </div>
      </div>

      <div class="record-hero__actions">
        <el-select v-model="form.patientId" placeholder="请选择患者" filterable class="record-hero__select">
          <el-option v-for="p in patients" :key="p.id" :label="`${p.name} (${p.phone || p.idCard || ''})`" :value="p.id" />
        </el-select>
        <el-date-picker v-model="form.examDate" type="date" value-format="YYYY-MM-DD" class="record-hero__select" />
        <el-button type="primary" class="record-hero__button" @click="handleSubmit" :loading="submitting">
          提交并触发风险评估
        </el-button>
      </div>
    </section>

    <section class="record-stat-grid">
      <article class="record-stat-card">
        <span>目标患者</span>
        <strong>{{ selectedPatientName }}</strong>
        <p>{{ form.patientId ? '当前提交将写入该患者档案并触发评估' : '请先选择患者再开始录入' }}</p>
      </article>
      <article class="record-stat-card">
        <span>体检日期</span>
        <strong>{{ form.examDate || '--' }}</strong>
        <p>所有录入指标将归档到这次体检记录中</p>
      </article>
      <article class="record-stat-card">
        <span>已填写字段</span>
        <strong>{{ filledFieldCount }}</strong>
        <p>系统只会保存你实际填写的指标，不会补空值</p>
      </article>
      <article class="record-stat-card">
        <span>BMI 自动计算</span>
        <strong>{{ bmiDisplay || '--' }}</strong>
        <p>{{ bmiDisplay ? '根据当前身高和体重即时计算' : '录入身高和体重后自动显示' }}</p>
      </article>
    </section>

    <section class="record-layout">
      <div class="record-form-card">
        <div class="record-form-card__head">
          <div>
            <span class="record-section__eyebrow">结构化录入</span>
            <h2>结构化体检表单</h2>
          </div>
          <p>按模块录入一般检查、血常规、生化检验与尿常规，保留现有字段和提交逻辑。</p>
        </div>

        <el-form :model="form" label-width="120px" class="record-form">
          <section class="record-section">
            <div class="record-section__head">
              <div>
                <span class="record-section__eyebrow">基础检查</span>
                <h3>一般检查</h3>
              </div>
              <p>记录基础体征，系统会自动计算 BMI 并作为风险评估输入。</p>
            </div>

            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="身高(cm)">
                  <el-input-number v-model="form.height" :precision="1" :min="50" :max="250" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="体重(kg)">
                  <el-input-number v-model="form.weight" :precision="1" :min="10" :max="300" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="收缩压(mmHg)">
                  <el-input-number v-model="form.systolicBp" :min="50" :max="300" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="舒张压(mmHg)">
                  <el-input-number v-model="form.diastolicBp" :min="20" :max="200" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="24" v-if="bmiDisplay">
              <el-col :span="12">
                <el-form-item label="BMI(自动)">
                  <el-tag :type="bmiTagType" size="large" effect="light">{{ bmiDisplay }}</el-tag>
                </el-form-item>
              </el-col>
            </el-row>
          </section>

          <section class="record-section">
            <div class="record-section__head">
              <div>
                <span class="record-section__eyebrow">血常规</span>
                <h3>血常规</h3>
              </div>
              <p>用于识别贫血、感染和血液学异常。</p>
            </div>

            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="血红蛋白(g/L)">
                  <el-input-number v-model="form.hemoglobin" :min="20" :max="250" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="白细胞(×10⁹/L)">
                  <el-input-number v-model="form.wbc" :precision="1" :step="0.1" :min="0" :max="100" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="血小板(×10⁹/L)">
                  <el-input-number v-model="form.platelet" :min="10" :max="1000" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="中性粒细胞(%)">
                  <el-input-number v-model="form.neutrophilPct" :min="0" :max="100" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </section>

          <section class="record-section">
            <div class="record-section__head">
              <div>
                <span class="record-section__eyebrow">生化检验</span>
                <h3>生化检验</h3>
              </div>
              <p>覆盖肝肾功能、血脂、血糖、心肌酶和电解质。</p>
            </div>

            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="ALT(U/L)">
                  <el-input-number v-model="form.alt" :min="0" :max="5000" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="AST(U/L)">
                  <el-input-number v-model="form.ast" :min="0" :max="5000" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="肌酐(μmol/L)">
                  <el-input-number v-model="form.creatinine" :precision="1" :step="1" :min="0" :max="2000" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="尿酸(μmol/L)">
                  <el-input-number v-model="form.uricAcid" :precision="1" :step="1" :min="0" :max="1500" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="总胆固醇(mmol/L)">
                  <el-input-number v-model="form.totalCholesterol" :precision="2" :step="0.1" :min="0" :max="20" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="甘油三酯(mmol/L)">
                  <el-input-number v-model="form.triglyceride" :precision="2" :step="0.1" :min="0" :max="30" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="HDL-C(mmol/L)">
                  <el-input-number v-model="form.hdlCholesterol" :precision="2" :step="0.1" :min="0" :max="5" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="LDL-C(mmol/L)">
                  <el-input-number v-model="form.ldlCholesterol" :precision="2" :step="0.1" :min="0" :max="15" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="空腹血糖(mmol/L)">
                  <el-input-number v-model="form.fastingGlucose" :precision="1" :step="0.1" :min="1" :max="40" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="HbA1c(%)">
                  <el-input-number v-model="form.hba1c" :precision="1" :step="0.1" :min="3" :max="20" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="24">
              <el-col :span="12">
                <el-form-item label="CK(U/L)">
                  <el-input-number v-model="form.ck" :min="0" :max="10000" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="血钾(mmol/L)">
                  <el-input-number v-model="form.potassium" :precision="1" :step="0.1" :min="1" :max="10" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
          </section>

          <section class="record-section">
            <div class="record-section__head">
              <div>
                <span class="record-section__eyebrow">尿常规</span>
                <h3>尿常规</h3>
              </div>
              <p>对蛋白尿、糖尿和潜血进行分级记录。</p>
            </div>

            <el-row :gutter="24">
              <el-col :span="8">
                <el-form-item label="尿蛋白">
                  <el-select v-model="form.urineProtein" placeholder="请选择" style="width:100%">
                    <el-option v-for="v in urineOptions" :key="`protein-${v}`" :label="v" :value="v" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="尿糖">
                  <el-select v-model="form.urineGlucose" placeholder="请选择" style="width:100%">
                    <el-option v-for="v in urineOptions" :key="`glucose-${v}`" :label="v" :value="v" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="尿潜血">
                  <el-select v-model="form.urineBlood" placeholder="请选择" style="width:100%">
                    <el-option v-for="v in urineOptions" :key="`blood-${v}`" :label="v" :value="v" />
                  </el-select>
                </el-form-item>
              </el-col>
            </el-row>
          </section>

          <div class="record-form__actions">
            <el-button type="primary" size="large" @click="handleSubmit" :loading="submitting">
              提交并触发风险评估
            </el-button>
          </div>
        </el-form>
      </div>

      <aside class="record-side-panel">
        <article class="record-side-card">
          <span class="record-section__eyebrow">设备数据导入</span>
          <h3>设备数据 JSON 导入</h3>
          <p class="record-side-copy">
            导入设备侧导出的 JSON 数据。系统会自动识别血压、心率、血糖、体重等指标，并写入患者健康记录。
          </p>
          <el-upload
            class="record-upload"
            drag
            :show-file-list="false"
            :auto-upload="false"
            :on-change="handleHuaweiJsonFileChange"
            accept=".json"
          >
            <div class="record-upload__title">{{ huaweiJsonFile ? huaweiJsonFile.name : '拖拽或点击上传设备 JSON' }}</div>
            <div class="record-upload__hint">支持平台导出的标准设备属性 JSON 结构。</div>
          </el-upload>
          <el-button
            type="primary"
            class="record-side-button"
            :loading="huaweiImportSubmitting"
            @click="handleHuaweiJsonImport"
          >
            导入设备数据
          </el-button>
          <p class="record-side-status" :class="{ 'record-side-status--ready': huaweiImportReady }">
            {{ huaweiImportHint }}
          </p>
          <div v-if="deviceImportResult" class="record-paper-result">
            <div class="record-paper-result__item">
              <span>数据来源 / 设备类型</span>
              <strong>{{ deviceImportResult.vendorLabel || '设备平台' }} / {{ deviceImportResult.deviceTypeLabel || '通用设备' }}</strong>
            </div>
            <div class="record-paper-result__item">
              <span>导入结果</span>
              <strong>{{ deviceImportResult.importedRecords }} / {{ deviceImportResult.totalRecords }} 条已写入健康记录</strong>
            </div>
            <div class="record-paper-result__item" v-if="deviceImportResult.sourceDeviceId">
              <span>设备 ID</span>
              <strong>{{ deviceImportResult.sourceDeviceId }}</strong>
            </div>
            <div class="record-paper-result__item" v-if="deviceImportResult.medplumSyncStatus || deviceImportResult.job?.jobStatus">
              <span>处理状态</span>
              <strong>{{ formatDeviceImportStatus(deviceImportResult) }}</strong>
            </div>
            <div v-if="deviceImportResult.medplumSyncMessage" class="record-paper-result__block">
              <span>处理说明</span>
              <p>{{ deviceImportResult.medplumSyncMessage }}</p>
            </div>
            <div v-if="deviceImportResult.warnings?.length" class="record-paper-result__block">
              <span>导入提示</span>
              <p>{{ deviceImportResult.warnings.join('\n') }}</p>
            </div>
          </div>
        </article>

        <article class="record-side-card">
          <span class="record-section__eyebrow">纸质病历识别</span>
          <h3>纸质病历识别</h3>
          <p class="record-side-copy">
            上传扫描图片或 PDF 后，系统会完成病历识别、结构化抽取，并返回标准疾病编码（ICD-11）候选结果供人工确认。
          </p>
          <el-upload
            class="record-upload"
            drag
            :show-file-list="false"
            :auto-upload="false"
            :on-change="handlePaperCaseFileChange"
            accept=".png,.jpg,.jpeg,.pdf"
          >
            <div class="record-upload__title">{{ paperCaseFile ? paperCaseFile.name : '拖拽或点击上传病历文件' }}</div>
            <div class="record-upload__hint">支持 PNG / JPG / PDF，建议先选择患者再上传。</div>
          </el-upload>
          <el-button
            type="primary"
            class="record-side-button"
            :disabled="!paperCaseFile || !form.patientId"
            :loading="paperCaseSubmitting"
            @click="handlePaperCaseSubmit"
          >
            上传并识别病历
          </el-button>
          <div v-if="paperCaseResult" class="record-paper-result">
            <div class="record-paper-result__item">
              <span>患者信息</span>
              <strong>{{ formatPatientInfo(paperCaseResult.patientInfo) }}</strong>
            </div>
            <div class="record-paper-result__item">
              <span>初步诊断</span>
              <strong>{{ paperCaseResult.preliminaryDiagnosis || '未识别' }}</strong>
            </div>
            <div class="record-paper-result__item">
              <span>标准疾病编码（ICD-11）</span>
              <strong>{{ formatIcd(paperCaseResult) }}</strong>
            </div>
            <div class="record-paper-result__item">
              <span>标准编码状态</span>
              <strong>{{ formatIcdStatus(paperCaseResult) }}</strong>
            </div>
            <div class="record-paper-result__block">
              <span>主诉</span>
              <p>{{ paperCaseResult.chiefComplaint || '未识别' }}</p>
            </div>
            <div class="record-paper-result__block">
              <span>现病史</span>
              <p>{{ paperCaseResult.presentIllness || '未识别' }}</p>
            </div>
            <div v-if="paperCaseResult?.icdCandidates?.length" class="record-paper-result__block">
              <span>标准疾病编码候选</span>
              <div class="record-paper-candidates">
                <label
                  v-for="candidate in paperCaseResult.icdCandidates"
                  :key="`${candidate.code}-${candidate.title}`"
                  class="record-paper-candidate"
                >
                  <input
                    v-model="selectedIcdCode"
                    :value="candidate.code"
                    type="radio"
                    name="paper-case-icd"
                  >
                  <div>
                    <strong>{{ candidate.code }} {{ candidate.title }}</strong>
                    <small>{{ candidate.source || 'system-mapping' }}{{ formatCandidateConfidence(candidate) }}</small>
                  </div>
                </label>
              </div>
              <div class="record-paper-confirm">
                <el-input
                  v-model="manualIcdTitle"
                  placeholder="如需人工修正，可填写标准诊断名称"
                  clearable
                />
                <el-button
                  type="success"
                  :loading="paperCaseConfirming"
                  :disabled="!paperCaseResult?.id || !selectedIcdCode"
                  @click="handleConfirmIcd"
                >
                  确认标准编码
                </el-button>
              </div>
            </div>
          </div>
        </article>

        <article class="record-side-card">
          <span class="record-section__eyebrow">当前摘要</span>
          <h3>当前录入摘要</h3>
          <dl class="record-side-list">
            <div>
              <dt>目标患者</dt>
              <dd>{{ selectedPatientName }}</dd>
            </div>
            <div>
              <dt>体检日期</dt>
              <dd>{{ form.examDate || '--' }}</dd>
            </div>
            <div>
              <dt>已填字段</dt>
              <dd>{{ filledFieldCount }} 项</dd>
            </div>
            <div>
              <dt>BMI</dt>
              <dd>{{ bmiDisplay || '待计算' }}</dd>
            </div>
          </dl>
        </article>

        <article class="record-side-card">
          <span class="record-section__eyebrow">Submission Tips</span>
          <h3>录入建议</h3>
          <ul class="record-tip-list">
            <li>当前页面适合录入门诊复查、体检或阶段性实验室检查结果。</li>
            <li>未填写的字段不会写入数据库，系统只保存实际录入的指标。</li>
            <li>提交后会自动触发规则评估与后续处理，无需手动再点风险分析。</li>
            <li>如果本次只关注单病种，也可以只录入与该病种直接相关的关键指标。</li>
          </ul>
        </article>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { patientApi, medicalExamApi, paperCaseApi, deviceImportApi } from '../../api'
import { useUserStore } from '../../stores/user'
import { ElMessage } from '../../utils/element-plus-service'

const route = useRoute()
const userStore = useUserStore()
const patients = ref([])
const submitting = ref(false)
const huaweiImportSubmitting = ref(false)
const paperCaseSubmitting = ref(false)
const paperCaseConfirming = ref(false)
const paperCaseFile = ref(null)
const huaweiJsonFile = ref(null)
const deviceImportResult = ref(null)
const paperCaseResult = ref(null)
const selectedIcdCode = ref('')
const manualIcdTitle = ref('')
const urineOptions = ['NEGATIVE', 'TRACE', '1+', '2+', '3+']

function createEmptyForm() {
  return {
    patientId: null,
    examDate: new Date().toISOString().slice(0, 10),
    height: null,
    weight: null,
    systolicBp: null,
    diastolicBp: null,
    hemoglobin: null,
    wbc: null,
    platelet: null,
    neutrophilPct: null,
    alt: null,
    ast: null,
    creatinine: null,
    uricAcid: null,
    totalCholesterol: null,
    triglyceride: null,
    hdlCholesterol: null,
    ldlCholesterol: null,
    fastingGlucose: null,
    hba1c: null,
    ck: null,
    potassium: null,
    urineProtein: null,
    urineGlucose: null,
    urineBlood: null
  }
}

const form = ref(createEmptyForm())

const selectedPatientName = computed(() => {
  const patient = patients.value.find(item => item.id === form.value.patientId)
  return patient?.name || '未选择患者'
})

const filledFieldCount = computed(() => {
  const excluded = new Set(['patientId', 'examDate'])
  return Object.entries(form.value).filter(([key, value]) => {
    if (excluded.has(key)) return false
    return value !== null && value !== undefined && value !== ''
  }).length
})

const bmiDisplay = computed(() => {
  const h = form.value.height
  const w = form.value.weight
  if (!h || !w || h <= 0) return null
  const hm = h / 100
  return (w / (hm * hm)).toFixed(1)
})

const bmiTagType = computed(() => {
  const v = parseFloat(bmiDisplay.value)
  if (isNaN(v)) return 'info'
  if (v < 18.5) return 'warning'
  if (v < 24) return 'success'
  if (v < 28) return 'warning'
  return 'danger'
})
const huaweiImportReady = computed(() => Boolean(form.value.patientId && huaweiJsonFile.value))
const huaweiImportHint = computed(() => {
  if (huaweiImportReady.value) {
    return '已满足导入条件，可直接点击“导入设备数据”。'
  }
  if (!form.value.patientId && !huaweiJsonFile.value) {
    return '请先选择患者，并上传设备 JSON 文件。'
  }
  if (!form.value.patientId) {
    return '请先选择患者，再执行导入。'
  }
  return '文件已选中，请继续确认目标患者。'
})
const routeJobContext = computed(() => {
  const jobId = normalizeId(route.query.jobId)
  const jobType = String(route.query.jobType || '').trim()
  const source = String(route.query.source || '').trim()
  if (!jobId && !jobType && !source) return ''
  const segments = []
  if (source === 'clinicaljob') {
    segments.push('来自临床作业异常恢复')
  }
  if (jobType === 'huawei_iot_import') {
    segments.push('设备数据导入作业')
  } else if (jobType) {
    segments.push(jobType)
  }
  if (jobId) {
    segments.push(`#${jobId}`)
  }
  return segments.join(' / ')
})

onMounted(async () => {
  patients.value = await patientApi.list({ doctorId: userStore.doctorId })
  syncPatientFromRoute()
})

watch(
  () => route.query.patientId,
  () => {
    syncPatientFromRoute()
  }
)

async function handleSubmit() {
  if (!form.value.patientId) return ElMessage.warning('请选择患者')
  submitting.value = true
  try {
    const payload = {
      ...form.value,
      recoveryJobId: normalizeId(route.query.jobId)
    }
    await medicalExamApi.create(payload)
    ElMessage.success('体检数据录入成功，风险评估已触发！')
  } catch (e) {
    ElMessage.error('录入失败：' + (e.response?.data?.message || e.message))
  } finally {
    submitting.value = false
  }
}

function handlePaperCaseFileChange(file) {
  paperCaseFile.value = file?.raw || null
}

function handleHuaweiJsonFileChange(file) {
  huaweiJsonFile.value = file?.raw || null
}

async function handlePaperCaseSubmit() {
  if (!form.value.patientId) {
    ElMessage.warning('请先选择患者')
    return
  }
  if (!paperCaseFile.value) {
    ElMessage.warning('请先选择病历文件')
    return
  }
  paperCaseSubmitting.value = true
  try {
    const result = await paperCaseApi.upload(form.value.patientId, paperCaseFile.value, form.value.examDate)
    paperCaseResult.value = result
    hydrateIcdSelection(result)
    applyStructuredMetricsFromPaperCase(result?.structuredMetrics)
    ElMessage.success(result?.icdCodingStatus === 'pending_review'
      ? '病历识别完成，已生成标准疾病编码候选，请医生确认后作为正式编码'
      : '病历识别完成，结果已同步到患者病史和结构化指标')
  } catch (e) {
    ElMessage.error('病历识别失败：' + (e.response?.data?.message || e.message))
  } finally {
    paperCaseSubmitting.value = false
  }
}

async function handleHuaweiJsonImport() {
  if (!form.value.patientId) {
    ElMessage.warning('请先选择患者')
    return
  }
  if (!huaweiJsonFile.value) {
    ElMessage.warning('请先选择设备 JSON 文件')
    return
  }
  huaweiImportSubmitting.value = true
  try {
    const result = await deviceImportApi.importHuaweiJson(form.value.patientId, huaweiJsonFile.value)
    deviceImportResult.value = result
    const latestRecord = result?.records?.[0]
    applyStructuredMetricsFromPaperCase(latestRecord)
    if (result?.medplumSyncStatus === 'duplicate') {
      ElMessage.warning('检测到重复导入请求，系统已复用最近一次导入作业')
    } else if (result?.medplumSyncStatus === 'cancelled') {
      ElMessage.warning('导入作业已取消，本次未完成导入')
    } else if ((result?.importedRecords || 0) > 0) {
      ElMessage.success(`设备数据导入完成，已写入 ${result.importedRecords} 条健康记录`)
    } else {
      ElMessage.warning(`导入已结束，但未写入新记录：${result?.medplumSyncMessage || '请检查 JSON 数据格式'}`)
    }
  } catch (e) {
    ElMessage.error('设备数据导入失败：' + (e.response?.data?.message || e.message))
  } finally {
    huaweiImportSubmitting.value = false
  }
}

async function handleConfirmIcd() {
  if (!paperCaseResult.value?.id) {
    ElMessage.warning('当前没有可确认的病历记录')
    return
  }
  if (!selectedIcdCode.value) {
    ElMessage.warning('请先选择一个标准疾病编码候选')
    return
  }
  const candidate = paperCaseResult.value.icdCandidates?.find(item => item.code === selectedIcdCode.value)
  paperCaseConfirming.value = true
  try {
    const updated = await paperCaseApi.confirmIcd(paperCaseResult.value.id, {
      code: selectedIcdCode.value,
      title: manualIcdTitle.value || candidate?.title || paperCaseResult.value.icdTitle || '',
      confidence: candidate?.confidence ?? paperCaseResult.value.icdConfidence ?? null
    })
    paperCaseResult.value = updated
    hydrateIcdSelection(updated)
    ElMessage.success('标准疾病编码已确认')
  } catch (e) {
    ElMessage.error('标准疾病编码确认失败：' + (e.response?.data?.message || e.message))
  } finally {
    paperCaseConfirming.value = false
  }
}

function hydrateIcdSelection(record) {
  const code = record?.icdCode || record?.icdCandidates?.[0]?.code || ''
  selectedIcdCode.value = code
  manualIcdTitle.value = record?.icdTitle || record?.icdCandidates?.find(item => item.code === code)?.title || ''
}

function applyStructuredMetricsFromPaperCase(metrics) {
  if (!metrics) return
  const fieldMap = {
    height: 'height',
    weight: 'weight',
    systolicBp: 'systolicBp',
    diastolicBp: 'diastolicBp',
    hemoglobin: 'hemoglobin',
    wbc: 'wbc',
    platelet: 'platelet',
    neutrophilPct: 'neutrophilPct',
    alt: 'alt',
    ast: 'ast',
    serumCreatinine: 'creatinine',
    uricAcid: 'uricAcid',
    totalCholesterol: 'totalCholesterol',
    triglycerides: 'triglyceride',
    hdlCholesterol: 'hdlCholesterol',
    ldlCholesterol: 'ldlCholesterol',
    fastingGlucose: 'fastingGlucose',
    hba1c: 'hba1c',
    ck: 'ck',
    potassium: 'potassium',
    urineProtein: 'urineProtein',
    urineGlucose: 'urineGlucose',
    urineBlood: 'urineBlood'
  }
  Object.entries(fieldMap).forEach(([source, target]) => {
    const value = metrics[source]
    if (value !== null && value !== undefined && value !== '') {
      form.value[target] = value
    }
  })
}

function formatPatientInfo(info) {
  if (!info) return '未识别'
  return [info.name, info.gender, info.age ? `${info.age}岁` : null].filter(Boolean).join(' / ') || '未识别'
}

function formatIcd(record) {
  if (!record?.icdCode) return '未匹配'
  const confidence = typeof record.icdConfidence === 'number'
    ? ` (${Math.round(record.icdConfidence * 100)}%)`
    : ''
  return `${record.icdCode} ${record.icdTitle || ''}${confidence}`.trim()
}

function formatIcdStatus(record) {
  const mapping = {
    pending_review: '待医生确认',
    auto_coded: '已自动编码',
    manually_confirmed: '已人工确认',
    not_coded: '未编码'
  }
  const key = record?.icdCodingStatus
  return mapping[key] || record?.icdCodingStatus || '未编码'
}

function formatCandidateConfidence(candidate) {
  if (typeof candidate?.confidence !== 'number') return ''
  return ` · ${Math.round(candidate.confidence * 100)}%`
}

function formatDeviceImportStatus(result) {
  const medplumStatusMap = {
    synced: '导入成功 / 云端已归档',
    skipped: '导入成功 / 跳过云端归档',
    failed: '导入成功 / 云端归档失败',
    duplicate: '重复导入 / 已复用既有作业',
    cancelled: '作业已取消'
  }
  if (result?.medplumSyncStatus && medplumStatusMap[result.medplumSyncStatus]) {
    return medplumStatusMap[result.medplumSyncStatus]
  }
  const jobStatusMap = {
    synced: '导入成功',
    failed: '导入失败',
    cancelled: '作业已取消',
    running: '处理中',
    received: '已接收'
  }
  return jobStatusMap[result?.job?.jobStatus] || result?.job?.jobStatus || '未知状态'
}

function normalizeId(value) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

function syncPatientFromRoute() {
  const patientId = normalizeId(route.query.patientId)
  if (!patientId) return
  const matched = patients.value.find(item => item.id === patientId)
  if (matched) {
    form.value.patientId = matched.id
    return
  }
  form.value.patientId = patientId
}
</script>

<style scoped>
.record-page {
  display: grid;
  gap: 22px;
}

.record-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgba(139, 173, 196, 0.18), transparent 34%),
    linear-gradient(135deg, #20303d 0%, #31485a 52%, #445d70 100%);
  box-shadow: 0 28px 60px rgba(19, 33, 43, 0.16);
  color: #f7fbff;
}

.record-hero__content {
  max-width: 760px;
}

.record-hero__eyebrow,
.record-section__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  letter-spacing: 0.1em;
}

.record-hero h1 {
  margin: 18px 0 10px;
  font-size: 34px;
  line-height: 1.05;
  color: #ffffff;
}

.record-hero p {
  margin: 0;
  max-width: 640px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(237, 244, 249, 0.82);
}

.record-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.record-hero__actions {
  display: grid;
  gap: 12px;
  align-content: start;
  width: 300px;
}

.record-hero__select,
.record-hero__button {
  width: 100%;
}

.record-hero__button {
  min-height: 46px;
}

.record-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.record-stat-card,
.record-form-card,
.record-side-card {
  border-radius: 24px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 18px 38px rgba(17, 28, 38, 0.08);
}

.record-stat-card {
  padding: 18px 20px;
}

.record-stat-card span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #6b7b86;
}

.record-stat-card strong {
  display: block;
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.2;
  color: #1f3340;
}

.record-stat-card p {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #72838d;
}

.record-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.9fr) minmax(280px, 0.9fr);
  gap: 18px;
  align-items: start;
}

.record-form-card {
  padding: 24px;
}

.record-form-card__head,
.record-section__head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
}

.record-form-card__head {
  margin-bottom: 18px;
}

.record-form-card__head h2,
.record-section__head h3,
.record-side-card h3 {
  margin: 10px 0 0;
  color: #1f3340;
}

.record-form-card__head h2 {
  font-size: 26px;
}

.record-form-card__head p,
.record-section__head p {
  margin: 0;
  max-width: 360px;
  font-size: 13px;
  line-height: 1.7;
  color: #6e7f89;
  text-align: right;
}

.record-form {
  display: grid;
  gap: 18px;
}

.record-section {
  padding: 18px 20px;
  border-radius: 22px;
  background: rgba(247, 249, 250, 0.92);
  border: 1px solid rgba(31, 51, 64, 0.06);
}

.record-section__head {
  margin-bottom: 14px;
}

.record-section__eyebrow {
  background: rgba(112, 147, 168, 0.14);
  color: #6a889c;
}

.record-form__actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 4px;
}

.record-side-panel {
  display: grid;
  gap: 16px;
}

.record-side-card {
  padding: 20px;
}

.record-side-copy {
  margin: 14px 0 16px;
  font-size: 13px;
  line-height: 1.75;
  color: #6e7f89;
}

.record-upload {
  margin-top: 4px;
}

.record-upload :deep(.el-upload-dragger) {
  width: 100%;
  padding: 24px 18px;
  border-radius: 18px;
  border: 1px dashed rgba(78, 113, 133, 0.32);
  background: rgba(246, 249, 251, 0.92);
}

.record-upload__title {
  font-size: 14px;
  font-weight: 600;
  color: #294153;
}

.record-upload__hint {
  margin-top: 6px;
  font-size: 12px;
  color: #72838d;
}

.record-side-button {
  width: 100%;
  margin-top: 14px;
}

.record-side-status {
  margin: 10px 2px 0;
  font-size: 12px;
  line-height: 1.7;
  color: #9a7a33;
}

.record-side-status--ready {
  color: #3d7a53;
}

.record-side-list {
  display: grid;
  gap: 14px;
  margin: 18px 0 0;
}

.record-side-list div {
  display: grid;
  gap: 4px;
}

.record-side-list dt {
  font-size: 12px;
  color: #6f808a;
  letter-spacing: 0.06em;
}

.record-side-list dd {
  margin: 0;
  font-size: 15px;
  color: #1f3340;
  font-weight: 600;
}

.record-tip-list {
  margin: 18px 0 0;
  padding-left: 18px;
  color: #5f717c;
}

.record-tip-list li {
  margin-bottom: 10px;
  line-height: 1.8;
}

.record-paper-result {
  display: grid;
  gap: 12px;
  margin-top: 18px;
}

.record-paper-result__item,
.record-paper-result__block {
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(247, 249, 250, 0.92);
  border: 1px solid rgba(31, 51, 64, 0.06);
}

.record-paper-result__item span,
.record-paper-result__block span {
  display: block;
  font-size: 12px;
  color: #6f808a;
}

.record-paper-result__item strong {
  display: block;
  margin-top: 8px;
  font-size: 14px;
  line-height: 1.6;
  color: #1f3340;
}

.record-paper-result__block p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.8;
  color: #425563;
  white-space: pre-wrap;
}

.record-paper-candidates {
  display: grid;
  gap: 10px;
  margin-top: 10px;
}

.record-paper-candidate {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 10px 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid rgba(31, 51, 64, 0.08);
  cursor: pointer;
}

.record-paper-candidate strong {
  display: block;
  font-size: 13px;
  line-height: 1.5;
  color: #1f3340;
}

.record-paper-candidate small {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #72838d;
}

.record-paper-confirm {
  display: grid;
  gap: 10px;
  margin-top: 12px;
}

@media (max-width: 1200px) {
  .record-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .record-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .record-hero,
  .record-form-card__head,
  .record-section__head {
    flex-direction: column;
  }

  .record-hero__actions {
    width: 100%;
  }

  .record-form-card__head p,
  .record-section__head p {
    max-width: none;
    text-align: left;
  }
}

@media (max-width: 640px) {
  .record-hero,
  .record-form-card,
  .record-side-card {
    padding: 20px;
    border-radius: 22px;
  }

  .record-stat-grid {
    grid-template-columns: 1fr;
  }

  .record-hero h1 {
    font-size: 28px;
  }
}
</style>
