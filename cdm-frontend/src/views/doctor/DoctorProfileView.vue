<template>
  <div class="profile-page">
    <!-- 头部 Banner -->
    <div class="profile-banner">
      <div class="banner-content">
        <div class="avatar-section" @click="triggerUpload">
          <el-avatar :size="80" :src="resolveAvatar(profile.avatar, 'doctor', profile.name)" style="border: 3px solid #fff; box-shadow: 0 2px 12px rgba(0,0,0,0.15); cursor: pointer; font-size: 28px">
            {{ (profile.name || '').charAt(0) }}
          </el-avatar>
          <div class="avatar-overlay">
            <el-icon :size="20"><Camera /></el-icon>
          </div>
          <input ref="fileInput" type="file" accept="image/*" style="display: none" @change="handleFileChange" />
        </div>
        <div class="banner-info">
          <h2 style="margin: 0; color: #fff; font-size: 22px">{{ profile.name || '加载中...' }}</h2>
          <div style="color: rgba(255,255,255,0.85); font-size: 14px; margin-top: 4px">
            <el-tag effect="dark" size="small" style="border: none; background: rgba(255,255,255,0.2)">
              {{ profile.department || '科室' }}
            </el-tag>
          </div>
        </div>
      </div>
    </div>

    <!-- 基本信息卡片 -->
    <el-card style="margin-top: -30px; margin-left: 20px; margin-right: 20px; border-radius: 12px; position: relative; z-index: 1">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span style="font-weight: 600; font-size: 16px">基本信息</span>
          <el-button v-if="!editing" type="primary" link @click="editing = true">
            <el-icon><Edit /></el-icon> 编辑
          </el-button>
          <div v-else style="display: flex; gap: 8px">
            <el-button size="small" @click="editing = false">取消</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="saveProfile">保存</el-button>
          </div>
        </div>
      </template>

      <template v-if="!editing">
        <div class="info-grid">
          <div class="info-item">
            <div class="info-label">姓名</div>
            <div class="info-value">{{ profile.name || '-' }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">科室</div>
            <div class="info-value">{{ profile.department || '-' }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">联系电话</div>
            <div class="info-value">{{ profile.phone || '-' }}</div>
          </div>
          <div class="info-item">
            <div class="info-label">用户名</div>
            <div class="info-value">{{ profile.username || '-' }}</div>
          </div>
        </div>
      </template>

      <template v-else>
        <el-form :model="editForm" label-width="80px" label-position="left">
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="姓名">
                <el-input v-model="editForm.name" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="科室">
                <el-input v-model="editForm.department" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-row :gutter="20">
            <el-col :span="12">
              <el-form-item label="联系电话">
                <el-input v-model="editForm.phone" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </template>
    </el-card>

    <!-- 工作统计 -->
    <el-card style="margin: 16px 20px; border-radius: 12px">
      <template #header>
        <span style="font-weight: 600; font-size: 16px">账号信息</span>
      </template>
      <div class="info-grid">
        <div class="info-item">
          <div class="info-label">登录账号</div>
          <div class="info-value">{{ userStore.loginName }}</div>
        </div>
        <div class="info-item">
          <div class="info-label">用户角色</div>
          <div class="info-value"><el-tag size="small">医生</el-tag></div>
        </div>
        <div class="info-item">
          <div class="info-label">注册时间</div>
          <div class="info-value">{{ formatDate(profile.createdAt) }}</div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, watch } from 'vue'
import { profileApi } from '../../api'
import { useUserStore } from '../../stores/user'
import { ElMessage } from '../../utils/element-plus-service'
import { resolveAvatar } from '../../utils/avatar'
import dayjs from 'dayjs'

const userStore = useUserStore()
const profile = ref({})
const editing = ref(false)
const saving = ref(false)
const fileInput = ref(null)

const editForm = reactive({ name: '', department: '', phone: '' })

onMounted(async () => {
  await loadProfile()
})

async function loadProfile() {
  if (!userStore.doctorId) {
    ElMessage.warning('请重新登录以加载个人信息')
    return
  }
  try {
    profile.value = await profileApi.getDoctor(userStore.doctorId)
    if (profile.value.avatar) {
      userStore.setAvatar(profile.value.avatar)
    }
  } catch (e) {
    ElMessage.error('加载个人信息失败')
  }
}

watch(editing, (val) => {
  if (val) {
    Object.assign(editForm, {
      name: profile.value.name || '',
      department: profile.value.department || '',
      phone: profile.value.phone || ''
    })
  }
})

async function saveProfile() {
  saving.value = true
  try {
    profile.value = await profileApi.updateDoctor(userStore.doctorId, {
      name: editForm.name,
      department: editForm.department,
      phone: editForm.phone
    })
    editing.value = false
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

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
      await profileApi.updateDoctor(userStore.doctorId, { avatar: res.url })
      profile.value.avatar = res.url
      userStore.setAvatar(res.url)
      ElMessage.success('头像更新成功')
    }
    if (res.message) {
      ElMessage.warning(res.message)
    }
  } catch (e) {
    ElMessage.error('头像上传失败')
  }
  e.target.value = ''
}

function formatDate(dt) {
  return dt ? dayjs(dt).format('YYYY-MM-DD') : '-'
}
</script>

<style scoped>
.profile-page {
  max-width: 800px;
  margin: 0 auto;
}

.profile-banner {
  background: linear-gradient(135deg, #304156 0%, #3d5a80 100%);
  padding: 40px 40px 60px;
  border-radius: 0 0 20px 20px;
}

.banner-content {
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar-section {
  position: relative;
  cursor: pointer;
}

.avatar-overlay {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 28px;
  height: 28px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  border: 2px solid #fff;
}

.banner-info {
  flex: 1;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.info-item {
  padding: 8px 0;
}

.info-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.info-value {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}
</style>
