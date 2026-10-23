<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>

<script setup>
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<style>
html,
body,
#app {
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: 100%;
}

:root {
  --app-font-sans: 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', 'Noto Sans SC', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  --app-shell-max-width: none;
  --app-content-max-width: none;
  --app-dialog-gap: clamp(14px, 3vh, 28px);
  --app-shell-padding: clamp(12px, 1.6vw, 18px);
  --app-content-padding-x: clamp(18px, 2vw, 28px);
  --app-content-padding-y: clamp(20px, 2.2vw, 30px);
  --patient-bg: #eef3ec;
  --patient-bg-soft: #f7f6f1;
  --patient-surface: rgba(255, 255, 255, 0.78);
  --patient-surface-strong: rgba(255, 252, 246, 0.92);
  --patient-border: rgba(34, 70, 86, 0.1);
  --patient-shadow: 0 24px 60px rgba(35, 52, 62, 0.12);
  --patient-shadow-soft: 0 16px 36px rgba(45, 64, 73, 0.08);
  --patient-ink: #1f3340;
  --patient-muted: #6b7d87;
  --patient-primary: #2b6c8f;
  --patient-primary-soft: #dcebf3;
  --patient-warm: #e7d7c4;
  --patient-success-soft: #dff2e8;
  --patient-warning-soft: #fff1d8;
  --patient-danger-soft: #f8dddd;
}

body {
  margin: 0;
  font-family: var(--app-font-sans);
  background:
    radial-gradient(circle at top left, rgba(213, 230, 221, 0.9), transparent 34%),
    radial-gradient(circle at top right, rgba(224, 214, 194, 0.7), transparent 28%),
    linear-gradient(180deg, #edf3ee 0%, #f6f5ef 100%);
  color: var(--patient-ink);
  overflow-x: hidden;
}

#app {
  overflow-x: hidden;
}

:where(.el-container, .el-main, .el-aside, .el-header, .el-footer) {
  min-width: 0;
}

* {
  box-sizing: border-box;
}

button,
input,
textarea,
select {
  font: inherit;
}

::selection {
  background: rgba(43, 108, 143, 0.18);
}

body .el-overlay-dialog {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--app-dialog-gap) 16px;
}

body .el-overlay-dialog .el-dialog {
  margin: 0 !important;
  max-width: calc(100vw - 32px);
  max-height: calc(100dvh - (var(--app-dialog-gap) * 2));
  display: flex;
  flex-direction: column;
}

body .el-overlay-dialog .el-dialog__body {
  overflow-y: auto;
}

@media (max-width: 768px) {
  body .el-overlay-dialog {
    padding: 12px;
  }

  body .el-overlay-dialog .el-dialog {
    max-width: calc(100vw - 24px);
    max-height: calc(100dvh - 24px);
  }
}
</style>
