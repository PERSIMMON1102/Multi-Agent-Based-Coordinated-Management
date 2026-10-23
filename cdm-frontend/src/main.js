import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  ArrowDown,
  Bell,
  Calendar,
  Camera,
  ChatDotRound,
  ChatLineSquare,
  Collection,
  Comment,
  DataAnalysis,
  Document,
  Edit,
  EditPen,
  House,
  MagicStick,
  Reading,
  Setting,
  Share,
  TrendCharts,
  User,
  UserFilled,
  VideoPlay,
  Warning
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import { setupElementPlus } from './plugins/element-plus'
import './styles/admin-theme.css'
import './styles/element-plus.js'

const app = createApp(App)

app.use(createPinia())
app.use(router)
setupElementPlus(app)

const elementPlusIcons = {
  ArrowDown,
  Bell,
  Calendar,
  Camera,
  ChatDotRound,
  ChatLineSquare,
  Collection,
  Comment,
  DataAnalysis,
  Document,
  Edit,
  EditPen,
  House,
  MagicStick,
  Reading,
  Setting,
  Share,
  TrendCharts,
  User,
  UserFilled,
  VideoPlay,
  Warning
}

for (const [key, component] of Object.entries(elementPlusIcons)) {
  app.component(key, component)
}

app.mount('#app')
