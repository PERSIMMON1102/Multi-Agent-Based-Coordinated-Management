<template>
  <div class="login-shell">
    <div class="login-shell__aurora login-shell__aurora--teal"></div>
    <div class="login-shell__aurora login-shell__aurora--cyan"></div>
    <div class="login-shell__aurora login-shell__aurora--amber"></div>
    <div class="login-shell__grid"></div>
    <div class="login-shell__scanline"></div>

    <div class="login-layout">
      <section class="login-brand-panel">
        <div class="login-brand-panel__halo"></div>

        <div class="login-brand-panel__top">
          <div class="login-brand-panel__status">
            <span class="login-brand-panel__status-dot"></span>
            <span>医疗服务在线</span>
          </div>

          <p class="login-brand-panel__eyebrow">Chronic Care Platform</p>
          <h1>基层慢性病协同管理平台</h1>
          <p class="login-brand-panel__copy">
            覆盖患者建档、风险评估、随访管理、健康宣教与报告生成，帮助基层团队持续开展慢病管理。
          </p>

          <div class="login-brand-panel__chips">
            <span class="login-chip">风险评估</span>
            <span class="login-chip">随访管理</span>
            <span class="login-chip">健康宣教</span>
          </div>
        </div>

        <div class="login-brand-panel__visual">
          <div class="signal-board">
            <div class="signal-board__header">
              <span>平台服务概览</span>
              <strong>慢病管理协同视图</strong>
            </div>

            <div class="signal-board__wave signal-board__wave--primary">
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
            </div>

            <div class="signal-board__wave signal-board__wave--secondary">
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
              <span></span>
            </div>

            <div class="signal-board__metrics">
              <article>
                <span>风险识别</span>
                <strong>98.4%</strong>
              </article>
              <article>
                <span>连续管理</span>
                <strong>24/7</strong>
              </article>
              <article>
                <span>宣教覆盖</span>
                <strong>6 Streams</strong>
              </article>
            </div>
          </div>

          <div class="care-orbit">
            <div class="care-orbit__ring care-orbit__ring--outer"></div>
            <div class="care-orbit__ring care-orbit__ring--inner"></div>
            <div class="care-orbit__core">
              <span>CARE</span>
              <strong>24H</strong>
            </div>
            <div class="care-orbit__node care-orbit__node--top">AI</div>
            <div class="care-orbit__node care-orbit__node--right">MDT</div>
            <div class="care-orbit__node care-orbit__node--bottom">EDU</div>
            <div class="care-orbit__node care-orbit__node--left">RISK</div>
          </div>
        </div>

        <div class="login-brand-panel__grid">
          <article class="brand-metric-card">
            <span>医生工作</span>
            <strong>评估 · 随访 · 趋势</strong>
            <p>医生端集中处理高风险患者、随访任务和个体化管理建议。</p>
          </article>
          <article class="brand-metric-card">
            <span>患者服务</span>
            <strong>记录 · 宣教 · 报告</strong>
            <p>患者端持续沉淀每日健康数据、宣教计划和个性化反馈。</p>
          </article>
          <article class="brand-metric-card">
            <span>平台管理</span>
            <strong>账号 · 视频 · 内容</strong>
            <p>管理员负责系统初始化、角色分发和平台资源治理。</p>
          </article>
          <article class="brand-policy-card">
            <div class="brand-policy-card__head">
              <span class="brand-policy-card__badge">账号说明</span>
              <strong>账号使用规则</strong>
            </div>
            <ul>
              <li>管理员账号仅用于系统初始化和医生账号管理。</li>
              <li>医生账号由管理员统一开通，不开放自注册。</li>
              <li>患者通过注册页面自行创建账号并继续完善资料。</li>
            </ul>
          </article>
        </div>

        <div class="login-brand-panel__stream">
          <div class="stream-item">
            <span class="stream-item__label">风险管理</span>
            <strong>高危患者优先路由</strong>
          </div>
          <div class="stream-item">
            <span class="stream-item__label">随访管理</span>
            <strong>随访任务自动协同派发</strong>
          </div>
          <div class="stream-item">
            <span class="stream-item__label">健康宣教</span>
            <strong>宣教内容按病种与趋势动态生成</strong>
          </div>
        </div>
      </section>

      <section class="login-auth-panel">
        <div class="login-auth-panel__glow"></div>

        <div class="login-auth-panel__head">
          <div class="login-auth-panel__eyebrow-wrap">
            <p class="login-auth-panel__eyebrow">Secure Access</p>
            <span class="login-auth-panel__eyebrow-line"></span>
          </div>
          <h2>登录平台</h2>
          <p>输入账号密码后自动识别角色并进入对应工作台。医生账号由管理员后台创建并分发。</p>
        </div>

        <div class="login-auth-panel__stats">
          <article class="auth-stat-card">
            <span>登录方式</span>
            <strong>统一登录</strong>
          </article>
          <article class="auth-stat-card">
            <span>进入方式</span>
            <strong>按角色进入工作台</strong>
          </article>
        </div>

        <div class="login-auth-panel__trust">
          <div class="trust-pill">
            <span class="trust-pill__dot"></span>
            <span>安全访问</span>
          </div>
          <div class="trust-lines">
            <div class="trust-line">
              <span>身份校验</span>
              <strong>账号密码认证</strong>
            </div>
            <div class="trust-line">
              <span>角色路由</span>
              <strong>管理员 / 医生 / 患者</strong>
            </div>
            <div class="trust-line">
              <span>进入方式</span>
              <strong>登录后自动跳转工作台</strong>
            </div>
          </div>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          class="login-form"
          @submit.prevent="handleLogin"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="form.username"
              placeholder="请输入用户名"
              prefix-icon="User"
              size="large"
            />
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              prefix-icon="Lock"
              size="large"
              show-password
            />
          </el-form-item>

          <el-button type="primary" size="large" class="login-submit-btn" :loading="loading" native-type="submit">
            进入系统
          </el-button>
        </el-form>

        <div class="login-entry-actions">
          <div class="login-entry-note">
            <strong>{{ patientRegistrationEnabled ? '患者可自助注册' : '患者自助注册已关闭' }}</strong>
            <span>
              {{ patientRegistrationEnabled ? '医生账号与管理员账号由后台统一维护，不开放自注册。' : '请联系管理员创建患者账号。' }}
            </span>
          </div>
          <el-link v-if="patientRegistrationEnabled" type="primary" @click="router.push('/register')">患者注册</el-link>
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
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)
const patientRegistrationEnabled = ref(true)

