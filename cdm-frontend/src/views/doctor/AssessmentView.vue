<template>
  <div class="assessment-page">
    <section class="assessment-hero">
      <div class="assessment-hero__content">
        <span class="assessment-hero__eyebrow">Risk Intelligence</span>
        <h1>风险评估工作台</h1>
        <p>
          把规则评估、专业报告和病史提醒放到同一个工作区，帮助医生快速识别高风险患者并决定下一步干预动作。
        </p>
        <div class="assessment-hero__chips">
          <el-tag effect="plain" round>当前患者：{{ selectedPatientName }}</el-tag>
          <el-tag effect="plain" round type="warning">高风险 {{ highRiskCount }}</el-tag>
          <el-tag effect="plain" round type="success">专业报告 {{ reports.length }}</el-tag>
        </div>
      </div>

      <div class="assessment-hero__actions">
        <el-select
          v-model="selectedPatient"
          placeholder="选择患者"
          clearable
          class="assessment-hero__select"
          @change="onPatientChange"
        >
          <el-option v-for="p in patients" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
        <el-button
          type="primary"
          class="assessment-hero__button"
          :loading="generating"
          :disabled="!selectedPatient"
          @click="generateAssessment"
        >
          <el-icon><MagicStick /></el-icon>
          <span>生成专业评估</span>
        </el-button>
      </div>
    </section>

    <section class="assessment-stat-grid">
      <article class="assessment-stat-card">
        <span>当前规则记录</span>
        <strong>{{ assessments.length }}</strong>
        <p>{{ selectedPatient ? '仅统计当前患者' : '已汇总当前医生全部患者' }}</p>
      </article>
      <article class="assessment-stat-card">
        <span>高风险与危急</span>
        <strong>{{ highRiskCount }}</strong>
        <p>包含高风险与危急评估结果</p>
      </article>
      <article class="assessment-stat-card">
        <span>AI 报告数</span>
        <strong>{{ reports.length }}</strong>
        <p>{{ reports.length > 0 ? '可直接查看最近专业报告' : '当前患者尚未生成专业报告' }}</p>
      </article>
      <article class="assessment-stat-card">
        <span>最近评估时间</span>
        <strong>{{ latestAssessmentAt ? formatDate(latestAssessmentAt) : '--' }}</strong>
        <p>用于判断本轮风险是否需要重新介入</p>
      </article>
    </section>

    <el-alert
      v-if="generating"
      class="assessment-banner"
      title="AI 正在整理专业风险评估报告，请稍候。"
      type="info"
      show-icon
      :closable="false"
    />

    <section class="assessment-section">
      <div class="assessment-section__header">
        <div>
          <span class="assessment-section__eyebrow">Professional View</span>
          <h2>专业评估报告</h2>
        </div>
        <p>{{ selectedPatient ? '聚焦单个患者的 AI 专业意见。' : '请先选择患者后生成 AI 专业报告。' }}</p>
      </div>

      <div v-if="reports.length > 0" class="assessment-report-list">
        <article
          v-for="report in reports"
          :key="report.id"
          class="assessment-report-card"
        >
          <div class="assessment-report-card__head">
            <div class="assessment-report-card__identity">
              <div class="assessment-report-card__badge">AI</div>
              <div>
                <h3>专业风险评估</h3>
                <p>{{ formatDate(report.generatedAt) }}</p>
              </div>
            </div>
            <el-tag type="success" effect="plain" round>已生成</el-tag>
          </div>
          <div class="assessment-report-card__body">
            <RichTextBlock class="report-content" :content="report.content" />
          </div>
        </article>
      </div>

      <div v-else class="assessment-empty-panel">
        <el-empty :description="selectedPatient ? '当前患者还没有专业评估报告' : '请选择患者后生成专业评估报告'" />
      </div>
    </section>

    <section class="assessment-section assessment-section--records">
      <div class="assessment-section__header">
        <div>
          <span class="assessment-section__eyebrow">Rules Engine</span>
          <h2>规则引擎评估记录</h2>
        </div>
        <p>保留结构化风险评分、病史提醒和建议，便于医生快速回看。</p>
      </div>

      <div class="assessment-table-shell">
        <div class="assessment-table-scroll">
          <el-table :data="assessments" stripe v-loading="loading" class="assessment-table">
            <el-table-column prop="patientName" label="患者" width="110" />
            <el-table-column prop="diseaseType" label="疾病类型" width="130">
              <template #default="{ row }">{{ diseaseLabel(row.diseaseType) }}</template>
            </el-table-column>
            <el-table-column prop="riskLevel" label="风险等级" width="110">
              <template #default="{ row }">
                <el-tag :type="riskTagType(row.riskLevel)" effect="light">{{ riskLabel(row.riskLevel) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="score" label="评分" width="90" />
            <el-table-column prop="assessmentDetail" label="评估详情" min-width="240">
              <template #default="{ row }">
                <el-tooltip
                  v-if="row.assessmentDetail"
                  placement="top-start"
                  :show-after="150"
                  popper-class="doctor-overflow-tooltip"
                >
                  <template #content>
                    <div class="tooltip-content">{{ row.assessmentDetail }}</div>
                  </template>
                  <span class="ellipsis-cell">{{ row.assessmentDetail }}</span>
                </el-tooltip>
                <span v-else class="table-empty">-</span>
              </template>
            </el-table-column>
            <el-table-column label="病史提醒" min-width="240">
              <template #default="{ row }">
                <div v-if="hasHistoryAlerts(row)" class="history-alert-cell">
                  <el-tag
                    v-for="(alert, index) in row.historyAlerts.slice(0, 2)"
                    :key="`${row.id || row.createdAt}-history-${index}`"
                    type="warning"
                    effect="plain"
                    size="small"
                  >
                    {{ alert }}
                  </el-tag>
                  <span v-if="row.historyAlerts.length > 2" class="table-empty">+{{ row.historyAlerts.length - 2 }}</span>
                </div>
                <span v-else class="table-empty">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="评估时间" width="160">
              <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="primary" link @click="showDetail(row)">查看详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="!loading && assessments.length === 0" class="assessment-empty-panel assessment-empty-panel--table">
          <el-empty :description="selectedPatient ? '当前患者暂无规则评估记录' : '当前暂无规则评估记录'" />
        </div>
      </div>
    </section>

    <el-dialog v-model="detailVisible" title="风险评估详情" width="720px" destroy-on-close append-to-body align-center>
      <template v-if="currentRow">
        <div class="assessment-detail">
          <div class="assessment-detail__summary">
            <div class="assessment-detail__headline">
              <h3>{{ currentRow.patientName }}</h3>
              <p>{{ diseaseLabel(currentRow.diseaseType) }} · {{ formatDate(currentRow.createdAt) }}</p>
            </div>
            <div class="assessment-detail__tags">
              <el-tag :type="riskTagType(currentRow.riskLevel)">{{ riskLabel(currentRow.riskLevel) }}</el-tag>
              <el-tag type="info" effect="plain">评分 {{ currentRow.score ?? '--' }}</el-tag>
            </div>
          </div>

          <div class="assessment-detail__section">
            <div class="assessment-detail__label">评估详情</div>
            <RichTextBlock class="detail-content" :content="currentRow.assessmentDetail" empty-text="暂无详情" />
          </div>

          <div v-if="hasHistoryAlerts(currentRow)" class="assessment-detail__section">
            <div class="assessment-detail__label">病史提醒</div>
            <div class="history-alert-panel">
              <el-tag
                v-for="(alert, index) in currentRow.historyAlerts"
                :key="`detail-history-${index}`"
                type="warning"
                effect="plain"
              >
                {{ alert }}
              </el-tag>
            </div>
          </div>

          <div class="assessment-detail__section">
            <div class="assessment-detail__label">AI 智能建议</div>
            <div v-if="currentRow.recommendations && currentRow.recommendations.length > 0" class="assessment-detail__recommendations">
              <div
                v-for="(recommendation, index) in currentRow.recommendations"
                :key="`recommendation-${index}`"
                class="assessment-recommendation-item"
              >
                <span>{{ index + 1 }}</span>
                <p>{{ recommendation }}</p>
              </div>
            </div>
            <div v-else class="assessment-detail__empty">暂无建议</div>
          </div>
        </div>
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { patientApi, assessmentApi, healthReportApi } from '../../api'
import { useUserStore } from '../../stores/user'
import { ElMessage } from '../../utils/element-plus-service'
import dayjs from 'dayjs'
import RichTextBlock from '../../components/RichTextBlock.vue'

const userStore = useUserStore()

const patients = ref([])
const assessments = ref([])
const reports = ref([])
const selectedPatient = ref(null)
const loading = ref(false)
const generating = ref(false)
const detailVisible = ref(false)
const currentRow = ref(null)

const diseaseMap = { DIABETES: '2型糖尿病', HYPERTENSION: '高血压', COPD: '慢阻肺', CKD: '慢性肾病', CHD: '冠心病', STROKE: '脑卒中' }
const riskMap = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '危急' }

const selectedPatientName = computed(() => {
  const patient = patients.value.find(item => item.id === selectedPatient.value)
  return patient?.name || '全部患者'
})

const highRiskCount = computed(() => assessments.value.filter(item => ['HIGH', 'CRITICAL'].includes(item.riskLevel)).length)
const latestAssessmentAt = computed(() => assessments.value[0]?.createdAt || null)

function diseaseLabel(t) { return diseaseMap[t] || t }
function riskLabel(l) { return riskMap[l] || l }
function riskTagType(l) { return { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }[l] || 'info' }
function formatDate(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '' }
function hasHistoryAlerts(row) { return Array.isArray(row?.historyAlerts) && row.historyAlerts.length > 0 }

function showDetail(row) {
  currentRow.value = row
  detailVisible.value = true
}

async function onPatientChange() {
  await loadAssessments()
  await loadReports()
}

async function loadAssessments() {
  loading.value = true
  try {
    if (selectedPatient.value) {
      assessments.value = await assessmentApi.getByPatient(selectedPatient.value)
      assessments.value = [...assessments.value].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    } else {
      const all = []
      for (const p of patients.value) {
        try {
          all.push(...(await assessmentApi.getByPatient(p.id)))
        } catch (e) {
          /* skip */
        }
      }
      assessments.value = all.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    }
  } finally {
    loading.value = false
  }
}

async function loadReports() {
  if (!selectedPatient.value) {
    reports.value = []
    return
  }
  try {
    reports.value = await healthReportApi.getByPatient(selectedPatient.value, 'doctor_assessment')
  } catch (e) {
    reports.value = []
  }
}

async function generateAssessment() {
  if (!selectedPatient.value) return ElMessage.warning('请先选择患者')
  generating.value = true
  try {
    const report = await healthReportApi.generateDoctorAssessment(selectedPatient.value)
    reports.value.unshift(report)
    ElMessage.success('评估报告生成成功')
  } catch (e) {
    ElMessage.error('评估生成失败，请稍后重试')
  } finally {
    generating.value = false
  }
}

onMounted(async () => {
  patients.value = await patientApi.list({ doctorId: userStore.doctorId })
  await loadAssessments()
})
</script>

<style scoped>
.assessment-page {
  display: grid;
  gap: 22px;
  min-width: 0;
}

.assessment-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgba(126, 164, 188, 0.22), transparent 34%),
    linear-gradient(135deg, #20303d 0%, #2f4352 52%, #3f5567 100%);
  box-shadow: 0 28px 60px rgba(19, 33, 43, 0.16);
  color: #f7fbff;
}

