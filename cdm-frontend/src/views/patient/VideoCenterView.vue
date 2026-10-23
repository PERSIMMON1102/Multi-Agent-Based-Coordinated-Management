<template>
  <div class="video-center">
    <el-card class="hero-card" shadow="never">
      <div class="hero-grid">
        <div>
          <div class="hero-kicker">Health Video Hub</div>
          <h2 class="hero-title">慢病健康视频中心</h2>
          <p class="hero-desc">查看健康课程，并根据您的风险评估快速定位更适合当前病情的内容。</p>
          <div class="hero-tags">
            <el-tag type="primary" effect="dark">视频总数 {{ videos.length }}</el-tag>
            <el-tag type="danger" effect="plain">推荐 {{ recommendedVideos.length }}</el-tag>
          </div>
        </div>
        <div class="hero-filters">
          <el-select v-model="selectedDisease" clearable placeholder="按病种筛选">
            <el-option v-for="item in diseaseOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="selectedCategory" clearable placeholder="按分类筛选">
            <el-option v-for="item in categoryOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </div>
      </div>
    </el-card>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="16">
        <el-card class="player-card" shadow="never">
          <template #header>
            <div class="section-head">
              <span>当前播放</span>
              <el-tag v-if="currentVideo" type="primary" effect="plain">{{ diseaseLabel(currentVideo.diseaseType) }}</el-tag>
            </div>
          </template>

          <template v-if="currentVideo">
            <video
              ref="videoPlayerRef"
              class="player"
              :src="currentVideo.videoUrl"
              :poster="currentVideo.coverUrl || undefined"
              controls
              preload="metadata"
              @play="handleVideoPlay"
              @pause="handleVideoPause"
              @ended="handleVideoEnded"
              @loadedmetadata="handleLoadedMetadata"
            />
            <div class="player-meta">
              <div class="player-title">{{ currentVideo.title }}</div>
              <div class="player-tags">
                <el-tag size="small" type="danger" effect="plain">{{ diseaseLabel(currentVideo.diseaseType) }}</el-tag>
                <el-tag v-if="currentVideo.category" size="small" type="success" effect="plain">{{ currentVideo.category }}</el-tag>
                <el-tag size="small" type="info" effect="plain">{{ formatDuration(currentVideo.durationSeconds) }}</el-tag>
              </div>
              <div v-if="currentVideo.recommendReason" class="player-reason-card">
                <div class="player-reason-card__head">
                  <span>推荐说明</span>
                  <el-button text type="primary" @click="openRecommendDetail(currentVideo)">查看说明</el-button>
                </div>
                <p>{{ currentVideo.recommendReason }}</p>
              </div>
              <p class="player-desc">{{ currentVideo.description || '暂无简介' }}</p>
            </div>
          </template>

          <el-empty v-else description="暂无可播放视频" />
        </el-card>
      </el-col>

      <el-col :span="8">
        <el-card shadow="never">
          <template #header>
            <span>为您推荐</span>
          </template>
          <div v-if="recommendedVideos.length > 0" class="recommend-list">
            <button
              v-for="item in recommendedVideos"
              :key="item.id"
              type="button"
              class="recommend-item"
              @click="selectVideo(item.id)"
            >
              <div class="recommend-title">{{ item.title }}</div>
              <div class="recommend-meta">
                <el-tag size="small" type="danger" effect="plain">{{ diseaseLabel(item.diseaseType) }}</el-tag>
                <el-tag v-if="item.category" size="small" type="success" effect="plain">{{ item.category }}</el-tag>
              </div>
              <div class="recommend-reason">{{ item.recommendReason || '根据当前评估推荐' }}</div>
              <div class="recommend-actions">
                <el-button text type="primary" @click.stop="openRecommendDetail(item)">查看说明</el-button>
              </div>
            </button>
          </div>
          <el-empty v-else description="暂无专属推荐" />
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div class="section-head">
          <span>全部视频</span>
          <el-tag type="info" effect="plain">共 {{ filteredVideos.length }} 条</el-tag>
        </div>
      </template>

      <div v-if="filteredVideos.length > 0" class="video-grid">
        <button
          v-for="item in filteredVideos"
          :key="item.id"
          type="button"
          :class="['video-card', { active: currentVideo?.id === item.id }]"
          @click="selectVideo(item.id)"
        >
          <div class="card-cover">
            <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title">
            <div v-else class="card-cover-fallback">视频</div>
          </div>
          <div class="card-body">
            <div class="card-title">{{ item.title }}</div>
            <div class="card-tags">
              <el-tag size="small" type="danger" effect="plain">{{ diseaseLabel(item.diseaseType) }}</el-tag>
              <el-tag v-if="item.category" size="small" type="success" effect="plain">{{ item.category }}</el-tag>
            </div>
            <div class="card-desc">{{ item.description || '暂无简介' }}</div>
            <div class="card-footer">
              <span>{{ formatDuration(item.durationSeconds) }}</span>
              <span v-if="isRecommended(item.id)" class="recommend-flag">推荐</span>
            </div>
          </div>
        </button>
      </div>

      <el-empty v-else description="当前筛选条件下暂无视频" />
    </el-card>

    <el-drawer v-model="recommendDetailVisible" title="推荐说明" size="420px" append-to-body>
      <template v-if="recommendDetailVideo">
        <div class="recommend-detail">
          <div class="recommend-detail__title">{{ recommendDetailVideo.title }}</div>
          <div class="recommend-detail__tags">
            <el-tag size="small" type="danger" effect="plain">{{ diseaseLabel(recommendDetailVideo.diseaseType) }}</el-tag>
            <el-tag v-if="recommendDetailVideo.category" size="small" type="success" effect="plain">{{ recommendDetailVideo.category }}</el-tag>
          </div>
          <div class="recommend-detail__block">
            <span>推荐原因</span>
            <p>{{ recommendDetailVideo.recommendReason || '当前没有更多说明。' }}</p>
          </div>
          <div class="recommend-detail__block">
            <span>适用场景</span>
            <p>{{ recommendScenarioText(recommendDetailVideo) }}</p>
          </div>
          <div class="recommend-detail__block">
            <span>内容定位</span>
            <p>{{ recommendDetailVideo.description || '该视频已被纳入当前病种宣教资源，可结合观看进度继续优化推荐。' }}</p>
          </div>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from '../../utils/element-plus-service'
