<template>
  <div class="patient-trend-page">
    <section class="patient-trend-hero">
      <div class="patient-trend-hero__content">
        <span class="patient-trend-hero__eyebrow">Personal Trend View</span>
        <h1>我的健康趋势</h1>
        <p>把每日自填数据和体检记录放在同一页里查看，便于观察血压、体重、血糖和肾功能的长期变化，而不是只看单次结果。</p>
        <div class="patient-trend-hero__chips">
          <el-tag effect="plain" round>每日记录 {{ dailyRecords.length }}</el-tag>
          <el-tag effect="plain" round type="info">体检记录 {{ examRecords.length }}</el-tag>
          <el-tag effect="plain" round type="success">最近更新 {{ latestRecordDate || '--' }}</el-tag>
        </div>
      </div>
      <div class="patient-trend-hero__aside">
        <strong>{{ latestRecordDate || '--' }}</strong>
        <span>最近一次趋势数据日期</span>
      </div>
    </section>

    <section class="patient-trend-stat-grid">
      <article class="patient-trend-stat-card">
        <span>每日记录数</span>
        <strong>{{ dailyRecords.length }}</strong>
        <p>用于展示血压、体重和 BMI 这类连续自填指标。</p>
      </article>
      <article class="patient-trend-stat-card">
        <span>体检记录数</span>
        <strong>{{ examRecords.length }}</strong>
        <p>用于展示血糖、HbA1c、肌酐和尿酸等实验室结果。</p>
      </article>
      <article class="patient-trend-stat-card">
        <span>最近血压</span>
        <strong>{{ latestBloodPressure }}</strong>
        <p>帮助快速判断近期血压控制区间，不用先进入图表查看。</p>
      </article>
      <article class="patient-trend-stat-card">
        <span>最近空腹血糖</span>
        <strong>{{ latestFastingGlucose }}</strong>
        <p>来自最近一条体检记录，适合作为当前趋势入口。</p>
      </article>
    </section>

    <section class="patient-trend-layout" v-loading="loading">
      <div class="patient-trend-main">
        <div class="patient-trend-panel">
          <div class="patient-trend-panel__head">
            <div>
              <span class="patient-trend-panel__eyebrow">Trend Charts</span>
              <h2>趋势总览</h2>
              <p>同一页面内同时查看日常记录和体检趋势，观察指标是否稳定、改善或出现新的波动。</p>
            </div>
          </div>

          <div v-if="dailyRecords.length === 0 && examRecords.length === 0 && !loading" class="patient-trend-empty">
            <el-empty description="暂无健康记录数据，请先录入健康数据" :image-size="88" />
          </div>

          <div v-else class="patient-trend-chart-grid">
            <article class="patient-trend-chart-card">
              <div class="patient-trend-chart-card__head">
                <div>
                  <span class="patient-trend-panel__eyebrow">Daily Record</span>
                  <h3>血压趋势</h3>
                </div>
                <p>收缩压与舒张压</p>
              </div>
              <div v-if="dailyRecords.length > 0" ref="bpChart" class="patient-trend-chart"></div>
              <div v-else class="patient-trend-chart patient-trend-chart--empty">暂无每日血压数据</div>
            </article>

            <article class="patient-trend-chart-card">
              <div class="patient-trend-chart-card__head">
                <div>
                  <span class="patient-trend-panel__eyebrow">Daily Record</span>
                  <h3>体重 / BMI 趋势</h3>
                </div>
                <p>体重与体重指数</p>
              </div>
              <div v-if="dailyRecords.length > 0" ref="weightChart" class="patient-trend-chart"></div>
              <div v-else class="patient-trend-chart patient-trend-chart--empty">暂无体重/BMI数据</div>
            </article>

            <article class="patient-trend-chart-card">
              <div class="patient-trend-chart-card__head">
                <div>
                  <span class="patient-trend-panel__eyebrow">Medical Exam</span>
                  <h3>血糖趋势</h3>
                </div>
                <p>空腹血糖与 HbA1c</p>
              </div>
              <div v-if="examRecords.length > 0" ref="glucoseChart" class="patient-trend-chart"></div>
              <div v-else class="patient-trend-chart patient-trend-chart--empty">暂无血糖体检数据</div>
            </article>

            <article class="patient-trend-chart-card">
              <div class="patient-trend-chart-card__head">
                <div>
                  <span class="patient-trend-panel__eyebrow">Medical Exam</span>
                  <h3>肾功能趋势</h3>
                </div>
                <p>肌酐与尿酸</p>
              </div>
              <div v-if="examRecords.length > 0" ref="renalChart" class="patient-trend-chart"></div>
              <div v-else class="patient-trend-chart patient-trend-chart--empty">暂无肾功能体检数据</div>
            </article>
          </div>
        </div>
      </div>

      <aside class="patient-trend-side">
        <div class="patient-trend-side-card">
          <span class="patient-trend-side-card__eyebrow">Latest Snapshot</span>
          <h3>最近指标</h3>
          <div class="patient-trend-side-card__grid">
            <div>
              <span>血压</span>
              <strong>{{ latestBloodPressure }}</strong>
            </div>
            <div>
              <span>体重</span>
              <strong>{{ latestWeight }}</strong>
            </div>
            <div>
              <span>BMI</span>
              <strong>{{ latestBmi }}</strong>
            </div>
            <div>
              <span>血糖</span>
              <strong>{{ latestFastingGlucose }}</strong>
            </div>
          </div>
        </div>

        <div class="patient-trend-side-card">
          <span class="patient-trend-side-card__eyebrow">Data Source</span>
          <h3>数据来源说明</h3>
          <ul class="patient-trend-side-card__list">
            <li>血压、体重、BMI 来自每日健康填报。</li>
            <li>血糖、HbA1c、肌酐、尿酸来自体检或检验记录。</li>
            <li>如果数据较旧，趋势只能反映历史状态，不能代替实时判断。</li>
          </ul>
        </div>

        <div class="patient-trend-side-card">
          <span class="patient-trend-side-card__eyebrow">Reading Tips</span>
          <h3>查看建议</h3>
          <ul class="patient-trend-side-card__list">
            <li>更关注连续变化，不要只看某一次偶发波动。</li>
            <li>若某条曲线持续上升或明显异常，应及时联系医生。</li>
            <li>图表会在页面加载后统一渲染，切出页面不会触发额外轮询。</li>
          </ul>
        </div>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from '../../utils/element-plus-service'