.assessment-hero__content {
  max-width: 760px;
}

.assessment-hero__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  letter-spacing: 0.1em;
}

.assessment-hero h1 {
  margin: 18px 0 10px;
  font-size: 34px;
  line-height: 1.05;
  color: #ffffff;
}

.assessment-hero p {
  margin: 0;
  max-width: 640px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(237, 244, 249, 0.82);
}

.assessment-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.assessment-hero__actions {
  display: grid;
  gap: 14px;
  align-content: start;
  width: 280px;
}

.assessment-hero__select,
.assessment-hero__button {
  width: 100%;
}

.assessment-hero__button {
  min-height: 46px;
}

.assessment-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.assessment-stat-card {
  padding: 18px 20px;
  border-radius: 22px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  background: rgba(255, 255, 255, 0.82);
  box-shadow: 0 16px 36px rgba(17, 28, 38, 0.08);
}

.assessment-stat-card span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #6b7b86;
}

.assessment-stat-card strong {
  display: block;
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.2;
  color: #1f3340;
}

.assessment-stat-card p {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #72838d;
}

.assessment-banner {
  border-radius: 18px;
}

.assessment-section {
  padding: 24px;
  border-radius: 28px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  background: rgba(255, 255, 255, 0.84);
  box-shadow: 0 20px 44px rgba(17, 28, 38, 0.08);
  min-width: 0;
}

