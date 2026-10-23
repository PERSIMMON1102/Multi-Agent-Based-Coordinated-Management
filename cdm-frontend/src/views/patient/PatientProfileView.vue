<template>
  <div class="profile-shell">
    <section class="profile-hero">
      <div class="profile-hero__identity">
        <div class="profile-avatar" @click="triggerUpload">
          <el-avatar :size="92" :src="resolveAvatar(profile.avatar, 'patient', profile.name)" class="profile-avatar__image">
            {{ (profile.name || '').charAt(0) }}
          </el-avatar>
          <div class="profile-avatar__edit">
            <el-icon :size="18"><Camera /></el-icon>
          </div>
          <input ref="fileInput" type="file" accept="image/*" class="profile-avatar__input" @change="handleFileChange" />
        </div>

        <div class="profile-hero__copy">
          <span class="profile-hero__eyebrow">个人档案</span>
          <h1>{{ profile.name || '加载中...' }}</h1>
          <p>维护基础信息、病史档案和管理医生，让评估、宣教与随访获得更稳定的长期参考。</p>
          <div class="profile-hero__chips">
            <span v-if="profile.gender" class="profile-chip">{{ profile.gender }}</span>
            <span v-if="profile.birthDate" class="profile-chip">{{ getAge(profile.birthDate) }}岁</span>
            <span class="profile-chip">{{ profile.phone || '未填写联系电话' }}</span>
          </div>
        </div>
      </div>

      <div class="profile-hero__stats">
        <div class="profile-stat-card">
          <span>病史完整度</span>
          <strong>{{ medicalHistoryCompletion }}</strong>
          <p>用于聊天、宣教、报告和随访上下文</p>
        </div>
        <div class="profile-stat-card">
          <span>管理医生</span>
          <strong>{{ currentDoctor ? '已绑定' : '未绑定' }}</strong>
          <p>{{ currentDoctor ? `${currentDoctor.name} · ${currentDoctor.department || '全科'}` : '建议尽快选择管理医生' }}</p>
        </div>
        <div class="profile-stat-card">
          <span>资料状态</span>
          <strong>{{ profile.createdAt ? '已建档' : '待同步' }}</strong>
          <p>{{ profile.createdAt ? `创建于 ${formatDate(profile.createdAt)}` : '等待档案同步' }}</p>
        </div>
      </div>
    </section>

    <section class="profile-section-grid">
      <el-card shadow="never" class="profile-card profile-card--wide">
        <template #header>
          <div class="profile-card__header">
            <div>
              <span class="profile-card__eyebrow">基础档案</span>
              <h2>基本信息</h2>
            </div>
            <div v-if="!editing">
              <el-button type="primary" plain round @click="editing = true">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
            </div>
            <div v-else class="profile-card__actions">
              <el-button round @click="cancelEdit">取消</el-button>
              <el-button round type="primary" :loading="saving" @click="saveProfile">保存</el-button>
            </div>
          </div>
        </template>

        <template v-if="!editing">
          <div class="profile-fact-grid">
            <div class="profile-fact">
              <span>姓名</span>
              <strong>{{ displayValue(profile.name) }}</strong>
            </div>
            <div class="profile-fact">
              <span>性别</span>
              <strong>{{ displayValue(profile.gender) }}</strong>
            </div>
            <div class="profile-fact">
              <span>出生日期</span>
              <strong>{{ displayValue(profile.birthDate) }}</strong>
            </div>
            <div class="profile-fact">
              <span>联系电话</span>
              <strong>{{ displayValue(profile.phone) }}</strong>
            </div>
            <div class="profile-fact">
              <span>身份证号</span>
              <strong>{{ maskIdCard(profile.idCard) }}</strong>
            </div>
            <div class="profile-fact">
              <span>住址</span>
              <strong>{{ displayValue(profile.address) }}</strong>
            </div>
          </div>
        </template>

        <template v-else>
          <el-form :model="editForm" label-position="top" class="profile-form">
            <el-row :gutter="18">
              <el-col :xs="24" :md="12">
                <el-form-item label="姓名">
                  <el-input v-model="editForm.name" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="性别">
                  <el-radio-group v-model="editForm.gender">
                    <el-radio value="男">男</el-radio>
                    <el-radio value="女">女</el-radio>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="18">
              <el-col :xs="24" :md="12">
                <el-form-item label="联系电话">
                  <el-input v-model="editForm.phone" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="住址">
                  <el-input v-model="editForm.address" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </template>
      </el-card>

      <el-card shadow="never" class="profile-card">
        <template #header>
          <div class="profile-card__header">
            <div>
              <span class="profile-card__eyebrow">联络信息</span>
              <h2>紧急联系人</h2>
            </div>
            <div v-if="!editingEmergency">
              <el-button type="primary" plain round @click="editingEmergency = true">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
            </div>
            <div v-else class="profile-card__actions">
              <el-button round @click="editingEmergency = false">取消</el-button>
              <el-button round type="primary" :loading="saving" @click="saveEmergency">保存</el-button>
            </div>
          </div>
        </template>

        <template v-if="!editingEmergency">
          <div class="profile-fact-grid profile-fact-grid--single">
            <div class="profile-fact">
              <span>联系人</span>
              <strong>{{ displayValue(profile.emergencyContact, '未设置') }}</strong>
            </div>
            <div class="profile-fact">
              <span>联系电话</span>
              <strong>{{ displayValue(profile.emergencyPhone, '未设置') }}</strong>
            </div>
          </div>
        </template>

        <template v-else>
          <el-form label-position="top" class="profile-form">
            <el-row :gutter="18">
              <el-col :xs="24" :md="12">
                <el-form-item label="联系人">
                  <el-input v-model="emergencyForm.emergencyContact" placeholder="紧急联系人姓名" />
                </el-form-item>
              </el-col>
              <el-col :xs="24" :md="12">
                <el-form-item label="联系电话">
                  <el-input v-model="emergencyForm.emergencyPhone" placeholder="紧急联系人电话" />
                </el-form-item>
              </el-col>
            </el-row>
          </el-form>
        </template>
      </el-card>
    </section>

    <el-card shadow="never" class="profile-card">
      <template #header>
        <div class="profile-card__header">
          <div>
            <span class="profile-card__eyebrow">长期临床背景</span>
            <h2>病史档案</h2>
          </div>
          <div v-if="!editingMedicalHistory">
            <el-button type="primary" plain round @click="editingMedicalHistory = true">
              <el-icon><Edit /></el-icon>
              编辑
            </el-button>
          </div>
          <div v-else class="profile-card__actions">
            <el-button round @click="editingMedicalHistory = false">取消</el-button>
            <el-button round type="primary" :loading="savingMedicalHistory" @click="saveMedicalHistory">保存</el-button>
          </div>
        </div>
      </template>

      <template v-if="!editingMedicalHistory">
        <div class="history-grid">
          <div class="history-item">
            <span>既往患病情况</span>
            <strong>{{ displayValue(profile.medicalHistory?.pastDiseases) }}</strong>
          </div>
          <div class="history-item">
            <span>手术史</span>
            <strong>{{ displayValue(profile.medicalHistory?.surgicalHistory) }}</strong>
          </div>
          <div class="history-item">
            <span>药物过敏</span>
            <strong>{{ displayValue(profile.medicalHistory?.drugAllergies) }}</strong>
          </div>
          <div class="history-item">
            <span>食物过敏</span>
            <strong>{{ displayValue(profile.medicalHistory?.foodAllergies) }}</strong>
          </div>
          <div class="history-item">
            <span>预防接种</span>
            <strong>{{ displayValue(profile.medicalHistory?.immunizationHistory) }}</strong>
          </div>
          <div class="history-item">
            <span>家族史</span>
            <strong>{{ displayValue(profile.medicalHistory?.familyHistory) }}</strong>
          </div>
          <div class="history-item history-item--wide">
            <span>其他备注</span>
            <strong>{{ displayValue(profile.medicalHistory?.otherNotes) }}</strong>
          </div>
        </div>
      </template>

      <template v-else>
        <el-form label-position="top" class="profile-form">
          <el-row :gutter="18">
            <el-col :xs="24" :md="12">
              <el-form-item label="既往患病情况">
                <el-input v-model="medicalHistoryForm.pastDiseases" type="textarea" :rows="3" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="手术史">
                <el-input v-model="medicalHistoryForm.surgicalHistory" type="textarea" :rows="3" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="18">
            <el-col :xs="24" :md="12">
              <el-form-item label="药物过敏">
                <el-input v-model="medicalHistoryForm.drugAllergies" type="textarea" :rows="3" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="食物过敏">
                <el-input v-model="medicalHistoryForm.foodAllergies" type="textarea" :rows="3" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="18">
            <el-col :xs="24" :md="12">
              <el-form-item label="预防接种">
                <el-input v-model="medicalHistoryForm.immunizationHistory" type="textarea" :rows="3" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :md="12">
              <el-form-item label="家族史">
                <el-input v-model="medicalHistoryForm.familyHistory" type="textarea" :rows="3" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="其他备注">
            <el-input v-model="medicalHistoryForm.otherNotes" type="textarea" :rows="4" />
          </el-form-item>
        </el-form>
      </template>
    </el-card>

    <section class="profile-section-grid">
      <el-card shadow="never" class="profile-card">
        <template #header>
          <div class="profile-card__header">
            <div>
              <span class="profile-card__eyebrow">医疗关系</span>
              <h2>管理医生</h2>
            </div>
            <div v-if="!editingDoctor" class="profile-card__actions">
              <el-button type="primary" plain round @click="startEditDoctor">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-popconfirm
                v-if="currentDoctor"
                title="确定要解除绑定吗？"
                confirm-button-text="解除"
                cancel-button-text="取消"
                @confirm="removeDoctor"
              >
                <template #reference>
                  <el-button type="danger" plain round>解除绑定</el-button>
                </template>
              </el-popconfirm>
            </div>
            <div v-else class="profile-card__actions">
              <el-button round @click="cancelEditDoctor">取消</el-button>
              <el-button round type="primary" :loading="savingDoctor" @click="saveDoctor">保存</el-button>
            </div>
          </div>
        </template>

        <template v-if="!editingDoctor">
          <div class="doctor-card" :class="{ 'doctor-card--empty': !currentDoctor && !profile.assignedDoctorId }">
            <template v-if="currentDoctor">
              <strong>{{ currentDoctor.name }}</strong>
              <span>{{ currentDoctor.department || '未设置科室' }}</span>
              <p>已绑定为你的管理医生，后续随访、风险评估和消息提醒会与这位医生协同。</p>
            </template>
            <template v-else-if="profile.assignedDoctorId">
              <strong>已绑定医生 ID：{{ profile.assignedDoctorId }}</strong>
              <span>未找到详细医生信息</span>
              <p>可以重新选择医生，确保后续协同服务正常衔接。</p>
            </template>
            <template v-else>
              <strong>暂未绑定管理医生</strong>
              <span>建议尽快完成绑定</span>
              <p>绑定后，健康评估、随访和消息提醒会更完整地串联起来。</p>
            </template>
          </div>
        </template>

        <template v-else>
          <el-form label-position="top" class="profile-form">
            <el-form-item label="选择医生">
              <el-select v-model="doctorForm.assignedDoctorId" placeholder="请选择管理医生" style="width: 100%">
                <el-option
                  v-for="d in doctorOptions"
                  :key="d.id"
                  :label="`${d.name}（${d.department || '全科'}）`"
                  :value="d.id"
                />
              </el-select>
            </el-form-item>
          </el-form>
        </template>
      </el-card>

      <el-card shadow="never" class="profile-card">
        <template #header>
          <div class="profile-card__header">
            <div>
              <span class="profile-card__eyebrow">账号信息</span>
              <h2>账户状态</h2>
            </div>
          </div>
        </template>

        <div class="profile-fact-grid profile-fact-grid--single">
          <div class="profile-fact">
            <span>登录账号</span>
            <strong>{{ userStore.loginName || '-' }}</strong>
          </div>
          <div class="profile-fact">
            <span>用户角色</span>
            <strong><el-tag type="success" size="small">患者</el-tag></strong>
          </div>
          <div class="profile-fact">
            <span>注册时间</span>
            <strong>{{ formatDate(profile.createdAt) }}</strong>
          </div>
        </div>
      </el-card>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { profileApi, authApi } from '../../api'