import { videoApi } from '../../api'
import { useUserStore } from '../../stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const diseaseMap = {
  GENERAL: '通用',
  DIABETES: '2型糖尿病',
  HYPERTENSION: '高血压',
  COPD: '慢阻肺',
  CKD: '慢性肾病',
  CHD: '冠心病',
  STROKE: '脑卒中'
}

const videos = ref([])
const recommendedVideos = ref([])
const currentVideoId = ref(null)
const selectedDisease = ref('')
const selectedCategory = ref('')
const videoPlayerRef = ref(null)
const watchSessionId = ref(null)
const watchVideoId = ref(null)
const recommendDetailVisible = ref(false)
const recommendDetailVideo = ref(null)

let progressTimer = null

const diseaseOptions = computed(() => {
  const values = Array.from(new Set(videos.value.map(item => item.diseaseType).filter(Boolean)))
  return values.map(value => ({ value, label: diseaseLabel(value) }))
})

const categoryOptions = computed(() => Array.from(new Set(videos.value.map(item => item.category).filter(Boolean))))

const filteredVideos = computed(() => videos.value.filter(item => {
  if (selectedDisease.value && item.diseaseType !== selectedDisease.value) return false
  if (selectedCategory.value && item.category !== selectedCategory.value) return false
  return true
}))

const currentVideo = computed(() => {
  return videos.value.find(item => item.id === currentVideoId.value)
    || filteredVideos.value[0]
    || videos.value[0]
    || null
})

onMounted(async () => {
  await loadData()
})

onBeforeUnmount(async () => {
  await flushWatchProgress(false)
  clearWatchState()
})

watch([filteredVideos, () => route.query.video], () => {
  syncCurrentVideo()
}, { deep: true })

watch(() => currentVideo.value?.id, async (nextId, prevId) => {
  if (prevId && prevId !== nextId) {
    await flushWatchProgress(false)
    clearWatchState()
  }
})

function diseaseLabel(value) {
  return diseaseMap[value] || value || '通用'
}

function formatDuration(value) {
  const seconds = Number(value)
  if (!seconds) return '时长未标注'
  const minutes = Math.floor(seconds / 60)
  const remain = seconds % 60
  return `${minutes}分${remain.toString().padStart(2, '0')}秒`
}