import { dailyRecordApi, medicalExamApi } from '../../api'
import { useUserStore } from '../../stores/user'
import { echarts } from '../../utils/echarts-basic'

const userStore = useUserStore()
const dailyRecords = ref([])
const examRecords = ref([])
const loading = ref(false)

const bpChart = ref(null)
const weightChart = ref(null)
const glucoseChart = ref(null)
const renalChart = ref(null)

let bpInst = null
let weightInst = null
let glucoseInst = null
let renalInst = null

const latestRecordDate = computed(() => {
  const dates = [
    ...dailyRecords.value.map(item => item.recordDate),
    ...examRecords.value.map(item => item.examDate)
  ].filter(Boolean)
  if (dates.length === 0) return null
  return dates.sort((a, b) => new Date(b) - new Date(a))[0]
})

const latestDailyRecord = computed(() => dailyRecords.value[dailyRecords.value.length - 1] || null)
const latestExamRecord = computed(() => examRecords.value[examRecords.value.length - 1] || null)
const latestBloodPressure = computed(() => {
  if (!latestDailyRecord.value) return '--'
  return `${latestDailyRecord.value.systolicBp || '--'}/${latestDailyRecord.value.diastolicBp || '--'}`
})
const latestWeight = computed(() => latestDailyRecord.value?.weight ?? '--')
const latestBmi = computed(() => latestDailyRecord.value?.bmi ?? '--')
const latestFastingGlucose = computed(() => latestExamRecord.value?.fastingGlucose ?? '--')

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await loadData()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})

function disposeCharts() {
  ;[bpInst, weightInst, glucoseInst, renalInst].forEach(instance => instance?.dispose())
  bpInst = null
  weightInst = null
  glucoseInst = null
  renalInst = null
}

function handleResize() {
  ;[bpInst, weightInst, glucoseInst, renalInst].forEach(instance => instance?.resize())
}

