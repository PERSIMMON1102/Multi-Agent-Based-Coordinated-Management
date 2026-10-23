<template>
  <div class="report-shell">
    <section class="report-hero">
      <div class="report-hero__copy">
        <span class="report-hero__eyebrow">每周健康报告</span>
        <h1>把评估结果、病史提醒和推荐内容整理成一份可回看的周报</h1>
        <p>系统会基于最近健康数据、风险评估和病史背景生成报告，方便您按周回看重点变化和执行建议。</p>
        <div class="report-hero__actions">
          <el-button type="primary" round size="large" :loading="generating" @click="generateReport">
            <el-icon><MagicStick /></el-icon>
            生成周报
          </el-button>
        </div>
      </div>

      <div class="report-hero__stats">
        <div class="report-stat-card">
          <span>历史报告</span>
          <strong>{{ reports.length }}</strong>
          <p>{{ reports.length > 0 ? '可回看历史周报' : '还没有生成报告' }}</p>
        </div>
        <div class="report-stat-card">
          <span>风险评估</span>
          <strong>{{ assessments.length }}</strong>
          <p>同步展示最近的风险评估记录</p>
        </div>
        <div class="report-stat-card">
          <span>最新报告</span>
          <strong>{{ latestReportLabel }}</strong>
          <p>{{ latestReportTime }}</p>
        </div>
      </div>
    </section>

    <el-alert
      v-if="generating"
      title="系统正在为您生成本周健康报告，请稍候..."
      type="info"
      show-icon
      :closable="false"
      class="report-generating-alert"
    />

    <section v-if="!generating && reports.length === 0" class="report-empty">
        <div class="report-empty__panel">
          <span class="report-empty__eyebrow">暂时还没有周报</span>
          <h2>先生成第一份健康报告</h2>
        <p>生成后会自动整理本周重点结论、执行建议和相关视频。</p>
        <el-button type="primary" round size="large" @click="generateReport">开始生成</el-button>
      </div>
    </section>

    <div v-for="report in reports" :key="report.id" class="report-entry">
      <div :ref="(el) => setReportExportRef(report.id, el)" class="report-paper pdf-export-surface report-export-surface">
        <div class="report-paper__header" data-pdf-block="true">
          <div class="report-paper__header-main">
            <div class="report-paper__icon">报</div>
            <div>
              <div class="report-paper__title">每周健康报告</div>
              <div class="report-paper__time">{{ formatDate(report.generatedAt) }}</div>
            </div>
          </div>
          <el-button
            data-pdf-ignore="true"
            round
            size="small"
            @click="downloadReportPdf(report)"
            :loading="exportingReportId === report.id"
          >
            下载 PDF
          </el-button>
        </div>

        <div class="report-paper__body">
          <div class="report-paper__content">
            <div
              v-for="(block, blockIndex) in formatContentBlocks(report.content)"
              :key="`${report.id}-content-${blockIndex}`"
              class="report-content-block"
              data-pdf-block="true"
              >
                <RichTextBlock :content="block" />
              </div>
            </div>
        </div>

        <div
          v-if="report.recommendedVideos && report.recommendedVideos.length > 0"
          class="report-section"
          data-pdf-ignore="true"
        >
          <div class="report-section__title">本次报告推荐的视频</div>
          <div class="video-recommend-grid">
            <div
              v-for="video in report.recommendedVideos"
              :key="`${report.id}-${video.id || video.videoUrl}`"
              class="video-recommend-card"
              @click="openSnapshotVideo(video, report)"
            >
              <div class="video-cover">
                <img v-if="video.coverUrl" :src="video.coverUrl" :alt="video.title">
                <div v-else class="video-cover-fallback">视频</div>
              </div>
              <div class="video-meta">
                <div class="video-title">{{ video.title }}</div>
                <div class="video-info">
                  <el-tag size="small" type="danger" effect="plain">{{ diseaseLabel(video.diseaseType) }}</el-tag>
                  <el-tag v-if="video.category" size="small" type="success" effect="plain">{{ video.category }}</el-tag>
                </div>
                <div class="video-reason">{{ video.recommendReason || '根据当次风险评估推荐' }}</div>
                <div class="video-footer">
                  <span>{{ formatDuration(video.durationSeconds) }}</span>
                  <div class="video-footer__actions" data-pdf-ignore="true">
                    <el-button type="primary" text @click.stop="openSnapshotVideo(video, report)">播放快照</el-button>
                    <el-button type="primary" text @click.stop="openVideoCenter(video, report)">视频中心</el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

      </div>
    </div>

    <el-card v-if="assessments.length > 0" shadow="never" class="assessment-surface">
      <template #header>
        <div class="assessment-surface__head">
          <div>
            <span class="assessment-surface__eyebrow">风险评估</span>
            <h2>风险评估记录</h2>
          </div>
        </div>
      </template>

      <div class="assessment-list">
        <el-card
          v-for="a in assessments"
          :key="a.id"
          shadow="never"
          class="assessment-item"
          :body-style="{ padding: 0 }"
        >
          <div :class="['assess-header', 'risk-' + (a.riskLevel || 'LOW').toLowerCase()]">
            <div class="assess-header__main">
              <el-tag :type="riskTagType(a.riskLevel)" effect="dark" size="small">{{ riskLabel(a.riskLevel) }}</el-tag>
              <span class="assess-header__disease">{{ diseaseLabel(a.diseaseType) }}</span>
              <span class="assess-header__time">{{ formatDate(a.createdAt) }}</span>
            </div>
            <div class="assess-header__score">{{ a.score != null ? a.score.toFixed(1) : '--' }}</div>
          </div>
          <div class="assessment-item__body">
            <RichTextBlock
              v-if="a.assessmentDetail"
              class="assessment-detail"
              :content="a.assessmentDetail"
              compact
            />
            <div v-if="hasHistoryAlerts(a)" class="assessment-history-alerts">
              <div class="assessment-history-title">病史提醒</div>
              <div class="assessment-history-tags">
                <el-tag
                  v-for="(alert, index) in a.historyAlerts"
                  :key="`report-history-${a.id || a.createdAt}-${index}`"
                  type="warning"
                  effect="plain"
                  size="small"
                >
                  {{ alert }}
                </el-tag>
              </div>
            </div>
            <div v-if="a.recommendations && a.recommendations.length > 0" class="assessment-recommend-list">
              <div v-for="(r, i) in a.recommendations" :key="i" class="assessment-recommend-item">
                <el-tag type="success" size="small" round>{{ i + 1 }}</el-tag>
                <span>{{ r }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </el-card>

    <el-dialog
      v-model="previewVisible"
      width="760px"
      title="健康视频"
      destroy-on-close
      append-to-body
      align-center
      @close="handlePreviewClose"
      @closed="handlePreviewClosed"
    >
      <div v-if="previewVideo">
        <video
          ref="snapshotPlayerRef"
          class="snapshot-player"
          :src="previewVideo.videoUrl"
          :poster="previewVideo.coverUrl || undefined"
          controls
          preload="metadata"
        />
        <div class="snapshot-meta">
          <div class="snapshot-title">{{ previewVideo.title }}</div>
          <div class="video-info">
            <el-tag size="small" type="danger" effect="plain">{{ diseaseLabel(previewVideo.diseaseType) }}</el-tag>
            <el-tag v-if="previewVideo.category" size="small" type="success" effect="plain">{{ previewVideo.category }}</el-tag>
            <el-tag size="small" type="info" effect="plain">{{ formatDuration(previewVideo.durationSeconds) }}</el-tag>
          </div>
          <div class="snapshot-caption">
            推荐生成时间：{{ formatDate(previewReportGeneratedAt) }}<span v-if="previewVideo.recommendReason">，{{ previewVideo.recommendReason }}</span>
          </div>
          <p class="snapshot-desc">{{ previewVideo.description || '暂无简介' }}</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { healthReportApi, assessmentApi } from '../../api'
import { useUserStore } from '../../stores/user'
import { ElMessage } from '../../utils/element-plus-service'
import dayjs from 'dayjs'
import { exportElementToPdf } from '../../utils/pdfExport'
import RichTextBlock from '../../components/RichTextBlock.vue'
import { splitRichTextBlocks } from '../../utils/richText'

const router = useRouter()
const userStore = useUserStore()
const reports = ref([])
const assessments = ref([])
const generating = ref(false)
const exportingReportId = ref(null)
const previewVisible = ref(false)
const previewVideo = ref(null)
const previewReportGeneratedAt = ref('')
const snapshotPlayerRef = ref(null)
const reportExportRefs = ref({})

const diseaseMap = { GENERAL: '通用', DIABETES: '2型糖尿病', HYPERTENSION: '高血压', COPD: '慢阻肺', CKD: '慢性肾病', CHD: '冠心病', STROKE: '脑卒中' }
const riskMap = { LOW: '低风险', MEDIUM: '中风险', HIGH: '高风险', CRITICAL: '危急' }

const latestReportLabel = computed(() => reports.value.length > 0 ? '已生成' : '未生成')
const latestReportTime = computed(() => reports.value[0]?.generatedAt ? formatDate(reports.value[0].generatedAt) : '暂时还没有周报')

function diseaseLabel(t) { return diseaseMap[t] || t }
function riskLabel(l) { return riskMap[l] || l }
function riskTagType(l) { return { LOW: 'success', MEDIUM: 'warning', HIGH: 'danger', CRITICAL: 'danger' }[l] || 'info' }
function formatDate(d) { return d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '' }
function hasHistoryAlerts(row) { return Array.isArray(row?.historyAlerts) && row.historyAlerts.length > 0 }
function formatDuration(value) {
  const seconds = Number(value)
  if (!seconds) return '时长未标注'
  const minutes = Math.floor(seconds / 60)
  const remain = seconds % 60
  return `${minutes}分${remain.toString().padStart(2, '0')}秒`
}

function formatContentBlocks(text) {
  return splitRichTextBlocks(text)
}

function setReportExportRef(reportId, element) {
  if (element) {
    reportExportRefs.value[reportId] = element
    return
  }
  delete reportExportRefs.value[reportId]
}

async function downloadReportPdf(report) {
  const element = reportExportRefs.value[report?.id]
  if (!report || !element) {
    ElMessage.warning('当前报告暂时无法导出')
    return
  }
  exportingReportId.value = report.id
  try {
    const timestamp = dayjs(report.generatedAt).isValid()
      ? dayjs(report.generatedAt).format('YYYYMMDD_HHmmss')
      : dayjs().format('YYYYMMDD_HHmmss')
    await exportElementToPdf(element, {
      filename: `每周健康报告_${timestamp}.pdf`
    })
    ElMessage.success('PDF 已开始下载')
  } catch (error) {
    console.error('Failed to export report PDF:', error)
    ElMessage.error('导出 PDF 失败，请稍后重试')
  } finally {
    exportingReportId.value = null
  }
}

async function generateReport() {
  const pid = userStore.patientId
  if (!pid) return ElMessage.warning('未找到患者ID')
  generating.value = true
  try {
    const report = await healthReportApi.generatePatientWeekly(pid)
    reports.value.unshift(report)
    ElMessage.success('报告生成成功')
  } catch (e) {
    ElMessage.error('报告生成失败，请稍后重试')
  } finally {
    generating.value = false
  }
}

function openSnapshotVideo(video, report) {
  previewVideo.value = video
  previewReportGeneratedAt.value = report?.generatedAt || ''
  previewVisible.value = true
}

function openVideoCenter(video, report) {
  router.push({
    path: '/patient/videos',
    query: {
      video: video?.id,
      sourceType: 'report_recommendation',
      sourceRefId: report?.id || ''
    }
  })
}

function stopPreviewPlayback() {
  const player = snapshotPlayerRef.value
  if (!player) return
  player.pause()
  player.currentTime = 0
  player.removeAttribute('src')
  player.load()
}

function handlePreviewClose() {
  stopPreviewPlayback()
}

function handlePreviewClosed() {
  previewVideo.value = null
  previewReportGeneratedAt.value = ''
  snapshotPlayerRef.value = null
}

watch(previewVisible, (visible) => {
  if (!visible) {
    stopPreviewPlayback()
  }
})

onBeforeUnmount(() => {
  stopPreviewPlayback()
})

onMounted(async () => {
  const pid = userStore.patientId
  if (!pid) return
  try {
    const [rpts, assts] = await Promise.all([
      healthReportApi.getByPatient(pid, 'patient_weekly'),
      assessmentApi.getByPatient(pid)
    ])
    reports.value = rpts
    assessments.value = assts
  } catch (e) {
    /* ignore */
  }
})
</script>

<style scoped>
.report-shell{display:grid;gap:22px}
.report-hero{display:grid;grid-template-columns:minmax(0,1.45fr) minmax(260px,.95fr);gap:18px}
.report-hero__copy,.report-hero__stats,.report-empty__panel,.report-paper,.assessment-surface{border:1px solid rgba(31,51,64,.08);border-radius:28px;box-shadow:var(--patient-shadow-soft)}
.report-hero__copy{padding:30px 32px;background:radial-gradient(circle at top right,rgba(220,235,243,.86),transparent 30%),linear-gradient(135deg,rgba(255,252,246,.94),rgba(245,249,251,.92))}
.report-hero__eyebrow,.report-empty__eyebrow,.assessment-surface__eyebrow{display:inline-block;font-size:12px;font-weight:700;letter-spacing:.12em;color:rgba(43,108,143,.72)}
.report-hero__copy h1,.report-empty__panel h2{margin:12px 0 10px;color:var(--patient-ink)}
.report-hero__copy h1{font-size:34px;line-height:1.12}
.report-empty__panel h2{font-size:30px}
.report-hero__copy p,.report-empty__panel p{margin:0;line-height:1.8;color:var(--patient-muted)}
.report-hero__actions{display:flex;gap:12px;flex-wrap:wrap;margin-top:24px}
.report-hero__stats{display:grid;gap:12px;padding:18px;background:rgba(255,255,255,.82)}
.report-stat-card{padding:16px 18px;border-radius:22px;background:rgba(247,243,236,.84)}
.report-stat-card span{display:block;font-size:12px;color:var(--patient-muted)}
.report-stat-card strong{display:block;margin:10px 0 6px;font-size:24px;color:var(--patient-ink)}
.report-stat-card p{margin:0;line-height:1.7;color:var(--patient-muted)}
.report-generating-alert{margin-bottom:4px}
.report-empty__panel{padding:40px 32px;background:rgba(255,255,255,.82);text-align:center}
.report-empty__panel p{max-width:560px;margin:0 auto 20px}
.report-entry{display:grid}
.report-paper{background:rgba(255,255,255,.84);overflow:hidden}
.report-paper__header{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:18px 22px;background:linear-gradient(135deg,#35607a 0%,#5b8698 100%)}
.report-paper__header-main{display:flex;align-items:center;gap:12px}
.report-paper__icon{width:44px;height:44px;border-radius:14px;background:rgba(255,255,255,.2);display:flex;align-items:center;justify-content:center;color:#fff;font-weight:700;font-size:14px}
.report-paper__title{font-size:16px;font-weight:700;color:#fff}
.report-paper__time{margin-top:4px;font-size:12px;color:rgba(255,255,255,.78)}
.report-paper__body{padding:22px}
.report-paper__content{font-size:14px;line-height:1.9;color:#4f5f69}
.report-content-block+.report-content-block{margin-top:14px}
.report-section{padding:0 22px 22px}
.report-section__title{margin-bottom:12px;font-size:14px;font-weight:700;color:var(--patient-ink)}
.video-recommend-grid,.assessment-list{display:grid;gap:12px}
.video-recommend-grid{grid-template-columns:repeat(auto-fill,minmax(240px,1fr))}
.video-recommend-card,.assessment-item{border:1px solid rgba(31,51,64,.08);border-radius:22px;background:rgba(248,250,251,.9)}
.video-recommend-card{overflow:hidden;cursor:pointer;transition:transform .18s ease,box-shadow .18s ease}
.video-recommend-card:hover{transform:translateY(-2px);box-shadow:0 16px 28px rgba(43,108,143,.12)}
.video-cover{height:140px;background:linear-gradient(135deg,#dbeafe,#bfdbfe)}
.video-cover img{width:100%;height:100%;object-fit:cover;display:block}
.video-cover-fallback{width:100%;height:100%;display:flex;align-items:center;justify-content:center;color:#1d4ed8;font-size:20px;font-weight:700;letter-spacing:.08em}
.video-meta{padding:14px}
.video-title,.snapshot-title{font-weight:700;color:var(--patient-ink)}
.video-title{font-size:15px}
.video-info{margin-top:10px;display:flex;gap:8px;flex-wrap:wrap}
.video-reason,.snapshot-desc{margin-top:10px;color:var(--patient-muted);font-size:13px;line-height:1.7}
.video-footer{margin-top:12px;display:flex;align-items:center;justify-content:space-between;gap:12px;color:rgba(31,51,64,.56);font-size:12px}
.video-footer__actions{display:flex;gap:8px}
.reference-meta,.agent-step-index,.snapshot-caption,.assess-header__time{margin-top:8px;display:flex;gap:12px;flex-wrap:wrap;color:rgba(31,51,64,.56);font-size:12px}
.assessment-surface{background:rgba(255,255,255,.82)}
.assessment-surface :deep(.el-card__header),.assessment-surface :deep(.el-card__body){padding:22px 24px}
.assessment-surface :deep(.el-card__header){border-bottom:1px solid rgba(31,51,64,.06)}
.assessment-surface__head h2{margin:8px 0 0;font-size:22px;color:var(--patient-ink)}
.assessment-item{overflow:hidden}
.assess-header{padding:14px 20px;display:flex;justify-content:space-between;align-items:center}
.assess-header.risk-low{background:linear-gradient(135deg,#67C23A,#85CE61)}
.assess-header.risk-medium{background:linear-gradient(135deg,#E6A23C,#F0C78A)}
.assess-header.risk-high{background:linear-gradient(135deg,#F56C6C,#F89898)}
.assess-header.risk-critical{background:linear-gradient(135deg,#C45656,#F56C6C)}
.assess-header__main{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.assess-header__disease,.assess-header__time,.assess-header__score{color:#fff}
.assess-header__disease{font-weight:700}
.assess-header__score{font-size:22px;font-weight:700}
.assessment-item__body{padding:16px}
.assessment-detail{font-size:13px;line-height:1.8;color:#4a4a4a;background:#f8f9fa;border-radius:12px;padding:12px;border-left:3px solid #409EFF;margin-bottom:12px}
.assessment-history-alerts{margin-bottom:12px;padding:10px 12px;background:#fff7e8;border:1px solid #f8d9a0;border-radius:12px}
.assessment-history-title{margin-bottom:8px;color:#ad6800;font-size:13px;font-weight:700}
.assessment-history-tags{display:flex;gap:8px;flex-wrap:wrap}
.assessment-recommend-list{display:grid;gap:8px}
.assessment-recommend-item{display:flex;gap:8px;padding:4px 0;font-size:13px;color:#4a4a4a}
.snapshot-player{width:100%;max-height:420px;border-radius:16px;background:#000}
.snapshot-meta{margin-top:16px}
.snapshot-title{font-size:20px}
.snapshot-desc{margin-bottom:0}
@media (max-width:1200px){.report-hero{grid-template-columns:1fr}}
@media (max-width:768px){.report-hero__copy,.report-empty__panel,.report-paper__body,.report-section{padding-left:18px;padding-right:18px}.assessment-surface :deep(.el-card__header),.assessment-surface :deep(.el-card__body){padding-left:18px;padding-right:18px}.report-paper__header,.assess-header,.video-footer,.agent-step-top{flex-direction:column;align-items:flex-start}}
@media (max-width:640px){.report-hero__copy h1,.report-empty__panel h2{font-size:28px}.report-hero__actions,.video-footer__actions{flex-direction:column;align-items:stretch}}
</style>
