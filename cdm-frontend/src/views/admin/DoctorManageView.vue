<template>
  <div class="admin-page">
    <section class="admin-hero">
      <div class="admin-hero__content">
        <span class="admin-hero__eyebrow">Account Control</span>
        <h1 class="admin-hero__title">医生账号管理</h1>
        <p class="admin-hero__desc">
          统一维护医生登录账号、科室归属和基础联系方式，保证后台账号体系和科室映射始终清晰可控。
        </p>
        <div class="admin-hero__chips">
          <el-tag effect="plain" round>账号 {{ doctorCount }}</el-tag>
          <el-tag effect="plain" round type="success">科室 {{ departmentCount }}</el-tag>
          <el-tag effect="plain" round type="warning">电话 {{ phoneCount }}/{{ doctorCount }}</el-tag>
        </div>
      </div>

      <div class="admin-hero__actions">
        <el-button class="admin-hero__ghost" :loading="loading" @click="loadDoctors">刷新列表</el-button>
        <el-button type="primary" class="admin-hero__primary" @click="openCreate">新增医生</el-button>
      </div>
    </section>

    <section class="admin-stat-grid">
      <article class="admin-stat-card admin-stat-card--blue">
        <span>医生总数</span>
        <strong>{{ doctorCount }}</strong>
        <p>当前已纳入后台统一管理的医生账号数量。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--green">
        <span>覆盖科室</span>
        <strong>{{ departmentCount }}</strong>
        <p>已形成科室归属的医生账号覆盖范围。</p>
      </article>
      <article class="admin-stat-card admin-stat-card--orange">
        <span>联系方式完整</span>
        <strong>{{ phoneCount }}</strong>
        <p>便于后续通知、协作和后台运维核验。</p>
      </article>
    </section>

    <el-card shadow="never" class="admin-card admin-card--table">
      <template #header>
        <div class="admin-section-head">
          <div>
            <span class="admin-section-head__eyebrow">Doctor Roster</span>
            <h2>医生账户清单</h2>
            <p>支持新增、编辑和删除医生账户，用户名与密码由管理员统一维护。</p>
          </div>
          <div class="admin-section-head__meta">
            <el-tag type="info" effect="plain">用户名已配置 {{ usernameReadyCount }}</el-tag>
            <el-tag type="warning" effect="plain">待补电话 {{ Math.max(doctorCount - phoneCount, 0) }}</el-tag>
          </div>
        </div>
      </template>

      <el-alert
        v-if="loadError"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
        title="医生管理接口暂不可用"
        :description="loadError"
      />

      <el-table class="admin-data-table" :data="doctors" stripe border v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="姓名" min-width="120" />
        <el-table-column prop="department" label="科室" min-width="140" />
        <el-table-column prop="phone" label="电话" min-width="150" />
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-popconfirm title="确认删除该医生账号？" @confirm="removeDoctor(row.id)">
              <template #reference>
                <el-button type="danger" link>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="!loading && doctors.length === 0" class="admin-empty-block">
        <el-empty description="暂无医生数据" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑医生' : '新增医生'" width="520px" destroy-on-close append-to-body align-center>
      <el-alert
        type="info"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
        title="密码规则"
        :description="editingId ? '留空并保存表示不修改当前密码；如需重置，请设置至少 8 位且同时包含字母和数字的新密码。' : '创建账号时请设置初始密码，密码至少 8 位，且需同时包含字母和数字。'"
      />

      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入医生姓名" />
        </el-form-item>
        <el-form-item label="科室" prop="department">
          <el-input v-model="form.department" placeholder="如：全科 / 心内科" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入联系电话" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入登录用户名" />
        </el-form-item>
        <el-form-item :label="editingId ? '新密码' : '初始密码'" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="editingId ? '留空则不修改；输入后将更新密码' : '请输入初始密码'"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            :placeholder="editingId ? '再次输入新密码' : '再次输入初始密码'"
          />
        </el-form-item>
        <el-form-item v-if="editingId" label="提示">
          <span class="admin-form-hint">留空并保存表示不修改当前密码；若需修改，请设置符合规则的新密码。</span>
        </el-form-item>
        <el-form-item v-else label="说明">
          <span class="admin-form-hint">创建账号后，医生可直接使用该用户名和初始密码登录医生端。</span>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from '../../utils/element-plus-service'
import { adminApi } from '../../api'

const doctors = ref([])
const loading = ref(false)
const loadError = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref(null)
const formRef = ref()

const form = reactive({
  name: '',
  department: '',
  phone: '',
  username: '',
  password: '',
  confirmPassword: ''
})

const doctorCount = computed(() => doctors.value.length)
const departmentCount = computed(() => new Set(doctors.value.map(item => (item.department || '').trim()).filter(Boolean)).size)
const phoneCount = computed(() => doctors.value.filter(item => (item.phone || '').trim()).length)
const usernameReadyCount = computed(() => doctors.value.filter(item => (item.username || '').trim()).length)

function validatePassword(_rule, value, callback) {
  if (!editingId.value && !value) {
    callback(new Error('请输入初始密码'))
    return
  }
  if (value && (value.trim().length < 8 || !/[A-Za-z]/.test(value) || !/\d/.test(value))) {
    callback(new Error('密码至少 8 位，且需同时包含字母和数字'))
    return
  }
  callback()
}

function validateConfirmPassword(_rule, value, callback) {
  if (!editingId.value && !value) {
    callback(new Error('请再次输入初始密码'))
    return
  }
  if ((form.password || value) && value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const rules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  department: [{ required: true, message: '请输入科室', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ validator: validatePassword, trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

onMounted(() => {
  loadDoctors()
})

async function loadDoctors() {
  loading.value = true
  loadError.value = ''
  try {
    doctors.value = await adminApi.getDoctors()
  } catch (e) {
    doctors.value = []
    loadError.value = e.response?.data?.message || '后端尚未提供 /api/admin/doctors 接口。'
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.name = ''
  form.department = ''
  form.phone = ''
  form.username = ''
  form.password = ''
  form.confirmPassword = ''
}

function openCreate() {
  editingId.value = null
  resetForm()
  formRef.value?.clearValidate?.()
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.name = row.name || ''
  form.department = row.department || ''
  form.phone = row.phone || ''
  form.username = row.username || ''
  form.password = ''
  form.confirmPassword = ''
  formRef.value?.clearValidate?.()
  dialogVisible.value = true
}

async function submitForm() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload = {
      name: form.name,
      department: form.department,
      phone: form.phone,
      username: form.username
    }
    if (form.password) payload.password = form.password

    if (editingId.value) {
      await adminApi.updateDoctor(editingId.value, payload)
      ElMessage.success(form.password ? '医生信息和密码已更新' : '医生信息已更新')
    } else {
      await adminApi.createDoctor(payload)
      ElMessage.success('医生账号已创建')
    }
    dialogVisible.value = false
    await loadDoctors()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function removeDoctor(id) {
  try {
    await adminApi.deleteDoctor(id)
    ElMessage.success('医生账号已删除')
    await loadDoctors()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '删除失败')
  }
}
</script>
