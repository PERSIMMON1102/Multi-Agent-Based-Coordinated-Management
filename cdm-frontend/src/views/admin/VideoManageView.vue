<template>
  <div class="admin-page">
    <section class="admin-hero">
      <div class="admin-hero__content">
        <span class="admin-hero__eyebrow">Video Library</span>
        <h1 class="admin-hero__title">视频资源管理</h1>
        <p class="admin-hero__desc">
          统一维护患者宣教视频、封面、讲稿和上下架状态，让内容检索、知识中心和患者观看体验保持一致。
        </p>
        <div class="admin-hero__chips">
          <el-tag effect="plain" round>{{ hasActiveFilters ? '当前筛选' : '全部资源' }} {{ videos.length }}</el-tag>
          <el-tag effect="plain" round type="success">上架 {{ activeCount }}</el-tag>
          <el-tag effect="plain" round type="warning">讲稿 {{ transcriptReadyCount }}</el-tag>
          <el-tag effect="plain" round type="info">向量 {{ vectorSummaryLabel }}</el-tag>
        </div>
      </div>

      <div class="admin-hero__actions">
        <el-button class="admin-hero__ghost" :loading="loading || uploadingVideo || uploadingCover" @click="refreshPage">
          刷新资源
        </el-button>
        <el-button type="primary" class="admin-hero__primary" @click="resetForm">
          {{ editingId ? '切换新建' : '清空表单' }}
        </el-button>
      </div>
    </section>

    <section class="admin-stat-grid admin-stat-grid--four">
      <article class="admin-stat-card admin-stat-card--blue">
        <span>当前结果</span>
        <strong>{{ videos.length }}</strong>
        <p>{{ hasActiveFilters ? '当前筛选条件命中的视频资源数量。' : '后台当前加载的全部视频资源数量。' }}</p>
      </article>
      <article class="admin-stat-card admin-stat-card--green">
        <span>上架视频</span>
        <strong>{{ activeCount }}</strong>
        <p>患者端和推荐服务当前可直接使用的视频资源。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--orange">
        <span>讲稿已建</span>
        <strong>{{ transcriptReadyCount }}</strong>
        <p>已配置讲稿的视频更利于语义检索和内容关联。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--red">
        <span>分类覆盖</span>
        <strong>{{ categoryCount }}</strong>
        <p>当前结果中已使用的视频分类数量。</p>
      </article>
    </section>

    <section class="admin-grid">
      <el-card shadow="never" class="admin-card">
        <template #header>
          <div class="admin-section-head admin-section-head--compact">
            <div>
              <span class="admin-section-head__eyebrow">Recommendation Feedback</span>
              <h2>推荐反馈概览</h2>
              <p>基于真实观看进度与完成率，反向校准宣教视频的推荐优先级。</p>
            </div>
          </div>
        </template>

        <div class="feedback-metrics">
          <article class="feedback-metric">
            <span>总观看会话</span>
            <strong>{{ feedbackSummary.totalWatchSessions || 0 }}</strong>
          </article>
          <article class="feedback-metric">
            <span>完成会话</span>
            <strong>{{ feedbackSummary.completedSessions || 0 }}</strong>
          </article>
          <article class="feedback-metric">
            <span>有反馈视频</span>
            <strong>{{ feedbackSummary.watchedVideos || 0 }}</strong>
          </article>
          <article class="feedback-metric">
            <span>平均进度</span>
            <strong>{{ formatPercent(feedbackSummary.avgProgress) }}</strong>
          </article>
        </div>

        <el-table :data="feedbackTopVideos" size="small" border>
          <el-table-column label="视频标题" min-width="220">
            <template #default="{ row }">
              <div class="feedback-title">{{ row.title }}</div>
              <div class="feedback-subline">{{ diseaseLabel(row.diseaseType) }}<span v-if="row.category"> / {{ row.category }}</span></div>
            </template>
          </el-table-column>
          <el-table-column prop="watchCount" label="观看次数" width="100" />
          <el-table-column prop="uniquePatientCount" label="覆盖患者" width="100" />
          <el-table-column label="完成率" width="110">
            <template #default="{ row }">{{ formatPercent(row.completionRate) }}</template>
          </el-table-column>
          <el-table-column label="平均进度" width="110">
            <template #default="{ row }">{{ formatPercent(row.avgProgress) }}</template>
          </el-table-column>
          <el-table-column prop="recommendationHint" label="优化建议" min-width="260" />
        </el-table>
      </el-card>
    </section>

    <section class="admin-grid admin-grid--editor">
      <el-card shadow="never" class="admin-card admin-sticky-card">
        <template #header>
          <div class="admin-section-head admin-section-head--compact">
            <div>
              <span class="admin-section-head__eyebrow">Editor</span>
              <h2>{{ editingId ? '编辑视频' : '上传视频' }}</h2>
              <p>维护患者可观看的健康教育视频资源，并同步讲稿与基础元信息。</p>
            </div>
            <div class="admin-inline-actions">
              <el-button v-if="editingId" text @click="resetForm">切换新建</el-button>
            </div>
          </div>
        </template>

        <el-form label-position="top">
          <el-form-item label="标题">
            <div class="admin-field-note admin-field-note--required">必填，用于确定视频名称，也是推荐解释的重要入口。</div>
            <el-input v-model="form.title" placeholder="例如：高血压日常饮食指导" />
          </el-form-item>

          <el-form-item label="核心病种">
            <div class="admin-field-note admin-field-note--required">必填，用于确定视频对应的核心病种。</div>
            <el-select v-model="form.diseaseType" placeholder="请选择病种" style="width: 100%">
              <el-option v-for="item in diseaseOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>

          <el-form-item label="内容标签">
            <div class="admin-field-note admin-field-note--recommended">推荐填写，帮助系统抽取主题，并提升推荐命中质量。</div>
            <el-select
              v-model="form.tags"
              multiple
              filterable
              allow-create
              default-first-option
              collapse-tags
              collapse-tags-tooltip
              placeholder="例如：居家管理、饮食、复诊提醒"
              style="width: 100%"
            >
              <el-option v-for="item in tagOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>

          <el-form-item label="分类">
            <div class="admin-field-note admin-field-note--recommended">推荐填写，可直接增强饮食、运动、用药、复诊等主题识别。</div>
            <el-select v-model="form.category" placeholder="请选择分类" allow-create filterable default-first-option style="width: 100%">
              <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>

          <el-form-item label="视频文件">
            <div class="admin-upload-line">
              <el-button :loading="uploadingVideo" @click="videoInput?.click()">上传视频</el-button>
              <span class="admin-upload-value">{{ form.videoUrl || '尚未上传视频文件' }}</span>
            </div>
            <el-progress
              v-if="uploadingVideo || videoUploadPercent > 0"
              :percentage="videoUploadPercent"
              :status="uploadingVideo ? '' : 'success'"
              style="margin-top: 10px"
            />
            <input ref="videoInput" type="file" accept=".mp4,.webm,.ogg,.mov" class="hidden-input" @change="handleAssetChange($event, 'video')" />
          </el-form-item>

          <el-form-item label="封面图片">
            <div class="admin-upload-line">
              <el-button :loading="uploadingCover" @click="coverInput?.click()">上传封面</el-button>
              <span class="admin-upload-value">{{ form.coverUrl || '可选' }}</span>
            </div>
            <el-progress
              v-if="uploadingCover || coverUploadPercent > 0"
              :percentage="coverUploadPercent"
              :status="uploadingCover ? '' : 'success'"
              style="margin-top: 10px"
            />
            <input ref="coverInput" type="file" accept=".jpg,.jpeg,.png,.webp" class="hidden-input" @change="handleAssetChange($event, 'cover')" />
          </el-form-item>

          <el-form-item label="视频时长（秒）">
            <el-input-number v-model="form.durationSeconds" :min="0" :step="30" style="width: 100%" />
          </el-form-item>

          <el-form-item label="讲稿 / 字幕">
            <div class="admin-field-note admin-field-note--recommended">推荐填写，讲稿越完整，后续语义检索和内容关联越准确。</div>
            <div class="transcript-toolbar">
              <el-button @click="transcriptFileInput?.click()">导入文本文件</el-button>
              <el-tag size="small" type="info" effect="plain">支持 txt / 字幕转写内容</el-tag>
              <el-tag size="small" :type="form.hasTranscript ? 'success' : 'info'" effect="plain">
                {{ form.hasTranscript ? `已建讲稿（${transcriptChunkCount} 块）` : '未配置讲稿' }}
              </el-tag>
              <el-tag v-if="transcriptIndexStatus" size="small" :type="indexStatusType(transcriptIndexStatus)" effect="plain">
                {{ transcriptIndexStatus }}
              </el-tag>
            </div>
            <el-input
              v-model="transcriptContent"
              type="textarea"
              :rows="6"
              placeholder="可粘贴视频讲稿、字幕或课程摘要。保存后会自动切分并建立检索索引。"
              style="margin-top: 10px"
            />
            <div class="transcript-tip">
              已索引片段 {{ transcriptIndexedCount }} / {{ transcriptChunkCount }}
            </div>
            <div v-if="vectorStatus" class="transcript-status">
              <el-tag size="small" :type="statusTagType(vectorStatus.available)" effect="plain">
                {{ vectorSummaryLabel }}
              </el-tag>
              <span>模型 {{ vectorStatus.embedding?.model || '--' }}</span>
              <span>Qdrant {{ vectorStatus.qdrantReachable ? '已连接' : '未连接' }}</span>
            </div>
            <div v-if="vectorStatus?.diagnosis" class="transcript-diagnosis">
              诊断：{{ vectorStatus.diagnosis }}
            </div>
            <div v-if="vectorStatus?.recommendation" class="transcript-recommendation">
              建议：{{ vectorStatus.recommendation }}
            </div>
            <div v-if="vectorError" class="transcript-error">
              {{ vectorError }}
            </div>
            <input
              ref="transcriptFileInput"
              type="file"
              accept=".txt,.md,.srt,.vtt"
              class="hidden-input"
              @change="handleTranscriptFileChange"
            />
          </el-form-item>

          <el-form-item label="排序值">
            <el-input-number v-model="form.sortOrder" :min="0" :step="1" style="width: 100%" />
          </el-form-item>

          <el-form-item label="简介">
            <div class="admin-field-note admin-field-note--recommended">推荐填写，便于主题抽取和管理员后续维护。</div>
            <el-input v-model="form.description" type="textarea" :rows="4" placeholder="简要说明视频适合的患者场景和观看重点" />
          </el-form-item>

          <el-form-item>
            <el-switch v-model="form.active" active-text="上架" inactive-text="下架" />
          </el-form-item>

          <el-form-item>
            <div class="form-actions">
              <el-button @click="resetForm">重置</el-button>
              <el-button type="primary" :loading="saving" @click="saveVideo">
                {{ editingId ? '保存修改' : '创建视频' }}
              </el-button>
            </div>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card shadow="never" class="admin-card admin-card--table">
        <template #header>
          <div class="admin-section-head">
            <div>
              <span class="admin-section-head__eyebrow">Resource List</span>
              <h2>视频资源列表</h2>
              <p>支持筛选、上下架、预览和删除，所有操作都围绕同一套后台资源视图展开。</p>
            </div>
            <div class="admin-section-head__meta">
              <el-tag type="primary" effect="plain">{{ hasActiveFilters ? '当前筛选' : '全部资源' }} {{ videos.length }}</el-tag>
              <el-tag type="success" effect="plain">上架 {{ activeCount }}</el-tag>
              <el-tag type="warning" effect="plain">讲稿 {{ transcriptReadyCount }}</el-tag>
            </div>
          </div>
        </template>

        <div class="admin-toolbar video-filter-bar">
          <el-input
            v-model="filters.keyword"
            placeholder="搜索标题/分类/简介"
            clearable
            class="video-filter-bar__keyword"
            @keyup.enter="loadVideos"
          />
          <el-select v-model="filters.active" placeholder="状态" clearable class="video-filter-bar__status">
            <el-option label="上架" :value="true" />
            <el-option label="下架" :value="false" />
          </el-select>
          <el-select v-model="filters.diseaseType" placeholder="病种" clearable class="video-filter-bar__disease">
            <el-option v-for="item in diseaseOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <div class="admin-inline-actions">
            <el-button type="primary" plain @click="loadVideos">筛选</el-button>
            <el-button @click="resetFilters">重置</el-button>
          </div>
        </div>

        <el-table class="admin-data-table" :data="videos" v-loading="loading" border>
          <el-table-column label="视频" min-width="240">
            <template #default="{ row }">
              <div class="title-cell">
                <img v-if="row.coverUrl" :src="row.coverUrl" class="cover-thumb" :alt="row.title">
                <div v-else class="cover-thumb cover-placeholder">视频</div>
                <div>
                  <div class="row-title">{{ row.title }}</div>
                  <div class="row-subline">{{ row.description || '暂无简介' }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="病种 / 分类" width="180">
            <template #default="{ row }">
              <div class="tags-line">
                <el-tag size="small" type="danger" effect="plain">{{ diseaseLabel(row.diseaseType) }}</el-tag>
                <el-tag v-if="row.category" size="small" type="success" effect="plain">{{ row.category }}</el-tag>
                <el-tag v-for="tag in row.tags || []" :key="`${row.id}-${tag}`" size="small" type="info" effect="plain">{{ tag }}</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="讲稿" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.hasTranscript ? 'success' : 'info'" effect="plain">
                {{ row.hasTranscript ? '已配置' : '未配置' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="时长" width="110">
            <template #default="{ row }">{{ formatDuration(row.durationSeconds) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-switch :model-value="row.active" @change="toggleStatus(row, $event)" />
            </template>
          </el-table-column>
          <el-table-column prop="sortOrder" label="排序" width="80" />
          <el-table-column label="更新时间" width="170">
            <template #default="{ row }">{{ formatTime(row.updatedAt || row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" @click="editVideo(row)">编辑</el-button>
              <el-button text type="primary" @click="previewVideo(row)">预览</el-button>
              <el-button text type="danger" @click="removeVideo(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="!loading && videos.length === 0" class="admin-empty-block">
          <el-empty description="暂无视频资源" />
        </div>
      </el-card>
    </section>

    <el-dialog v-model="previewVisible" width="720px" title="视频预览" destroy-on-close append-to-body align-center>
      <div v-if="previewItem">
        <video class="preview-player" :src="previewItem.videoUrl" :poster="previewItem.coverUrl || undefined" controls preload="metadata" />
        <div class="preview-meta">
          <div class="preview-title">{{ previewItem.title }}</div>
          <div class="tags-line">
            <el-tag size="small" type="danger" effect="plain">{{ diseaseLabel(previewItem.diseaseType) }}</el-tag>
            <el-tag v-if="previewItem.category" size="small" type="success" effect="plain">{{ previewItem.category }}</el-tag>
            <el-tag v-for="tag in previewItem.tags || []" :key="`preview-${tag}`" size="small" type="info" effect="plain">{{ tag }}</el-tag>
            <el-tag size="small" type="info" effect="plain">{{ formatDuration(previewItem.durationSeconds) }}</el-tag>
          </div>
          <p class="preview-desc">{{ previewItem.description || '暂无简介' }}</p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from '../../utils/element-plus-service'
import dayjs from 'dayjs'
import { knowledgeApi, knowledgeGraphApi, videoApi } from '../../api'

const diseaseOptions = [
  { label: '通用', value: 'GENERAL' },
  { label: '2型糖尿病', value: 'DIABETES' },
  { label: '高血压', value: 'HYPERTENSION' },
  { label: '慢阻肺', value: 'COPD' },
  { label: '慢性肾病', value: 'CKD' },
  { label: '冠心病', value: 'CHD' },
  { label: '脑卒中', value: 'STROKE' }
]
const diseaseMap = Object.fromEntries(diseaseOptions.map(item => [item.value, item.label]))
const categoryOptions = ['饮食', '运动', '用药', '康复', '复诊提醒', '生活方式']
const tagOptions = ['居家管理', '饮食', '运动', '用药', '复诊', '并发症预防', '监测指标', '宣教', '康复', '心理支持']

const loading = ref(false)
const saving = ref(false)
const uploadingVideo = ref(false)
const uploadingCover = ref(false)
const videoUploadPercent = ref(0)
const coverUploadPercent = ref(0)
const editingId = ref(null)
const videos = ref([])
const previewVisible = ref(false)
const previewItem = ref(null)
const videoInput = ref(null)
const coverInput = ref(null)
const transcriptFileInput = ref(null)
const transcriptContent = ref('')
const transcriptChunkCount = ref(0)
const transcriptIndexedCount = ref(0)
const transcriptIndexStatus = ref('')
const vectorStatus = ref(null)
const feedbackSummary = ref({})

const filters = reactive({
  keyword: '',
  active: '',
  diseaseType: ''
})

const form = reactive(createEmptyForm())

const activeCount = computed(() => videos.value.filter(item => item.active).length)
const transcriptReadyCount = computed(() => videos.value.filter(item => item.hasTranscript).length)
const categoryCount = computed(() => new Set(videos.value.map(item => (item.category || '').trim()).filter(Boolean)).size)
const hasActiveFilters = computed(() => Boolean(filters.keyword || filters.diseaseType || filters.active !== ''))
const vectorSummaryLabel = computed(() => {
  if (!vectorStatus.value) return '未检测'
  if (vectorStatus.value.available) return '可用'
  return vectorStatus.value.enabled === false ? '未启用' : '异常'
})
const feedbackTopVideos = computed(() => Array.isArray(feedbackSummary.value?.topVideos) ? feedbackSummary.value.topVideos : [])

onMounted(() => {
  refreshPage()
})

function createEmptyForm() {
  return {
    title: '',
    diseaseType: 'GENERAL',
    tags: [],
    category: '',
    description: '',
    coverUrl: '',
    videoUrl: '',
    objectKey: '',
    coverObjectKey: '',
    sourceType: '',
    durationSeconds: 0,
    sortOrder: 0,
    active: true,
    hasTranscript: false
  }
}

function diseaseLabel(value) {
  return diseaseMap[value] || value || '通用'
}

function formatDuration(value) {
  const seconds = Number(value)
  if (!seconds) return '未标注'
  const minutes = Math.floor(seconds / 60)
  const remain = seconds % 60
  return `${minutes}分${remain.toString().padStart(2, '0')}秒`
}

function formatTime(value) {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '--'
}

function formatPercent(value) {
  const num = Number(value)
  if (!Number.isFinite(num)) return '0%'
  return `${Math.round(num)}%`
}

const vectorError = computed(() => vectorStatus.value?.embedding?.lastError || vectorStatus.value?.lastError || '')

async function refreshPage() {
  await Promise.all([loadVectorStatus(), loadVideos(), loadFeedbackSummary()])
}

async function loadVideos() {
  loading.value = true
  try {
    videos.value = await videoApi.listAdmin({
      keyword: filters.keyword || undefined,
      diseaseType: filters.diseaseType || undefined,
      active: filters.active === '' ? undefined : filters.active
    })
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '加载视频列表失败')
  } finally {
    loading.value = false
  }
}

function statusTagType(ok) {
  return ok ? 'success' : 'danger'
}

function indexStatusType(status) {
  if (status === 'INDEXED') return 'success'
  if (status === 'PARTIAL') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

async function loadVectorStatus() {
  try {
    vectorStatus.value = await knowledgeApi.getStatus()
  } catch (e) {
    vectorStatus.value = null
  }
}

async function loadFeedbackSummary() {
  try {
    feedbackSummary.value = await videoApi.getRecommendationFeedback()
  } catch (e) {
    feedbackSummary.value = {}
  }
}

function resetFilters() {
  filters.keyword = ''
  filters.active = ''
  filters.diseaseType = ''
  loadVideos()
}

function resetForm() {
  editingId.value = null
  Object.assign(form, createEmptyForm())
  transcriptContent.value = ''
  transcriptChunkCount.value = 0
  transcriptIndexedCount.value = 0
  transcriptIndexStatus.value = ''
}

async function editVideo(row) {
  editingId.value = row.id
  Object.assign(form, {
    title: row.title || '',
    diseaseType: row.diseaseType || 'GENERAL',
    tags: Array.isArray(row.tags) ? [...row.tags] : [],
    category: row.category || '',
    description: row.description || '',
    coverUrl: row.coverUrl || '',
    videoUrl: row.videoUrl || '',
    objectKey: row.objectKey || '',
    coverObjectKey: row.coverObjectKey || '',
    sourceType: row.sourceType || '',
    durationSeconds: Number(row.durationSeconds) || 0,
    sortOrder: Number(row.sortOrder) || 0,
    active: row.active !== false,
    hasTranscript: row.hasTranscript === true
  })
  try {
    const transcript = await videoApi.getTranscript(row.id)
    transcriptContent.value = transcript.content || ''
    transcriptChunkCount.value = Number(transcript.chunkCount) || 0
    transcriptIndexedCount.value = transcript.chunks?.filter(item => item.embeddingStatus === 'INDEXED').length || 0
    transcriptIndexStatus.value = transcript.indexStatus || ''
    form.hasTranscript = transcript.hasTranscript === true
  } catch (e) {
    transcriptContent.value = ''
    transcriptChunkCount.value = 0
    transcriptIndexedCount.value = 0
    transcriptIndexStatus.value = ''
    form.hasTranscript = row.hasTranscript === true
  }
}

async function handleAssetChange(event, kind) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  const target = kind === 'video' ? uploadingVideo : uploadingCover
  const progressTarget = kind === 'video' ? videoUploadPercent : coverUploadPercent
  target.value = true
  progressTarget.value = 0
  try {
    const [result, detectedDuration] = await Promise.all([
      videoApi.uploadFile(file, kind, {
        onUploadProgress: (progressEvent) => {
          if (!progressEvent.total) return
          progressTarget.value = Math.min(100, Math.round((progressEvent.loaded / progressEvent.total) * 100))
        }
      }),
      kind === 'video' ? extractVideoDuration(file) : Promise.resolve(null)
    ])
    if (kind === 'video') {
      form.videoUrl = result.url
      form.objectKey = result.objectKey
      form.sourceType = result.sourceType || form.sourceType
      if (detectedDuration != null) {
        form.durationSeconds = detectedDuration
      }
    } else {
      form.coverUrl = result.url
      form.coverObjectKey = result.objectKey
    }
    progressTarget.value = 100
    ElMessage.success(kind === 'video' ? '视频上传成功' : '封面上传成功')
  } catch (e) {
    progressTarget.value = 0
    const msg = e.code === 'ECONNABORTED'
      ? '上传超时，请检查网络或继续调大上传超时时间'
      : (e.response?.data?.message || '上传失败')
    ElMessage.error(msg)
  } finally {
    target.value = false
  }
}

function extractVideoDuration(file) {
  return new Promise((resolve) => {
    const objectUrl = URL.createObjectURL(file)
    const video = document.createElement('video')
    let settled = false

    const cleanup = () => {
      if (settled) return
      settled = true
      video.removeAttribute('src')
      video.load()
      URL.revokeObjectURL(objectUrl)
    }

    video.preload = 'metadata'
    video.onloadedmetadata = () => {
      const seconds = Number.isFinite(video.duration) && video.duration > 0
        ? Math.round(video.duration)
        : null
      cleanup()
      resolve(seconds)
    }
    video.onerror = () => {
      cleanup()
      resolve(null)
    }
    video.src = objectUrl
  })
}

function handleTranscriptFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    transcriptContent.value = typeof reader.result === 'string' ? reader.result : ''
  }
  reader.onerror = () => {
    ElMessage.error('读取讲稿文件失败')
  }
  reader.readAsText(file, 'utf-8')
}

