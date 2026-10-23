<template>
  <div class="patient-list-shell">
    <section class="patient-list-overview">
      <div class="patient-list-overview__stats">
        <div class="patient-list-stat">
          <span>当前结果</span>
          <strong>{{ patients.length }}</strong>
        </div>
        <div class="patient-list-stat">
          <span>搜索关键词</span>
          <strong>{{ searchName || '全部' }}</strong>
        </div>
        <div class="patient-list-stat patient-list-stat--attention">
          <span>需处理病历</span>
          <strong>{{ patientsNeedingPaperCaseAction }}</strong>
        </div>
      </div>
      <div class="patient-list-overview__toolbar">
        <div class="patient-list-overview__copy">
          <span class="patient-list-overview__eyebrow">患者列表</span>
          <h2>当前管理患者</h2>
        </div>
        <div class="patient-list-toolbar">
          <el-input
            v-model="searchName"
            placeholder="按姓名搜索"
            clearable
            class="patient-list-search"
            @clear="loadPatients"
            @keyup.enter="searchPatients"
          />
          <el-button type="primary" @click="searchPatients">搜索</el-button>
        </div>
      </div>
    </section>

    <el-card shadow="never" class="patient-list-card">
      <template #header>
        <div class="patient-list-card__head">
          <div class="patient-list-card__title">患者清单</div>
        </div>
      </template>

      <el-table :data="patients" stripe v-loading="loading" class="patient-table" :empty-text="patientTableEmptyText">
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column prop="name" label="姓名" min-width="110" />
        <el-table-column prop="gender" label="性别" width="72" />
        <el-table-column prop="birthDate" label="出生日期" width="120" />
        <el-table-column prop="phone" label="电话" width="140" />
        <el-table-column prop="idCard" label="身份证号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
        <el-table-column label="病历处理" min-width="280">
          <template #default="{ row }">
            <div class="patient-paper-case-cell">
              <div class="patient-paper-case-cell__tags">
                <el-tag v-if="row.paperCaseAttentionCount > 0" size="small" type="danger" effect="plain">
                  关注 {{ row.paperCaseAttentionCount }}
                </el-tag>
                <el-tag v-if="row.paperCasePendingReviewCount > 0" size="small" type="warning" effect="plain">
                  待审核 {{ row.paperCasePendingReviewCount }}
                </el-tag>
                <el-tag v-if="row.paperCaseFailedSyncCount > 0" size="small" type="danger" effect="plain">
                  归档异常 {{ row.paperCaseFailedSyncCount }}
                </el-tag>
                <el-tag v-if="row.paperCaseTotalCount > 0" size="small" effect="plain">
                  总病历 {{ row.paperCaseTotalCount }}
                </el-tag>
                <span v-if="!row.paperCaseTotalCount" class="patient-paper-case-cell__empty">暂无纸质病历</span>
              </div>
              <el-button
                v-if="row.paperCaseTotalCount > 0"
                class="patient-paper-case-cell__action"
                link
                type="primary"
                @click="goToPaperCaseReview(row.id)"
              >
                去处理
              </el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="patient-table__actions">
              <el-button
                v-if="row.paperCaseAttentionCount > 0"
                class="patient-table__detail-button"
                type="warning"
                plain
                @click="goToPaperCaseReview(row.id)"
              >
                去处理
              </el-button>
              <el-button class="patient-table__detail-button" type="primary" @click="goToPatient(row.id)">详情</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { patientApi } from '../../api'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()
const patients = ref([])
const loading = ref(false)
const searchName = ref('')
const patientsNeedingPaperCaseAction = computed(() =>
  patients.value.filter(item => Number(item?.paperCaseAttentionCount || 0) > 0).length
)
const patientTableEmptyText = computed(() => {
  if (loading.value) {
    return '正在加载患者数据...'
  }
  if (searchName.value) {
    return `未找到姓名包含“${searchName.value}”的患者`
  }
  return '当前医生名下暂无患者，请检查患者归属或重新登录刷新'
})