async function loadData() {
  if (!userStore.patientId) return
  loading.value = true
  try {
    const [daily, exams] = await Promise.all([
      dailyRecordApi.getRecent(userStore.patientId),
      medicalExamApi.getByPatient(userStore.patientId)
    ])
    dailyRecords.value = daily.sort((a, b) => new Date(a.recordDate) - new Date(b.recordDate))
    examRecords.value = exams.sort((a, b) => new Date(a.examDate) - new Date(b.examDate))

    if (dailyRecords.value.length === 0 && examRecords.value.length === 0) {
      disposeCharts()
      return
    }

    await nextTick()
    renderCharts()
  } catch (e) {
    dailyRecords.value = []
    examRecords.value = []
    disposeCharts()
    ElMessage.error(e.response?.data?.message || '加载健康趋势失败')
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  renderBpChart()
  renderWeightChart()
  renderGlucoseChart()
  renderRenalChart()
}

function renderBpChart() {
  if (!bpChart.value || dailyRecords.value.length === 0) {
    bpInst?.dispose()
    bpInst = null
    return
  }
  bpInst?.dispose()
  bpInst = echarts.init(bpChart.value)
  const dates = dailyRecords.value.map(r => r.recordDate)
  bpInst.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['收缩压', '舒张压'] },
    grid: { left: 50, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', name: 'mmHg' },
    series: [
      {
        name: '收缩压',
        type: 'line',
        data: dailyRecords.value.map(r => r.systolicBp),
        smooth: true,
        itemStyle: { color: '#f56c6c' },
        markLine: { silent: true, data: [{ yAxis: 140, lineStyle: { color: '#f56c6c', type: 'dashed' } }] }
      },
      {
        name: '舒张压',
        type: 'line',
        data: dailyRecords.value.map(r => r.diastolicBp),
        smooth: true,
        itemStyle: { color: '#e6a23c' },
        markLine: { silent: true, data: [{ yAxis: 90, lineStyle: { color: '#e6a23c', type: 'dashed' } }] }
      }
    ]
  })
}

function renderWeightChart() {
  if (!weightChart.value || dailyRecords.value.length === 0) {
    weightInst?.dispose()
    weightInst = null
    return
  }
  weightInst?.dispose()
  weightInst = echarts.init(weightChart.value)
  const dates = dailyRecords.value.map(r => r.recordDate)
  weightInst.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['体重', 'BMI'] },
    grid: { left: 50, right: 50, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: dates },
    yAxis: [
      { type: 'value', name: 'kg', position: 'left' },
      { type: 'value', name: 'BMI', position: 'right', splitLine: { show: false } }
    ],
    series: [
      { name: '体重', type: 'bar', data: dailyRecords.value.map(r => r.weight), yAxisIndex: 0, itemStyle: { color: '#409eff' } },
      {
        name: 'BMI',
        type: 'line',
        data: dailyRecords.value.map(r => r.bmi),
        yAxisIndex: 1,
        smooth: true,
        itemStyle: { color: '#e6a23c' },
        markLine: {
          silent: true,
          data: [
            { yAxis: 24, lineStyle: { color: '#e6a23c', type: 'dashed' } },
            { yAxis: 28, lineStyle: { color: '#f56c6c', type: 'dashed' } }
          ]
        }
      }
    ]
  })
}

function renderGlucoseChart() {
  if (!glucoseChart.value || examRecords.value.length === 0) {
    glucoseInst?.dispose()
    glucoseInst = null
    return
  }
  glucoseInst?.dispose()
  glucoseInst = echarts.init(glucoseChart.value)
  const dates = examRecords.value.map(r => r.examDate)
  glucoseInst.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['空腹血糖', 'HbA1c'] },
    grid: { left: 50, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', name: 'mmol/L / %' },
    series: [
      {
        name: '空腹血糖',
        type: 'line',
        data: examRecords.value.map(r => r.fastingGlucose),
        smooth: true,
        itemStyle: { color: '#409eff' },
        markLine: { silent: true, data: [{ yAxis: 7.0, lineStyle: { color: '#f56c6c', type: 'dashed' } }] }
      },
      {
        name: 'HbA1c',
        type: 'line',
        data: examRecords.value.map(r => r.hba1c),
        smooth: true,
        itemStyle: { color: '#e6a23c' }
      }
    ]
  })
}

function renderRenalChart() {
  if (!renalChart.value || examRecords.value.length === 0) {
    renalInst?.dispose()
    renalInst = null
    return
  }
  renalInst?.dispose()
  renalInst = echarts.init(renalChart.value)
  const dates = examRecords.value.map(r => r.examDate)
  renalInst.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['肌酐', '尿酸'] },
    grid: { left: 50, right: 50, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: dates },
    yAxis: [
      { type: 'value', name: 'μmol/L', position: 'left' },
      { type: 'value', name: 'μmol/L', position: 'right', splitLine: { show: false } }
    ],
    series: [
      {
        name: '肌酐',
        type: 'line',
        data: examRecords.value.map(r => r.creatinine),
        yAxisIndex: 0,
        smooth: true,
        itemStyle: { color: '#67c23a' },
        markLine: { silent: true, data: [{ yAxis: 133, lineStyle: { color: '#e6a23c', type: 'dashed' } }] }
      },
      {
        name: '尿酸',
        type: 'line',
        data: examRecords.value.map(r => r.uricAcid),
        yAxisIndex: 1,
        smooth: true,
        itemStyle: { color: '#f56c6c' },
        markLine: { silent: true, data: [{ yAxis: 420, lineStyle: { color: '#f56c6c', type: 'dashed' } }] }
      }
    ]
  })
}
</script>

