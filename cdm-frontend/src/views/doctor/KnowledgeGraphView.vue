<template>
  <div :class="['graph-page', { 'graph-page--admin': userStore.isAdmin }]" v-loading="loading">
    <section class="graph-hero">
      <div class="graph-hero__content">
        <span class="graph-hero__eyebrow">KNOWLEDGE CENTER</span>
        <h1>知识中心</h1>
        <p>
          在一个视图里查看疾病、主题、知识文档和视频之间的关联关系，方便医生理解知识内容与宣教资源的组织方式。
        </p>
        <div class="graph-hero__chips">
          <el-tag effect="plain" round>内容项 {{ status.nodeCount }}</el-tag>
          <el-tag effect="plain" round type="success">关系 {{ status.edgeCount }}</el-tag>
          <el-tag effect="plain" round type="warning">当前筛选 {{ currentFilterLabel }}</el-tag>
        </div>
      </div>

      <div class="graph-hero__actions">
        <el-button class="graph-hero__ghost" :loading="refreshing" @click="loadGraph()">刷新内容</el-button>
        <el-button
          v-if="userStore.isAdmin"
          type="primary"
          class="graph-hero__primary"
          :loading="rebuilding"
          @click="rebuildGraph()"
        >
          重建内容关系
        </el-button>
      </div>
    </section>

    <section class="graph-stat-grid">
      <article class="graph-stat-card graph-stat-card--red">
        <span>疾病内容</span>
        <strong>{{ typeCount('DISEASE') }}</strong>
        <p>用于承载慢病种类、风险分类和主要干预对象。</p>
      </article>
      <article class="graph-stat-card graph-stat-card--blue">
        <span>视频内容</span>
        <strong>{{ typeCount('VIDEO') }}</strong>
        <p>连接宣教视频、讲稿内容与疾病主题。</p>
      </article>
      <article class="graph-stat-card graph-stat-card--green">
        <span>知识文档</span>
        <strong>{{ typeCount('KNOWLEDGE_DOC') }}</strong>
        <p>沉淀结构化文档级证据，供聊天和报告调用。</p>
      </article>
      <article class="graph-stat-card graph-stat-card--orange">
        <span>主题内容</span>
        <strong>{{ typeCount('TOPIC') }}</strong>
        <p>用于串联饮食、运动、监测与并发症管理主题。</p>
      </article>
    </section>

    <section class="graph-filter-card">
      <div class="graph-filter-card__head">
        <div>
          <span class="graph-section__eyebrow">Content Filter</span>
          <h2>内容筛选器</h2>
        </div>
        <p>按内容类型、关键词和返回数量筛选当前可视化结果。</p>
      </div>

      <div class="graph-filter-bar">
        <el-select v-model="filters.nodeType" clearable placeholder="按内容类型筛选" class="graph-filter-bar__field">
          <el-option label="全部内容" value="" />
          <el-option
            v-for="item in categories"
            :key="item.name"
            :label="item.label"
            :value="item.name"
          />
        </el-select>
        <el-input
          v-model="filters.keyword"
          placeholder="搜索内容名称"
          clearable
          class="graph-filter-bar__field graph-filter-bar__field--wide"
          @keyup.enter="loadGraph"
        />
        <el-input-number v-model="filters.limit" :min="20" :max="180" :step="10" />
        <el-button type="primary" @click="loadGraph()">应用筛选</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </section>

    <section class="graph-layout">
      <div class="graph-main-column">
        <article class="graph-card">
          <div class="graph-card__head">
            <div>
              <span class="graph-section__eyebrow">Graph Canvas</span>
              <h2>内容关联视图</h2>
            </div>
            <p>当前展示 {{ graph.nodes.length }} 项内容、{{ graph.edges.length }} 条关联，可点击任意内容查看详情。</p>
          </div>
          <div ref="chartRef" class="graph-chart"></div>
        </article>

        <div class="graph-table-grid">
          <article class="graph-card">
            <div class="graph-card__head">
              <div>
                <span class="graph-section__eyebrow">Content List</span>
                <h2>内容清单</h2>
              </div>
              <p>当前可视化返回的内容子集。</p>
            </div>
            <el-table :data="graph.nodes" size="small" stripe height="360" @row-click="handleNodeRowClick">
              <el-table-column prop="displayName" label="内容" min-width="180" />
              <el-table-column prop="nodeTypeLabel" label="类型" min-width="100" />
              <el-table-column prop="sourceType" label="来源" min-width="110" />
            </el-table>
          </article>

          <article class="graph-card">
            <div class="graph-card__head">
              <div>
                <span class="graph-section__eyebrow">Relation List</span>
              <h2>关联清单</h2>
              </div>
              <p>当前展示图里的主要关系路径。</p>
            </div>
            <el-table :data="edgeRows" size="small" stripe height="360">
              <el-table-column prop="sourceName" label="起点" min-width="150" />
              <el-table-column prop="relationLabel" label="关系" min-width="100" />
              <el-table-column prop="targetName" label="终点" min-width="150" />
              <el-table-column prop="evidenceText" label="说明" min-width="180" show-overflow-tooltip />
            </el-table>
          </article>
        </div>
      </div>

      <aside class="graph-side-column">
        <article class="graph-card graph-card--detail">
          <div class="graph-card__head">
            <div>
              <span class="graph-section__eyebrow">Content Detail</span>
              <h2>内容详情</h2>
            </div>
            <p>{{ selectedNode ? '当前选中内容的结构化信息' : '点击左侧内容查看详情' }}</p>
          </div>

          <div v-if="selectedNode" class="node-detail">
            <div class="node-detail__hero">
              <div>
                <div class="detail-name">{{ selectedNode.displayName }}</div>
                <div class="detail-tags">
                  <el-tag size="small" :style="{ borderColor: selectedNodeColor, color: selectedNodeColor }" effect="plain">
                    {{ selectedNode.nodeTypeLabel }}
                  </el-tag>
                  <el-tag size="small" type="info" effect="plain">{{ selectedNode.nodeKey }}</el-tag>
                </div>
              </div>
              <div class="node-detail__dot" :style="{ backgroundColor: selectedNodeColor }"></div>
            </div>

            <div class="detail-line">
              <span>来源</span>
              <strong>{{ selectedNode.sourceType || '—' }}</strong>
            </div>
            <div class="detail-line">
              <span>来源标识</span>
              <strong>{{ selectedNode.sourceRefId || '—' }}</strong>
            </div>

            <div class="relation-section">
              <div class="section-title">关联路径</div>
              <el-timeline>
                <el-timeline-item
                  v-for="relation in selectedRelations"
                  :key="relation.id"
                  :timestamp="relation.direction"
                  type="primary"
                >
                  <div class="relation-title">
                    <strong>{{ relation.otherNodeName }}</strong>
                    <el-tag size="small" effect="plain">{{ relation.relationLabel }}</el-tag>
                  </div>
                  <div class="relation-evidence">{{ relation.evidenceText || '无附加说明' }}</div>
                </el-timeline-item>
              </el-timeline>
            </div>
          </div>

          <div v-else class="graph-empty-inline">
            <el-empty description="选择一项内容查看详情" />
          </div>
        </article>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from '../../utils/element-plus-service'