import { useUserStore } from '../../stores/user'
import { ElMessage } from '../../utils/element-plus-service'
import { resolveAvatar } from '../../utils/avatar'
import dayjs from 'dayjs'

const userStore = useUserStore()
const profile = ref({})
const editing = ref(false)
const editingEmergency = ref(false)
const editingMedicalHistory = ref(false)
const editingDoctor = ref(false)
const saving = ref(false)
const savingMedicalHistory = ref(false)
const savingDoctor = ref(false)
const fileInput = ref(null)

const editForm = reactive({ name: '', gender: '', phone: '', address: '' })
const emergencyForm = reactive({ emergencyContact: '', emergencyPhone: '' })
const medicalHistoryForm = reactive(emptyMedicalHistory())
const doctorForm = reactive({ assignedDoctorId: null })

const doctorOptions = ref([])
const currentDoctor = ref(null)

const medicalHistoryCompletion = computed(() => {
  const history = normalizeMedicalHistory(profile.value.medicalHistory)
  const values = Object.values(history)
  const filled = values.filter(value => String(value || '').trim() !== '').length
  if (filled === 0) {
    return '0 / 7'
  }
  return `${filled} / ${values.length}`
})

onMounted(async () => {
  await Promise.all([loadProfile(), loadDoctors()])
})