function isRecommended(videoId) {
  return recommendedVideos.value.some(item => item.id === videoId)
}

function openRecommendDetail(video) {
  recommendDetailVideo.value = video
  recommendDetailVisible.value = true
}

function selectVideo(id) {
  currentVideoId.value = id
  const nextQuery = { ...route.query, video: id }
  if (Number(route.query.video) !== id) {
    delete nextQuery.sourceType
    delete nextQuery.sourceRefId
  }
  router.replace({ query: nextQuery })
}

function syncCurrentVideo() {
  const queryId = Number(route.query.video) || null
  const availableIds = new Set(filteredVideos.value.map(item => item.id))
  if (queryId && availableIds.has(queryId)) {
    currentVideoId.value = queryId
    return
  }
  if (currentVideoId.value && availableIds.has(currentVideoId.value)) {
    return
  }
  currentVideoId.value = filteredVideos.value[0]?.id || videos.value[0]?.id || null
}

async function loadData() {
  if (!userStore.patientId) {
    ElMessage.warning('无法识别患者身份')
    return
  }
  try {
    const [videoList, recommendations] = await Promise.all([
      videoApi.listPublished(),
      videoApi.getRecommendations(userStore.patientId, { limit: 4 })
    ])
    videos.value = videoList
    recommendedVideos.value = recommendations
    if (!route.query.video && recommendations[0]?.id) {
      currentVideoId.value = recommendations[0].id
      router.replace({ query: { ...route.query, video: recommendations[0].id } })
      return
    }
    syncCurrentVideo()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '加载视频内容失败')
  }
}

function recommendScenarioText(video) {
  if (!video) {
    return '暂无推荐场景说明。'
  }
  if (video.category && video.diseaseType) {
    return `当前视频主要面向 ${diseaseLabel(video.diseaseType)} 的「${video.category}」宣教场景。`
  }
  if (video.diseaseType) {
    return `当前视频主要面向 ${diseaseLabel(video.diseaseType)} 患者。`
  }
  return '当前视频属于通用慢病管理宣教内容。'
}

function handleLoadedMetadata() {
  const player = videoPlayerRef.value
  if (!player || !currentVideo.value) return
  if (!currentVideo.value.durationSeconds && Number.isFinite(player.duration) && player.duration > 0) {
    currentVideo.value.durationSeconds = Math.round(player.duration)
  }
}

async function handleVideoPlay() {
  await ensureWatchSession()
  startProgressTimer()
}

async function handleVideoPause() {
  stopProgressTimer()
  await flushWatchProgress(false)
}

async function handleVideoEnded() {
  stopProgressTimer()
  await flushWatchProgress(true)
  clearWatchState()
}

async function ensureWatchSession() {
  const video = currentVideo.value
  if (!video || !userStore.patientId) return
  if (watchSessionId.value && watchVideoId.value === video.id) {
    return
  }
  if (watchSessionId.value && watchVideoId.value !== video.id) {
    await flushWatchProgress(false)
    clearWatchState()
  }
  try {
    const result = await videoApi.startWatch({
      patientId: userStore.patientId,
      videoId: video.id,
      sourceType: currentSourceType(),
      sourceRefId: currentSourceRefId(),
      durationSeconds: resolveDurationSeconds()
    })
    watchSessionId.value = result.watchSessionId
    watchVideoId.value = video.id
  } catch (e) {
    console.error('Failed to start video watch session:', e)
  }
}

async function flushWatchProgress(markComplete) {
  if (!watchSessionId.value || !watchVideoId.value) {
    return
  }
  const player = videoPlayerRef.value
  const watchedSeconds = player && Number.isFinite(player.currentTime)
    ? Math.floor(player.currentTime)
    : 0
  const progressPercent = resolveProgressPercent(player, watchedSeconds)
  try {
    if (markComplete) {
      await videoApi.completeWatch(watchSessionId.value, { watchedSeconds, progressPercent })
    } else {
      await videoApi.updateWatchProgress(watchSessionId.value, { watchedSeconds, progressPercent })
    }
  } catch (e) {
    console.error('Failed to update video watch progress:', e)
  }
}

function startProgressTimer() {
  stopProgressTimer()
  progressTimer = window.setInterval(() => {
    flushWatchProgress(false)
  }, 10000)
}

