<template>
  <div class="register-shell">
    <div class="register-layout">
      <section class="register-brand-panel">
        <div class="register-brand-panel__top">
          <p class="register-brand-panel__eyebrow">Patient Onboarding</p>
          <h1>创建患者账号</h1>
          <p class="register-brand-panel__copy">
            完成注册后即可进入患者端，继续完善个人资料、病史档案和健康数据，从而启用趋势分析、宣教方案和健康报告。
          </p>
        </div>

        <div class="register-brand-panel__grid">
          <article class="register-feature-card">
            <span>注册后可继续</span>
            <strong>完善健康资料</strong>
            <p>补全基础信息、病史档案和管理医生信息，形成更完整的患者画像。</p>
          </article>
          <article class="register-feature-card">
            <span>后续能力</span>
            <strong>宣教 / 报告 / 趋势</strong>
            <p>患者端支持查看个性化宣教方案、健康周报和趋势图。</p>
          </article>
          <article class="register-feature-card register-feature-card--note">
            <span>密码规则</span>
            <strong>至少 8 位</strong>
            <p>密码需同时包含字母和数字，建议不要与其他网站重复使用。</p>
          </article>
        </div>
      </section>

      <section class="register-auth-panel">
        <div class="register-auth-panel__head">
          <p class="register-auth-panel__eyebrow">Create Account</p>
          <h2>患者注册</h2>
          <p>仅开放患者账号自注册。医生和管理员账号由后台统一创建与维护。</p>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="register-form"
          @submit.prevent="handleRegister"
        >
          <el-form-item label="姓名" prop="name">
            <el-input v-model="form.name" size="large" placeholder="请输入真实姓名" />
          </el-form-item>

          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" size="large" placeholder="请输入登录用户名" />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" size="large" type="password" show-password placeholder="请设置登录密码" />
          </el-form-item>

          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="form.confirmPassword" size="large" type="password" show-password placeholder="请再次输入密码" />
          </el-form-item>

          <el-button type="primary" size="large" class="register-submit-btn" :loading="submitting" native-type="submit">
            创建患者账号
          </el-button>
        </el-form>

        <div class="register-tips-card">
          <strong>注册后流程</strong>
          <ul>
            <li>注册成功后返回登录页，用新账号登录。</li>
            <li>首次登录会进入资料完善页，继续补全个人档案。</li>
            <li>完成资料后即可使用患者端主要功能。</li>
          </ul>
        </div>

        <div class="register-footer">
          <span>已有账号？</span>
          <el-link type="primary" @click="router.push('/login')">返回登录</el-link>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from '../utils/element-plus-service'
import { authApi } from '../api'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)
const registrationEnabled = ref(true)

const form = reactive({
  name: '',
  username: '',
  password: '',
  confirmPassword: ''
})

const validateConfirmPassword = (_rule, value, callback) => {
  if (!value) return callback(new Error('请再次输入密码'))
  if (value !== form.password) return callback(new Error('两次输入的密码不一致'))
  callback()
}

const rules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{
    validator: (_rule, value, callback) => {
      if (!value) return callback(new Error('请输入密码'))
      if (value.length < 8 || !/[A-Za-z]/.test(value) || !/\d/.test(value)) {
        return callback(new Error('密码至少 8 位，且需同时包含字母和数字'))
      }
      callback()
    },
    trigger: 'blur'
  }],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

onMounted(async () => {
  try {
    const config = await authApi.getConfig()
    registrationEnabled.value = config?.patientRegistrationEnabled !== false
  } catch (_e) {
    registrationEnabled.value = true
  }
  if (!registrationEnabled.value) {
    ElMessage.warning('患者自助注册已关闭，请联系管理员')
    router.replace('/login')
  }
})