const form = reactive({
  username: '',
  password: ''
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(async () => {
  try {
    const config = await authApi.getConfig()
    patientRegistrationEnabled.value = config?.patientRegistrationEnabled !== false
  } catch (_e) {
    patientRegistrationEnabled.value = true
  }
})

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await authApi.login(form.username, form.password)
    userStore.login(
      res.role,
      res.displayName,
      res.patientId,
      res.username,
      res.doctorId,
      res.assignedDoctorId,
      res.mustChangePassword,
      res.token
    )
    if (res.role === 'admin' && res.mustChangePassword) {
      ElMessage.warning('首次登录请先修改管理员密码')
    } else {
      ElMessage.success(`欢迎，${res.displayName}`)
    }

    if (res.role === 'admin') {
      router.replace('/admin')
    } else if (res.role === 'doctor') {
      router.replace('/doctor')
    } else if (res.role === 'patient') {
      if (res.profileCompleted === false) {
        userStore.setProfileCompleted(false)
        router.replace('/patient/setup')
      } else {
        userStore.setProfileCompleted(true)
        router.replace('/patient')
      }
    }
  } catch (e) {
    const msg = e.response?.data?.error || e.response?.data?.message || '登录失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-shell {
  position: relative;
  min-height: 100vh;
  padding: 26px;
  overflow: hidden;
  background:
    radial-gradient(circle at top left, rgba(29, 178, 180, 0.18), transparent 30%),
    radial-gradient(circle at right 20%, rgba(50, 130, 246, 0.14), transparent 24%),
    radial-gradient(circle at bottom right, rgba(255, 163, 71, 0.12), transparent 22%),
    linear-gradient(135deg, #04101a 0%, #081926 32%, #0b2230 68%, #10283a 100%);
}

.login-shell__aurora,
.login-shell__grid,
.login-shell__scanline {
  position: absolute;
  pointer-events: none;
}

.login-shell__aurora {
  border-radius: 999px;
  filter: blur(22px);
  opacity: 0.6;
  animation: auroraFloat 12s ease-in-out infinite;
}

.login-shell__aurora--teal {
  top: 6%;
  left: -6%;
  width: 300px;
  height: 300px;
  background: rgba(38, 211, 197, 0.34);
}

.login-shell__aurora--cyan {
  top: 24%;
  right: -4%;
  width: 340px;
  height: 340px;
  background: rgba(82, 167, 255, 0.28);
  animation-delay: -4s;
}

.login-shell__aurora--amber {
  bottom: -5%;
  left: 28%;
  width: 240px;
  height: 240px;
  background: rgba(255, 176, 84, 0.18);
  animation-delay: -7s;
}

.login-shell__grid {
  inset: 0;
  background-image:
    linear-gradient(rgba(116, 178, 214, 0.08) 1px, transparent 1px),
    linear-gradient(90deg, rgba(116, 178, 214, 0.08) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(circle at center, rgba(0, 0, 0, 0.9), transparent 88%);
}

.login-shell__scanline {
  top: -20%;
  left: 0;
  width: 100%;
  height: 160px;
  background: linear-gradient(180deg, rgba(84, 219, 210, 0), rgba(84, 219, 210, 0.18), rgba(84, 219, 210, 0));
  animation: scanlineMove 7s linear infinite;
}

.login-layout {
  position: relative;
  z-index: 1;
  width: min(1320px, 100%);
  min-height: calc(100vh - 48px);
  margin: 0 auto;
  display: grid;
  grid-template-columns: minmax(0, 1.24fr) minmax(390px, 470px);
  background: linear-gradient(180deg, rgba(7, 19, 30, 0.92), rgba(9, 25, 38, 0.88));
  border: 1px solid rgba(108, 176, 213, 0.2);
  border-radius: 34px;
  overflow: hidden;
  box-shadow:
    0 32px 110px rgba(2, 10, 16, 0.46),
    inset 0 1px 0 rgba(175, 232, 255, 0.08);
  backdrop-filter: blur(22px);
}

.login-brand-panel {
  position: relative;
  padding: 60px 56px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  background:
    linear-gradient(180deg, rgba(36, 219, 210, 0.06), rgba(36, 219, 210, 0)),
    linear-gradient(135deg, rgba(9, 29, 42, 0.96) 0%, rgba(10, 35, 48, 0.94) 48%, rgba(14, 38, 52, 0.92) 100%);
  color: #e7fbff;
  border-right: 1px solid rgba(108, 176, 213, 0.16);
}

.login-brand-panel__halo {
  position: absolute;
  top: 42px;
  right: 52px;
  width: 180px;
  height: 180px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(54, 206, 201, 0.24), rgba(54, 206, 201, 0));
  filter: blur(10px);
}

.login-brand-panel__eyebrow,
.login-auth-panel__eyebrow {
  margin: 0 0 12px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.login-brand-panel__eyebrow {
  color: #6fdfe0;
}

.login-brand-panel h1,
.login-auth-panel h2 {
  margin: 0;
  line-height: 1.12;
}

.login-brand-panel h1 {
  font-size: clamp(34px, 3.8vw, 48px);
}

.login-brand-panel__copy,
.login-auth-panel__head p {
  margin: 18px 0 0;
  color: rgba(213, 235, 244, 0.74);
  line-height: 1.8;
  font-size: 15px;
}

.login-brand-panel__status {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  padding: 7px 14px;
  border: 1px solid rgba(111, 223, 224, 0.22);
  border-radius: 999px;
  background: rgba(10, 35, 48, 0.54);
  color: rgba(201, 246, 250, 0.86);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.login-brand-panel__status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #51f3d2;
  box-shadow: 0 0 12px rgba(81, 243, 210, 0.9);
}

.login-brand-panel__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 22px;
}

.login-chip {
  display: inline-flex;
  align-items: center;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  border: 1px solid rgba(111, 223, 224, 0.18);
  background: rgba(9, 34, 48, 0.5);
  color: #c4f5ff;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.login-brand-panel__visual {
  margin-top: 34px;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) 220px;
  gap: 18px;
  align-items: center;
}

.signal-board {
  position: relative;
  padding: 22px 22px 18px;
  border: 1px solid rgba(107, 195, 228, 0.18);
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgba(10, 32, 45, 0.92), rgba(7, 24, 35, 0.78));
  box-shadow:
    inset 0 1px 0 rgba(215, 250, 255, 0.06),
    0 20px 42px rgba(1, 12, 19, 0.28);
  overflow: hidden;
}

