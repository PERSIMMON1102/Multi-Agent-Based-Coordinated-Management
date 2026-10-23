<template>
  <div class="doctor-report-page" v-loading="loading">
    <section class="doctor-report-page__hero">
      <div>
        <span class="doctor-report-page__eyebrow">医生报告</span>
        <h1>{{ report?.title || '医生评估报告详情' }}</h1>
        <p>
          把风险判断、医生干预建议、随访建议和引用依据汇总到一个独立详情页，方便医生回看和继续沟通。
        </p>
      </div>
      <div class="doctor-report-page__actions">
        <el-button plain @click="goBackToPatient">返回患者详情</el-button>
        <el-button
          plain
          :disabled="!report"
          @click="copyReportSummary"
        >
          复制摘要
        </el-button>
        <el-button
          plain
          :disabled="!report"
          @click="copyReportFull"
        >
          复制全文
        </el-button>
        <el-button
          plain
          :loading="exportingPdf"
          :disabled="!report"
          @click="downloadReportPdf"
        >
          导出 PDF
        </el-button>
        <el-button
          plain
          :disabled="!report"
          @click="printReport"
        >
          打印
        </el-button>
        <el-button
          type="primary"
          :loading="regenerating"
          :disabled="!patientId"
          @click="regenerateReport"
        >
          重新生成报告
        </el-button>
      </div>
    </section>

    <section v-if="report" class="doctor-report-page__grid">
      <article class="doctor-report-summary-card">
        <span>患者</span>
        <strong>{{ patientName }}</strong>
        <p>{{ patientId ? `患者 ID：${patientId}` : '未识别患者信息' }}</p>
      </article>
      <article class="doctor-report-summary-card">
        <span>报告时间</span>
        <strong>{{ formatDate(report.generatedAt) || '--' }}</strong>
        <p>{{ reportHistoryHint }}</p>
      </article>
      <article class="doctor-report-summary-card">
        <span>引用依据</span>
        <strong>{{ report.references?.length || 0 }}</strong>
        <p>用于支撑报告结论和建议的参考资料</p>
      </article>
      <article class="doctor-report-summary-card">
        <span>整理摘要</span>
        <strong>{{ report.agentSteps?.length || 0 }}</strong>
        <p>展示当前报告整理与汇总时的关键步骤</p>
      </article>
    </section>

    <section v-if="reportHistory.length > 1" class="doctor-report-page__section">
      <div class="doctor-report-page__section-head">
        <div>
          <span class="doctor-report-page__eyebrow">历史版本</span>
          <h2>报告历史</h2>
        </div>
        <div class="doctor-report-page__sub">保留历史版本，便于回看不同时间点的评估结论</div>
      </div>
      <div class="doctor-report-history-list">
        <article
          v-for="item in reportHistory"
          :key="item.id"
          class="doctor-report-history-card"
          :data-active="Number(item.id) === Number(report?.id)"
        >
          <span>{{ formatDate(item.generatedAt) || '--' }}</span>
          <strong>{{ item.title || '医生评估报告' }}</strong>
          <p>{{ [item.workflowStatusLabel, item.freshnessLabel].filter(Boolean).join(' · ') || '可回看历史结论' }}</p>
          <el-button
            size="small"
            :type="Number(item.id) === Number(report?.id) ? 'primary' : 'default'"
            @click="openHistoryReport(item)"
          >
            {{ Number(item.id) === Number(report?.id) ? '当前查看' : '查看此版本' }}
          </el-button>
        </article>
      </div>
    </section>

    <template v-if="report">
      <div ref="pdfContainerRef" class="doctor-report-page__pdf-root">
      <section class="doctor-report-page__section">
        <div class="doctor-report-page__section-head">
          <div>
            <span class="doctor-report-page__eyebrow">报告内容</span>
            <h2>报告正文</h2>
          </div>
          <div class="doctor-report-page__sub">{{ reportStatusText }}</div>
        </div>

        <div class="doctor-report-outline">
          <article class="doctor-report-outline__item">
            <span>报告用途</span>
            <strong>医生复核风险与明确干预动作</strong>
            <p>用于把本次风险判断、随访安排和干预建议收口为单次临床处理记录。</p>
          </article>
          <article class="doctor-report-outline__item">
            <span>阅读建议</span>
            <strong>先看正文，再看证据与过程</strong>
            <p>先确认结论与建议，再根据引用依据和整理过程回看支撑信息。</p>
          </article>
        </div>

        <div class="doctor-report-page__content-card">
          <RichTextBlock :content="report.content || '当前没有报告正文'" />
        </div>
      </section>

      <section v-if="report.references && report.references.length > 0" class="doctor-report-page__section">
        <div class="doctor-report-page__section-head">
          <div>
            <span class="doctor-report-page__eyebrow">依据来源</span>
            <h2>引用依据</h2>
          </div>
          <div class="doctor-report-page__sub">当前报告引用的知识与证据摘要</div>
        </div>
        <div class="doctor-report-reference-list">
          <article
            v-for="(reference, index) in report.references"
            :key="`${report.id}-reference-${index}`"
            class="doctor-report-reference-card"
          >
            <strong>{{ reference.title || '参考资料' }}</strong>
            <div class="doctor-report-reference-card__meta">
              {{ [reference.source, reference.section, reference.publishedAt].filter(Boolean).join(' · ') || '未标注来源信息' }}
            </div>
            <p>{{ reference.contentSnippet || '当前没有摘要' }}</p>
          </article>
        </div>
      </section>

      <section v-if="report.agentSteps && report.agentSteps.length > 0" class="doctor-report-page__section">
        <div class="doctor-report-page__section-head">
          <div>
            <span class="doctor-report-page__eyebrow">整理摘要</span>
            <h2>报告整理摘要</h2>
          </div>
          <div class="doctor-report-page__sub">用于帮助医生回看报告形成时的关键整理步骤</div>
        </div>
        <el-collapse class="doctor-report-collapse">
          <el-collapse-item title="查看关键整理步骤" name="steps">
            <div class="doctor-report-agent-list">
              <article
                v-for="(step, index) in report.agentSteps"
                :key="`${report.id}-agent-${index}`"
                class="doctor-report-agent-card"
              >
                <span>步骤 {{ step.index || index + 1 }}</span>
                <strong>{{ formatReportStepName(step.agentName) }}</strong>
                <p>{{ step.summary || '已完成该步骤' }}</p>
              </article>
            </div>
          </el-collapse-item>
        </el-collapse>
      </section>

      <section class="doctor-report-page__section">
        <div class="doctor-report-page__section-head">
          <div>
            <span class="doctor-report-page__eyebrow">阅读指引</span>
            <h2>报告怎么看</h2>
          </div>
          <div class="doctor-report-page__sub">帮助医生快速理解这份报告的阅读顺序与实际用途</div>
        </div>
        <div class="doctor-report-interpret">
          <article class="doctor-report-interpret__card">
            <strong>1. 风险结论</strong>
            <p>先看报告正文里的总体判断，明确本次是以哪类慢病风险和哪组证据作为主线。</p>
          </article>
          <article class="doctor-report-interpret__card">
            <strong>2. 干预重点</strong>
            <p>再看报告中的医生建议，确认当前更适合优先处理用药、指标、生活方式还是随访安排。</p>
          </article>
          <article class="doctor-report-interpret__card">
            <strong>3. 证据支撑</strong>
            <p>若需要解释来源，可查看引用依据区，快速定位引用的是哪份知识条目或标准材料。</p>
          </article>
          <article class="doctor-report-interpret__card">
            <strong>4. 整理摘要</strong>
            <p>若需要回看报告形成过程，可看整理摘要，了解主要信息是如何逐步汇总完成的。</p>
          </article>
        </div>
      </section>
      </div>
    </template>

    <section v-else-if="!loading" class="doctor-report-page__empty">
      <el-empty description="没有找到对应的医生报告，可能已经被新的报告覆盖。">
        <el-button type="primary" @click="goBackToPatient">返回患者详情</el-button>
      </el-empty>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage } from '../../utils/element-plus-service'
