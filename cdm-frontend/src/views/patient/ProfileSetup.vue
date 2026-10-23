<template>
  <div class="setup-page">
    <div class="setup-shell">
      <div class="intro">
        <p class="eyebrow">Profile Setup</p>
        <h1>完善患者资料</h1>
        <p class="lead">首次登录后请补充基础信息，系统会据此生成个性化的随访与健康管理建议。</p>
      </div>

      <el-card class="setup-card" shadow="never">
        <el-alert
          v-if="doctorLoadError"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 16px"
          title="医生列表暂不可用"
          :description="doctorLoadError"
        />

        <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
          <el-row :gutter="18">
            <el-col :md="12" :sm="24">
              <el-form-item label="姓名" prop="name">
                <el-input v-model="form.name" placeholder="请输入姓名" />
              </el-form-item>
            </el-col>
            <el-col :md="12" :sm="24">
              <el-form-item label="性别" prop="gender">
                <el-radio-group v-model="form.gender">
                  <el-radio value="男">男</el-radio>
                  <el-radio value="女">女</el-radio>
                </el-radio-group>
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="18">
            <el-col :md="12" :sm="24">
              <el-form-item label="出生日期" prop="birthDate">
                <el-date-picker v-model="form.birthDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
              </el-form-item>
            </el-col>
            <el-col :md="12" :sm="24">
              <el-form-item label="联系电话" prop="phone">
                <el-input v-model="form.phone" placeholder="请输入手机号" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="居住地址" prop="address">
            <el-input v-model="form.address" placeholder="请输入当前居住地址" />
          </el-form-item>

          <el-form-item label="管理医生">
            <el-select v-model="form.assignedDoctorId" placeholder="可选，后续也可在个人中心修改" style="width: 100%" clearable>
              <el-option
                v-for="doctor in doctors"
                :key="doctor.id"
                :label="`${doctor.name}${doctor.department ? `（${doctor.department}）` : ''}`"
                :value="doctor.id"
              />
            </el-select>
          </el-form-item>

          <el-row :gutter="18">
            <el-col :md="12" :sm="24">
              <el-form-item label="紧急联系人">
                <el-input v-model="form.emergencyContact" placeholder="请输入紧急联系人姓名" />
              </el-form-item>
            </el-col>
            <el-col :md="12" :sm="24">
              <el-form-item label="紧急电话">
                <el-input v-model="form.emergencyPhone" placeholder="请输入紧急联系电话" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-divider>病史档案（选填）</el-divider>

          <el-row :gutter="18">
            <el-col :md="12" :sm="24">
              <el-form-item label="既往患病情况">
                <el-input v-model="medicalHistory.pastDiseases" type="textarea" :rows="3" placeholder="如高血压、糖尿病等" />
              </el-form-item>
            </el-col>
            <el-col :md="12" :sm="24">
              <el-form-item label="手术史">
                <el-input v-model="medicalHistory.surgicalHistory" type="textarea" :rows="3" placeholder="如阑尾切除术后" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="18">
            <el-col :md="12" :sm="24">
              <el-form-item label="药物过敏">
                <el-input v-model="medicalHistory.drugAllergies" type="textarea" :rows="3" placeholder="如青霉素过敏" />
              </el-form-item>
            </el-col>
            <el-col :md="12" :sm="24">
              <el-form-item label="食物过敏">
                <el-input v-model="medicalHistory.foodAllergies" type="textarea" :rows="3" placeholder="如海鲜过敏" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-row :gutter="18">
            <el-col :md="12" :sm="24">
              <el-form-item label="预防接种">
                <el-input v-model="medicalHistory.immunizationHistory" type="textarea" :rows="3" placeholder="如流感疫苗已接种" />
              </el-form-item>
            </el-col>
            <el-col :md="12" :sm="24">
              <el-form-item label="家族史">
                <el-input v-model="medicalHistory.familyHistory" type="textarea" :rows="3" placeholder="如父母有高血压史" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item label="其他备注">
            <el-input v-model="medicalHistory.otherNotes" type="textarea" :rows="4" placeholder="可填写其他需要提醒医生和系统的背景信息" />
          </el-form-item>

          <el-form-item>
            <div class="action-row">
              <el-button @click="router.replace('/login')">返回登录</el-button>
              <el-button type="primary" :loading="submitting" @click="handleSubmit">保存并进入患者端</el-button>
            </div>
          </el-form-item>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from '../../utils/element-plus-service'
