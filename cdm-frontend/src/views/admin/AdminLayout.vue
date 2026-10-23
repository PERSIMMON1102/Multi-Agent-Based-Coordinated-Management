<template>
  <el-container class="admin-shell">
    <el-aside width="208px" class="sidebar">
      <div class="brand">
        <div class="brand-kicker">Control Center</div>
        <div class="brand-title">平台管理后台</div>
      </div>

      <el-menu
        :default-active="route.path"
        router
        background-color="#152033"
        text-color="#dbe4f0"
        active-text-color="#f59e0b"
      >
        <el-menu-item index="/admin">
          <el-icon><User /></el-icon>
          <span>医生管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/patient-access">
          <el-icon><MagicStick /></el-icon>
          <span>患者接入</span>
        </el-menu-item>
        <el-menu-item index="/admin/paper-case-review">
          <el-icon><Document /></el-icon>
          <span>病历审核</span>
        </el-menu-item>
        <el-menu-item index="/admin/knowledge">
          <el-icon><Collection /></el-icon>
          <span>知识库管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/videos">
          <el-icon><VideoPlay /></el-icon>
          <span>视频资源管理</span>
        </el-menu-item>
        <el-menu-item index="/admin/clinical-data-jobs">
          <el-icon><Operation /></el-icon>
          <span>临床作业队列</span>
        </el-menu-item>
        <el-menu-item index="/admin/paper-case-audit">
          <el-icon><Document /></el-icon>
          <span>病历访问审计</span>
        </el-menu-item>
        <el-menu-item index="/admin/harness">
          <el-icon><DataAnalysis /></el-icon>
          <span>系统诊断</span>
        </el-menu-item>
        <el-menu-item index="/admin/orchestration">
          <el-icon><DataAnalysis /></el-icon>
          <span>任务治理</span>
        </el-menu-item>
        <el-menu-item index="/admin/knowledge-graph">
          <el-icon><Share /></el-icon>
          <span>知识中心</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container direction="vertical">
      <el-header class="header">
        <div>
          <div class="header-title">管理员端</div>
          <div class="header-subtitle">维护医生账号、患者接入、知识内容与宣教视频资源</div>
        </div>

        <div class="header-actions">
          <el-tag type="warning" effect="dark">系统管理</el-tag>
          <el-tag
            size="small"
            :type="graphStatus?.built ? 'success' : 'warning'"
            effect="plain"
          >
            知识中心 {{ graphStatus?.built ? `已整理 ${graphStatus?.nodeCount || 0}/${graphStatus?.edgeCount || 0}` : '待更新' }}
          </el-tag>
          <el-button size="small" :loading="graphLoading" @click="loadGraphStatus">刷新状态</el-button>
          <el-button size="small" type="primary" :loading="graphRebuilding" @click="rebuildGraph">更新知识中心</el-button>
          <el-dropdown @command="handleCommand">
            <el-button text>
              <el-icon><Setting /></el-icon>
              {{ userStore.username || '管理员' }}
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item command="login">返回登录页</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <div class="main__inner">
          <router-view />
        </div>
      </el-main>
    </el-container>
  </el-container>

  <el-dialog
    v-model="passwordDialogVisible"
    title="修改管理员密码"
    width="460px"
    append-to-body
    align-center
    :close-on-click-modal="!forcePasswordChange"
    :close-on-press-escape="!forcePasswordChange"
    :show-close="!forcePasswordChange"
  >
    <el-alert
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
      title="密码规则"
      :description="forcePasswordChange ? '首次登录必须先修改管理员密码后才能继续使用后台。新密码至少 8 位，且需同时包含字母和数字。' : '新密码至少 8 位，且需同时包含字母和数字。'"
    />

    <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="92px">
      <el-form-item label="当前密码" prop="currentPassword">
        <el-input v-model="passwordForm.currentPassword" type="password" show-password placeholder="请输入当前管理员密码" />
      </el-form-item>
      <el-form-item label="新密码" prop="newPassword">
        <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码" />
      </el-form-item>
      <el-form-item label="确认新密码" prop="confirmPassword">
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button v-if="!forcePasswordChange" @click="passwordDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="passwordSubmitting" @click="submitPasswordChange">保存新密码</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Collection, DataAnalysis, Document, MagicStick, Operation, Setting, Share, User, VideoPlay } from '@element-plus/icons-vue'
import { ElMessage } from '../../utils/element-plus-service'
import { useUserStore } from '../../stores/user'
import { adminApi, knowledgeGraphApi } from '../../api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const graphStatus = ref(null)
const graphLoading = ref(false)
const graphRebuilding = ref(false)
const passwordDialogVisible = ref(false)
const passwordSubmitting = ref(false)
const passwordFormRef = ref()
const forcePasswordChange = computed(() => userStore.isAdmin && userStore.mustChangePassword)
const passwordForm = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})