import { healthReportApi, patientApi } from '../../api'
import RichTextBlock from '../../components/RichTextBlock.vue'
import { exportElementToPdf } from '../../utils/pdfExport'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const regenerating = ref(false)
const exportingPdf = ref(false)
const report = ref(null)
const reportHistory = ref([])
const patient = ref(null)
const pdfContainerRef = ref(null)

const patientId = computed(() => {
  const value = Number(route.params.id)
  return Number.isInteger(value) && value > 0 ? value : null
})
const reportId = computed(() => {
  const value = Number(route.params.reportId)
  return Number.isInteger(value) && value > 0 ? value : null
})
const patientName = computed(() => patient.value?.name || report.value?.patientName || '当前患者')
const reportStatusText = computed(() => report.value?.workflowStatusLabel || '医生评估报告')
const reportHistoryHint = computed(() => {
  if (!report.value) {
    return ''
  }
  return reportId.value
    ? '当前展示的是所选时间点生成的医生评估报告，可用于回看当时结论。'
    : '当前展示的是该患者最近一次生成的医生评估报告。'
})
const reportSummaryText = computed(() => {
  if (!report.value) {
    return ''
  }
  const referenceCount = Array.isArray(report.value.references) ? report.value.references.length : 0
  const traceCount = Array.isArray(report.value.agentSteps) ? report.value.agentSteps.length : 0
  return [
    report.value.title || '医生评估报告',
    `患者：${patientName.value}`,
    `报告时间：${formatDate(report.value.generatedAt) || '--'}`,
    `引用依据：${referenceCount} 条`,
    `整理摘要：${traceCount} 步`
  ].join('\n')
})

