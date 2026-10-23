<template>
  <div class="trend-page">
    <section class="trend-hero">
      <div class="trend-hero__content">
        <span class="trend-hero__eyebrow">Trend Monitoring</span>
        <h1>健康趋势工作台</h1>
        <p>
          聚合患者每日自填数据和体检记录，从血压、体重、血糖、肾功能、血脂到血常规，帮助医生快速观察波动和长期变化。
        </p>
        <div class="trend-hero__chips">
          <el-tag effect="plain" round>当前患者：{{ selectedPatientName }}</el-tag>
          <el-tag effect="plain" round type="info">每日记录 {{ dailyRecords.length }}</el-tag>
          <el-tag effect="plain" round type="success">体检记录 {{ examRecords.length }}</el-tag>
          <el-tag effect="plain" round type="warning">最近更新 {{ latestRecordDate || '--' }}</el-tag>
        </div>
      </div>

      <div class="trend-hero__actions">
        <el-select v-model="selectedPatient" placeholder="选择患者" filterable class="trend-hero__select" @change="loadData">
          <el-option v-for="p in patients" :key="p.id" :label="p.name" :value="p.id" />
        </el-select>
      </div>
    </section>

    <section class="trend-stat-grid">
      <article class="trend-stat-card">
        <span>目标患者</span>
        <strong>{{ selectedPatientName }}</strong>
        <p>{{ selectedPatient ? '当前图表均按该患者历史记录绘制' : '请选择患者后加载趋势图' }}</p>
      </article>
      <article class="trend-stat-card">
        <span>每日记录数</span>
        <strong>{{ dailyRecords.length }}</strong>
        <p>用于观察血压、体重和 BMI 等连续趋势</p>
      </article>
      <article class="trend-stat-card">
        <span>体检记录数</span>
        <strong>{{ examRecords.length }}</strong>
        <p>用于观察血糖、肾功能、血脂和血常规指标变化</p>
      </article>
      <article class="trend-stat-card">
        <span>最近记录日期</span>
        <strong>{{ latestRecordDate || '--' }}</strong>
        <p>帮助判断当前趋势数据是否足够新</p>
      </article>
    </section>

    <section class="trend-panel" v-loading="loading">
      <div class="trend-panel__head">
        <div>
          <span class="trend-panel__eyebrow">Visual Monitoring</span>
          <h2>患者趋势总览</h2>
        </div>
        <p>选择患者后，系统会同时渲染日常指标和体检指标的时间序列图。</p>
      </div>

      <div v-if="!selectedPatient" class="trend-empty">
        <el-empty description="请选择患者查看健康趋势" />
      </div>

      <template v-else-if="dailyRecords.length > 0 || examRecords.length > 0">
        <div class="trend-chart-grid">
          <article class="trend-chart-card">
            <div class="trend-chart-card__head">
              <div>
                <span class="trend-panel__eyebrow">Daily Record</span>
                <h3>血压趋势</h3>
              </div>
              <p>每日自填数据</p>
            </div>
            <div v-if="dailyRecords.length > 0" ref="bpChart" class="trend-chart"></div>
            <div v-else class="trend-chart trend-chart--empty">暂无每日血压数据</div>
          </article>

          <article class="trend-chart-card">
            <div class="trend-chart-card__head">
              <div>
                <span class="trend-panel__eyebrow">Daily Record</span>
                <h3>体重 / BMI 趋势</h3>
              </div>
              <p>每日自填数据</p>
            </div>
            <div v-if="dailyRecords.length > 0" ref="weightChart" class="trend-chart"></div>
            <div v-else class="trend-chart trend-chart--empty">暂无体重/BMI数据</div>
          </article>

          <article class="trend-chart-card">
            <div class="trend-chart-card__head">
              <div>
                <span class="trend-panel__eyebrow">Medical Exam</span>
                <h3>血糖趋势</h3>
              </div>
              <p>体检数据</p>
            </div>
            <div v-if="examRecords.length > 0" ref="glucoseChart" class="trend-chart"></div>
            <div v-else class="trend-chart trend-chart--empty">暂无血糖体检数据</div>
          </article>

          <article class="trend-chart-card">
            <div class="trend-chart-card__head">
              <div>
                <span class="trend-panel__eyebrow">Medical Exam</span>
                <h3>肾功能趋势</h3>
              </div>
              <p>体检数据</p>
            </div>
            <div v-if="examRecords.length > 0" ref="renalChart" class="trend-chart"></div>
            <div v-else class="trend-chart trend-chart--empty">暂无肾功能体检数据</div>
          </article>

          <article class="trend-chart-card">
            <div class="trend-chart-card__head">
              <div>
                <span class="trend-panel__eyebrow">Medical Exam</span>
                <h3>血脂趋势</h3>
              </div>
              <p>体检数据</p>
            </div>
            <div v-if="examRecords.length > 0" ref="lipidChart" class="trend-chart"></div>
            <div v-else class="trend-chart trend-chart--empty">暂无血脂体检数据</div>
          </article>

          <article class="trend-chart-card">
            <div class="trend-chart-card__head">
              <div>
                <span class="trend-panel__eyebrow">Medical Exam</span>
                <h3>血常规趋势</h3>
              </div>
              <p>体检数据</p>
            </div>
            <div v-if="examRecords.length > 0" ref="bloodChart" class="trend-chart"></div>
            <div v-else class="trend-chart trend-chart--empty">暂无血常规体检数据</div>
          </article>
        </div>
      </template>

      <div v-else class="trend-empty">
        <el-empty description="暂无健康记录数据" />
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, onUnmounted, computed } from 'vue'
import { patientApi, dailyRecordApi, medicalExamApi } from '../../api'
import { useUserStore } from '../../stores/user'
import { echarts } from '../../utils/echarts-basic'

