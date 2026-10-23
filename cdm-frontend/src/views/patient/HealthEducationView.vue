<template>
  <div class="education-shell">
    <section class="education-hero">
      <div class="education-hero__copy">
        <span class="education-hero__eyebrow">健康宣教</span>
        <h1>为你的慢病组合生成一份可持续执行的生活方案</h1>
        <p>系统会结合风险评估、病史背景和近期健康情况，生成饮食、运动、作息三部分的健康建议。</p>
        <div class="education-hero__actions">
          <el-button type="primary" round size="large" @click="generatePlan" :loading="generating">
            {{ plan ? '重新生成方案' : '生成我的方案' }}
          </el-button>
          <el-button v-if="plan" round size="large" @click="downloadPlanPdf" :loading="exportingPdf">下载 PDF</el-button>
        </div>
        <div v-if="plan && plan.diseases && plan.diseases.length > 0" class="education-hero__chips">
          <span class="education-hero__chip-label">当前病种</span>
          <el-tag v-for="d in plan.diseases" :key="d" type="primary" effect="plain" size="small">{{ d }}</el-tag>
        </div>
      </div>
    </section>

    <section v-if="!plan && !generating" class="education-empty">
      <div class="education-empty__panel">
        <span class="education-empty__eyebrow">还没有当前方案</span>
        <h2>先生成一份新的健康宣教方案</h2>
        <p>生成后会展示饮食、运动和生活方式建议，方便你直接按周执行。</p>
        <el-button type="primary" round size="large" @click="generatePlan">开始生成</el-button>
      </div>
    </section>

    <div v-if="plan" ref="pdfContainerRef" class="education-content-grid">
      <div class="education-main">
        <div class="education-cover-card" data-pdf-block="true">
          <div class="education-cover-card__eyebrow">本次方案</div>
          <div class="education-cover-card__title">健康宣教方案</div>
          <div class="education-cover-card__meta">
            <span>生成时间：{{ formatGeneratedAt(plan.generatedAt) }}</span>
          </div>
          <div v-if="plan.diseases && plan.diseases.length > 0" class="education-cover-card__tags">
            <el-tag v-for="d in plan.diseases" :key="`plan-${d}`" type="primary" effect="plain" size="small">{{ d }}</el-tag>
          </div>
        </div>

        <div class="education-plan-list">
          <el-card class="education-plan-card education-plan-card--diet" shadow="never" :body-style="{ padding: 0 }" data-pdf-block="true">
            <div class="education-plan-card__header">
              <div class="education-plan-card__icon">食</div>
              <div>
                <div class="education-plan-card__title">饮食方案</div>
                <div class="education-plan-card__subtitle">营养师视角的稳定执行方案</div>
              </div>
            </div>
            <div class="education-plan-card__body">
              <RichTextBlock class="plan-text" :content="plan.diet" />
            </div>
          </el-card>

          <el-card class="education-plan-card education-plan-card--exercise" shadow="never" :body-style="{ padding: 0 }" data-pdf-block="true">
            <div class="education-plan-card__header">
              <div class="education-plan-card__icon">动</div>
              <div>
                <div class="education-plan-card__title">运动方案</div>
                <div class="education-plan-card__subtitle">更适合长期坚持的活动安排</div>
              </div>
            </div>
            <div class="education-plan-card__body">
              <RichTextBlock class="plan-text" :content="plan.exercise" />
            </div>
          </el-card>

          <el-card class="education-plan-card education-plan-card--lifestyle" shadow="never" :body-style="{ padding: 0 }" data-pdf-block="true">
            <div class="education-plan-card__header">
              <div class="education-plan-card__icon">息</div>
              <div>
                <div class="education-plan-card__title">作息与生活方式</div>
                <div class="education-plan-card__subtitle">把管理动作融进每天的节奏里</div>
              </div>
            </div>
            <div class="education-plan-card__body">
              <RichTextBlock class="plan-text" :content="plan.lifestyle" />
            </div>
          </el-card>
        </div>

        <div class="education-disclaimer" data-pdf-block="true">
          本次方案生成时间：{{ formatGeneratedAt(plan.generatedAt) }} ｜ 具体请遵医嘱
        </div>
      </div>
    </div>

    <el-card v-if="history.length > 0" shadow="never" class="education-history-card">
      <template #header>
        <div class="education-history-card__head">
          <div>
            <span class="education-side-card__eyebrow">历史记录</span>
            <h3>过往方案</h3>
          </div>
          <el-text size="small" type="info">可切换查看之前生成的内容</el-text>
        </div>
      </template>

      <el-timeline>
        <el-timeline-item
          v-for="(item, index) in history"
          :key="getPlanKey(item, index)"
          :timestamp="formatGeneratedAt(item.generatedAt)"
          placement="top"
          :type="selectedPlanKey === getPlanKey(item, index) ? 'primary' : 'info'"
        >
          <div class="history-item">
            <div>
              <div class="history-item__top">
                <el-button type="primary" text @click="selectPlan(item, index)">
                  {{ selectedPlanKey === getPlanKey(item, index) ? '正在查看' : '查看方案' }}
                </el-button>
                <el-tag v-if="selectedPlanKey === getPlanKey(item, index)" size="small" type="primary" effect="plain">当前查看</el-tag>
              </div>
              <div v-if="item.diseases && item.diseases.length > 0" class="history-item__diseases">
                {{ item.diseases.join(' / ') }}
              </div>
              <div class="history-preview">{{ getPlanPreview(item) }}</div>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>

  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { ElMessage } from '../../utils/element-plus-service'