.signal-board::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    linear-gradient(90deg, rgba(111, 223, 224, 0.08) 1px, transparent 1px),
    linear-gradient(rgba(111, 223, 224, 0.06) 1px, transparent 1px);
  background-size: 28px 28px;
  opacity: 0.35;
  pointer-events: none;
}

.signal-board__header {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
  color: #dff9ff;
}

.signal-board__header span {
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: rgba(175, 227, 242, 0.72);
}

.signal-board__header strong {
  font-size: 16px;
  font-weight: 600;
}

.signal-board__wave {
  position: relative;
  z-index: 1;
  display: grid;
  align-items: end;
  gap: 8px;
  height: 108px;
  margin-top: 18px;
}

.signal-board__wave--primary {
  grid-template-columns: repeat(12, minmax(0, 1fr));
}

.signal-board__wave--secondary {
  grid-template-columns: repeat(9, minmax(0, 1fr));
  height: 60px;
  margin-top: 14px;
}

.signal-board__wave span {
  border-radius: 999px 999px 6px 6px;
  background: linear-gradient(180deg, rgba(106, 255, 236, 0.95), rgba(47, 147, 255, 0.24));
  animation: wavePulse 2.8s ease-in-out infinite;
}

.signal-board__wave--primary span:nth-child(1) { height: 26%; animation-delay: 0s; }
.signal-board__wave--primary span:nth-child(2) { height: 52%; animation-delay: 0.1s; }
.signal-board__wave--primary span:nth-child(3) { height: 34%; animation-delay: 0.2s; }
.signal-board__wave--primary span:nth-child(4) { height: 84%; animation-delay: 0.3s; }
.signal-board__wave--primary span:nth-child(5) { height: 46%; animation-delay: 0.4s; }
.signal-board__wave--primary span:nth-child(6) { height: 94%; animation-delay: 0.5s; }
.signal-board__wave--primary span:nth-child(7) { height: 58%; animation-delay: 0.6s; }
.signal-board__wave--primary span:nth-child(8) { height: 76%; animation-delay: 0.7s; }
.signal-board__wave--primary span:nth-child(9) { height: 40%; animation-delay: 0.8s; }
.signal-board__wave--primary span:nth-child(10) { height: 88%; animation-delay: 0.9s; }
.signal-board__wave--primary span:nth-child(11) { height: 44%; animation-delay: 1s; }
.signal-board__wave--primary span:nth-child(12) { height: 66%; animation-delay: 1.1s; }