<style scoped>
.patient-trend-page {
  display: grid;
  gap: 22px;
}

.patient-trend-hero {
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

.patient-trend-hero__content {
  max-width: 760px;
}

.patient-trend-hero__eyebrow,
.patient-trend-panel__eyebrow,
.patient-trend-side-card__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  font-size: 12px;
  letter-spacing: 0.1em;
}

.patient-trend-hero__eyebrow {
  background: rgba(255, 255, 255, 0.12);
}

.patient-trend-hero h1 {
  margin: 18px 0 10px;
  font-size: 34px;
  line-height: 1.05;
}

.patient-trend-hero p {
  margin: 0;
  max-width: 640px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(237, 244, 249, 0.82);
}

.patient-trend-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.patient-trend-hero__aside {
  min-width: 220px;
  display: grid;
  align-content: center;
  justify-items: end;
  gap: 6px;
  text-align: right;
}

.patient-trend-hero__aside strong {
  font-size: 34px;
  line-height: 1;
}

.patient-trend-hero__aside span {
  font-size: 13px;
  color: rgba(237, 244, 249, 0.82);
}

.patient-trend-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.patient-trend-stat-card,
.patient-trend-panel,
.patient-trend-chart-card,
.patient-trend-side-card {
  border-radius: 24px;
  border: 1px solid rgba(39, 81, 104, 0.08);
  background: rgba(255, 255, 255, 0.9);
  box-shadow: 0 18px 38px rgba(17, 28, 38, 0.08);
}

.patient-trend-stat-card {
  padding: 18px 20px;
}

.patient-trend-stat-card span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #6b7b86;
}

.patient-trend-stat-card strong {
  display: block;
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.2;
  color: #1f3340;
}

.patient-trend-stat-card p {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #72838d;
}

.patient-trend-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  align-items: start;
}

.patient-trend-panel,
.patient-trend-side-card {
  padding: 22px;
}

.patient-trend-panel__head,
.patient-trend-chart-card__head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
}

.patient-trend-panel__head {
  margin-bottom: 18px;
}

.patient-trend-panel__head h2,
.patient-trend-chart-card__head h3,
.patient-trend-side-card h3 {
  margin: 10px 0 0;
  color: #1f3340;
}

.patient-trend-panel__head p,
.patient-trend-chart-card__head p,
.patient-trend-side-card__list {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.8;
  color: #6e7f89;
}

.patient-trend-panel__eyebrow,
.patient-trend-side-card__eyebrow {
  background: rgba(203, 229, 238, 0.46);
  color: #2e6f8b;
}

.patient-trend-empty {
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 22px;
  background: rgba(247, 250, 251, 0.92);
}

.patient-trend-chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.patient-trend-chart-card {
  padding: 20px;
  background: rgba(247, 250, 251, 0.92);
}

.patient-trend-chart {
  height: 320px;
  margin-top: 16px;
}

.patient-trend-chart--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  color: #7b8c96;
  font-size: 14px;
}

.patient-trend-side {
  display: grid;
  gap: 16px;
}

.patient-trend-side-card__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 16px;
}

.patient-trend-side-card__grid div {
  padding: 14px 12px;
  border-radius: 18px;
  background: rgba(203, 229, 238, 0.28);
}

.patient-trend-side-card__grid span {
  display: block;
  font-size: 12px;
  color: #6b7b86;
}

.patient-trend-side-card__grid strong {
  display: block;
  margin-top: 6px;
  font-size: 18px;
  color: #1f3340;
}

.patient-trend-side-card__list {
  padding-left: 18px;
}

@media (max-width: 1200px) {
  .patient-trend-layout {
    grid-template-columns: 1fr;
  }

  .patient-trend-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .patient-trend-chart-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .patient-trend-hero,
  .patient-trend-panel__head,
  .patient-trend-chart-card__head {
    flex-direction: column;
    align-items: stretch;
  }

  .patient-trend-hero {
    padding: 20px;
  }

  .patient-trend-hero__aside {
    justify-items: start;
    text-align: left;
  }

  .patient-trend-hero h1 {
    font-size: 28px;
  }

  .patient-trend-stat-grid,
  .patient-trend-side-card__grid {
    grid-template-columns: 1fr;
  }
}
</style>
