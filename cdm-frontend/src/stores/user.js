import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('cdm_token') || '')
  const role = ref(localStorage.getItem('cdm_role') || '')
  const username = ref(localStorage.getItem('cdm_username') || '')
  const loginName = ref(localStorage.getItem('cdm_loginName') || '')
  const patientId = ref(Number(localStorage.getItem('cdm_patientId')) || null)
  const doctorId = ref(Number(localStorage.getItem('cdm_doctorId')) || null)
  const assignedDoctorId = ref(Number(localStorage.getItem('cdm_assignedDoctorId')) || null)
  const avatar = ref(localStorage.getItem('cdm_avatar') || '')
  const profileCompleted = ref(localStorage.getItem('cdm_profileCompleted') !== 'false')
  const mustChangePassword = ref(localStorage.getItem('cdm_mustChangePassword') === 'true')

  const isLoggedIn = computed(() => !!role.value && !!token.value)
  const isDoctor = computed(() => role.value === 'doctor')
  const isPatient = computed(() => role.value === 'patient')
  const isAdmin = computed(() => role.value === 'admin')

  function login(userRole, name, pid, rawLoginName, did, assignedDid, forcePasswordChange, authToken) {
    token.value = authToken || ''
    role.value = userRole
    username.value = name
    loginName.value = rawLoginName || name
    patientId.value = pid || null
    doctorId.value = userRole === 'doctor' ? (did || null) : null
    assignedDoctorId.value = userRole === 'patient' ? (assignedDid ?? did ?? null) : null
    mustChangePassword.value = userRole === 'admin' ? !!forcePasswordChange : false
    avatar.value = ''
    if (token.value) {
      localStorage.setItem('cdm_token', token.value)
    } else {
      localStorage.removeItem('cdm_token')
    }
    localStorage.setItem('cdm_role', userRole)
    localStorage.setItem('cdm_username', name)
    localStorage.setItem('cdm_loginName', rawLoginName || name)
    if (pid) {
      localStorage.setItem('cdm_patientId', pid)
    } else {
      localStorage.removeItem('cdm_patientId')
    }
    if (did) {
      localStorage.setItem('cdm_doctorId', did)
    } else {
      localStorage.removeItem('cdm_doctorId')
    }
    if (assignedDoctorId.value) {
      localStorage.setItem('cdm_assignedDoctorId', assignedDoctorId.value)
    } else {
      localStorage.removeItem('cdm_assignedDoctorId')
    }
    if (mustChangePassword.value) {
      localStorage.setItem('cdm_mustChangePassword', 'true')
    } else {
      localStorage.removeItem('cdm_mustChangePassword')
    }
    localStorage.removeItem('cdm_avatar')
  }

  function setAvatar(url) {
    avatar.value = url || ''
    if (url) {
      localStorage.setItem('cdm_avatar', url)
    } else {
      localStorage.removeItem('cdm_avatar')
    }
  }

  function setProfileCompleted(val) {
    profileCompleted.value = !!val
    localStorage.setItem('cdm_profileCompleted', val ? 'true' : 'false')
  }
  function setAssignedDoctorId(id) {
    assignedDoctorId.value = id || null
    if (id) {
      localStorage.setItem('cdm_assignedDoctorId', id)
    } else {
      localStorage.removeItem('cdm_assignedDoctorId')
    }
  }

  function setUsername(name) {
    username.value = name
    localStorage.setItem('cdm_username', name)
  }

  function setMustChangePassword(val) {
    mustChangePassword.value = !!val
    if (mustChangePassword.value) {
      localStorage.setItem('cdm_mustChangePassword', 'true')
    } else {
      localStorage.removeItem('cdm_mustChangePassword')
    }
  }

  function logout() {
    token.value = ''
    role.value = ''
    username.value = ''
    loginName.value = ''
    patientId.value = null
    doctorId.value = null
    assignedDoctorId.value = null
    avatar.value = ''
    profileCompleted.value = true
    mustChangePassword.value = false
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
  }

  return { token, role, username, loginName, patientId, doctorId, assignedDoctorId, avatar, profileCompleted, mustChangePassword, isLoggedIn, isDoctor, isPatient, isAdmin, login, setAvatar, setProfileCompleted, setAssignedDoctorId, setUsername, setMustChangePassword, logout }
})
