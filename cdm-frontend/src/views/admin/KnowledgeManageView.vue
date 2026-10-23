<template>
  <div class="admin-page">
    <section class="admin-hero">
      <div class="admin-hero__content">
        <span class="admin-hero__eyebrow">Knowledge Base</span>
        <h1 class="admin-hero__title">知识库管理</h1>
        <p class="admin-hero__desc">
          上传结构化知识文档、检查向量状态并更新知识中心，让问答、推荐和报告都能及时使用最新证据。
        </p>
        <div class="admin-hero__chips">
          <el-tag effect="plain" round>向量 {{ vectorSummaryLabel }}</el-tag>
          <el-tag effect="plain" round type="success">知识中心 {{ graphSummaryLabel }}</el-tag>
          <el-tag effect="plain" round type="warning">最近索引 {{ lastIndexLabel }}</el-tag>
        </div>
      </div>

      <div class="admin-hero__actions">
        <el-button class="admin-hero__ghost" :loading="statusLoading || graphStatusLoading" @click="refreshOverview">
          刷新状态
        </el-button>
        <el-button type="primary" class="admin-hero__primary" :loading="reindexing" @click="handleReindex">
          重新索引
        </el-button>
        <el-button class="admin-hero__ghost" @click="goToGraphView">查看知识中心</el-button>
      </div>
    </section>

    <section class="admin-stat-grid admin-stat-grid--four">
      <article class="admin-stat-card admin-stat-card--blue">
        <span>向量索引</span>
        <strong>{{ vectorSummaryLabel }}</strong>
        <p>Qdrant {{ vectorStatus?.qdrantReachable ? '已连接' : '未连接' }}，模型 {{ vectorModelLabel }}</p>
      </article>
      <article class="admin-stat-card admin-stat-card--green">
        <span>Embedding</span>
        <strong>{{ embeddingSummaryLabel }}</strong>
        <p>用于支撑知识检索、问答引用和相似内容召回。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--orange">
        <span>内容项数量</span>
        <strong>{{ graphNodeCount }}</strong>
        <p>当前关联数 {{ graphEdgeCount }}，可用于医生端内容展示和推荐引用。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--red">
        <span>最近结果</span>
        <strong>{{ lastIndexLabel }}</strong>
        <p>最近一次索引片段 {{ lastIndexedSummary }}。</p>
      </article>
    </section>

    <section class="admin-grid admin-grid--sidebar">
      <el-card shadow="never" class="admin-card">
        <template #header>
          <div class="admin-section-head">
            <div>
              <span class="admin-section-head__eyebrow">Upload Console</span>
              <h2>知识库上传</h2>
              <p>上传结构化文档，为健康问答、推荐和报告生成提供可靠的知识来源。</p>
            </div>
          </div>
        </template>

        <el-form :model="form" label-width="88px">
          <el-form-item label="核心病种">
            <div class="admin-field-note admin-field-note--required">必填，用于确定文档对应的核心病种。</div>
            <el-select v-model="form.disease" placeholder="请选择病种" style="width: 100%">
              <el-option v-for="item in diseaseOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>

          <el-form-item label="内容标签">
            <div class="admin-field-note admin-field-note--recommended">推荐填写，帮助系统抽取饮食、运动、复诊、监测等主题。</div>
            <el-select
              v-model="form.contentTags"
              multiple
              filterable
              allow-create
              default-first-option
              collapse-tags
              collapse-tags-tooltip
              placeholder="例如：居家管理、饮食、复诊、用药依从性"
              style="width: 100%"
            >
              <el-option v-for="item in contentTagOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>

          <el-form-item label="标题">
            <div class="admin-field-note admin-field-note--required">必填，用于确定知识文档名称，也是主题抽取的重要来源。</div>
            <el-input v-model="form.title" placeholder="例如：高血压社区管理规范（2026版）" />
          </el-form-item>

          <el-form-item label="来源">
            <div class="admin-field-note admin-field-note--recommended">推荐填写，便于形成更可信的知识文档支撑链路。</div>
            <el-input v-model="form.source" placeholder="例如：国家基层慢病管理指南" />
          </el-form-item>

          <el-form-item label="发布日期">
            <div class="admin-field-note admin-field-note--recommended">推荐填写，用于区分版本并提升文档聚合质量。</div>
            <el-date-picker v-model="form.publishedAt" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>

          <el-form-item label="文件">
            <div class="admin-upload-line">
              <el-button @click="triggerFileSelect">选择文件</el-button>
              <span class="admin-upload-value">{{ selectedFileName }}</span>
              <input ref="fileInput" type="file" accept=".txt,.md,.markdown" style="display: none" @change="handleFileChange" />
            </div>
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="uploading" @click="handleUpload">上传到知识库</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <div class="admin-aside-stack">
        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Vector Status</span>
                <h2>向量状态</h2>
                <p>查看当前向量引擎、Embedding 模型和集合连接情况。</p>
              </div>
            </div>
          </template>
          <div v-loading="statusLoading" class="admin-status-stack">
            <div class="admin-status-line">
              <span>向量索引</span>
              <el-tag size="small" :type="statusTagType(vectorStatus?.available)">
                {{ vectorSummaryLabel }}
              </el-tag>
            </div>
            <div class="admin-status-line">
              <span>Embedding</span>
              <el-tag size="small" :type="statusTagType(vectorStatus?.embedding?.available)">
                {{ embeddingSummaryLabel }}
              </el-tag>
            </div>
            <div class="admin-status-item">模型：{{ vectorModelLabel }}</div>
            <div class="admin-status-item">Qdrant：{{ vectorStatus?.qdrantReachable ? '已连接' : '未连接' }}</div>
            <div class="admin-status-item">集合：{{ formatCollections(vectorStatus?.collections) }}</div>
            <div class="admin-status-item"><strong>诊断：</strong>{{ vectorStatus?.diagnosis || '--' }}</div>
            <div class="admin-status-item">建议：{{ vectorStatus?.recommendation || '--' }}</div>
            <div v-if="vectorStatus?.embedding?.lastError" class="admin-status-error">
              最近错误：{{ vectorStatus.embedding.lastError }}
            </div>
            <div v-else-if="vectorStatus?.lastError" class="admin-status-error">
              最近错误：{{ vectorStatus.lastError }}
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Knowledge Center</span>
                <h2>知识中心</h2>
                <p>导入知识库、上传视频或保存讲稿后，可从这里快速更新内容关联。</p>
              </div>
              <div class="admin-inline-actions">
                <el-button plain size="small" @click="loadGraphStatus">刷新</el-button>
                <el-button type="primary" size="small" :loading="graphRebuilding" @click="handleGraphRebuild">
                  更新内容
                </el-button>
              </div>
            </div>
          </template>
          <div v-loading="graphStatusLoading" class="admin-status-stack">
            <div class="admin-status-line">
              <span>知识中心状态</span>
              <el-tag size="small" :type="graphStatus?.built ? 'success' : 'warning'">
                {{ graphSummaryLabel }}
              </el-tag>
            </div>
            <div class="admin-status-item">内容项数：{{ graphNodeCount }}</div>
            <div class="admin-status-item">关联数：{{ graphEdgeCount }}</div>
            <div class="admin-status-item">病种内容：{{ graphStatus?.nodeTypes?.DISEASE ?? 0 }}</div>
            <div class="admin-status-item">视频内容：{{ graphStatus?.nodeTypes?.VIDEO ?? 0 }}</div>
            <div class="admin-status-item">知识文档：{{ graphStatus?.nodeTypes?.KNOWLEDGE_DOC ?? 0 }}</div>
            <div class="admin-status-item">主题内容：{{ graphStatus?.nodeTypes?.TOPIC ?? 0 }}</div>
            <div class="admin-status-item">说明：点击“更新内容”即可把最新知识内容同步到知识中心。</div>
          </div>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Upload Notes</span>
                <h2>上传建议</h2>
                <p>尽量让文档元信息完整，后续检索和内容关联会更稳定。</p>
              </div>
            </div>
          </template>
          <ul class="admin-tips-list">
            <li>优先上传带章节标题的指南、规范、宣教材料。</li>
            <li>核心病种保持稳定，更多内容差异请通过“内容标签”体现。</li>
            <li>标题、来源、标签尽量完整，便于后续检索和版本管理。</li>
            <li>病种、标题、标签越完整，内容关联和推荐命中越准确。</li>
            <li>若上传成功但索引失败，请先查看上方“向量状态”和最近错误。</li>
          </ul>
        </el-card>

        <el-card v-if="lastResult" shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Last Run</span>
                <h2>最近一次结果</h2>
                <p>保留最近的上传或重建结果，便于快速核对索引状态。</p>
              </div>
            </div>
          </template>
          <div v-if="lastResult.indexStatus" class="admin-result-summary">
            <el-tag size="small" :type="indexStatusType(lastResult.indexStatus)">
              {{ lastResult.indexStatus }}
            </el-tag>
            <span>已索引 {{ lastResult.indexedChunkCount || 0 }} / {{ lastResult.chunkCount || 0 }}</span>
          </div>
          <pre class="admin-result-box">{{ JSON.stringify(lastResult, null, 2) }}</pre>
        </el-card>

        <el-card shadow="never" class="admin-card">
          <template #header>
            <div class="admin-section-head admin-section-head--compact">
              <div>
                <span class="admin-section-head__eyebrow">Quality Check</span>
                <h2>知识检索质量检查</h2>
                <p>使用内置问题集检查当前知识检索是否仍然稳定命中重点主题。</p>
              </div>
              <div class="admin-inline-actions">
                <el-button type="primary" size="small" :loading="evaluatingRag" @click="runRagEvaluation">
                  开始检查
                </el-button>
              </div>
            </div>
          </template>

          <div class="admin-status-stack">
            <div class="admin-status-line">
              <span>检查题数</span>
              <strong>{{ ragEvaluationCases.length }}</strong>
            </div>
            <div class="admin-status-line">
              <span>最近命中率</span>
              <el-tag size="small" :type="ragPassRate >= 80 ? 'success' : (ragPassRate >= 60 ? 'warning' : 'danger')">
                {{ ragPassRateLabel }}
              </el-tag>
            </div>
            <div class="admin-status-item">平均匹配数：{{ ragAvgHitLabel }}</div>
          </div>

          <el-table v-if="ragEvaluationRows.length > 0" :data="ragEvaluationRows" size="small" border style="margin-top: 14px">
            <el-table-column prop="caseId" label="检查项" min-width="160" />
            <el-table-column prop="scenario" label="场景" width="120" />
            <el-table-column prop="focus" label="问题焦点" min-width="220" />
            <el-table-column prop="hitCount" label="匹配数" width="90" />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.passed ? 'success' : 'danger'">
                  {{ row.passed ? '正常' : '关注' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="首条结果" min-width="220">
              <template #default="{ row }">{{ row.topReferences?.[0]?.title || '--' }}</template>
            </el-table-column>
          </el-table>

          <div v-else class="admin-field-note admin-field-note--recommended" style="margin-top: 8px">
            还没有运行过质量检查。点击“开始检查”即可快速查看当前知识检索表现。
          </div>
        </el-card>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from '../../utils/element-plus-service'
import { useRouter } from 'vue-router'
import { knowledgeApi, knowledgeGraphApi } from '../../api'
import ragRegressionDataset from '../../utils/rag-regression-dataset.json'

const router = useRouter()
const fileInput = ref(null)
const selectedFile = ref(null)
const uploading = ref(false)
const reindexing = ref(false)
const statusLoading = ref(false)
const vectorStatus = ref(null)
const lastResult = ref(null)
const graphStatusLoading = ref(false)
const graphRebuilding = ref(false)
const graphStatus = ref(null)
const evaluatingRag = ref(false)
const ragEvaluation = ref(null)

const diseaseOptions = ['通用', '高血压', '糖尿病', '慢阻肺', '慢性肾病', '冠心病', '脑卒中']
const contentTagOptions = ['居家管理', '饮食', '运动', '用药', '复诊', '并发症预防', '监测指标', '宣教', '康复', '心理支持']

const form = reactive({
  disease: '通用',
  contentTags: [],
  title: '',
  source: '',
  publishedAt: ''
})

const vectorSummaryLabel = computed(() => {
  if (!vectorStatus.value) return '未检测'
  if (vectorStatus.value.available) return '可用'
  return vectorStatus.value.enabled ? '异常' : '未启用'
})

const embeddingSummaryLabel = computed(() => {
  if (!vectorStatus.value?.embedding) return '未检测'
  if (vectorStatus.value.embedding.available) return '可用'
  return vectorStatus.value.embedding.enabled ? '异常' : '未启用'
})

const vectorModelLabel = computed(() => vectorStatus.value?.embedding?.model || '--')
const graphSummaryLabel = computed(() => graphStatus.value?.built ? '已构建' : '待重建')
const graphNodeCount = computed(() => Number(graphStatus.value?.nodeCount) || 0)
const graphEdgeCount = computed(() => Number(graphStatus.value?.edgeCount) || 0)
const lastIndexLabel = computed(() => lastResult.value?.indexStatus || '未执行')
const lastIndexedSummary = computed(() => {
  if (!lastResult.value) return '--'
  return `${lastResult.value.indexedChunkCount || 0} / ${lastResult.value.chunkCount || 0}`
})
const selectedFileName = computed(() => selectedFile.value?.name || '尚未选择文件')
const ragEvaluationCases = computed(() => Array.isArray(ragRegressionDataset) ? ragRegressionDataset : [])
const ragEvaluationRows = computed(() => Array.isArray(ragEvaluation.value?.results) ? ragEvaluation.value.results : [])
const ragPassRate = computed(() => Number(ragEvaluation.value?.passRate) || 0)
const ragPassRateLabel = computed(() => `${Math.round(ragPassRate.value)}%`)
const ragAvgHitLabel = computed(() => {
  const value = Number(ragEvaluation.value?.avgHitCount)
  return Number.isFinite(value) ? value.toFixed(2) : '0.00'
})

onMounted(() => {
  refreshOverview()
})

function goToGraphView() {
  router.push('/admin/knowledge-graph')
}

function triggerFileSelect() {
  fileInput.value?.click()
}

function handleFileChange(event) {
  selectedFile.value = event.target.files?.[0] || null
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

function formatCollections(collections) {
  return Array.isArray(collections) && collections.length ? collections.join(', ') : '暂无'
}

async function refreshOverview() {
  await Promise.all([loadStatus(), loadGraphStatus()])
}

async function loadStatus() {
  statusLoading.value = true
  try {
    vectorStatus.value = await knowledgeApi.getStatus()
  } catch (e) {
    vectorStatus.value = null
  } finally {
    statusLoading.value = false
  }
}

async function loadGraphStatus() {
  graphStatusLoading.value = true
  try {
    graphStatus.value = await knowledgeGraphApi.getStatus()
  } catch (e) {
    graphStatus.value = null
  } finally {
    graphStatusLoading.value = false
  }
}

async function handleReindex() {
  reindexing.value = true
  try {
    const res = await knowledgeApi.reindex()
    lastResult.value = res
    await loadStatus()
    ElMessage[res.indexStatus === 'INDEXED' ? 'success' : 'warning'](
      res.indexStatus === 'INDEXED' ? '知识库重新索引完成' : `重新索引已执行，当前状态：${res.indexStatus}`
    )
  } catch (e) {
    const msg = e.response?.data?.message || e.response?.data?.error || '重新索引失败'
    ElMessage.error(msg)
  } finally {
    reindexing.value = false
  }
}

async function handleGraphRebuild() {
  graphRebuilding.value = true
  try {
    const res = await knowledgeGraphApi.rebuild()
    await loadGraphStatus()
    ElMessage.success(`知识中心更新完成：内容项 ${res.nodeCount || 0}，关联 ${res.edgeCount || 0}`)
  } catch (e) {
    const msg = e.response?.data?.message || e.response?.data?.error || '知识中心更新失败'
    ElMessage.error(msg)
  } finally {
    graphRebuilding.value = false
  }
}

async function promptRebuildAfterUpload() {
  try {
    await ElMessageBox.confirm(
      '知识库上传成功，是否立即更新知识中心？这样医生端内容展示和推荐会立刻使用最新内容。',
      '更新知识中心',
      {
        confirmButtonText: '立即更新',
        cancelButtonText: '稍后手动更新',
        type: 'info'
      }
    )
  } catch {
    await loadGraphStatus()
    return
  }

  await handleGraphRebuild()
}

async function handleUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    Object.entries(form).forEach(([key, value]) => {
      if (Array.isArray(value)) {
        if (value.length > 0) formData.append(key, value.join(', '))
        return
      }
      if (value) formData.append(key, value)
    })
    const res = await knowledgeApi.upload(formData)
    lastResult.value = res
    await loadStatus()
    ElMessage[res.indexStatus === 'INDEXED' ? 'success' : 'warning'](
      res.indexStatus === 'INDEXED' ? '知识库文件上传并索引成功' : '知识库文件上传成功，但索引未完全成功'
    )
    selectedFile.value = null
    if (fileInput.value) {
      fileInput.value.value = ''
    }
    await promptRebuildAfterUpload()
  } catch (e) {
    const msg = e.response?.data?.message || e.response?.data?.error || '知识库上传失败'
    lastResult.value = { message: msg }
    ElMessage.error(msg)
  } finally {
    uploading.value = false
  }
}

async function runRagEvaluation() {
  evaluatingRag.value = true
  try {
    ragEvaluation.value = await knowledgeApi.evaluateRag(ragEvaluationCases.value)
    ElMessage.success(`知识检索质量检查完成，当前命中率 ${ragPassRateLabel.value}`)
  } catch (e) {
    const msg = e.response?.data?.message || e.response?.data?.error || '知识检索质量检查失败'
    ElMessage.error(msg)
  } finally {
    evaluatingRag.value = false
  }
}
</script>

<style scoped>
.admin-result-summary strong{font-weight:600;color:#203847}
</style>