onMounted(() => loadPatients())

async function loadPatients() {
  loading.value = true
  try {
    patients.value = await patientApi.list({ doctorId: userStore.doctorId })
  } finally {
    loading.value = false
  }
}

async function searchPatients() {
  if (!searchName.value) {
    await loadPatients()
    return
  }
  loading.value = true
  try {
    patients.value = await patientApi.list({ name: searchName.value, doctorId: userStore.doctorId })
  } finally {
    loading.value = false
  }
}

function goToPatient(patientId) {
  if (!patientId) return
  router.push(`/doctor/patients/${patientId}`)
}

function goToPaperCaseReview(patientId) {
  if (!patientId) return
  router.push({ name: 'DoctorPaperCaseReview', query: { patientId: String(patientId) } })
}
</script>

<style scoped>
.patient-list-shell{display:grid;gap:18px}
.patient-list-overview,.patient-list-card{border:1px solid rgba(31,51,64,.08);border-radius:28px;box-shadow:0 18px 34px rgba(17,28,38,.08)}
.patient-list-overview{display:grid;gap:16px;padding:18px 20px;background:radial-gradient(circle at top right,rgba(201,223,235,.32),transparent 24%),linear-gradient(135deg,rgba(255,255,255,.92),rgba(241,245,247,.9))}
.patient-list-overview__stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}
.patient-list-overview__toolbar{display:flex;align-items:center;justify-content:space-between;gap:14px;flex-wrap:wrap}
.patient-list-overview__copy{display:grid;gap:6px}
.patient-list-overview__eyebrow{display:inline-block;font-size:12px;font-weight:700;letter-spacing:.12em;color:rgba(43,108,143,.72)}
.patient-list-overview__copy h2{margin:0;color:#1f3340;font-size:22px;line-height:1.2}
.patient-list-stat{padding:16px 18px;border-radius:22px;background:rgba(247,243,236,.84)}
.patient-list-stat span{display:block;font-size:12px;color:#66757f}
.patient-list-stat strong{display:block;margin:10px 0 6px;font-size:24px;color:#152635}
.patient-list-stat p{margin:0;line-height:1.7;color:#66757f}
.patient-list-stat--attention{background:linear-gradient(135deg,rgba(255,245,232,.92),rgba(255,236,223,.92))}
.patient-list-card{background:rgba(255,255,255,.84)}
.patient-list-card :deep(.el-card__header){padding:22px 24px 18px;border-bottom:1px solid rgba(31,51,64,.06)}
.patient-list-card :deep(.el-card__body){padding:22px 24px 24px}
.patient-list-card__head{display:flex;align-items:center;justify-content:space-between;gap:14px}
.patient-list-card__title{font-size:20px;font-weight:800;color:#1f3340}
.patient-list-toolbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap}
.patient-list-search{width:240px}
.patient-paper-case-cell{display:flex;align-items:center;justify-content:space-between;gap:12px}
.patient-paper-case-cell__tags{display:flex;flex-wrap:wrap;gap:6px}
.patient-paper-case-cell__action{padding:0}
.patient-paper-case-cell__empty{font-size:12px;color:#8a99a3}
.patient-table :deep(.el-table){border-radius:18px;overflow:hidden}
.patient-table :deep(th.el-table__cell){background:rgba(247,243,236,.8);color:#4a5b64}
.patient-table :deep(td.el-table__cell){color:#31434d}
.patient-table__detail-button{border-radius:999px}
.patient-table__actions{display:flex;justify-content:flex-end;gap:8px}
@media (max-width:1100px){.patient-list-overview__stats{grid-template-columns:1fr}}
@media (max-width:720px){.patient-list-overview,.patient-list-card :deep(.el-card__header),.patient-list-card :deep(.el-card__body){padding-left:18px;padding-right:18px}.patient-list-overview__toolbar,.patient-list-card__head{flex-direction:column;align-items:flex-start}.patient-list-toolbar,.patient-list-search{width:100%}}
</style>
