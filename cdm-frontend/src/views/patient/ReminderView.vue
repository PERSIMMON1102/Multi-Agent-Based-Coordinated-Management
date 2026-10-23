<template>
  <div class="reminder-page">
    <section class="reminder-hero">
      <div class="reminder-hero__content">
        <span class="reminder-hero__eyebrow">Follow-up Schedule</span>
        <h1>随访提醒中心</h1>
        <p>集中查看待执行、进行中和已逾期的随访任务，提前准备检查项目，减少漏访和延迟处理。</p>
        <div class="reminder-hero__chips">
          <el-tag effect="plain" round>总任务 {{ followUps.length }}</el-tag>
          <el-tag effect="plain" round type="danger">逾期 {{ overdueCount }}</el-tag>
          <el-tag effect="plain" round type="warning">待执行 {{ pendingCount }}</el-tag>
          <el-tag effect="plain" round type="success">近 7 天 {{ soonCount }}</el-tag>
        </div>
      </div>
      <div class="reminder-hero__aside">
        <strong>{{ nextFollowDate }}</strong>
        <span>最近一次待处理随访日期</span>
      </div>
    </section>

    <section class="reminder-stat-grid">
      <article class="reminder-stat-card">
        <span>待执行随访</span>
        <strong>{{ pendingCount }}</strong>
        <p>建议优先确认检查项目和联系方式，减少遗漏。</p>
      </article>
      <article class="reminder-stat-card">
        <span>进行中随访</span>
        <strong>{{ inProgressCount }}</strong>
        <p>表示正在推进或等待反馈的任务。</p>
      </article>
      <article class="reminder-stat-card">
        <span>已逾期</span>
        <strong>{{ overdueCount }}</strong>
        <p>逾期任务需要尽快补做或主动联系医生。</p>
      </article>
      <article class="reminder-stat-card">
        <span>近 7 天到期</span>
        <strong>{{ soonCount }}</strong>
        <p>适合提前准备复查项目和病情反馈。</p>
      </article>
    </section>

    <section class="reminder-layout">
      <div class="reminder-main">
        <div class="reminder-panel">
          <div class="reminder-panel__head">
            <div>
              <span class="reminder-panel__eyebrow">Task Feed</span>
              <h2>随访任务列表</h2>
              <p>按照日期和状态统一浏览，便于快速识别优先级。</p>
            </div>
          </div>

          <div v-loading="loading" class="reminder-list">
            <template v-if="followUps.length > 0">
              <article
                v-for="f in followUps"
                :key="f.id"
                class="reminder-card"
              >
                <div :class="['reminder-card__stripe', isOverdue(f.nextFollowDate) ? 'overdue' : statusClass(f.status)]"></div>

                <div class="reminder-card__body">
                  <div class="reminder-card__main">
                    <div class="reminder-card__title-row">
                      <el-tag :type="statusType(f.status)" size="small" effect="dark">{{ statusLabel(f.status) }}</el-tag>
                      <h3>{{ diseaseLabel(f.diseaseType) }}</h3>
                    </div>

                    <div class="reminder-card__info-grid">
                      <div class="reminder-card__info-item">
                        <span>随访频次</span>
                        <strong>{{ freqLabel(f.frequency) }}</strong>
                      </div>
                      <div class="reminder-card__info-item">
                        <span>下次随访</span>
                        <strong :class="{ 'reminder-card__date--overdue': isOverdue(f.nextFollowDate) }">{{ f.nextFollowDate || '--' }}</strong>
                      </div>
                    </div>

                    <div v-if="f.checkItems && f.checkItems.length > 0" class="reminder-card__checks">
                      <span class="reminder-card__checks-label">检查项目</span>
                      <div class="reminder-card__checks-tags">
                        <el-tag
                          v-for="(item, i) in f.checkItems.slice(0, 4)"
                          :key="i"
                          size="small"
                          type="info"
                          effect="plain"
                        >
                          {{ item }}
                        </el-tag>
                        <el-tag v-if="f.checkItems.length > 4" size="small" type="info" effect="plain">
                          +{{ f.checkItems.length - 4 }}项
                        </el-tag>
                      </div>
                    </div>
                  </div>

                  <div class="reminder-card__badge">
                    <div v-if="isOverdue(f.nextFollowDate)" class="reminder-date-badge reminder-date-badge--overdue">
                      <span>已逾期</span>
                      <strong>{{ daysOverdue(f.nextFollowDate) }}天</strong>
                    </div>
                    <div v-else class="reminder-date-badge reminder-date-badge--normal">
                      <span>距下次</span>
                      <strong>{{ daysUntil(f.nextFollowDate) }}天</strong>
                    </div>
                  </div>
                </div>
              </article>
            </template>

            <el-empty v-else description="暂无随访提醒" :image-size="88" />
          </div>
        </div>
      </div>

      <aside class="reminder-side">
        <div class="reminder-side-card">
          <span class="reminder-side-card__eyebrow">Schedule Snapshot</span>
          <h3>当前摘要</h3>
          <div class="reminder-side-card__grid">
            <div>
              <span>最近任务</span>
              <strong>{{ nextFollowDate }}</strong>
            </div>
            <div>
              <span>待执行</span>
              <strong>{{ pendingCount }}</strong>
            </div>
            <div>
              <span>逾期</span>
              <strong>{{ overdueCount }}</strong>
            </div>
            <div>
              <span>近 7 天</span>
              <strong>{{ soonCount }}</strong>
            </div>
          </div>
        </div>

        <div class="reminder-side-card">
          <span class="reminder-side-card__eyebrow">Action Tips</span>
          <h3>处理建议</h3>
          <ul class="reminder-side-card__list">
            <li>若任务已逾期，建议优先联系医生补做或确认延期原因。</li>
            <li>有检查项目时可提前准备资料，减少随访当天的信息缺失。</li>
            <li>页面会每 30 秒自动刷新一次，切回页面也会主动刷新。</li>
          </ul>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from '../../utils/element-plus-service'
