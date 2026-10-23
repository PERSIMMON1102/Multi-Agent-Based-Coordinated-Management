import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('../views/Register.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    redirect: { name: 'Login' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: { name: 'Login' }
  },
  {
    path: '/admin',
    component: () => import('../views/admin/AdminLayout.vue'),
    meta: { role: 'admin' },
    children: [
      { path: '', name: 'AdminDoctors', component: () => import('../views/admin/DoctorManageView.vue') },
      { path: 'patient-access', name: 'AdminPatientAccess', component: () => import('../views/admin/DemoDataManageView.vue') },
      { path: 'paper-case-review', name: 'AdminPaperCaseReview', component: () => import('../views/admin/PaperCaseReviewManageView.vue') },
      { path: 'knowledge', name: 'AdminKnowledge', component: () => import('../views/admin/KnowledgeManageView.vue') },
      { path: 'videos', name: 'AdminVideos', component: () => import('../views/admin/VideoManageView.vue') },
      { path: 'clinical-data-jobs', name: 'AdminClinicalDataJobs', component: () => import('../views/admin/ClinicalDataJobManageView.vue') },
      { path: 'paper-case-audit', name: 'AdminPaperCaseAudit', component: () => import('../views/admin/PaperCaseAuditManageView.vue') },
      { path: 'harness', name: 'AdminHarness', component: () => import('../views/admin/HarnessManageView.vue') },
      { path: 'orchestration', name: 'AdminOrchestration', component: () => import('../views/admin/OrchestrationManageView.vue') },
      { path: 'knowledge-graph', name: 'AdminKnowledgeGraph', component: () => import('../views/doctor/KnowledgeGraphView.vue') }
    ]
  },
  {
    path: '/doctor',
    component: () => import('../views/doctor/DoctorLayout.vue'),
    meta: { role: 'doctor' },
    children: [
      { path: '', name: 'DoctorDashboard', component: () => import('../views/doctor/Dashboard.vue') },
      { path: 'patients', name: 'PatientList', component: () => import('../views/doctor/PatientList.vue') },
      { path: 'patients/:id', name: 'PatientDetail', component: () => import('../views/doctor/PatientDetail.vue') },
      { path: 'patients/:id/reports/:reportId', name: 'DoctorReportDetail', component: () => import('../views/doctor/DoctorReportDetail.vue') },
      { path: 'paper-case-review', name: 'DoctorPaperCaseReview', component: () => import('../views/admin/PaperCaseReviewManageView.vue') },
      { path: 'assessments', name: 'Assessments', component: () => import('../views/doctor/AssessmentView.vue') },
      { path: 'follow-ups', name: 'FollowUps', component: () => import('../views/doctor/FollowUpView.vue') },
      { path: 'health-record', name: 'HealthRecord', component: () => import('../views/doctor/HealthRecordForm.vue') },
      { path: 'health-trend', name: 'HealthTrend', component: () => import('../views/doctor/HealthTrendView.vue') },
      { path: 'chat', name: 'DoctorChat', component: () => import('../views/shared/ChatView.vue') },
      { path: 'knowledge-graph', name: 'DoctorKnowledgeGraph', component: () => import('../views/doctor/KnowledgeGraphView.vue') },
      { path: 'messaging', name: 'DoctorMessaging', component: () => import('../views/shared/MessagingView.vue') },
      { path: 'forum', name: 'DoctorForum', component: () => import('../views/shared/ForumView.vue') },
      { path: 'profile', name: 'DoctorProfile', component: () => import('../views/doctor/DoctorProfileView.vue') }
    ]
  },
  {
    path: '/patient/setup',
    name: 'PatientSetup',
    component: () => import('../views/patient/ProfileSetup.vue'),
    meta: { role: 'patient', setup: true }
  },
  {
    path: '/patient',
    component: () => import('../views/patient/PatientLayout.vue'),
    meta: { role: 'patient' },
    children: [
      { path: '', name: 'PatientHome', component: () => import('../views/patient/PatientHome.vue') },
      { path: 'daily-record', name: 'DailyRecord', component: () => import('../views/patient/DailyRecordView.vue') },
      { path: 'reports', name: 'PatientReports', component: () => import('../views/patient/ReportView.vue') },
      { path: 'reminders', name: 'PatientReminders', component: () => import('../views/patient/ReminderView.vue') },
      { path: 'health-trend', name: 'PatientHealthTrend', component: () => import('../views/patient/PatientHealthTrendView.vue') },
      { path: 'chat', name: 'PatientChat', component: () => import('../views/shared/ChatView.vue') },
      { path: 'education', name: 'PatientEducation', component: () => import('../views/patient/HealthEducationView.vue') },
      { path: 'videos', name: 'PatientVideos', component: () => import('../views/patient/VideoCenterView.vue') },
      { path: 'messaging', name: 'PatientMessaging', component: () => import('../views/shared/MessagingView.vue') },
      { path: 'forum', name: 'PatientForum', component: () => import('../views/shared/ForumView.vue') },
      { path: 'profile', name: 'PatientProfile', component: () => import('../views/patient/PatientProfileView.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from) => {
  const userStore = useUserStore()
  const homeByRole = () => {
    if (userStore.role === 'admin') return '/admin'
    if (userStore.role === 'doctor') return '/doctor'
    return '/patient'
  }

  // 公开页面：已登录用户回到各自工作台
  if (to.meta.public) {
    if (userStore.isLoggedIn) {
      return homeByRole()
    }
    return true
  }

  // 未登录跳转登录页
  if (!userStore.isLoggedIn) {
    return { name: 'Login', query: { redirect: to.fullPath } }
  }

  // 检查角色权限
  const requiredRole = to.matched.find(r => r.meta.role)?.meta.role
  if (requiredRole && requiredRole !== userStore.role) {
    return homeByRole()
  }

  // 患者未完善资料时，强制跳转到 setup 页面
  if (userStore.role === 'patient' && !userStore.profileCompleted && !to.meta.setup) {
    return '/patient/setup'
  }

  return true
})

export default router