.signal-board__wave--secondary span:nth-child(1) { height: 35%; animation-delay: 0.3s; }
.signal-board__wave--secondary span:nth-child(2) { height: 70%; animation-delay: 0.45s; }
.signal-board__wave--secondary span:nth-child(3) { height: 48%; animation-delay: 0.6s; }
.signal-board__wave--secondary span:nth-child(4) { height: 88%; animation-delay: 0.75s; }
.signal-board__wave--secondary span:nth-child(5) { height: 56%; animation-delay: 0.9s; }
.signal-board__wave--secondary span:nth-child(6) { height: 78%; animation-delay: 1.05s; }
.signal-board__wave--secondary span:nth-child(7) { height: 42%; animation-delay: 1.2s; }
.signal-board__wave--secondary span:nth-child(8) { height: 66%; animation-delay: 1.35s; }
.signal-board__wave--secondary span:nth-child(9) { height: 36%; animation-delay: 1.5s; }

.signal-board__metrics {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 18px;
}

.signal-board__metrics article {
  padding: 14px 12px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(138, 206, 229, 0.12);
}

.signal-board__metrics span {
  display: block;
  font-size: 11px;
  color: rgba(177, 224, 237, 0.68);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.signal-board__metrics strong {
  display: block;
  margin-top: 8px;
  font-size: 21px;
  color: #ebfeff;
}

.care-orbit {
  position: relative;
  width: 220px;
  height: 220px;
  justify-self: center;
}

.care-orbit__ring,
.care-orbit__core,
.care-orbit__node {
  position: absolute;
  border-radius: 50%;
}

.care-orbit__ring {
  inset: 0;
  border: 1px solid rgba(111, 223, 224, 0.18);
}

.care-orbit__ring--outer {
  animation: orbitSpin 16s linear infinite;
}

.care-orbit__ring--inner {
  inset: 28px;
  border-style: dashed;
  animation: orbitSpinReverse 10s linear infinite;
}

.care-orbit__core {
  inset: 62px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: radial-gradient(circle, rgba(61, 229, 220, 0.24), rgba(18, 70, 103, 0.72));
  border: 1px solid rgba(147, 241, 246, 0.24);
  box-shadow: 0 0 28px rgba(61, 229, 220, 0.16);
  color: #ecfdff;
}

.care-orbit__core span {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(200, 244, 248, 0.76);
}

.care-orbit__core strong {
  margin-top: 8px;
  font-size: 30px;
  line-height: 1;
}

.care-orbit__node {
  width: 46px;
  height: 46px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(7, 29, 42, 0.88);
  border: 1px solid rgba(111, 223, 224, 0.2);
  color: #baf7ff;
  font-size: 11px;
  letter-spacing: 0.08em;
  backdrop-filter: blur(8px);
  box-shadow: 0 10px 24px rgba(1, 12, 19, 0.18);
}

.care-orbit__node--top {
  top: 0;
  left: 50%;
  transform: translateX(-50%);
}

.care-orbit__node--right {
  right: 0;
  top: 50%;
  transform: translateY(-50%);
}

.care-orbit__node--bottom {
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
}

.care-orbit__node--left {
  left: 0;
  top: 50%;
  transform: translateY(-50%);
}

.login-brand-panel__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  margin-top: 34px;
}