import { educationApi } from '../../api'
import { useUserStore } from '../../stores/user'
import { exportElementToPdf } from '../../utils/pdfExport'
import RichTextBlock from '../../components/RichTextBlock.vue'

const userStore = useUserStore()
const router = useRouter()
const generating = ref(false)
const exportingPdf = ref(false)
const history = ref([])
const selectedPlanKey = ref('')
const pdfContainerRef = ref(null)
const diseaseMap = {
  GENERAL: '通用',
  DIABETES: '2型糖尿病',
  HYPERTENSION: '高血压',
  COPD: '慢阻肺',
  CKD: '慢性肾病',
  CHD: '冠心病',
  STROKE: '脑卒中'
}

const plan = computed(() => {
  return history.value.find((item, index) => getPlanKey(item, index) === selectedPlanKey.value) || history.value[0] || null
})

onMounted(async () => {
  if (!userStore.patientId) return
  try {
    history.value = await educationApi.getByPatient(userStore.patientId, { limit: 12 })
    if (history.value.length > 0) {
      selectedPlanKey.value = getPlanKey(history.value[0], 0)
    }
  } catch (e) {
    history.value = []
  }
})

async function generatePlan() {
  if (!userStore.patientId) {
    ElMessage.warning('无法确定患者身份')
    return
  }
  generating.value = true
  try {
    const newPlan = await educationApi.generate(userStore.patientId)
    history.value = [newPlan, ...history.value].slice(0, 12)
    selectedPlanKey.value = getPlanKey(newPlan, 0)
    ElMessage.success('健康宣教方案已生成')
  } catch (e) {
    const isTimeout = e?.code === 'ECONNABORTED' || String(e?.message || '').toLowerCase().includes('timeout')
    ElMessage.error(isTimeout ? '生成超时：宣教方案内容较长，请稍后重试。' : ('生成失败: ' + (e.message || e)))
  } finally {
    generating.value = false
  }
}

function selectPlan(item, index) {
  selectedPlanKey.value = getPlanKey(item, index)
}

function getPlanKey(item, index) {
  return item?.planId || `${item?.generatedAt || 'plan'}-${index}`
}

function getPlanPreview(item) {
  const source = [item?.diet, item?.exercise, item?.lifestyle].find(Boolean) || ''
  const normalized = source.replace(/\s+/g, ' ').trim()
  return normalized.slice(0, 58) + (normalized.length > 58 ? '...' : '')
}

function formatGeneratedAt(value) {
  if (!value) return '未知时间'
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : value
}

async function downloadPlanPdf() {
  if (!plan.value || !pdfContainerRef.value) {
    ElMessage.warning('当前没有可导出的宣教方案')
    return
  }
  exportingPdf.value = true
  try {
    const timestamp = dayjs(plan.value.generatedAt).isValid()
      ? dayjs(plan.value.generatedAt).format('YYYYMMDD_HHmmss')
      : dayjs().format('YYYYMMDD_HHmmss')
    await exportElementToPdf(pdfContainerRef.value, {
      filename: `健康宣教方案_${timestamp}.pdf`
    })
    ElMessage.success('PDF 已开始下载')
  } catch (error) {
    console.error('Failed to export education PDF:', error)
    ElMessage.error('导出 PDF 失败，请稍后重试')
  } finally {
    exportingPdf.value = false
  }
}
</script>

