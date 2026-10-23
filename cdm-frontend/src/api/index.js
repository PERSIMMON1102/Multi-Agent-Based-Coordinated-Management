import axios from 'axios'

const DEFAULT_REQUEST_TIMEOUT = 5 * 60 * 1000
const CHAT_REQUEST_TIMEOUT = 10 * 60 * 1000

const api = axios.create({
  baseURL: '/api',
  timeout: DEFAULT_REQUEST_TIMEOUT
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('cdm_token')
  if (token) {
    config.headers = config.headers || {}
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  res => res.data,
  err => {
    console.error('API Error:', err.response?.data || err.message)
    if (err.response?.status === 401) {
      localStorage.removeItem('cdm_token')
      localStorage.removeItem('cdm_role')
      localStorage.removeItem('cdm_username')
      localStorage.removeItem('cdm_loginName')
      localStorage.removeItem('cdm_patientId')
      localStorage.removeItem('cdm_doctorId')
      localStorage.removeItem('cdm_assignedDoctorId')
      localStorage.removeItem('cdm_avatar')
      localStorage.removeItem('cdm_profileCompleted')
      localStorage.removeItem('cdm_mustChangePassword')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(err)
  }
)

function toArray(data) {
  return Array.isArray(data) ? data : []
}

function normalizeDailyRecord(record) {
  return {
    ...record,
    recordDate: record?.recordDate || record?.examDate || null
  }
}

function normalizeExamRecord(record) {
  return {
    ...record,
    examDate: record?.examDate || record?.recordDate || null,
    creatinine: record?.creatinine ?? record?.serumCreatinine ?? null,
    triglyceride: record?.triglyceride ?? record?.triglycerides ?? null
  }
}

function calcBmi(height, weight) {
  const h = Number(height)
  const w = Number(weight)
  if (!h || !w || h <= 0) return null
  const meters = h / 100
  return Math.round((w / (meters * meters)) * 10) / 10
}

function toHealthRecordPayload(data = {}) {
  const payload = {
    ...data,
    recordDate: data.recordDate || data.examDate || undefined,
    triglycerides: data.triglycerides ?? data.triglyceride ?? undefined,
    serumCreatinine: data.serumCreatinine ?? data.creatinine ?? undefined,
    recoveryJobId: Number.isInteger(Number(data.recoveryJobId)) && Number(data.recoveryJobId) > 0
      ? Number(data.recoveryJobId)
      : undefined
  }
  if ((payload.bmi == null || payload.bmi === '') && payload.height && payload.weight) {
    payload.bmi = calcBmi(payload.height, payload.weight)
  }
  delete payload.examDate
  delete payload.triglyceride
  delete payload.creatinine
  return payload
}

// 登录认证
export const authApi = {
  login: (username, password) => api.post('/auth/login', { username, password }),
  register: (username, password, name) => api.post('/auth/register', { username, password, name }),
  getDoctorsList: () => api.get('/auth/doctors/list'),
  getConfig: () => api.get('/auth/config')
}

// 患者管理
export const patientApi = {
  list: (params) => api.get('/patients', { params: typeof params === 'string' ? { name: params } : (params || {}) }),
  get: (id) => api.get(`/patients/${id}`),
  create: (data) => api.post('/patients', data),
  update: (id, data) => api.put(`/patients/${id}`, data),
  delete: (id) => api.delete(`/patients/${id}`),
  completeProfile: (id, data) => api.put(`/patients/${id}/complete-profile`, data)
}

// 健康记录（旧接口保留兼容）
export const healthRecordApi = {
  getByPatient: (patientId) => api.get(`/health-records/patient/${patientId}`),
  create: (data) => api.post('/health-records', data)
}

// 患者每日自填数据
export const dailyRecordApi = {
  getByPatient: async (patientId) =>
    toArray(await api.get(`/health-records/patient/${patientId}`)).map(normalizeDailyRecord),
  getRecent: async (patientId) =>
    toArray(await api.get(`/health-records/patient/${patientId}`)).map(normalizeDailyRecord).slice(0, 30),
  create: (data) => api.post('/health-records', toHealthRecordPayload(data))
}

// 医生体检数据
export const medicalExamApi = {
  getByPatient: async (patientId) =>
    toArray(await api.get(`/health-records/patient/${patientId}`)).map(normalizeExamRecord),
  getLatest: async (patientId) => {
    const records = toArray(await api.get(`/health-records/patient/${patientId}`)).map(normalizeExamRecord)
    return records[0] || null
  },
  create: (data) => api.post('/health-records', toHealthRecordPayload(data))
}

export const paperCaseApi = {
  getByPatient: (patientId) => api.get(`/paper-cases/patient/${patientId}`),
  getPatientSummary: (patientId) => api.get(`/paper-cases/patient/${patientId}/summary`),
  getPendingReview: () => api.get('/paper-cases/pending-review'),
  getFailedSync: () => api.get('/paper-cases/sync-failed'),
  getAuditSummary: (params = {}) => api.get('/paper-cases/audit', { params }),
  confirmIcd: (paperCaseId, data) => api.post(`/paper-cases/${paperCaseId}/confirm-icd`, data),
  downloadFile: (paperCaseId) => api.get(`/paper-cases/${paperCaseId}/file`, { responseType: 'blob' }),
  upload: (patientId, file, recordDate, options = {}) => {
    const formData = new FormData()
    formData.append('patientId', String(patientId))
    formData.append('file', file)
    if (recordDate) {
      formData.append('recordDate', recordDate)
    }
    return api.post('/paper-cases/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: options.timeout ?? 10 * 60 * 1000,
      onUploadProgress: options.onUploadProgress
    })
  }
}

export const deviceImportApi = {
  importHuaweiJson: (patientId, file, options = {}) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post(`/device-imports/huawei/patient/${patientId}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: options.timeout ?? 5 * 60 * 1000,
      onUploadProgress: options.onUploadProgress
    })
  }
}

export const clinicalDataJobApi = {
  getSummary: () => api.get('/clinical-data-jobs/summary'),
  listByStatus: (status) => api.get('/clinical-data-jobs', { params: { status } }),
  listByPatient: (patientId, params = {}) => api.get(`/clinical-data-jobs/patient/${patientId}`, { params }),
  listByCorrelationId: (correlationId) => api.get(`/clinical-data-jobs/correlation/${encodeURIComponent(correlationId)}`),
  listRetryable: () => api.get('/clinical-data-jobs/retryable'),
  listRetryableByPatient: (patientId) => api.get(`/clinical-data-jobs/patient/${patientId}/retryable`),
  requeue: (jobId) => api.post(`/clinical-data-jobs/${jobId}/requeue`),
  requeueDue: () => api.post('/clinical-data-jobs/requeue-due'),
  cancel: (jobId, reason) => api.post(`/clinical-data-jobs/${jobId}/cancel`, null, { params: { reason } })
}

// 风险评估
export const assessmentApi = {
  getByPatient: (patientId) => api.get(`/assessments/patient/${patientId}`),
  getByDisease: (patientId, diseaseType) => api.get(`/assessments/patient/${patientId}/disease/${diseaseType}`),
  getLatest: (patientId, diseaseType) => api.get(`/assessments/patient/${patientId}/latest/${diseaseType}`),
  trigger: (patientId, healthData, diseaseType) =>
    api.post(`/assessments/trigger/${patientId}`, healthData, { params: diseaseType ? { diseaseType } : {} })
}

// 随访管理
export const followUpApi = {
  getByPatient: (patientId) => api.get(`/follow-ups/patient/${patientId}`),
  getOverdue: () => api.get('/follow-ups/overdue'),
  getPending: () => api.get('/follow-ups/pending'),
  getInProgress: () => api.get('/follow-ups/in-progress'),
  getCompleted: () => api.get('/follow-ups/completed'),
  getCancelled: () => api.get('/follow-ups/cancelled'),
  create: (data) => api.post('/follow-ups', data),
  update: (id, data) => api.put(`/follow-ups/${id}`, data),
  start: (id) => api.put(`/follow-ups/${id}/start`),
  complete: (id, data = {}) => api.put(`/follow-ups/${id}/complete`, data),
  cancel: (id) => api.put(`/follow-ups/${id}/cancel`)
}

// 医疗资源
export const resourceApi = {
  getAll: () => api.get('/resources'),
  findNearby: (lat, lng, radius) => api.get('/resources/nearby', { params: { lat, lng, radius } }),
  getRecommendations: (patientId) => api.get(`/resources/recommendations/${patientId}`)
}

// FHIR
export const fhirApi = {
  getPatient: (patientId) => api.get(`/fhir/patient/${patientId}`),
  getObservations: (patientId) => api.get(`/fhir/observations/${patientId}`)
}

export const medplumApi = {
  getStatus: (probe = true) => api.get('/medplum/status', { params: { probe } }),
  syncPatient: (patientId, includeObservations = true) =>
    api.post(`/medplum/patient/${patientId}/sync`, null, { params: { includeObservations } }),
  syncAll: (includeObservations = true, continueOnError = true) =>
    api.post('/medplum/sync-all', null, { params: { includeObservations, continueOnError } }),
  getPatient: (patientId) => api.get(`/medplum/patient/${patientId}`),
  getObservations: (patientId, limit = 5) => api.get(`/medplum/patient/${patientId}/observations`, { params: { limit } }),
  getPaperCaseResources: (patientId, limit = 5) => api.get(`/medplum/patient/${patientId}/paper-cases`, { params: { limit } }),
  listReplayableJobs: () => api.get('/medplum/jobs/replayable'),
  replayJobSync: (jobId) => api.post(`/medplum/jobs/${jobId}/replay-sync`),
  replayFailedJobs: (limit = 10) => api.post('/medplum/jobs/replay-failed', null, { params: { limit } })
}

// AI健康咨询
export const chatApi = {
  send: ({ sessionId, ownerRole, ownerUserId, patientId, message }) =>
    api.post('/chat/send', { sessionId, ownerRole, ownerUserId, patientId, message }, {
      timeout: CHAT_REQUEST_TIMEOUT
    }),
  clearSession: (sessionId, params) => api.delete(`/chat/session/${sessionId}`, { params }),
  getStatus: () => api.get('/chat/status'),
  getBootstrap: (params) => api.get('/chat/bootstrap', { params }),
  listSessions: (params) => api.get('/chat/sessions', { params }),
  getHistory: (sessionId, params) => api.get(`/chat/session/${sessionId}/messages`, { params }),
  getOrchestration: (sessionId, params) => api.get(`/chat/session/${sessionId}/orchestration`, { params })
}

export const referenceApi = {
  getDetail: (params) => api.get('/references/detail', { params })
}

// AI智能随访
export const smartFollowUpApi = {
  generate: (patientId) => api.post(`/smart-follow-up/generate/${patientId}`),
  send: (patientId, message, agentSteps = [], historyAlerts = []) =>
    api.post('/smart-follow-up/send', { patientId, message, agentSteps, historyAlerts }),
  getHistory: (params = {}) => api.get('/smart-follow-up/history', { params }),
  feedback: (messageId, feedback) => api.post('/smart-follow-up/feedback', { messageId, feedback })
}

// 健康宣教
export const educationApi = {
  generate: (patientId, options = {}) => api.post(`/education/generate/${patientId}`, null, {
    timeout: options.timeout ?? 5 * 60 * 1000
  }),
  getByPatient: (patientId, params = {}) => api.get(`/education/patient/${patientId}`, { params })
}

// 健康视频
export const videoApi = {
  listAdmin: (params) => api.get('/videos/admin', { params: params || {} }),
  getRecommendationFeedback: () => api.get('/videos/admin/recommendation-feedback'),
  listPublished: (params) => api.get('/videos/published', { params: params || {} }),
  getRecommendations: (patientId, params) => api.get(`/videos/recommendations/${patientId}`, { params: params || {} }),
  startWatch: (data) => api.post('/videos/watch/start', data),
  updateWatchProgress: (watchSessionId, data) => api.put(`/videos/watch/${watchSessionId}/progress`, data),
  completeWatch: (watchSessionId, data = {}) => api.put(`/videos/watch/${watchSessionId}/complete`, data),
  getTranscript: (id) => api.get(`/videos/${id}/transcript`),
  saveTranscript: (id, data) => api.post(`/videos/${id}/transcript`, data),
  create: (data) => api.post('/videos', data),
  update: (id, data) => api.put(`/videos/${id}`, data),
  delete: (id) => api.delete(`/videos/${id}`),
  uploadFile: (file, kind = 'video', options = {}) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('kind', kind)
    return api.post('/videos/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: options.timeout ?? 15 * 60 * 1000,
      onUploadProgress: options.onUploadProgress
    })
  }
}

// 医患消息
export const messagingApi = {
  send: (data) => api.post('/messaging/send', data),
  getConversation: (userId1, userId2) => api.get(`/messaging/conversation/${userId1}/${userId2}`),
  getContacts: (userId) => api.get(`/messaging/contacts/${userId}`)
}

// 论坛
export const forumApi = {
  getPosts: () => api.get('/forum/posts'),
  getPost: (id) => api.get(`/forum/posts/${id}`),
  createPost: (data) => api.post('/forum/posts', data),
  addComment: (postId, data) => api.post(`/forum/posts/${postId}/comments`, data),
  addReply: (commentId, data) => api.post(`/forum/comments/${commentId}/replies`, data),
  deletePost: (postId) => api.delete(`/forum/posts/${postId}`),
  deleteComment: (commentId) => api.delete(`/forum/comments/${commentId}`),
  deleteReply: (replyId) => api.delete(`/forum/replies/${replyId}`)
}

// 消息通知
export const notificationApi = {
  getAll: () => api.get('/notifications'),
  getByPatient: (patientId) => api.get(`/notifications/patient/${patientId}`),
  getUnreadCount: (patientId) => api.get(`/notifications/patient/${patientId}/unread`),
  markRead: (id) => api.put(`/notifications/${id}/read`),
  markAllGlobal: () => api.put('/notifications/read-all'),
  markAllRead: (patientId) => api.put(`/notifications/patient/${patientId}/read-all`),
  deleteOne: (id) => api.delete(`/notifications/${id}`),
  deleteReadGlobal: () => api.delete('/notifications/read'),
  deleteRead: (patientId) => api.delete(`/notifications/patient/${patientId}/read`)
}

// 知识库
export const knowledgeApi = {
  upload: (data) => api.post('/knowledge/upload', data),
  search: (params) => api.get('/knowledge/search', { params }),
  getStatus: () => api.get('/knowledge/status'),
  reindex: () => api.post('/knowledge/reindex'),
  evaluateRag: (cases) => api.post('/knowledge/evaluate-rag', cases)
}

export const knowledgeGraphApi = {
  getStatus: () => api.get('/knowledge-graph/status'),
  getVisualization: (params) => api.get('/knowledge-graph/visualization', { params: params || {} }),
  rebuild: () => api.post('/knowledge-graph/rebuild')
}

// 个人中心
export const profileApi = {
  getPatient: (id) => api.get(`/profile/patient/${id}`),
  updatePatient: (id, data) => api.put(`/profile/patient/${id}`, data),
  getDoctor: (id) => api.get(`/profile/doctor/${id}`),
  updateDoctor: (id, data) => api.put(`/profile/doctor/${id}`, data),
  uploadAvatar: (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/profile/avatar/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}

// 管理员
export const adminApi = {
  getDoctors: () => api.get('/admin/doctors'),
  createDoctor: (data) => api.post('/admin/doctors', data),
  updateDoctor: (id, data) => api.put(`/admin/doctors/${id}`, data),
  deleteDoctor: (id) => api.delete(`/admin/doctors/${id}`),
  changePassword: (data) => api.put('/admin/profile/password', data),
  getHarnessOverview: (params = {}) => api.get('/admin/harness/overview', { params }),
  getOrchestrationActive: () => api.get('/admin/orchestration/active'),
  getOrchestrationOverview: () => api.get('/admin/orchestration/overview'),
  getOrchestrationFilters: () => api.get('/admin/orchestration/filters'),
  getOrchestrationCards: (params = {}) => api.get('/admin/orchestration/cards', { params }),
  getOrchestrationTaskDetail: (taskId) => api.get(`/admin/orchestration/${taskId}`),
  getLatestSessionOrchestrationTask: (sessionId) => api.get(`/admin/orchestration/session/${sessionId}`),
  triggerOrchestrationTick: () => api.post('/admin/orchestration/tick'),
  advanceOrchestrationTaskNow: (taskId) => api.post(`/admin/orchestration/${taskId}/advance-now`),
  pauseOrchestrationTask: (taskId, reason) => api.post(`/admin/orchestration/${taskId}/pause`, null, { params: { reason } }),
  resumeOrchestrationTask: (taskId, reason) => api.post(`/admin/orchestration/${taskId}/resume`, null, { params: { reason } }),
  acknowledgeOrchestrationReview: (taskId, reason) => api.post(`/admin/orchestration/${taskId}/ack-review`, null, { params: { reason } }),
  cancelOrchestrationTask: (taskId, reason) => api.post(`/admin/orchestration/${taskId}/cancel`, null, { params: { reason } }),
  retryOrchestrationTask: (taskId, reason) => api.post(`/admin/orchestration/${taskId}/retry`, null, { params: { reason } }),
  skipOrchestrationSubTask: (taskId, subTaskId, reason) =>
    api.post(`/admin/orchestration/${taskId}/subtasks/${subTaskId}/skip`, null, { params: { reason } }),
  resetOrchestrationSubTask: (taskId, subTaskId, reason) =>
    api.post(`/admin/orchestration/${taskId}/subtasks/${subTaskId}/reset`, null, { params: { reason } }),
  handoffOrchestrationSubTask: (taskId, subTaskId, targetAgent, reason) =>
    api.post(`/admin/orchestration/${taskId}/subtasks/${subTaskId}/handoff`, null, { params: { targetAgent, reason } }),
  handoffOrchestrationSubTaskByCapability: (taskId, subTaskId, capability, reason) =>
    api.post(`/admin/orchestration/${taskId}/subtasks/${subTaskId}/handoff-by-capability`, null, { params: { capability, reason } }),
  getPatientAccessOverview: () => api.get('/admin/patient-access/overview'),
  refreshPatientAccessData: () => api.post('/admin/patient-access/refresh'),
  importPatientAccessMedplumData: (data) => api.post('/admin/patient-access/import-medplum', toHealthRecordPayload(data)),
  getPatientIntegrationOverview: (params = {}) => api.get('/admin/patient-integration/overview', { params }),
  getPatientIntegrationStatus: (patientId) => api.get(`/admin/patient-integration/${patientId}/status`),
  syncPatientIntegration: (patientId) => api.post(`/admin/patient-integration/${patientId}/sync`),
  importPatientIntegrationMockToMedplum: (data) => api.post('/admin/patient-integration/import-medplum', toHealthRecordPayload(data)),
  bindPatientIntegrationDoctor: (patientId, doctorId) =>
    api.put(`/admin/patient-integration/${patientId}/doctor`, { doctorId })
}

// AI健康报告
export const healthReportApi = {
  generatePatientWeekly: (patientId) => api.post(`/health-reports/patient-weekly/${patientId}`),
  generateDoctorAssessment: (patientId) => api.post(`/health-reports/doctor-assessment/${patientId}`),
  getByPatient: (patientId, type) => api.get(`/health-reports/patient/${patientId}`, { params: type ? { type } : {} })
}

export const doctorDashboardApi = {
  getOverview: (doctorId) => api.get('/doctor-dashboard/overview', { params: doctorId ? { doctorId } : {} }),
  getPresence: () => api.get('/doctor-dashboard/presence')
}

export default api
