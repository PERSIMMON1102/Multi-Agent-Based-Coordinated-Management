const FALLBACK_AVATAR_URL = '/default-avatar.png'

const ROLE_THEME = {
  admin: {
    bgStart: '#23395b',
    bgEnd: '#3e5d8c',
    halo: '#f2c66d',
    hair: '#1a2233',
    suit: '#dfe8f7',
    detail: '#ffd98a',
    marker: '#17253d',
    label: 'A',
    accessory: 'glasses'
  },
  doctor: {
    bgStart: '#7fc8ef',
    bgEnd: '#3f8ed8',
    halo: '#d9f2ff',
    hair: '#49607a',
    suit: '#f9fbff',
    detail: '#f05b6b',
    marker: '#245a9a',
    label: 'D',
    accessory: 'cross'
  },
  patient: {
    bgStart: '#f7b4c7',
    bgEnd: '#f4d37a',
    halo: '#fff2d5',
    hair: '#6b4d7f',
    suit: '#f7f7fb',
    detail: '#8fd3b6',
    marker: '#85505e',
    label: 'P',
    accessory: 'sparkle'
  }
}

const avatarCache = new Map()

function normalizeRole(role) {
  const key = String(role || '').trim().toLowerCase()
  return ROLE_THEME[key] ? key : 'patient'
}

function normalizeName(name) {
  return String(name || '').trim()
}

function shouldUseFallback(src) {
  const value = String(src || '').trim()
  return !value || value === FALLBACK_AVATAR_URL
}

function svgToDataUri(svg) {
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`
}

function buildAccessory(theme) {
  switch (theme.accessory) {
    case 'cross':
      return `
        <circle cx="119" cy="44" r="15" fill="${theme.detail}" />
        <rect x="116" y="37" width="6" height="14" rx="2" fill="#ffffff" />
        <rect x="112" y="41" width="14" height="6" rx="2" fill="#ffffff" />
      `
    case 'glasses':
      return `
        <circle cx="68" cy="72" r="10" fill="none" stroke="#475772" stroke-width="4" />
        <circle cx="92" cy="72" r="10" fill="none" stroke="#475772" stroke-width="4" />
        <rect x="77" y="70" width="6" height="3" rx="1.5" fill="#475772" />
        <path d="M58 69c-4-2-7-3-10-2" fill="none" stroke="#475772" stroke-width="3" stroke-linecap="round" />
        <path d="M102 69c4-2 7-3 10-2" fill="none" stroke="#475772" stroke-width="3" stroke-linecap="round" />
      `
    default:
      return `
        <path d="M119 32l3 7 7 3-7 3-3 7-3-7-7-3 7-3z" fill="${theme.detail}" />
        <circle cx="108" cy="53" r="4" fill="#ffffff" opacity="0.9" />
      `
  }
}

function buildAvatarSvg(role, name) {
  const theme = ROLE_THEME[normalizeRole(role)]
  const marker = normalizeName(name).slice(0, 1).toUpperCase() || theme.label
  const accessory = buildAccessory(theme)

  return `
    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 160" role="img" aria-label="${theme.label} avatar">
      <defs>
        <linearGradient id="bg" x1="0%" x2="100%" y1="0%" y2="100%">
          <stop offset="0%" stop-color="${theme.bgStart}" />
          <stop offset="100%" stop-color="${theme.bgEnd}" />
        </linearGradient>
        <linearGradient id="coat" x1="0%" x2="100%" y1="0%" y2="100%">
          <stop offset="0%" stop-color="${theme.suit}" />
          <stop offset="100%" stop-color="rgba(255,255,255,0.92)" />
        </linearGradient>
      </defs>
      <rect width="160" height="160" rx="46" fill="url(#bg)" />
      <circle cx="80" cy="67" r="44" fill="${theme.halo}" opacity="0.35" />
      <ellipse cx="80" cy="122" rx="48" ry="29" fill="url(#coat)" />
      <path d="M41 126c8-18 22-28 39-28s31 10 39 28" fill="none" stroke="rgba(255,255,255,0.95)" stroke-width="16" stroke-linecap="round" />
      <path d="M55 117c5 9 14 16 25 16s20-7 25-16" fill="none" stroke="${theme.detail}" stroke-width="5" opacity="0.5" stroke-linecap="round" />
      <ellipse cx="80" cy="66" rx="30" ry="33" fill="#ffe7d6" />
      <path d="M50 65c0-22 12-37 30-37s30 15 30 37c-3-10-9-18-18-22-4 7-19 12-42 13z" fill="${theme.hair}" />
      <path d="M56 60c4-10 13-18 24-18 13 0 23 7 28 19-7-6-15-9-27-9-11 0-18 3-25 8z" fill="${theme.hair}" opacity="0.92" />
      <ellipse cx="69" cy="74" rx="5.5" ry="7.5" fill="#2d3445" />
      <ellipse cx="91" cy="74" rx="5.5" ry="7.5" fill="#2d3445" />
      <circle cx="67" cy="72" r="1.6" fill="#ffffff" />
      <circle cx="89" cy="72" r="1.6" fill="#ffffff" />
      <path d="M72 89c4 4 12 4 16 0" fill="none" stroke="#d86d74" stroke-width="3.5" stroke-linecap="round" />
      <ellipse cx="59" cy="85" rx="6" ry="3.4" fill="#f7a6b4" opacity="0.65" />
      <ellipse cx="101" cy="85" rx="6" ry="3.4" fill="#f7a6b4" opacity="0.65" />
      <path d="M79 78c-1 4-1 7 1 9" fill="none" stroke="#e0a793" stroke-width="2.6" stroke-linecap="round" />
      <path d="M66 106l14 12 14-12" fill="none" stroke="rgba(255,255,255,0.92)" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" />
      ${accessory}
      <circle cx="40" cy="38" r="16" fill="rgba(255,255,255,0.78)" />
      <text x="40" y="44" text-anchor="middle" font-size="14" font-family="Arial, sans-serif" font-weight="700" fill="${theme.marker}">${marker}</text>
    </svg>
  `.replace(/\s+/g, ' ').trim()
}

export function resolveAvatar(src, role = 'patient', name = '') {
  if (!shouldUseFallback(src)) {
    return src
  }
  const cacheKey = `${normalizeRole(role)}::${normalizeName(name)}`
  if (!avatarCache.has(cacheKey)) {
    avatarCache.set(cacheKey, svgToDataUri(buildAvatarSvg(role, name)))
  }
  return avatarCache.get(cacheKey)
}