.brand-metric-card,
.brand-policy-card {
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(102, 191, 224, 0.14);
  background: rgba(10, 34, 46, 0.58);
  box-shadow: inset 0 1px 0 rgba(224, 250, 255, 0.04);
}

.brand-metric-card span {
  display: block;
  font-size: 12px;
  letter-spacing: 0.12em;
  color: #6fdfe0;
  text-transform: uppercase;
}

.brand-metric-card strong {
  display: block;
  margin-top: 10px;
  color: #edfaff;
  font-size: 20px;
  line-height: 1.3;
}

.brand-metric-card p,
.brand-policy-card ul {
  margin: 12px 0 0;
  color: rgba(199, 227, 236, 0.76);
  line-height: 1.8;
  font-size: 14px;
}

.brand-policy-card__head {
  display: flex;
  align-items: center;
  gap: 12px;
  color: #edfaff;
}

.brand-policy-card__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 72px;
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  background: linear-gradient(135deg, #22c7c9, #2b6cfa);
  color: #041520;
  font-size: 12px;
  font-weight: 600;
}

.brand-policy-card ul {
  padding-left: 18px;
}

.login-brand-panel__stream {
  display: grid;
  gap: 10px;
  margin-top: 22px;
}

.stream-item {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(102, 191, 224, 0.12);
  background: rgba(8, 29, 42, 0.44);
}

.stream-item__label {
  font-size: 12px;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: #77dfe2;
}

.stream-item strong {
  color: #dff7ff;
  font-size: 14px;
  font-weight: 500;
  text-align: right;
}

.login-auth-panel {
  position: relative;
  padding: 56px 40px 40px;
  background:
    linear-gradient(180deg, rgba(245, 251, 255, 0.96) 0%, rgba(230, 243, 252, 0.92) 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.login-auth-panel__glow {
  position: absolute;
  top: -80px;
  right: -60px;
  width: 220px;
  height: 220px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(50, 130, 246, 0.18), rgba(50, 130, 246, 0));
  filter: blur(8px);
}

.login-auth-panel__eyebrow {
  color: #146884;
}

.login-auth-panel__eyebrow-wrap {
  display: flex;
  align-items: center;
  gap: 14px;
}

.login-auth-panel__eyebrow-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, rgba(20, 104, 132, 0.3), rgba(20, 104, 132, 0));
}