import { knowledgeGraphApi } from '../../api'
import { useUserStore } from '../../stores/user'
import { echarts } from '../../utils/echarts-graph'

const userStore = useUserStore()

const loading = ref(false)
const refreshing = ref(false)
const rebuilding = ref(false)
const chartRef = ref(null)
const chartInstance = ref(null)

const filters = ref({
  nodeType: '',
  keyword: '',
  limit: 80
})

const status = ref({
  nodeCount: 0,
  edgeCount: 0,
  nodeTypes: {},
  relationTypes: {}
})

const categories = ref([])
const graph = ref({
  nodes: [],
  edges: []
})
const selectedNodeId = ref(null)

const nodeMap = computed(() => {
  const map = new Map()
  graph.value.nodes.forEach(node => map.set(String(node.id), node))
  return map
})

const selectedNode = computed(() => nodeMap.value.get(String(selectedNodeId.value)) || null)

const selectedRelations = computed(() => {
  if (!selectedNode.value) return []
  return graph.value.edges
    .filter(edge => String(edge.source) === String(selectedNode.value.id) || String(edge.target) === String(selectedNode.value.id))
    .map(edge => {
      const incoming = String(edge.target) === String(selectedNode.value.id)
      const otherNode = nodeMap.value.get(String(incoming ? edge.source : edge.target))
      return {
        ...edge,
        otherNodeName: otherNode?.displayName || otherNode?.name || '未知节点',
        direction: incoming ? '关联到当前内容' : '由当前内容关联'
      }
    })
})

const selectedNodeColor = computed(() => selectedNode.value?.itemStyle?.color || '#409eff')

const edgeRows = computed(() => graph.value.edges.map(edge => ({
  ...edge,
  sourceName: nodeMap.value.get(String(edge.source))?.displayName || edge.source,
  targetName: nodeMap.value.get(String(edge.target))?.displayName || edge.target
})))

const currentFilterLabel = computed(() => {
  if (filters.value.nodeType) {
    const category = categories.value.find(item => item.name === filters.value.nodeType)
    return category?.label || filters.value.nodeType
  }
  if (filters.value.keyword) {
    return `关键词：${filters.value.keyword}`
  }
  return '全部内容'
})

function typeCount(type) {
  return status.value?.nodeTypes?.[type] || 0
}

