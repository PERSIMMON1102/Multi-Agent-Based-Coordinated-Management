<template>
  <div class="admin-page paper-case-audit-page">
    <section class="admin-hero">
      <div class="admin-hero__content">
        <span class="admin-hero__eyebrow">Security Audit</span>
        <h1 class="admin-hero__title">病历访问审计</h1>
        <p class="admin-hero__desc">
          回看纸质病历原件下载的访问轨迹，按患者或病历定位访问记录，辅助排查越权下载、频繁访问和异常失败。
        </p>
        <div class="admin-hero__chips">
          <el-tag effect="plain" round type="info">当前记录 {{ summary.totalEntries || 0 }}</el-tag>
          <el-tag effect="plain" round type="success">成功访问 {{ outcomeCount('success') }}</el-tag>
          <el-tag effect="plain" round type="danger">失败/拒绝 {{ nonSuccessCount }}</el-tag>
        </div>
      </div>

      <div class="admin-hero__actions">
        <el-button class="admin-hero__ghost" :loading="loading" @click="loadSummary">刷新审计</el-button>
      </div>
    </section>

    <el-alert
      v-if="errorMessage"
      class="paper-case-audit-alert"
      type="error"
      :closable="false"
      :title="errorMessage"
      show-icon
    />

    <section class="admin-stat-grid admin-stat-grid--four">
      <article class="admin-stat-card admin-stat-card--blue">
        <span>访问总量</span>
        <strong>{{ summary.totalEntries || 0 }}</strong>
        <p>当前筛选窗口下的病历原件访问记录。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--green">
        <span>下载成功</span>
        <strong>{{ actionCount('download') }}</strong>
        <p>包含医生与管理员的成功下载轨迹。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--orange">
        <span>医生访问</span>
        <strong>{{ roleCount('doctor') }}</strong>
        <p>医生对自己负责患者病历的访问次数。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--red">
        <span>异常结果</span>
        <strong>{{ nonSuccessCount }}</strong>
        <p>包含 denied 等非 success 结果，适合重点复核。</p>
      </article>
    </section>

    <section class="admin-grid admin-grid--sidebar">
      <el-card shadow="never" class="admin-card">
        <template #header>
          <div class="admin-section-head">
            <div>
              <span class="admin-section-head__eyebrow">Filters</span>
              <h2>筛选条件</h2>
              <p>支持按患者 ID 或病历 ID 精确定位；两者都为空时，管理员查看最近全局审计。</p>
            </div>
          </div>
        </template>

        <div class="admin-toolbar paper-case-audit-toolbar">
          <el-input v-model="filters.patientId" clearable placeholder="患者 ID" />
          <el-input v-model="filters.paperCaseId" clearable placeholder="病历 ID" />
          <el-input-number v-model="filters.limit" :min="10" :max="200" :step="10" />
          <el-button type="primary" :loading="loading" @click="loadSummary">应用筛选</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </div>

        <el-table class="admin-data-table" :data="summary.entries || []" stripe border v-loading="loading">
          <el-table-column prop="timestamp" label="时间" min-width="180" />
          <el-table-column prop="paperCaseId" label="病历 ID" min-width="100" />
          <el-table-column prop="patientId" label="患者 ID" min-width="100" />
          <el-table-column prop="actorRole" label="角色" min-width="100">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="roleTagType(row.actorRole)">
                {{ row.actorRole || '--' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="actorUserId" label="访问者 ID" min-width="110" />
          <el-table-column prop="action" label="动作" min-width="110" />
          <el-table-column prop="outcome" label="结果" min-width="110">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="row.outcome === 'success' ? 'success' : 'danger'">
                {{ row.outcome || '--' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="requestIp" label="来源 IP" min-width="140" />
          <el-table-column prop="targetSummary" label="目标摘要" min-width="180" show-overflow-tooltip />
          <el-table-column prop="detailSummary" label="细节摘要" min-width="220" show-overflow-tooltip />
        </el-table>

        <div v-if="!loading && (!summary.entries || summary.entries.length === 0)" class="admin-empty-block">
          <el-empty description="当前筛选下暂无病历访问审计记录" />
        </div>
      </el-card>

      <div class="admin-aside-stack">
        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Outcome</span>
                <h2>结果分布</h2>
              </div>
            </div>
          </template>
          <div class="admin-status-stack">
            <div
              v-for="item in outcomeItems"
              :key="`outcome-${item.key}`"
              class="admin-status-item paper-case-audit-item"
            >
              <strong>{{ item.key }}</strong>
              <span>{{ item.value }} 次</span>
            </div>
            <el-empty v-if="outcomeItems.length === 0" description="暂无结果分布" :image-size="72" />
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Actor Role</span>
                <h2>访问角色分布</h2>
              </div>
            </div>
          </template>
          <div class="admin-status-stack">
            <div
              v-for="item in roleItems"
              :key="`role-${item.key}`"
              class="admin-status-item paper-case-audit-item"
            >
              <strong>{{ item.key }}</strong>
              <span>{{ item.value }} 次</span>
            </div>
            <el-empty v-if="roleItems.length === 0" description="暂无角色分布" :image-size="72" />
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Resource</span>
                <h2>资源类型分布</h2>
              </div>
            </div>
          </template>
          <div class="admin-status-stack">
            <div
              v-for="item in resourceItems"
              :key="`resource-${item.key}`"
              class="admin-status-item paper-case-audit-item"
            >
              <strong>{{ item.key }}</strong>
              <span>{{ item.value }} 次</span>
            </div>
            <el-empty v-if="resourceItems.length === 0" description="暂无资源分布" :image-size="72" />
          </div>
        </el-card>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from '../../utils/element-plus-service'
import { paperCaseApi } from '../../api'

const loading = ref(false)
const errorMessage = ref('')
const summary = ref({
  totalEntries: 0,
  actionCounts: {},
  outcomeCounts: {},
  actorRoleCounts: {},
  resourceTypeCounts: {},
  entries: []
})

const filters = reactive({
  patientId: '',
  paperCaseId: '',
  limit: 50
})

const outcomeItems = computed(() => toItems(summary.value.outcomeCounts))
const roleItems = computed(() => toItems(summary.value.actorRoleCounts))
const resourceItems = computed(() => toItems(summary.value.resourceTypeCounts))
const nonSuccessCount = computed(() =>
  outcomeItems.value
    .filter(item => item.key !== 'success')
    .reduce((total, item) => total + item.value, 0)
)

onMounted(() => {
  loadSummary()
})

async function loadSummary() {
  loading.value = true
  errorMessage.value = ''
  try {
    summary.value = await paperCaseApi.getAuditSummary(buildParams())
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '加载病历访问审计失败'
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
}

function buildParams() {
  const params = { limit: filters.limit }
  if (filters.patientId) {
    params.patientId = Number(filters.patientId)
  }
  if (filters.paperCaseId) {
    params.paperCaseId = Number(filters.paperCaseId)
  }
  return params
}

function resetFilters() {
  filters.patientId = ''
  filters.paperCaseId = ''
  filters.limit = 50
  loadSummary()
}

function actionCount(key) {
  return Number(summary.value.actionCounts?.[key] || 0)
}

function outcomeCount(key) {
  return Number(summary.value.outcomeCounts?.[key] || 0)
}

function roleCount(key) {
  return Number(summary.value.actorRoleCounts?.[key] || 0)
}

function toItems(source) {
  return Object.entries(source || {}).map(([key, value]) => ({ key, value: Number(value || 0) }))
}

function roleTagType(role) {
  if (role === 'admin') return 'warning'
  if (role === 'doctor') return 'success'
  return 'info'
}
</script>

<style scoped>
.paper-case-audit-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 140px auto auto;
}

.paper-case-audit-item strong {
  word-break: break-word;
}

@media (max-width: 1200px) {
  .paper-case-audit-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