function formatDate(value) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : ''
}

function formatReportStepName(name) {
  const text = String(name || '').trim()
  if (!text) {
    return '处理步骤'
  }
  const normalized = text.toLowerCase()
  if (normalized.includes('context')) return '信息整理'
  if (normalized.includes('reference')) return '依据整理'
  if (normalized.includes('draft')) return '内容起草'
  if (normalized.includes('supervisor')) return '结果汇总'
  if (normalized.includes('followup')) return '随访整理'
  if (normalized.includes('assessment')) return '风险评估整理'
  return text
}

async function copyText(value, successMessage) {
  if (!value) {
    ElMessage.warning('当前没有可复制的内容')
    return
  }
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(value)
    } else {
      const textarea = document.createElement('textarea')
      textarea.value = value
      textarea.setAttribute('readonly', 'readonly')
      textarea.style.position = 'fixed'
      textarea.style.left = '-9999px'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
    }
    ElMessage.success(successMessage)
  } catch (error) {
    console.error('Failed to copy report text:', error)
    ElMessage.error('复制失败，请稍后重试')
  }
}

async function loadReportDetail() {
  if (!patientId.value) {
    report.value = null
    patient.value = null
    loading.value = false
    return
  }

  loading.value = true
  try {
    const [patientResult, reports] = await Promise.all([
      patientApi.get(patientId.value).catch(() => null),
      healthReportApi.getByPatient(patientId.value, 'doctor_assessment').catch(() => [])
    ])
    patient.value = patientResult
    const list = Array.isArray(reports) ? reports : []
    reportHistory.value = list
    report.value = list.find(item => Number(item?.id) === reportId.value) || list[0] || null
  } catch (error) {
    report.value = null
    reportHistory.value = []
    patient.value = null
    ElMessage.error(error.response?.data?.message || error.message || '医生报告加载失败')
  } finally {
    loading.value = false
  }
}

async function regenerateReport() {
  if (!patientId.value) {
    return
  }
  regenerating.value = true
  try {
    const generated = await healthReportApi.generateDoctorAssessment(patientId.value)
    if (generated?.id) {
      report.value = generated
      await router.replace({
        name: 'DoctorReportDetail',
        params: {
          id: String(patientId.value),
          reportId: String(generated.id)
        }
      })
    }
    ElMessage.success('医生评估报告已重新生成')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || error.message || '医生报告生成失败')
  } finally {
    regenerating.value = false
  }
}