import { authApi, patientApi, profileApi } from '../../api'
import { useUserStore } from '../../stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const submitting = ref(false)
const doctors = ref([])
const doctorLoadError = ref('')

const form = reactive({
  name: '',
  gender: '',
  birthDate: '',
  phone: '',
  address: '',
  assignedDoctorId: null,
  emergencyContact: '',
  emergencyPhone: ''
})

const medicalHistory = reactive(emptyMedicalHistory())

const rules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  birthDate: [{ required: true, message: '请选择出生日期', trigger: 'change' }],
  phone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
  address: [{ required: true, message: '请输入居住地址', trigger: 'blur' }]
}

onMounted(async () => {
  if (!userStore.patientId) {
    ElMessage.warning('未获取到患者身份，请重新登录')
    router.replace('/login')
    return
  }
  await Promise.all([loadDoctors(), loadProfile()])
})

async function loadDoctors() {
  doctorLoadError.value = ''
  try {
    doctors.value = await authApi.getDoctorsList()
  } catch (e) {
    doctors.value = []
    doctorLoadError.value = e.response?.data?.message || '当前后端未提供医生下拉接口。'
  }
}

async function loadProfile() {
  try {
    const profile = await profileApi.getPatient(userStore.patientId)
    form.name = profile.name || userStore.username || ''
    form.gender = profile.gender || ''
    form.birthDate = profile.birthDate || ''
    form.phone = profile.phone || ''
    form.address = profile.address || ''
    form.assignedDoctorId = profile.assignedDoctorId || userStore.assignedDoctorId || null
    form.emergencyContact = profile.emergencyContact || ''
    form.emergencyPhone = profile.emergencyPhone || ''
    Object.assign(medicalHistory, normalizeMedicalHistory(profile.medicalHistory))
  } catch (_e) {
    form.name = userStore.username || ''
  }
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const result = await patientApi.completeProfile(userStore.patientId, {
      ...form,
      assignedDoctorId: form.assignedDoctorId || null,
      medicalHistory: { ...medicalHistory }
    })
    userStore.setProfileCompleted(true)
    userStore.setUsername(form.name)
    userStore.setAssignedDoctorId(result.assignedDoctorId || form.assignedDoctorId || null)
    ElMessage.success('资料已完善')
    router.replace('/patient')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

function emptyMedicalHistory() {
  return {
    pastDiseases: '',
    surgicalHistory: '',
    drugAllergies: '',
    foodAllergies: '',
    immunizationHistory: '',
    familyHistory: '',
    otherNotes: ''
  }
}

function normalizeMedicalHistory(history) {
  return {
    ...emptyMedicalHistory(),
    ...(history || {})
  }
}
</script>

<style scoped>
.setup-page {
  min-height: 100vh;
  padding: 32px 18px;
  background:
    radial-gradient(circle at top left, rgba(64, 158, 255, 0.18), transparent 28%),
    radial-gradient(circle at bottom right, rgba(22, 163, 74, 0.18), transparent 32%),
    linear-gradient(140deg, #eff6ff 0%, #f8fafc 54%, #f7fee7 100%);
}

.setup-shell {
  max-width: 980px;
  margin: 0 auto;
}

.intro {
  margin-bottom: 18px;
}

.eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #0369a1;
}

.intro h1 {
  margin: 0;
  font-size: 34px;
  color: #0f172a;
}

.lead {
  max-width: 620px;
  margin: 12px 0 0;
  color: #475569;
  line-height: 1.8;
}

.setup-card {
  border: none;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.92);
}

.action-row {
  width: 100%;
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