.assessment-section__header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.assessment-section__eyebrow {
  display: inline-flex;
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: #7093a8;
}

.assessment-section__header h2 {
  margin: 0;
  font-size: 26px;
  color: #1f3340;
}

.assessment-section__header p {
  margin: 0;
  max-width: 420px;
  font-size: 13px;
  line-height: 1.7;
  color: #6e7f89;
  text-align: right;
}

.assessment-report-list {
  display: grid;
  gap: 16px;
}

.assessment-report-card {
  overflow: hidden;
  border-radius: 24px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  background: linear-gradient(180deg, rgba(252, 253, 255, 0.98) 0%, rgba(246, 248, 250, 0.96) 100%);
}

.assessment-report-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 14px;
  padding: 18px 22px;
  background: linear-gradient(135deg, #223342 0%, #31485a 100%);
}

.assessment-report-card__identity {
  display: flex;
  align-items: center;
  gap: 14px;
}

.assessment-report-card__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 15px;
  background: rgba(255, 255, 255, 0.14);
  color: #ffffff;
  font-weight: 700;
}

.assessment-report-card__identity h3 {
  margin: 0;
  font-size: 16px;
  color: #ffffff;
}

.assessment-report-card__identity p {
  margin: 6px 0 0;
  font-size: 12px;
  color: rgba(230, 239, 245, 0.76);
}