async function downloadReportPdf() {
  if (!report.value || !pdfContainerRef.value) {
    ElMessage.warning('当前没有可导出的医生报告')
    return
  }
  exportingPdf.value = true
  try {
    const timestamp = dayjs(report.value.generatedAt).isValid()
      ? dayjs(report.value.generatedAt).format('YYYYMMDD_HHmmss')
      : dayjs().format('YYYYMMDD_HHmmss')
    await exportElementToPdf(pdfContainerRef.value, {
      filename: `医生评估报告_${patientName.value}_${timestamp}.pdf`
    })
    ElMessage.success('PDF 已开始下载')
  } catch (error) {
    console.error('Failed to export doctor report PDF:', error)
    ElMessage.error('导出 PDF 失败，请稍后重试')
  } finally {
    exportingPdf.value = false
  }
}

function printReport() {
  if (!report.value) {
    ElMessage.warning('当前没有可打印的医生报告')
    return
  }
  window.print()
}

async function copyReportSummary() {
  await copyText(reportSummaryText.value, '报告摘要已复制')
}

async function copyReportFull() {
  const content = [
    reportSummaryText.value,
    '',
    report.value?.content || ''
  ].filter(Boolean).join('\n\n')
  await copyText(content, '报告全文已复制')
}

function openHistoryReport(item) {
  if (!item?.id || !patientId.value) {
    return
  }
  router.replace({
    name: 'DoctorReportDetail',
    params: {
      id: String(patientId.value),
      reportId: String(item.id)
    }
  })
}

function goBackToPatient() {
  if (!patientId.value) {
    router.push({ name: 'PatientList' })
    return
  }
  router.push({
    name: 'PatientDetail',
    params: { id: String(patientId.value) },
    query: { tab: 'intervention' }
  })
}

watch(() => [route.params.id, route.params.reportId], () => {
  void loadReportDetail()
})

onMounted(() => {
  void loadReportDetail()
})
</script>