const userStore = useUserStore()
const patients = ref([])
const selectedPatient = ref(null)
const dailyRecords = ref([])
const examRecords = ref([])
const loading = ref(false)

const bpChart = ref(null)
const weightChart = ref(null)
const glucoseChart = ref(null)
const renalChart = ref(null)
const lipidChart = ref(null)
const bloodChart = ref(null)

let bpInst = null
let weightInst = null
let glucoseInst = null
let renalInst = null
let lipidInst = null
let bloodInst = null

const selectedPatientName = computed(() => {
  const patient = patients.value.find(item => item.id === selectedPatient.value)
  return patient?.name || '未选择患者'
})

const latestRecordDate = computed(() => {
  const dates = [
    ...dailyRecords.value.map(item => item.recordDate),
    ...examRecords.value.map(item => item.examDate)
  ].filter(Boolean)
  if (dates.length === 0) return null
  return dates.sort((a, b) => new Date(b) - new Date(a))[0]
})

onMounted(async () => {
  patients.value = await patientApi.list({ doctorId: userStore.doctorId })
  window.addEventListener('resize', handleResize)
  if (patients.value.length > 0) {
    selectedPatient.value = patients.value[0].id
    await loadData()
  }
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  disposeCharts()
})

function disposeCharts() {
  ;[bpInst, weightInst, glucoseInst, renalInst, lipidInst, bloodInst].forEach(instance => instance?.dispose())
  bpInst = null
  weightInst = null
  glucoseInst = null
  renalInst = null
  lipidInst = null
  bloodInst = null
}

function handleResize() {
  ;[bpInst, weightInst, glucoseInst, renalInst, lipidInst, bloodInst].forEach(instance => instance?.resize())
}

async function loadData() {
  if (!selectedPatient.value) {
    dailyRecords.value = []
    examRecords.value = []
    disposeCharts()
    return
  }

  loading.value = true
  try {
    const [daily, exams] = await Promise.all([
      dailyRecordApi.getByPatient(selectedPatient.value),
      medicalExamApi.getByPatient(selectedPatient.value)
    ])

    dailyRecords.value = daily.sort((a, b) => new Date(a.recordDate) - new Date(b.recordDate))
    examRecords.value = exams.sort((a, b) => new Date(a.examDate) - new Date(b.examDate))

    if (dailyRecords.value.length === 0 && examRecords.value.length === 0) {
      disposeCharts()
      return
    }

    await nextTick()
    renderCharts()
  } finally {
    loading.value = false
  }
}

