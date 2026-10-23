<template>
  <el-drawer
    :model-value="modelValue"
    title="引用详情"
    size="40%"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
    @closed="handleClosed"
  >
    <div v-if="loading" class="reference-detail-loading">
      正在加载引用详情...
    </div>
    <div v-else-if="detail" class="reference-detail">
      <div class="reference-detail-head">
        <div>
          <div class="reference-detail-title">{{ detail.title || '引用详情' }}</div>
          <div v-if="referenceSignals.length > 0" class="reference-detail-signals">
            <el-tag
              v-for="signal in referenceSignals"
              :key="signal.label"
              size="small"
              :type="signal.type"
              effect="plain"
            >
              {{ signal.label }}
            </el-tag>
          </div>
        </div>
        <el-tag size="small" type="success" effect="plain">
          {{ detail.sourceLabel || formatSourceType(detail.sourceType) }}
        </el-tag>
      </div>
      <div v-if="detail.subtitle" class="reference-detail-subtitle">{{ detail.subtitle }}</div>
      <div v-if="referenceHint" class="reference-detail-hint">{{ referenceHint }}</div>
      <div class="reference-detail-meta">
        <span v-if="detail.section">{{ detail.section }}</span>
        <span v-if="detail.source">{{ detail.source }}</span>
        <span v-if="detail.publishedAt">{{ formatDate(detail.publishedAt) }}</span>
        <span v-if="detail.versionTag">版本 {{ detail.versionTag }}</span>
      </div>
      <RichTextBlock class="reference-detail-content" :content="detail.content" />
      <div v-if="previousReference || nextReference" class="reference-detail-nav">
        <button
          v-if="previousReference"
          type="button"
          class="reference-nav-card"
          @click="loadDetail(previousReference)"
        >
          <div class="reference-nav-label">上一条相关片段</div>
          <div class="reference-nav-title">{{ previousReference.label }}</div>
          <div class="reference-nav-snippet">{{ previousReference.snippet }}</div>
        </button>
        <button
          v-if="nextReference"
          type="button"
          class="reference-nav-card"
          @click="loadDetail(nextReference)"
        >
          <div class="reference-nav-label">下一条相关片段</div>
          <div class="reference-nav-title">{{ nextReference.label }}</div>
          <div class="reference-nav-snippet">{{ nextReference.snippet }}</div>
        </button>
      </div>
      <div v-if="primaryActionLabel || canOpenFile" class="reference-detail-actions">
        <el-button v-if="primaryActionLabel" type="primary" @click="openReferenceOrigin">
          {{ primaryActionLabel }}
        </el-button>
        <el-button v-if="canOpenFile" @click="openReferenceFile">
          打开视频文件
        </el-button>
      </div>
    </div>
    <el-empty v-else description="暂无可展示的引用详情" />
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from '../utils/element-plus-service'
import dayjs from 'dayjs'
import { referenceApi } from '../api'
import { useUserStore } from '../stores/user'
import RichTextBlock from './RichTextBlock.vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  reference: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue'])

const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const detail = ref(null)
let requestToken = 0

const previousReference = computed(() => detail.value?.navigation?.previous || null)
const nextReference = computed(() => detail.value?.navigation?.next || null)
const referenceScore = computed(() => {
  const score = Number(props.reference?.score)
  return Number.isNaN(score) ? null : score
})
const referenceSignals = computed(() => {
  const signals = []
  const score = referenceScore.value
  if (score != null) {
    signals.push({
      label: relevanceLabel(score),
      type: relevanceType(score)
    })
    signals.push({
      label: `匹配度 ${formatScore(score)}`,
      type: 'info'
    })
  }
  if (props.reference?.diseaseKey) {
    signals.push({
      label: diseaseLabel(props.reference.diseaseKey),
      type: 'warning'
    })
  }
  return signals
})
const referenceHint = computed(() => {
  const hints = []
  if (props.reference?.diseaseKey) {
    hints.push(`命中病种：${diseaseLabel(props.reference.diseaseKey)}`)
  }
  if (detail.value?.section) {
    hints.push(`命中章节：${detail.value.section}`)
  }
  return hints.join(' · ')
})
const primaryActionLabel = computed(() => {
  if (!detail.value) return ''
  if (detail.value.sourceType === 'VIDEO_TRANSCRIPT') {
    if (userStore.isPatient && detail.value.videoId) return '前往视频中心'
    if (userStore.isAdmin) return '打开视频管理'
  }
  if (detail.value.sourceType === 'KNOWLEDGE' && userStore.isAdmin) {
    return '打开知识库管理'
  }
  return ''
})
const canOpenFile = computed(() => {
  return !!(detail.value?.sourceType === 'VIDEO_TRANSCRIPT' && detail.value?.videoUrl)
})