import { followUpApi } from '../../api'
import { useUserStore } from '../../stores/user'
import dayjs from 'dayjs'

const userStore = useUserStore()
const followUps = ref([])
const loading = ref(false)
let refreshTimer = null

const diseaseMap = { DIABETES: '2型糖尿病', HYPERTENSION: '高血压', COPD: '慢阻肺', CKD: '慢性肾病', CHD: '冠心病', STROKE: '脑卒中' }
const freqMap = { IMMEDIATE: '立即', WEEKLY: '每周', BIWEEKLY: '双周', MONTHLY: '每月', QUARTERLY: '每季度' }
const statusMap = { PENDING: '待执行', IN_PROGRESS: '进行中', COMPLETED: '已完成', OVERDUE: '已逾期', CANCELLED: '已取消' }

const overdueCount = computed(() => followUps.value.filter(item => isOverdue(item.nextFollowDate)).length)
const pendingCount = computed(() => followUps.value.filter(item => item.status === 'PENDING').length)
const inProgressCount = computed(() => followUps.value.filter(item => item.status === 'IN_PROGRESS').length)
const soonCount = computed(() => followUps.value.filter(item => {
  const days = daysUntil(item.nextFollowDate)
  return typeof days === 'number' && days <= 7 && !isOverdue(item.nextFollowDate)
}).length)
const nextFollowDate = computed(() => {
  const upcoming = followUps.value
    .map(item => item.nextFollowDate)
    .filter(Boolean)
    .sort((a, b) => dayjs(a).valueOf() - dayjs(b).valueOf())
  return upcoming[0] || '--'
})

function diseaseLabel(t) { return diseaseMap[t] || t }
function freqLabel(f) { return freqMap[f] || f }
function statusLabel(s) { return statusMap[s] || s }
function statusType(s) { return { PENDING: 'warning', IN_PROGRESS: '', COMPLETED: 'success', OVERDUE: 'danger' }[s] || 'info' }
function statusClass(s) { return { PENDING: 'pending', IN_PROGRESS: 'progress', COMPLETED: 'completed' }[s] || 'pending' }
function isOverdue(date) { return date && dayjs(date).isBefore(dayjs(), 'day') }
function daysUntil(date) {
  if (!date) return '--'
  const diff = dayjs(date).diff(dayjs(), 'day')
  return diff >= 0 ? diff : 0
}
function daysOverdue(date) {
  if (!date) return '--'
  return dayjs().diff(dayjs(date), 'day')
}

async function loadFollowUps() {
  const pid = userStore.patientId
  if (!pid) return
  loading.value = true
  try {
    followUps.value = await followUpApi.getByPatient(pid)
  } catch (e) {
    followUps.value = []
    ElMessage.error(e.response?.data?.message || '加载随访提醒失败')
  } finally {
    loading.value = false
  }
}

function handleVisibilityChange() {
  if (document.visibilityState === 'visible') {
    loadFollowUps()
  }
}