async function loadGraph() {
  loading.value = true
  refreshing.value = true
  try {
    const data = await knowledgeGraphApi.getVisualization(filters.value)
    status.value = data?.status || {}
    categories.value = data?.categories || []
    graph.value = {
      nodes: data?.nodes || [],
      edges: data?.edges || []
    }
    if (!graph.value.nodes.length) {
      selectedNodeId.value = null
    } else if (!selectedNodeId.value || !nodeMap.value.has(String(selectedNodeId.value))) {
      selectedNodeId.value = graph.value.nodes[0].id
    }
    await nextTick()
    renderGraph()
  } catch (error) {
    console.error('Failed to load knowledge graph visualization:', error)
    ElMessage.error('加载知识中心失败')
  } finally {
    loading.value = false
    refreshing.value = false
  }
}

async function rebuildGraph() {
  if (!userStore.isAdmin) {
    return
  }
  try {
    await ElMessageBox.confirm('将重新根据知识文档和视频资源整理内容关联，是否继续？', '重建内容关系', {
      type: 'warning',
      confirmButtonText: '重建',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  rebuilding.value = true
  try {
    await knowledgeGraphApi.rebuild()
    ElMessage.success('内容关系重建完成')
    await loadGraph()
  } catch (error) {
    console.error('Failed to rebuild knowledge graph:', error)
    ElMessage.error('内容关系重建失败')
  } finally {
    rebuilding.value = false
  }
}

function resetFilters() {
  filters.value = {
    nodeType: '',
    keyword: '',
    limit: 80
  }
  loadGraph()
}

function handleNodeRowClick(row) {
  selectedNodeId.value = row.id
  highlightNode(row.id)
}

function renderGraph() {
  if (!chartRef.value) return
  if (!chartInstance.value) {
    chartInstance.value = echarts.init(chartRef.value)
    chartInstance.value.on('click', params => {
      if (params?.dataType === 'node' && params?.data?.id != null) {
        selectedNodeId.value = params.data.id
      }
    })
  }

  chartInstance.value.setOption({
    backgroundColor: 'transparent',
    tooltip: {
      formatter: params => {
        if (params.dataType === 'edge') {
          return `${params.data.relationLabel}<br/>${params.data.evidenceText || '无附加说明'}`
        }
        return `${params.data.displayName}<br/>${params.data.nodeTypeLabel}`
      }
    },
    legend: {
      top: 8,
      textStyle: { color: '#5f6f78' },
      data: categories.value.map(item => item.label)
    },
    series: [{
      type: 'graph',
      layout: 'force',
      roam: true,
      draggable: true,
      force: {
        repulsion: 260,
        edgeLength: [70, 130]
      },
      label: {
        show: true,
        position: 'right',
        color: '#2d4250',
        formatter: value => value.data.displayName
      },
      lineStyle: {
        color: '#94a3b8',
        curveness: 0.18,
        opacity: 0.75
      },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 6,
      categories: categories.value.map(item => ({
        name: item.label,
        itemStyle: { color: item.color }
      })),
      data: graph.value.nodes.map(node => ({
        ...node,
        symbolSize: 46,
        category: categories.value.findIndex(item => item.name === node.nodeType)
      })),
      links: graph.value.edges
    }]
  }, true)

  nextTick(() => highlightNode(selectedNodeId.value))
}

function highlightNode(nodeId) {
  if (!chartInstance.value || nodeId == null) return
  chartInstance.value.dispatchAction({ type: 'downplay', seriesIndex: 0 })
  const index = graph.value.nodes.findIndex(node => String(node.id) === String(nodeId))
  if (index < 0) return
  chartInstance.value.dispatchAction({
    type: 'highlight',
    seriesIndex: 0,
    dataIndex: index
  })
}

function resizeChart() {
  chartInstance.value?.resize()
}

onMounted(async () => {
  await loadGraph()
  window.addEventListener('resize', resizeChart)
})

onUnmounted(() => {
  window.removeEventListener('resize', resizeChart)
  chartInstance.value?.dispose()
  chartInstance.value = null
})
</script>

<style scoped>
.graph-page {
  display: grid;
  gap: 22px;
}

.graph-page--admin .graph-hero {
  background:
    radial-gradient(circle at top right, rgba(227, 179, 65, 0.18), transparent 32%),
    radial-gradient(circle at bottom left, rgba(142, 176, 197, 0.18), transparent 36%),
    linear-gradient(135deg, #22313d 0%, #334a5a 52%, #486676 100%);
}

.graph-page--admin .graph-stat-card,
.graph-page--admin .graph-filter-card,
.graph-page--admin .graph-card {
  border-radius: 24px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  background: rgba(255, 255, 255, 0.88);
  box-shadow: 0 18px 38px rgba(17, 28, 38, 0.08);
}

.graph-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgba(142, 176, 197, 0.18), transparent 34%),
    linear-gradient(135deg, #22313d 0%, #34495a 52%, #466173 100%);
  box-shadow: 0 28px 60px rgba(19, 33, 43, 0.16);
  color: #f7fbff;
}

.graph-hero__content {
  max-width: 760px;
}

.graph-hero__eyebrow,
.graph-section__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  letter-spacing: 0.1em;
}

.graph-hero h1 {
  margin: 18px 0 10px;
  font-size: 34px;
  line-height: 1.05;
  color: #ffffff;
}

.graph-hero p {
  margin: 0;
  max-width: 640px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(237, 244, 249, 0.82);
}

.graph-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.graph-hero__actions {
  display: grid;
  gap: 12px;
  align-content: start;
  width: 220px;
}

.graph-hero__ghost,
.graph-hero__primary {
  width: 100%;
  min-height: 46px;
}

.graph-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.graph-stat-card,
.graph-filter-card,
.graph-card {
  border-radius: 24px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 18px 38px rgba(17, 28, 38, 0.08);
}

.graph-stat-card {
  padding: 18px 20px;
}

.graph-stat-card span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #6b7b86;
}

.graph-stat-card strong {
  display: block;
  margin-top: 10px;
  font-size: 28px;
  line-height: 1.2;
  color: #1f3340;
}

.graph-stat-card p {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #72838d;
}

.graph-stat-card--red {
  background: linear-gradient(135deg, #fff5f6 0%, #ffe5e7 100%);
}

.graph-stat-card--blue {
  background: linear-gradient(135deg, #f3f8ff 0%, #dde9ff 100%);
}

.graph-stat-card--green {
  background: linear-gradient(135deg, #f1fcf7 0%, #dcf7e9 100%);
}

.graph-stat-card--orange {
  background: linear-gradient(135deg, #fff8ef 0%, #ffedd8 100%);
}

.graph-filter-card,
.graph-card {
  padding: 24px;
}

.graph-filter-card__head,
.graph-card__head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
}

.graph-filter-card__head {
  margin-bottom: 18px;
}

.graph-filter-card__head h2,
.graph-card__head h2 {
  margin: 10px 0 0;
  color: #1f3340;
}

.graph-filter-card__head h2 {
  font-size: 26px;
}

.graph-filter-card__head p,
.graph-card__head p {
  margin: 0;
  max-width: 360px;
  font-size: 13px;
  line-height: 1.7;
  color: #6e7f89;
  text-align: right;
}

.graph-section__eyebrow {
  background: rgba(112, 147, 168, 0.14);
  color: #6a889c;
}

.graph-filter-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.graph-filter-bar__field {
  width: 180px;
}

.graph-filter-bar__field--wide {
  width: 280px;
}

.graph-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.9fr) minmax(320px, 0.9fr);
  gap: 18px;
  align-items: start;
}

.graph-main-column,
.graph-side-column,
.graph-table-grid {
  display: grid;
  gap: 18px;
}

.graph-table-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.graph-chart {
  height: 640px;
  margin-top: 16px;
}

.graph-card--detail {
  min-height: 100%;
}

.node-detail {
  display: grid;
  gap: 14px;
}

.node-detail__hero {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 18px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(34, 51, 66, 0.06), rgba(112, 147, 168, 0.12));
}

.detail-name {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.detail-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 10px;
}

.node-detail__dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  box-shadow: 0 0 0 10px rgba(148, 163, 184, 0.14);
  flex-shrink: 0;
  margin-top: 6px;
}