async function saveVideo() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入视频标题')
    return
  }
  if (!form.videoUrl.trim()) {
    ElMessage.warning('请先上传视频文件')
    return
  }

  saving.value = true
  const wasEditing = !!editingId.value
  const payload = {
    title: form.title,
    diseaseType: form.diseaseType,
    category: form.category,
    tags: form.tags,
    description: form.description,
    coverUrl: form.coverUrl,
    videoUrl: form.videoUrl,
    objectKey: form.objectKey,
    coverObjectKey: form.coverObjectKey,
    sourceType: form.sourceType,
    durationSeconds: form.durationSeconds,
    sortOrder: form.sortOrder,
    active: form.active
  }

  try {
    let savedVideo
    let savedTranscript = false
    if (editingId.value) {
      savedVideo = await videoApi.update(editingId.value, payload)
      ElMessage.success('视频已更新')
    } else {
      savedVideo = await videoApi.create(payload)
      ElMessage.success('视频已创建')
    }

    if (savedVideo?.id && (editingId.value || transcriptContent.value.trim())) {
      const transcriptResult = await videoApi.saveTranscript(savedVideo.id, {
        content: transcriptContent.value,
        sourceType: 'MANUAL'
      })
      form.hasTranscript = transcriptResult.hasTranscript === true
      transcriptChunkCount.value = Number(transcriptResult.chunkCount) || 0
      transcriptIndexedCount.value = Number(transcriptResult.indexedChunkCount) || 0
      transcriptIndexStatus.value = transcriptResult.indexStatus || ''
      savedTranscript = true
      await loadVectorStatus()
    }
    resetForm()
    await loadVideos()
    await promptRebuildAfterVideoChange({
      created: !wasEditing && !!savedVideo?.id,
      updated: wasEditing,
      transcriptUpdated: savedTranscript
    })
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function promptRebuildAfterVideoChange({ created, updated, transcriptUpdated }) {
  const actions = []
  if (created) actions.push('新增视频')
  if (updated) actions.push('更新视频')
  if (transcriptUpdated) actions.push('保存讲稿')
  if (!actions.length) return

  try {
    await ElMessageBox.confirm(
      `${actions.join('、')}已完成，是否立即更新知识中心？这样医生端内容展示和推荐会立刻使用最新视频内容。`,
      '更新知识中心',
      {
        confirmButtonText: '立即更新',
        cancelButtonText: '稍后手动更新',
        type: 'info'
      }
    )
  } catch {
    return
  }

  try {
    const result = await knowledgeGraphApi.rebuild()
    ElMessage.success(`知识中心更新完成：内容项 ${result.nodeCount || 0}，关联 ${result.edgeCount || 0}`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '知识中心更新失败')
  }
}

async function toggleStatus(row, nextValue) {
  try {
    await videoApi.update(row.id, { active: nextValue })
    row.active = nextValue
    ElMessage.success(nextValue ? '视频已上架' : '视频已下架')
  } catch (e) {
    row.active = !nextValue
    ElMessage.error(e.response?.data?.message || '状态更新失败')
  }
}

function previewVideo(row) {
  previewItem.value = row
  previewVisible.value = true
}

async function removeVideo(row) {
  try {
    await ElMessageBox.confirm(`确认删除视频《${row.title}》吗？`, '删除确认', {
      type: 'warning'
    })
    const result = await videoApi.delete(row.id)
    ElMessage.success(result.message || '视频已删除')
    if (editingId.value === row.id) {
      resetForm()
    }
    await loadVideos()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '删除失败')
    }
  }
}
</script>