onMounted(async () => {
  await loadFollowUps()
  refreshTimer = window.setInterval(loadFollowUps, 30000)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    window.clearInterval(refreshTimer)
    refreshTimer = null
  }
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.reminder-page {
  display: grid;
  gap: 22px;
}

.reminder-hero {
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

.reminder-hero__content {
  max-width: 760px;
}

.reminder-hero__eyebrow,
.reminder-panel__eyebrow,
.reminder-side-card__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: 0.1em;
}

.reminder-hero__eyebrow {
  background: rgba(255, 255, 255, 0.12);
}

.reminder-hero h1 {
  margin: 18px 0 10px;
  font-size: 34px;
  line-height: 1.05;
}

.reminder-hero p {
  margin: 0;
  max-width: 640px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(237, 244, 249, 0.82);
}

.reminder-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.reminder-hero__aside {
  min-width: 220px;
  display: grid;
  align-content: center;
  justify-items: end;
  gap: 6px;
  text-align: right;
}

.reminder-hero__aside strong {
  font-size: 34px;
  line-height: 1;
}

.reminder-hero__aside span {
  font-size: 13px;
  color: rgba(237, 244, 249, 0.82);
}

.reminder-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.reminder-stat-card,
.reminder-panel,
.reminder-side-card {
  border-radius: 24px;
  border: 1px solid rgba(39, 81, 104, 0.08);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 18px 38px rgba(17, 28, 38, 0.08);
}

.reminder-stat-card {
  padding: 18px 20px;
}

.reminder-stat-card span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #6b7b86;
}

.reminder-stat-card strong {
  display: block;
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.2;
  color: #1f3340;
}

.reminder-stat-card p {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #72838d;
}

.reminder-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
}

.reminder-panel,
.reminder-side-card {
  padding: 22px;
}

.reminder-panel__head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  margin-bottom: 18px;
}

.reminder-panel__head h2,
.reminder-side-card h3 {
  margin: 10px 0 0;
  color: #1f3340;
}

.reminder-panel__head p,
.reminder-side-card__list {
  margin: 8px 0 0;
  font-size: 14px;
  line-height: 1.8;
  color: #5d707d;
}

.reminder-panel__eyebrow,
.reminder-side-card__eyebrow {
  background: rgba(203, 229, 238, 0.46);
  color: #2e6f8b;
}

.reminder-list {
  display: grid;
  gap: 16px;
}

.reminder-card {
  overflow: hidden;
  border-radius: 22px;
  border: 1px solid rgba(39, 81, 104, 0.08);
  background: rgba(247, 250, 251, 0.92);
}

.reminder-card__stripe {
  height: 5px;
}

.reminder-card__stripe.pending {
  background: linear-gradient(90deg, #e6a23c, #f0c78a);
}

.reminder-card__stripe.progress {
  background: linear-gradient(90deg, #409eff, #79bbff);
}

.reminder-card__stripe.completed {
  background: linear-gradient(90deg, #67c23a, #95d475);
}

.reminder-card__stripe.overdue {
  background: linear-gradient(90deg, #f56c6c, #f89898);
}

.reminder-card__body {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 18px;
}

.reminder-card__main {
  flex: 1;
}

.reminder-card__title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.reminder-card__title-row h3 {
  margin: 0;
  font-size: 18px;
  color: #1f3340;
}

.reminder-card__info-grid {
  display: flex;
  gap: 22px;
  margin-top: 14px;
}

.reminder-card__info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.reminder-card__info-item span,
.reminder-card__checks-label,
.reminder-side-card__grid span {
  font-size: 12px;
  color: #80909a;
}

.reminder-card__info-item strong,
.reminder-side-card__grid strong {
  color: #223846;
  font-size: 15px;
}

.reminder-card__date--overdue {
  color: #d9534f;
}

.reminder-card__checks {
  margin-top: 14px;
}

.reminder-card__checks-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.reminder-card__badge {
  flex-shrink: 0;
}

.reminder-date-badge {
  min-width: 98px;
  padding: 12px 14px;
  border-radius: 18px;
  color: #ffffff;
  text-align: center;
}

.reminder-date-badge span {
  display: block;
  font-size: 11px;
}

.reminder-date-badge strong {
  display: block;
  margin-top: 6px;
  font-size: 20px;
}

.reminder-date-badge--normal {
  background: linear-gradient(135deg, #409eff, #79bbff);
}

.reminder-date-badge--overdue {
  background: linear-gradient(135deg, #f56c6c, #f89898);
}

.reminder-side {
  display: grid;
  gap: 16px;
}

.reminder-side-card__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.reminder-side-card__grid div {
  padding: 14px 12px;
  border-radius: 18px;
  background: rgba(203, 229, 238, 0.28);
}

.reminder-side-card__list {
  padding-left: 18px;
}

@media (max-width: 1200px) {
  .reminder-layout {
    grid-template-columns: 1fr;
  }

  .reminder-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 768px) {
  .reminder-hero,
  .reminder-panel__head,
  .reminder-card__body {
    flex-direction: column;
    align-items: stretch;
  }

  .reminder-hero {
    padding: 20px;
  }

  .reminder-hero__aside {
    justify-items: start;
    text-align: left;
  }

  .reminder-hero h1 {
    font-size: 28px;
  }

  .reminder-stat-grid,
  .reminder-side-card__grid {
    grid-template-columns: 1fr;
  }

  .reminder-card__info-grid {
    flex-direction: column;
    gap: 12px;
  }
}
</style>