async function handleRegister() {
  if (!registrationEnabled.value) {
    ElMessage.warning('患者自助注册已关闭，请联系管理员')
    router.replace('/login')
    return
  }
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await authApi.register(form.username, form.password, form.name)
    ElMessage.success('注册成功，请使用新账号登录')
    router.replace('/login')
  } catch (e) {
    const status = e.response?.status
    const msg = e.response?.data?.error || e.response?.data?.message
    if (status === 404) {
      ElMessage.error('当前后端未启用注册接口，请联系管理员创建患者账号')
    } else {
      ElMessage.error(msg || '注册失败')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.register-shell {
  min-height: 100vh;
  padding: 24px;
  background:
    radial-gradient(circle at top left, rgba(18, 183, 106, 0.18), transparent 30%),
    radial-gradient(circle at bottom right, rgba(245, 158, 11, 0.16), transparent 34%),
    linear-gradient(135deg, #f6f8ef 0%, #eef4ff 48%, #fff9f0 100%);
}

.register-layout {
  width: min(1180px, 100%);
  min-height: calc(100vh - 48px);
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1.04fr) minmax(380px, 450px);
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(148, 163, 184, 0.18);
  border-radius: 34px;
  overflow: hidden;
  box-shadow: 0 30px 90px rgba(15, 23, 42, 0.12);
  backdrop-filter: blur(18px);
}

.register-brand-panel {
  padding: 60px 56px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background:
    linear-gradient(180deg, rgba(22, 163, 74, 0.06), rgba(22, 163, 74, 0)),
    linear-gradient(135deg, #f7fcfb 0%, #eef7f1 42%, #fff8ef 100%);
  color: #102a43;
}

.register-brand-panel__eyebrow,
.register-auth-panel__eyebrow {
  margin: 0 0 12px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.register-brand-panel__eyebrow {
  color: #15803d;
}

.register-brand-panel h1,
.register-auth-panel h2 {
  margin: 0;
  line-height: 1.12;
}

.register-brand-panel h1 {
  font-size: clamp(34px, 3.8vw, 46px);
}

.register-brand-panel__copy,
.register-auth-panel__head p {
  margin: 18px 0 0;
  color: #52606d;
  line-height: 1.8;
  font-size: 15px;
}

.register-brand-panel__grid {
  display: grid;
  gap: 16px;
  margin-top: 34px;
}

.register-feature-card {
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(22, 163, 74, 0.12);
  background: rgba(255, 255, 255, 0.78);
}

.register-feature-card span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.12em;
  color: #15803d;
}

.register-feature-card strong {
  display: block;
  margin-top: 10px;
  color: #12314a;
  font-size: 22px;
  line-height: 1.3;
}

.register-feature-card p {
  margin: 12px 0 0;
  color: #486581;
  line-height: 1.8;
  font-size: 14px;
}

.register-feature-card--note {
  background: linear-gradient(135deg, rgba(21, 128, 61, 0.08), rgba(245, 158, 11, 0.08));
}

.register-auth-panel {
  padding: 56px 40px 40px;
  background: rgba(255, 255, 255, 0.94);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.register-auth-panel__eyebrow {
  color: #176b53;
}

.register-auth-panel h2 {
  font-size: 30px;
  color: #111827;
}

.register-form {
  margin-top: 28px;
}

.register-submit-btn {
  width: 100%;
  margin-top: 8px;
  height: 50px;
  border: none;
  border-radius: 16px;
  background: linear-gradient(135deg, #15803d 0%, #1f7a62 100%);
  box-shadow: 0 18px 32px rgba(21, 128, 61, 0.18);
}

.register-tips-card {
  margin-top: 20px;
  padding: 18px 20px;
  border-radius: 20px;
  background: #f8fafc;
  border: 1px solid rgba(148, 163, 184, 0.18);
}

.register-tips-card strong {
  display: block;
  color: #1f2937;
}

.register-tips-card ul {
  margin: 12px 0 0;
  padding-left: 18px;
  color: #64748b;
  font-size: 14px;
  line-height: 1.8;
}

.register-footer {
  margin-top: 18px;
  padding-top: 16px;
  border-top: 1px solid rgba(148, 163, 184, 0.18);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: #64748b;
  font-size: 14px;
}

:deep(.el-form-item__label) {
  color: #1f2937;
  font-weight: 600;
}

:deep(.el-input__wrapper) {
  min-height: 48px;
  border-radius: 16px;
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.18) inset;
}

@media (max-width: 1040px) {
  .register-layout {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .register-brand-panel {
    padding: 40px 32px 24px;
  }

  .register-auth-panel {
    padding: 32px;
  }
}

@media (max-width: 640px) {
  .register-shell {
    padding: 14px;
  }

  .register-layout {
    border-radius: 26px;
  }

  .register-brand-panel,
  .register-auth-panel {
    padding: 24px 20px;
  }

  .register-footer {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