watch(
  () => [props.modelValue, props.reference],
  async ([visible, reference]) => {
    if (!visible || !reference) {
      return
    }
    await loadDetail(reference)
  },
  { deep: true }
)

async function loadDetail(reference) {
  if (!reference?.sourceType || !reference?.refId) {
    detail.value = null
    loading.value = false
    ElMessage.warning('该引用暂不支持查看详情')
    emit('update:modelValue', false)
    return
  }

  loading.value = true
  const token = ++requestToken

  try {
    const result = await referenceApi.getDetail({
      sourceType: reference.sourceType,
      refId: reference.refId,
      videoId: reference.videoId || undefined
    })
    if (token !== requestToken) {
      return
    }
    detail.value = result
  } catch (e) {
    if (token !== requestToken) {
      return
    }
    detail.value = null
    emit('update:modelValue', false)
    ElMessage.error(e.response?.data?.message || '引用详情加载失败')
  } finally {
    if (token === requestToken) {
      loading.value = false
    }
  }
}

function handleClosed() {
  detail.value = null
  loading.value = false
}

function openReferenceOrigin() {
  if (!detail.value) return

  if (detail.value.sourceType === 'VIDEO_TRANSCRIPT') {
    if (userStore.isPatient && detail.value.videoId) {
      router.push({ path: '/patient/videos', query: { video: detail.value.videoId } })
      emit('update:modelValue', false)
      return
    }
    if (userStore.isAdmin) {
      router.push('/admin/videos')
      emit('update:modelValue', false)
      return
    }
  }

  if (detail.value.sourceType === 'KNOWLEDGE' && userStore.isAdmin) {
    router.push('/admin/knowledge')
    emit('update:modelValue', false)
  }
}

function openReferenceFile() {
  if (!detail.value?.videoUrl) {
    ElMessage.warning('暂无可打开的视频文件')
    return
  }
  window.open(detail.value.videoUrl, '_blank', 'noopener')
}

function formatSourceType(type) {
  const sourceTypeMap = {
    KNOWLEDGE: '知识库',
    GUIDELINE: '指南',
    EDUCATION: '宣教',
    VIDEO_TRANSCRIPT: '视频讲稿'
  }
  return sourceTypeMap[type] || type || ''
}

function formatDate(value) {
  return value ? dayjs(value).format('YYYY-MM-DD') : ''
}

function formatScore(value) {
  const score = Number(value)
  if (Number.isNaN(score)) return '--'
  return score <= 1 ? `${Math.round(score * 100)}%` : score.toFixed(2)
}

function relevanceLabel(value) {
  const score = Number(value)
  if (Number.isNaN(score)) return '已匹配'
  if (score >= 0.85) return '高相关'
  if (score >= 0.65) return '中相关'
  return '低相关'
}

function relevanceType(value) {
  const score = Number(value)
  if (Number.isNaN(score)) return 'info'
  if (score >= 0.85) return 'success'
  if (score >= 0.65) return 'warning'
  return 'info'
}

function diseaseLabel(value) {
  return {
    GENERAL: '通用',
    DIABETES: '2型糖尿病',
    HYPERTENSION: '高血压',
    COPD: '慢阻肺',
    CKD: '慢性肾病',
    CHD: '冠心病',
    STROKE: '脑卒中'
  }[value] || value || '相关病种'
}

</script>

<style scoped>
.reference-detail-loading {
  color: #909399;
  font-size: 14px;
}

.reference-detail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.reference-detail-title {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
}

.reference-detail-signals {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.reference-detail-subtitle {
  margin-top: 8px;
  color: #606266;
  font-size: 14px;
}

.reference-detail-hint {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  color: #4b5563;
  font-size: 13px;
  line-height: 1.6;
}

.reference-detail-meta {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: #909399;
  font-size: 12px;
}

.reference-detail-content {
  margin-top: 16px;
  padding: 16px;
  border-radius: 12px;
  background: #f8fafc;
  color: #303133;
  line-height: 1.8;
  font-size: 14px;
}

.reference-detail-nav {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.reference-nav-card {
  padding: 14px;
  border-radius: 12px;
  border: 1px solid #dbeafe;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, transform 0.2s ease;
}

.reference-nav-card:hover {
  border-color: #93c5fd;
  box-shadow: 0 10px 24px rgba(59, 130, 246, 0.12);
  transform: translateY(-1px);
}

.reference-nav-label {
  font-size: 12px;
  color: #2563eb;
}

.reference-nav-title {
  margin-top: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.reference-nav-snippet {
  margin-top: 8px;
  font-size: 12px;
  line-height: 1.6;
  color: #6b7280;
}

.reference-detail-actions {
  margin-top: 16px;
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