<style scoped>
.doctor-report-page{display:flex;flex-direction:column;gap:24px}
.doctor-report-page__hero{display:flex;justify-content:space-between;gap:20px;align-items:flex-start;padding:28px 30px;border-radius:30px;background:linear-gradient(120deg,rgba(255,255,255,.96),rgba(233,244,250,.9));border:1px solid rgba(197,212,223,.45);box-shadow:0 22px 46px rgba(149,167,181,.18)}
.doctor-report-page__eyebrow{display:inline-flex;align-items:center;padding:7px 14px;border-radius:999px;background:rgba(229,241,247,.9);color:#2f6f93;font-size:12px;letter-spacing:.08em;text-transform:uppercase}
.doctor-report-page__hero h1{margin:14px 0 10px;font-size:34px;line-height:1.18;color:#203849}
.doctor-report-page__hero p{margin:0;max-width:760px;font-size:16px;line-height:1.8;color:#5d7281}
.doctor-report-page__actions{display:flex;gap:12px;flex-wrap:wrap}
.doctor-report-page__pdf-root{display:grid;gap:24px}
.doctor-report-page__grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:18px}
.doctor-report-history-list{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px}
.doctor-report-history-card{padding:18px 20px;border-radius:22px;background:rgba(255,255,255,.94);border:1px solid rgba(213,223,231,.58);box-shadow:0 16px 30px rgba(152,170,183,.12)}
.doctor-report-history-card[data-active="true"]{border-color:rgba(64,158,255,.55);box-shadow:0 18px 34px rgba(64,158,255,.16)}
.doctor-report-history-card span{display:block;font-size:12px;color:#728491}
.doctor-report-history-card strong{display:block;margin-top:10px;font-size:16px;line-height:1.5;color:#203847}
.doctor-report-history-card p{margin:10px 0 14px;font-size:14px;line-height:1.7;color:#5d7281}
.doctor-report-summary-card{padding:22px 24px;border-radius:24px;background:rgba(255,252,246,.95);border:1px solid rgba(228,214,196,.55);box-shadow:0 18px 34px rgba(173,154,127,.12)}
.doctor-report-summary-card span{display:block;font-size:13px;color:#7a8b98}
.doctor-report-summary-card strong{display:block;margin-top:10px;font-size:28px;line-height:1.2;color:#213847}
.doctor-report-summary-card p{margin:10px 0 0;font-size:14px;line-height:1.7;color:#667987}
.doctor-report-page__section{display:flex;flex-direction:column;gap:16px}
.doctor-report-page__section-head{display:flex;justify-content:space-between;gap:16px;align-items:flex-end}
.doctor-report-page__section-head h2{margin:8px 0 0;font-size:28px;color:#203847}
.doctor-report-page__sub{font-size:14px;color:#6d7f8c}
.doctor-report-page__content-card{padding:26px 28px;border-radius:28px;background:rgba(255,255,255,.94);border:1px solid rgba(213,223,231,.58);box-shadow:0 24px 44px rgba(152,170,183,.14)}
.doctor-report-outline{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}
.doctor-report-outline__item{padding:18px 20px;border-radius:22px;background:rgba(244,249,252,.86);border:1px solid rgba(205,220,228,.65)}
.doctor-report-outline__item span{display:block;font-size:12px;color:#6f8593}
.doctor-report-outline__item strong{display:block;margin-top:8px;font-size:16px;color:#203847}
.doctor-report-outline__item p{margin:10px 0 0;font-size:14px;line-height:1.7;color:#516775}
.doctor-report-reference-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}
.doctor-report-reference-card{padding:18px 20px;border-radius:22px;background:rgba(247,243,236,.76);border:1px solid rgba(229,217,200,.6)}
.doctor-report-reference-card strong{display:block;font-size:16px;color:#203847}
.doctor-report-reference-card__meta{margin-top:8px;font-size:12px;color:#7b8d98}
.doctor-report-reference-card p{margin:12px 0 0;font-size:14px;line-height:1.8;color:#4b606e}
.doctor-report-collapse{border:none;background:transparent}
.doctor-report-collapse :deep(.el-collapse-item__header){padding:0 4px;background:transparent;border:none;font-size:15px;color:#203847}
.doctor-report-collapse :deep(.el-collapse-item__wrap){background:transparent;border:none}
.doctor-report-collapse :deep(.el-collapse-item__content){padding-bottom:0}
.doctor-report-agent-list{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}
.doctor-report-agent-card{padding:18px 20px;border-radius:22px;background:linear-gradient(160deg,rgba(243,249,252,.94),rgba(255,255,255,.94));border:1px solid rgba(205,220,228,.65)}
.doctor-report-agent-card span{display:block;font-size:12px;color:#6f8593}
.doctor-report-agent-card strong{display:block;margin-top:10px;font-size:16px;color:#203847}
.doctor-report-agent-card p{margin:10px 0 0;font-size:14px;line-height:1.7;color:#516775}
.doctor-report-interpret{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}
.doctor-report-interpret__card{padding:18px 20px;border-radius:22px;background:#fff;border:1px solid rgba(213,223,231,.7)}
.doctor-report-interpret__card strong{display:block;font-size:16px;color:#203847}
.doctor-report-interpret__card p{margin:10px 0 0;font-size:14px;line-height:1.75;color:#516775}
.doctor-report-page__empty{padding:30px;border-radius:24px;background:rgba(255,255,255,.92);border:1px solid rgba(213,223,231,.58)}

@media (max-width: 1100px) {
  .doctor-report-history-list,
  .doctor-report-page__grid,
  .doctor-report-outline,
  .doctor-report-reference-list,
  .doctor-report-agent-list,
  .doctor-report-interpret{grid-template-columns:1fr 1fr}
}

@media (max-width: 900px) {
  .doctor-report-page__hero,
  .doctor-report-page__section-head{flex-direction:column;align-items:flex-start}
  .doctor-report-history-list,
  .doctor-report-page__grid,
  .doctor-report-outline,
  .doctor-report-reference-list,
  .doctor-report-agent-list,
  .doctor-report-interpret{grid-template-columns:1fr}
  .doctor-report-page__hero h1{font-size:28px}
}

@media print {
  .doctor-report-page{background:#fff}
  .doctor-report-page__hero{box-shadow:none;border-color:#d7dee3}
  .doctor-report-page__actions{display:none}
}
</style>