function validateAdminPassword(_rule, value, callback) {
  if (!value) {
    callback(new Error('请输入新密码'))
    return
  }
  if (value.length < 8 || !/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    callback(new Error('密码至少 8 位，且需同时包含字母和数字'))
    return
  }
  callback()
}

function validateConfirmPassword(_rule, value, callback) {
  if (!value) {
    callback(new Error('请再次输入新密码'))
    return
  }
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的新密码不一致'))
    return
  }
  callback()
}

const passwordRules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [{ validator: validateAdminPassword, trigger: 'blur' }],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }]
}

onMounted(() => {
  loadGraphStatus()
  if (forcePasswordChange.value) {
    openPasswordDialog()
  }
})

function handleCommand(command) {
  if (command === 'password') {
    openPasswordDialog()
    return
  }
  if (command === 'login') {
    if (forcePasswordChange.value) {
      ElMessage.warning('请先完成管理员密码修改')
      openPasswordDialog()
      return
    }
    router.push('/login')
    return
  }
  if (command === 'logout') {
    if (forcePasswordChange.value) {
      ElMessage.warning('请先完成管理员密码修改')
      openPasswordDialog()
      return
    }
    userStore.logout()
    router.replace('/login')
    ElMessage.success('已退出登录')
  }
}

function resetPasswordForm() {
  passwordForm.currentPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
}

function openPasswordDialog() {
  resetPasswordForm()
  passwordFormRef.value?.clearValidate?.()
  passwordDialogVisible.value = true
}

async function submitPasswordChange() {
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return

  passwordSubmitting.value = true
  try {
    await adminApi.changePassword({
      username: userStore.loginName || userStore.username,
      currentPassword: passwordForm.currentPassword,
      newPassword: passwordForm.newPassword
    })
    userStore.setMustChangePassword(false)
    passwordDialogVisible.value = false
    ElMessage.success('管理员密码已更新，请妥善保管新密码')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '修改密码失败')
  } finally {
    passwordSubmitting.value = false
  }
}

async function loadGraphStatus() {
  graphLoading.value = true
  try {
    graphStatus.value = await knowledgeGraphApi.getStatus()
  } catch (error) {
    graphStatus.value = null
  } finally {
    graphLoading.value = false
  }
}

async function rebuildGraph() {
  graphRebuilding.value = true
  try {
    const result = await knowledgeGraphApi.rebuild()
    graphStatus.value = await knowledgeGraphApi.getStatus()
    ElMessage.success(`知识中心更新完成：内容 ${result.nodeCount || 0}，关联 ${result.edgeCount || 0}`)
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '知识中心更新失败')
  } finally {
    graphRebuilding.value = false
  }
}
</script>

<style scoped>
.admin-shell {
  width: 100%;
  min-width: 0;
  min-height: 100vh;
  max-width: var(--app-shell-max-width);
  margin: 0 auto;
  background: linear-gradient(180deg, #edf3f7 0%, #f7fafc 180px, #eef3f7 100%);
}

.sidebar {
  background: linear-gradient(180deg, #152033 0%, #1d2b40 100%);
  box-shadow: 18px 0 40px rgba(17, 28, 38, 0.08);
}

.brand {
  padding: 24px 20px 18px;
  color: #fff;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand-kicker {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: #94a3b8;
}

.brand-title {
  margin-top: 8px;
  font-size: 20px;
  font-weight: 700;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  background: rgba(255, 255, 255, 0.86);
  border-bottom: 1px solid #e5e7eb;
  backdrop-filter: blur(16px);
  box-shadow: 0 12px 28px rgba(17, 28, 38, 0.06);
}

.header-title {
  color: #111827;
  font-size: 18px;
  font-weight: 700;
}

.header-subtitle {
  margin-top: 4px;
  color: #64748b;
  font-size: 13px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.main {
  min-width: 0;
  padding: var(--app-content-padding-y) var(--app-content-padding-x);
  overflow-x: hidden;
}

.main__inner {
  width: 100%;
}

:deep(.el-menu) {
  border-right: none;
}

:deep(.el-menu-item) {
  margin: 6px 12px;
  border-radius: 12px;
}

:deep(.el-menu-item.is-active) {
  background: rgba(245, 158, 11, 0.12) !important;
}
</style>