.login-auth-panel h2 {
  font-size: 32px;
  color: #0d1b2a;
}

.login-auth-panel__stats {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 26px;
}

.auth-stat-card {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(84, 140, 174, 0.16);
  background: rgba(255, 255, 255, 0.66);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.auth-stat-card span {
  display: block;
  font-size: 12px;
  color: #53748a;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.auth-stat-card strong {
  display: block;
  margin-top: 7px;
  font-size: 18px;
  color: #102a43;
}

.login-auth-panel__trust {
  margin-top: 18px;
  padding: 16px 18px;
  border-radius: 22px;
  border: 1px solid rgba(90, 141, 174, 0.16);
  background: rgba(255, 255, 255, 0.56);
}

.trust-pill {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 34px;
  padding: 0 14px;
  border-radius: 999px;
  background: rgba(16, 82, 108, 0.08);
  color: #11526c;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.trust-pill__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #14b8a6;
  box-shadow: 0 0 10px rgba(20, 184, 166, 0.66);
}

.trust-lines {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}

.trust-line {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid rgba(132, 167, 187, 0.18);
}

.trust-line:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}

.trust-line span {
  color: #587384;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.trust-line strong {
  color: #15384d;
  font-size: 13px;
  text-align: right;
}

.login-form {
  margin-top: 28px;
}

.login-submit-btn {
  width: 100%;
  height: 52px;
  margin-top: 10px;
  border: none;
  border-radius: 18px;
  background: linear-gradient(135deg, #0b8fb0 0%, #2369f4 100%);
  box-shadow: 0 20px 36px rgba(35, 105, 244, 0.22);
  transition: transform 0.22s ease, box-shadow 0.22s ease, filter 0.22s ease;
}

.login-submit-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 24px 40px rgba(35, 105, 244, 0.28);
  filter: brightness(1.03);
}

.login-entry-actions {
  margin-top: 20px;
  padding-top: 18px;
  border-top: 1px solid rgba(123, 161, 184, 0.22);
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.login-entry-note {
  display: flex;
  flex-direction: column;
  gap: 4px;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.login-entry-note strong {
  color: #12314a;
  font-size: 14px;
}

:deep(.el-form-item__label) {
  color: #14344a;
  font-weight: 600;
}

:deep(.el-input__wrapper) {
  min-height: 50px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  box-shadow:
    0 0 0 1px rgba(130, 171, 197, 0.24) inset,
    0 8px 22px rgba(26, 70, 103, 0.06);
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 1px rgba(35, 105, 244, 0.58) inset,
    0 0 0 5px rgba(35, 105, 244, 0.1);
}

:deep(.el-input__prefix-inner) {
  color: #4b6c82;
}

@media (max-width: 1080px) {
  .login-layout {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .login-brand-panel {
    padding: 40px 32px 24px;
  }

  .login-brand-panel__grid {
    grid-template-columns: 1fr;
  }

  .login-brand-panel__visual {
    grid-template-columns: 1fr;
  }

  .login-auth-panel {
    padding: 32px;
  }

  .signal-board__metrics {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .login-shell {
    padding: 14px;
  }

  .login-layout {
    border-radius: 26px;
  }

  .login-brand-panel,
  .login-auth-panel {
    padding: 24px 20px;
  }

  .login-auth-panel__stats,
  .login-brand-panel__grid {
    grid-template-columns: 1fr;
  }

  .stream-item,
  .trust-line {
    flex-direction: column;
    align-items: flex-start;
  }

  .stream-item strong,
  .trust-line strong {
    text-align: left;
  }

  .login-entry-actions {
    flex-direction: column;
    align-items: stretch;
  }
}

@keyframes auroraFloat {
  0%, 100% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(12px, -18px, 0) scale(1.08);
  }
}

@keyframes scanlineMove {
  0% {
    transform: translateY(0);
  }
  100% {
    transform: translateY(140vh);
  }
}

@keyframes wavePulse {
  0%, 100% {
    transform: scaleY(0.92);
    opacity: 0.84;
  }
  50% {
    transform: scaleY(1.08);
    opacity: 1;
  }
}

@keyframes orbitSpin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

@keyframes orbitSpinReverse {
  from {
    transform: rotate(360deg);
  }
  to {
    transform: rotate(0deg);
  }
}
</style>
