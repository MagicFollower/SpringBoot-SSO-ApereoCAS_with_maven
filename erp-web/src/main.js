import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
// 样式顺序不可换：index.css -> dark/css-vars.css -> design-system.css
import 'element-plus/dist/index.css'
import 'element-plus/theme-chalk/dark/css-vars.css'
import './styles/design-system.css'
import './styles/main.css'
import { applyTheme } from './styles/theme'
import App from './App.vue'
import router from './router'

// 品牌主色：一次写全 7 个派生，避免悬停/选中仍是默认蓝
const PRIMARY = '#2F80ED'

const stored = localStorage.getItem('erp_theme')
const dark = stored ? stored === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches
applyTheme(PRIMARY, dark)

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.mount('#app')