function stopProgressTimer() {
  if (progressTimer) {
    window.clearInterval(progressTimer)
    progressTimer = null
  }
}

function clearWatchState() {
  stopProgressTimer()
  watchSessionId.value = null
  watchVideoId.value = null
}

function currentSourceType() {
  if (Number(route.query.video) === currentVideo.value?.id && route.query.sourceType) {
    return route.query.sourceType
  }
  return 'video_center'
}

function currentSourceRefId() {
  if (Number(route.query.video) === currentVideo.value?.id && route.query.sourceRefId) {
    const value = Number(route.query.sourceRefId)
    return Number.isFinite(value) ? value : null
  }
  return null
}

function resolveDurationSeconds() {
  if (currentVideo.value?.durationSeconds) return currentVideo.value.durationSeconds
  const player = videoPlayerRef.value
  if (player && Number.isFinite(player.duration) && player.duration > 0) {
    return Math.round(player.duration)
  }
  return null
}

function resolveProgressPercent(player, watchedSeconds) {
  const duration = resolveDurationSeconds()
  if (!duration || duration <= 0) {
    return null
  }
  const actual = player && Number.isFinite(player.currentTime) ? player.currentTime : watchedSeconds
  return Math.min(100, Math.max(0, Number(((actual / duration) * 100).toFixed(1))))
}
</script>

<style scoped>
.video-center {
  min-height: 100%;
}

.hero-card {
  border: none;
  background:
    radial-gradient(circle at top right, rgba(59, 130, 246, 0.18), transparent 32%),
    linear-gradient(135deg, #eff6ff 0%, #ffffff 46%, #f8fafc 100%);
}

.hero-grid {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  flex-wrap: wrap;
}

.hero-kicker {
  color: #2563eb;
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hero-title {
  margin: 10px 0 8px;
  font-size: 28px;
  color: #0f172a;
}

.hero-desc {
  margin: 0;
  max-width: 640px;
  color: #475569;
  line-height: 1.7;
}

.hero-tags {
  margin-top: 14px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.hero-filters {
  min-width: 320px;
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.player-card {
  height: 100%;
}

.player {
  width: 100%;
  min-height: 360px;
  border-radius: 12px;
  background: #000;
}

.player-meta {
  margin-top: 18px;
}

.player-title {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.player-tags {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.player-desc {
  margin-top: 14px;
  line-height: 1.8;
  color: #475569;
  font-size: 14px;
}

.player-reason-card {
  margin-top: 14px;
  padding: 14px 16px;
  border-radius: 14px;
  background: linear-gradient(135deg, #f8fafc, #eff6ff);
  border: 1px solid #dbeafe;
}

.player-reason-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.player-reason-card__head span {
  color: #1d4ed8;
  font-size: 13px;
  font-weight: 600;
}

.player-reason-card p {
  margin: 8px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recommend-item {
  padding: 14px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.recommend-item:hover {
  border-color: #93c5fd;
  transform: translateY(-1px);
  box-shadow: 0 10px 20px rgba(15, 23, 42, 0.06);
}

.recommend-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.recommend-meta {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.recommend-reason {
  margin-top: 10px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.recommend-actions {
  margin-top: 6px;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.video-card {
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  overflow: hidden;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.video-card:hover,
.video-card.active {
  border-color: #60a5fa;
  transform: translateY(-2px);
  box-shadow: 0 14px 28px rgba(15, 23, 42, 0.08);
}

.card-cover {
  height: 144px;
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
}

.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.card-cover-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1d4ed8;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.card-body {
  padding: 14px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: #111827;
}

.card-tags {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.card-desc {
  margin-top: 10px;
  min-height: 40px;
  color: #6b7280;
  font-size: 13px;
  line-height: 1.6;
}

.card-footer {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #6b7280;
  font-size: 12px;
}

.recommend-flag {
  color: #dc2626;
  font-weight: 600;
}

.recommend-detail {
  display: grid;
  gap: 16px;
}

.recommend-detail__title {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

.recommend-detail__tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.recommend-detail__block {
  padding: 16px;
  border-radius: 16px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.recommend-detail__block span {
  display: block;
  color: #334155;
  font-size: 13px;
  font-weight: 600;
}

.recommend-detail__block p {
  margin: 8px 0 0;
  color: #475569;
  font-size: 14px;
  line-height: 1.75;
}
</style>