.assessment-report-card__body {
  padding: 22px;
}

.report-content {
  font-size: 14px;
  line-height: 1.9;
  color: #44545f;
}

:deep(.report-content__headline) {
  margin: 14px 0 8px;
  font-size: 15px;
  font-weight: 700;
  color: #1f3340;
}

.assessment-table-shell {
  overflow: hidden;
  border-radius: 22px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  background: rgba(248, 250, 251, 0.88);
  min-width: 0;
}

.assessment-table-scroll {
  width: 100%;
  min-width: 0;
  overflow-x: auto;
}

.assessment-table {
  min-width: 1080px;
}

.assessment-empty-panel {
  min-height: 220px;
  border-radius: 24px;
  border: 1px dashed rgba(77, 103, 120, 0.22);
  background: rgba(245, 248, 249, 0.86);
  display: flex;
  align-items: center;
  justify-content: center;
}

.assessment-empty-panel--table {
  min-height: 180px;
  border-radius: 0;
  border: 0;
  box-shadow: none;
}

.ellipsis-cell {
  display: inline-block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.table-empty {
  color: #909399;
}

.tooltip-content {
  max-width: 420px;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}

.history-alert-cell,
.history-alert-panel {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

:deep(.doctor-overflow-tooltip) {
  max-width: 460px;
}

.assessment-detail {
  display: grid;
  gap: 20px;
}

.assessment-detail__summary {
  display: flex;
  justify-content: space-between;
  align-items: start;
  gap: 16px;
  padding: 18px 20px;
  border-radius: 22px;
  background: linear-gradient(135deg, rgba(34, 51, 66, 0.06), rgba(112, 147, 168, 0.12));
}

.assessment-detail__headline h3 {
  margin: 0;
  font-size: 22px;
  color: #1f3340;
}

.assessment-detail__headline p {
  margin: 8px 0 0;
  color: #667984;
}

.assessment-detail__tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.assessment-detail__section {
  display: grid;
  gap: 10px;
}

.assessment-detail__label {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #6c8091;
}

.detail-content {
  line-height: 1.9;
  padding: 16px 18px;
  background: #f5f7fa;
  border-radius: 18px;
  font-size: 14px;
  max-height: 320px;
  overflow-y: auto;
  color: #44545f;
}

.assessment-detail__recommendations {
  display: grid;
  gap: 10px;
}

.assessment-recommendation-item {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 12px;
  align-items: start;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(244, 248, 251, 0.88);
}

.assessment-recommendation-item span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #284457;
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
}

.assessment-recommendation-item p {
  margin: 0;
  line-height: 1.8;
  color: #44545f;
}

.assessment-detail__empty {
  color: #8a98a3;
}

@media (max-width: 1200px) {
  .assessment-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .assessment-table {
    min-width: 980px;
  }
}

@media (max-width: 960px) {
  .assessment-hero,
  .assessment-section__header,
  .assessment-detail__summary {
    flex-direction: column;
  }

  .assessment-hero__actions {
    width: 100%;
  }

  .assessment-section__header p {
    max-width: none;
    text-align: left;
  }
}

@media (max-width: 640px) {
  .assessment-hero,
  .assessment-section {
    padding: 20px;
    border-radius: 22px;
  }

  .assessment-stat-grid {
    grid-template-columns: 1fr;
  }

  .assessment-hero h1 {
    font-size: 28px;
  }
}
</style>