<style scoped>
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  width: 100%;
}

.transcript-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.transcript-tip {
  margin-top: 8px;
  color: #64748b;
  font-size: 12px;
}

.transcript-status {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
  color: #475569;
  font-size: 12px;
}

.transcript-error {
  margin-top: 8px;
  color: #b91c1c;
  font-size: 12px;
  line-height: 1.6;
}

.transcript-diagnosis {
  margin-top: 8px;
  color: #0f172a;
  font-size: 12px;
  font-weight: 600;
}

.transcript-recommendation {
  margin-top: 6px;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
}

.hidden-input {
  display: none;
}

.video-filter-bar__keyword {
  width: 260px;
}

.video-filter-bar__status {
  width: 120px;
}

.video-filter-bar__disease {
  width: 160px;
}

.title-cell {
  display: flex;
  gap: 12px;
  align-items: center;
}

.cover-thumb {
  width: 92px;
  height: 56px;
  border-radius: 8px;
  object-fit: cover;
  background: #e5e7eb;
  flex-shrink: 0;
}

.cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
}

.row-title {
  font-size: 14px;
  font-weight: 600;
  color: #111827;
}

.row-subline {
  margin-top: 6px;
  color: #6b7280;
  font-size: 12px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.feedback-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}

.feedback-metric {
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 16px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(241, 245, 249, 0.92));
  padding: 16px 18px;
}

.feedback-metric span {
  display: block;
  color: #64748b;
  font-size: 12px;
}

.feedback-metric strong {
  display: block;
  margin-top: 8px;
  color: #0f172a;
  font-size: 26px;
  font-weight: 700;
}

.feedback-title {
  color: #111827;
  font-size: 14px;
  font-weight: 600;
}

.feedback-subline {
  margin-top: 4px;
  color: #64748b;
  font-size: 12px;
}

.tags-line {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.preview-player {
  width: 100%;
  max-height: 420px;
  border-radius: 12px;
  background: #000;
}

.preview-meta {
  margin-top: 16px;
}

.preview-title {
  font-size: 18px;
  font-weight: 700;
  color: #111827;
}

.preview-desc {
  margin: 12px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #475569;
}

@media (max-width: 720px) {
  .video-filter-bar__keyword,
  .video-filter-bar__status,
  .video-filter-bar__disease {
    width: 100%;
  }

  .feedback-metrics {
    grid-template-columns: 1fr 1fr;
  }

  .title-cell {
    align-items: flex-start;
  }
}
</style>