function renderCharts() {
  renderBpChart()
  renderWeightChart()
  renderGlucoseChart()
  renderRenalChart()
  renderLipidChart()
  renderBloodChart()
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
        itemStyle: { color: '#F56C6C' },
        markLine: { silent: true, data: [{ yAxis: 140, lineStyle: { color: '#F56C6C', type: 'dashed' } }] }
      },
      {
        name: '舒张压',
        type: 'line',
        data: dailyRecords.value.map(r => r.diastolicBp),
        smooth: true,
        itemStyle: { color: '#E6A23C' },
        markLine: { silent: true, data: [{ yAxis: 90, lineStyle: { color: '#E6A23C', type: 'dashed' } }] }
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
      { name: '体重', type: 'bar', data: dailyRecords.value.map(r => r.weight), yAxisIndex: 0, itemStyle: { color: '#409EFF' } },
      {
        name: 'BMI',
        type: 'line',
        data: dailyRecords.value.map(r => r.bmi),
        yAxisIndex: 1,
        smooth: true,
        itemStyle: { color: '#E6A23C' },
        markLine: {
          silent: true,
          data: [
            { yAxis: 24, lineStyle: { color: '#E6A23C', type: 'dashed' } },
            { yAxis: 28, lineStyle: { color: '#F56C6C', type: 'dashed' } }
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
        itemStyle: { color: '#409EFF' },
        markLine: { silent: true, data: [{ yAxis: 7.0, lineStyle: { color: '#F56C6C', type: 'dashed' } }] }
      },
      { name: 'HbA1c', type: 'line', data: examRecords.value.map(r => r.hba1c), smooth: true, itemStyle: { color: '#E6A23C' } }
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
        itemStyle: { color: '#67C23A' },
        markLine: { silent: true, data: [{ yAxis: 133, lineStyle: { color: '#E6A23C', type: 'dashed' } }] }
      },
      {
        name: '尿酸',
        type: 'line',
        data: examRecords.value.map(r => r.uricAcid),
        yAxisIndex: 1,
        smooth: true,
        itemStyle: { color: '#F56C6C' },
        markLine: { silent: true, data: [{ yAxis: 420, lineStyle: { color: '#F56C6C', type: 'dashed' } }] }
      }
    ]
  })
}

function renderLipidChart() {
  if (!lipidChart.value || examRecords.value.length === 0) {
    lipidInst?.dispose()
    lipidInst = null
    return
  }
  lipidInst?.dispose()
  lipidInst = echarts.init(lipidChart.value)
  const dates = examRecords.value.map(r => r.examDate)
  lipidInst.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['总胆固醇', '甘油三酯', 'HDL-C', 'LDL-C'] },
    grid: { left: 50, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: dates },
    yAxis: { type: 'value', name: 'mmol/L' },
    series: [
      { name: '总胆固醇', type: 'line', data: examRecords.value.map(r => r.totalCholesterol), smooth: true, itemStyle: { color: '#409EFF' } },
      { name: '甘油三酯', type: 'line', data: examRecords.value.map(r => r.triglyceride), smooth: true, itemStyle: { color: '#E6A23C' } },
      { name: 'HDL-C', type: 'line', data: examRecords.value.map(r => r.hdlCholesterol), smooth: true, itemStyle: { color: '#67C23A' } },
      {
        name: 'LDL-C',
        type: 'line',
        data: examRecords.value.map(r => r.ldlCholesterol),
        smooth: true,
        itemStyle: { color: '#F56C6C' },
        markLine: { silent: true, data: [{ yAxis: 3.4, lineStyle: { color: '#F56C6C', type: 'dashed' } }] }
      }
    ]
  })
}

function renderBloodChart() {
  if (!bloodChart.value || examRecords.value.length === 0) {
    bloodInst?.dispose()
    bloodInst = null
    return
  }
  bloodInst?.dispose()
  bloodInst = echarts.init(bloodChart.value)
  const dates = examRecords.value.map(r => r.examDate)
  bloodInst.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['血红蛋白', '白细胞', '血小板'] },
    grid: { left: 50, right: 50, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: dates },
    yAxis: [
      { type: 'value', name: 'g/L', position: 'left' },
      { type: 'value', name: '×10⁹/L', position: 'right', splitLine: { show: false } }
    ],
    series: [
      { name: '血红蛋白', type: 'line', data: examRecords.value.map(r => r.hemoglobin), yAxisIndex: 0, smooth: true, itemStyle: { color: '#F56C6C' } },
      { name: '白细胞', type: 'line', data: examRecords.value.map(r => r.wbc), yAxisIndex: 1, smooth: true, itemStyle: { color: '#409EFF' } },
      { name: '血小板', type: 'line', data: examRecords.value.map(r => r.platelet), yAxisIndex: 1, smooth: true, itemStyle: { color: '#67C23A' } }
    ]
  })
}
</script>