async function loadProfile() {
  if (!userStore.patientId) {
    ElMessage.warning('请重新登录以加载个人信息')
    return
  }
  try {
    profile.value = await profileApi.getPatient(userStore.patientId)
    profile.value.medicalHistory = normalizeMedicalHistory(profile.value.medicalHistory)
    const assignedId = profile.value.assignedDoctorId || userStore.assignedDoctorId || null
    if (assignedId) {
      await loadCurrentDoctor(assignedId)
      profile.value.assignedDoctorId = assignedId
      userStore.setAssignedDoctorId(assignedId)
    } else {
      currentDoctor.value = null
      profile.value.assignedDoctorId = null
      userStore.setAssignedDoctorId(null)
    }
    if (profile.value.avatar) {
      userStore.setAvatar(profile.value.avatar)
    }
  } catch (e) {
    ElMessage.error('加载个人信息失败')
  }
}

function cancelEdit() {
  editing.value = false
}

async function saveProfile() {
  saving.value = true
  try {
    profile.value = await profileApi.updatePatient(userStore.patientId, {
      name: editForm.name,
      gender: editForm.gender,
      phone: editForm.phone,
      address: editForm.address
    })
    profile.value.medicalHistory = normalizeMedicalHistory(profile.value.medicalHistory)
    editing.value = false
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function saveEmergency() {
  saving.value = true
  try {
    profile.value = await profileApi.updatePatient(userStore.patientId, {
      emergencyContact: emergencyForm.emergencyContact,
      emergencyPhone: emergencyForm.emergencyPhone
    })
    profile.value.medicalHistory = normalizeMedicalHistory(profile.value.medicalHistory)
    editingEmergency.value = false
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

async function saveMedicalHistory() {
  savingMedicalHistory.value = true
  try {
    profile.value = await profileApi.updatePatient(userStore.patientId, {
      medicalHistory: { ...medicalHistoryForm }
    })
    profile.value.medicalHistory = normalizeMedicalHistory(profile.value.medicalHistory)
    editingMedicalHistory.value = false
    ElMessage.success('病史档案已更新')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    savingMedicalHistory.value = false
  }
}

watch(editing, (val) => {
  if (val) {
    Object.assign(editForm, {
      name: profile.value.name || '',
      gender: profile.value.gender || '',
      phone: profile.value.phone || '',
      address: profile.value.address || ''
    })
  }
})

watch(editingEmergency, (val) => {
  if (val) {
    Object.assign(emergencyForm, {
      emergencyContact: profile.value.emergencyContact || '',
      emergencyPhone: profile.value.emergencyPhone || ''
    })
  }
})

watch(editingMedicalHistory, (val) => {
  if (val) {
    Object.assign(medicalHistoryForm, normalizeMedicalHistory(profile.value.medicalHistory))
  }
})

watch(editingDoctor, (val) => {
  if (val) {
    doctorForm.assignedDoctorId = profile.value.assignedDoctorId || null
  }
})

function triggerUpload() {
  fileInput.value?.click()
}

async function handleFileChange(e) {
  const file = e.target.files?.[0]
  if (!file) return
  if (file.size > 3 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 3MB，请压缩后重试')
    return
  }
  try {
    const res = await profileApi.uploadAvatar(file)
    if (res.url) {
      await profileApi.updatePatient(userStore.patientId, { avatar: res.url })
      profile.value.avatar = res.url
      userStore.setAvatar(res.url)
      ElMessage.success('头像更新成功')
    }
    if (res.message) {
      ElMessage.warning(res.message)
    }
  } catch (error) {
    ElMessage.error('头像上传失败')
  }
  e.target.value = ''
}

function getAge(birthDate) {
  if (!birthDate) return ''
  return dayjs().diff(dayjs(birthDate), 'year')
}

function maskIdCard(idCard) {
  if (!idCard) return '-'
  if (idCard.length > 10) {
    return `${idCard.slice(0, 6)}****${idCard.slice(-4)}`
  }
  return idCard
}

function formatDate(dt) {
  return dt ? dayjs(dt).format('YYYY-MM-DD') : '-'
}

function displayValue(value, fallback = '未填写') {
  return String(value || '').trim() || fallback
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

async function loadDoctors() {
  try {
    doctorOptions.value = await authApi.getDoctorsList()
  } catch (e) {
    doctorOptions.value = []
  }
}

async function loadCurrentDoctor(id) {
  try {
    currentDoctor.value = await profileApi.getDoctor(id)
  } catch (e) {
    currentDoctor.value = null
  }
}

function startEditDoctor() {
  editingDoctor.value = true
  doctorForm.assignedDoctorId = profile.value.assignedDoctorId || null
}

function cancelEditDoctor() {
  editingDoctor.value = false
}

async function saveDoctor() {
  if (!doctorForm.assignedDoctorId) {
    ElMessage.warning('请选择医生')
    return
  }
  savingDoctor.value = true
  try {
    profile.value = await profileApi.updatePatient(userStore.patientId, {
      assignedDoctorId: doctorForm.assignedDoctorId
    })
    profile.value.medicalHistory = normalizeMedicalHistory(profile.value.medicalHistory)
    await loadCurrentDoctor(doctorForm.assignedDoctorId)
    userStore.setAssignedDoctorId(doctorForm.assignedDoctorId)
    editingDoctor.value = false
    ElMessage.success('管理医生已更新')
  } catch (e) {
    ElMessage.error('更新失败')
  } finally {
    savingDoctor.value = false
  }
}

async function removeDoctor() {
  savingDoctor.value = true
  try {
    profile.value = await profileApi.updatePatient(userStore.patientId, { assignedDoctorId: null })
    profile.value.medicalHistory = normalizeMedicalHistory(profile.value.medicalHistory)
    currentDoctor.value = null
    userStore.setAssignedDoctorId(null)
    editingDoctor.value = false
    ElMessage.success('已解除绑定')
  } catch (e) {
    ElMessage.error('解除失败')
  } finally {
    savingDoctor.value = false
  }
}
</script>

<style scoped>
.profile-shell {
  display: grid;
  gap: 22px;
}

.profile-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.45fr) minmax(260px, 0.95fr);
  gap: 18px;
}

.profile-hero__identity,
.profile-hero__stats {
  border: 1px solid rgba(31, 51, 64, 0.08);
  border-radius: 28px;
  box-shadow: var(--patient-shadow-soft);
}

.profile-hero__identity {
  display: flex;
  align-items: center;
  gap: 22px;
  padding: 28px 30px;
  background:
    radial-gradient(circle at top right, rgba(214, 233, 223, 0.78), transparent 30%),
    linear-gradient(135deg, rgba(255, 252, 246, 0.92), rgba(244, 249, 246, 0.92));
}

.profile-avatar {
  position: relative;
  display: inline-flex;
  cursor: pointer;
}

.profile-avatar__image {
  border: 4px solid rgba(255, 255, 255, 0.9);
  box-shadow: 0 16px 34px rgba(35, 52, 62, 0.16);
}

.profile-avatar__edit {
  position: absolute;
  right: 4px;
  bottom: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: rgba(31, 51, 64, 0.72);
  color: #fff;
  border: 2px solid rgba(255, 255, 255, 0.92);
}

.profile-avatar__input {
  display: none;
}

.profile-hero__copy {
  flex: 1;
}

.profile-hero__eyebrow {
  display: inline-block;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: rgba(43, 108, 143, 0.72);
}

.profile-hero__copy h1 {
  margin: 12px 0 10px;
  font-size: 34px;
  line-height: 1.1;
  color: var(--patient-ink);
}

.profile-hero__copy p {
  margin: 0;
  max-width: 560px;
  line-height: 1.8;
  color: var(--patient-muted);
}

.profile-hero__chips {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 18px;
}

.profile-chip {
  display: inline-flex;
  align-items: center;
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(43, 108, 143, 0.08);
  color: var(--patient-primary);
  font-size: 13px;
  font-weight: 600;
}

.profile-hero__stats {
  display: grid;
  gap: 12px;
  padding: 18px;
  background: rgba(255, 255, 255, 0.82);
}

.profile-stat-card {
  padding: 16px 18px;
  border-radius: 22px;
  background: rgba(247, 243, 236, 0.84);
}

.profile-stat-card span {
  display: block;
  font-size: 12px;
  color: var(--patient-muted);
}

.profile-stat-card strong {
  display: block;
  margin: 10px 0 6px;
  font-size: 24px;
  color: var(--patient-ink);
}

.profile-stat-card p {
  margin: 0;
  line-height: 1.7;
  color: var(--patient-muted);
}

.profile-section-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(280px, 0.9fr);
  gap: 18px;
}

.profile-card {
  border: 1px solid rgba(31, 51, 64, 0.08);
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow: var(--patient-shadow-soft);
}

.profile-card--wide {
  min-height: 100%;
}

.profile-card :deep(.el-card__header) {
  padding: 22px 24px 18px;
  border-bottom: 1px solid rgba(31, 51, 64, 0.06);
}

.profile-card :deep(.el-card__body) {
  padding: 22px 24px 24px;
}

.profile-card__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.profile-card__eyebrow {
  display: inline-block;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: rgba(43, 108, 143, 0.72);
}

.profile-card__header h2 {
  margin: 8px 0 0;
  font-size: 22px;
  color: var(--patient-ink);
}

.profile-card__actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.profile-form :deep(.el-form-item__label) {
  color: var(--patient-ink);
  font-weight: 600;
}

.profile-fact-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.profile-fact-grid--single {
  grid-template-columns: 1fr;
}

.profile-fact {
  padding: 16px 18px;
  border-radius: 20px;
  background: rgba(247, 243, 236, 0.82);
}

.profile-fact span {
  display: block;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--patient-muted);
}

.profile-fact strong {
  display: block;
  font-size: 15px;
  line-height: 1.75;
  color: var(--patient-ink);
  white-space: pre-wrap;
  word-break: break-word;
}

.history-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.history-item {
  padding: 18px;
  border-radius: 22px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(247, 248, 245, 0.92));
  border: 1px solid rgba(31, 51, 64, 0.08);
}