<style scoped>
.education-shell{display:grid;gap:22px}
.education-hero,.education-content-grid{display:grid;gap:18px}
.education-main,.education-plan-list{display:grid;gap:18px}
.education-hero__copy,.education-cover-card,.education-history-card,.education-plan-card,.education-empty__panel{border:1px solid rgba(31,51,64,.08);border-radius:28px;box-shadow:var(--patient-shadow-soft)}
.education-hero__copy,.education-cover-card{background:radial-gradient(circle at top right,rgba(220,235,243,.86),transparent 30%),linear-gradient(135deg,rgba(255,252,246,.94),rgba(245,249,251,.92))}
.education-hero__copy{padding:30px 32px}
.education-hero__eyebrow,.education-empty__eyebrow,.education-side-card__eyebrow{display:inline-block;font-size:12px;font-weight:700;letter-spacing:.12em;color:rgba(43,108,143,.72)}
.education-hero__copy h1,.education-empty__panel h2{margin:12px 0 10px;color:var(--patient-ink)}
.education-hero__copy h1{font-size:34px;line-height:1.12}
.education-empty__panel h2{font-size:30px}
.education-hero__copy p,.education-empty__panel p{margin:0;line-height:1.8;color:var(--patient-muted)}
.education-hero__actions,.education-hero__chips,.education-cover-card__tags,.education-cover-card__meta,.reference-meta,.history-item__top{display:flex;gap:8px;flex-wrap:wrap}
.education-hero__actions{gap:12px;margin-top:24px}
.education-hero__chips{align-items:center;margin-top:18px}
.education-hero__chip-label{font-size:13px;font-weight:600;color:var(--patient-muted)}
.education-empty__panel{padding:40px 32px;background:rgba(255,255,255,.82);text-align:center}
.education-empty__panel p{max-width:560px;margin:0 auto 20px}
.education-cover-card{padding:24px 26px}
.education-cover-card__eyebrow{font-size:12px;letter-spacing:.1em;color:var(--patient-muted)}
.education-cover-card__title{margin-top:8px;font-size:28px;font-weight:700;color:var(--patient-ink)}
.education-cover-card__meta{margin-top:10px;font-size:13px;color:var(--patient-muted)}
.education-plan-card{overflow:hidden;background:rgba(255,255,255,.84)}
.education-plan-card__header{display:flex;align-items:center;gap:16px;padding:18px 22px;color:#fff}
.education-plan-card--diet .education-plan-card__header{background:linear-gradient(135deg,#3f7f5f 0%,#77a67f 100%)}
.education-plan-card--exercise .education-plan-card__header{background:linear-gradient(135deg,#b8792f 0%,#d9a25b 100%)}
.education-plan-card--lifestyle .education-plan-card__header{background:linear-gradient(135deg,#5a6779 0%,#7d8ca5 100%)}
.education-plan-card__icon{display:inline-flex;align-items:center;justify-content:center;width:52px;height:52px;border-radius:16px;background:rgba(255,255,255,.18);font-size:20px;font-weight:700;backdrop-filter:blur(6px)}
.education-plan-card__title{font-size:18px;font-weight:700}
.education-plan-card__subtitle{margin-top:4px;font-size:13px;color:rgba(255,255,255,.84)}
.education-plan-card__body{padding:22px}
.plan-text{font-size:14px;line-height:1.9;color:#4f5f69}
.education-disclaimer{text-align:center;font-size:12px;color:rgba(31,51,64,.5)}
.education-history-card{background:rgba(255,255,255,.82)}
.education-history-card :deep(.el-card__header){padding:22px 24px 18px;border-bottom:1px solid rgba(31,51,64,.06)}
.education-history-card :deep(.el-card__body){padding:22px 24px 24px}
.education-history-card__head{display:flex;align-items:flex-start;justify-content:space-between;gap:12px}
.education-history-card__head h3{margin:8px 0 0;font-size:22px;color:var(--patient-ink)}
.history-preview{margin-top:8px;font-size:13px;line-height:1.7;color:var(--patient-muted)}
.history-item__diseases{margin-top:8px;font-size:12px;color:rgba(31,51,64,.56)}
@media (max-width:768px){.education-hero__copy,.education-cover-card,.education-plan-card__body{padding-left:20px;padding-right:20px}.education-history-card :deep(.el-card__header),.education-history-card :deep(.el-card__body){padding-left:18px;padding-right:18px}.education-history-card__head{flex-direction:column}}
@media (max-width:640px){.education-hero__copy h1{font-size:28px}.education-hero__actions{flex-direction:column;align-items:stretch}.education-cover-card__meta{flex-direction:column;align-items:flex-start}}
</style>