<style scoped>
.trend-page {
  display: grid;
  gap: 22px;
}

.trend-hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  padding: 28px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top right, rgba(139, 173, 196, 0.18), transparent 34%),
    linear-gradient(135deg, #21313d 0%, #33495a 52%, #466173 100%);
  box-shadow: 0 28px 60px rgba(19, 33, 43, 0.16);
  color: #f7fbff;
}

.trend-hero__content {
  max-width: 760px;
}

.trend-hero__eyebrow,
.trend-panel__eyebrow {
  display: inline-flex;
  align-items: center;
  padding: 6px 11px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  font-size: 12px;
  letter-spacing: 0.1em;
}

.trend-hero h1 {
  margin: 18px 0 10px;
  font-size: 34px;
  line-height: 1.05;
  color: #ffffff;
}

.trend-hero p {
  margin: 0;
  max-width: 640px;
  font-size: 14px;
  line-height: 1.8;
  color: rgba(237, 244, 249, 0.82);
}

.trend-hero__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.trend-hero__actions {
  width: 280px;
}

.trend-hero__select {
  width: 100%;
}

.trend-stat-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.trend-stat-card,
.trend-chart-card,
.trend-panel {
  border-radius: 24px;
  border: 1px solid rgba(31, 51, 64, 0.08);
  background: rgba(255, 255, 255, 0.86);
  box-shadow: 0 18px 38px rgba(17, 28, 38, 0.08);
}

.trend-stat-card {
  padding: 18px 20px;
}

.trend-stat-card span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.08em;
  color: #6b7b86;
}

.trend-stat-card strong {
  display: block;
  margin-top: 10px;
  font-size: 24px;
  line-height: 1.2;
  color: #1f3340;
}

.trend-stat-card p {
  margin: 10px 0 0;
  font-size: 13px;
  line-height: 1.7;
  color: #72838d;
}

.trend-panel {
  padding: 24px;
}

.trend-panel__head,
.trend-chart-card__head {
  display: flex;
  justify-content: space-between;
  gap: 18px;
}

.trend-panel__head {
  margin-bottom: 18px;
}

.trend-panel__head h2,
.trend-chart-card__head h3 {
  margin: 10px 0 0;
  color: #1f3340;
}

.trend-panel__head h2 {
  font-size: 26px;
}

.trend-panel__head p,
.trend-chart-card__head p {
  margin: 0;
  max-width: 360px;
  font-size: 13px;
  line-height: 1.7;
  color: #6e7f89;
  text-align: right;
}

.trend-empty {
  min-height: 260px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 22px;
  background: rgba(246, 249, 250, 0.9);
}

.trend-chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.trend-chart-card {
  padding: 20px;
}

.trend-panel__eyebrow {
  background: rgba(112, 147, 168, 0.14);
  color: #6a889c;
}

.trend-chart {
  height: 320px;
  margin-top: 16px;
}

.trend-chart--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 18px;
  background: rgba(246, 249, 250, 0.9);
  color: #7b8c96;
  font-size: 14px;
}

@media (max-width: 1200px) {
  .trend-stat-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 960px) {
  .trend-hero,
  .trend-panel__head,
  .trend-chart-card__head {
    flex-direction: column;
  }

  .trend-hero__actions {
    width: 100%;
  }

  .trend-panel__head p,
  .trend-chart-card__head p {
    max-width: none;
    text-align: left;
  }

  .trend-chart-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .trend-hero,
  .trend-panel,
  .trend-chart-card {
    padding: 20px;
    border-radius: 22px;
  }

  .trend-stat-grid {
    grid-template-columns: 1fr;
  }

  .trend-hero h1 {
    font-size: 28px;
  }
}
</style>