.history-item--wide {
  grid-column: 1 / -1;
}

.history-item span {
  display: block;
  margin-bottom: 10px;
  font-size: 12px;
  color: var(--patient-muted);
}

.history-item strong {
  display: block;
  font-size: 14px;
  line-height: 1.8;
  color: var(--patient-ink);
  white-space: pre-wrap;
  word-break: break-word;
}

.doctor-card {
  padding: 20px;
  border-radius: 22px;
  background: rgba(247, 243, 236, 0.84);
}

.doctor-card--empty {
  background: rgba(249, 247, 242, 0.92);
}

.doctor-card strong {
  display: block;
  font-size: 20px;
  color: var(--patient-ink);
}

.doctor-card span {
  display: block;
  margin-top: 8px;
  font-size: 13px;
  color: rgba(43, 108, 143, 0.8);
  font-weight: 600;
}

.doctor-card p {
  margin: 12px 0 0;
  line-height: 1.8;
  color: var(--patient-muted);
}

@media (max-width: 1180px) {
  .profile-hero,
  .profile-section-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .profile-hero__identity {
    flex-direction: column;
    align-items: flex-start;
    padding: 24px 22px;
  }

  .profile-card :deep(.el-card__header),
  .profile-card :deep(.el-card__body) {
    padding-left: 18px;
    padding-right: 18px;
  }

  .profile-fact-grid,
  .history-grid {
    grid-template-columns: 1fr;
  }

  .profile-card__header {
    flex-direction: column;
  }

  .profile-card__actions {
    width: 100%;
  }
}

@media (max-width: 640px) {
  .profile-hero__copy h1 {
    font-size: 28px;
  }

  .profile-hero__chips {
    gap: 8px;
  }

  .profile-chip {
    width: 100%;
    justify-content: center;
  }
}
</style>