.detail-line {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(246, 249, 250, 0.92);
  color: #475569;
  font-size: 14px;
}

.relation-section {
  margin-top: 4px;
}

.section-title {
  font-weight: 600;
  color: #111827;
  margin-bottom: 10px;
}

.relation-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.relation-evidence {
  color: #64748b;
  font-size: 13px;
  line-height: 1.7;
}

.graph-empty-inline {
  min-height: 320px;
  display: flex;
  align-items: center;
  justify-content: center;
}

@media (max-width: 1200px) {
  .graph-stat-grid,
  .graph-table-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .graph-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .graph-hero,
  .graph-filter-card__head,
  .graph-card__head,
  .node-detail__hero {
    flex-direction: column;
  }

  .graph-hero__actions {
    width: 100%;
  }

  .graph-filter-card__head p,
  .graph-card__head p {
    max-width: none;
    text-align: left;
  }
}

@media (max-width: 640px) {
  .graph-hero,
  .graph-filter-card,
  .graph-card {
    padding: 20px;
    border-radius: 22px;
  }

  .graph-stat-grid,
  .graph-table-grid {
    grid-template-columns: 1fr;
  }

  .graph-hero h1 {
    font-size: 28px;
  }

  .graph-chart {
    height: 460px;
  }
}
</style>
