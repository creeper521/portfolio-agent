import { createApp } from 'vue'

import App from './app/App.vue'
import { createAppRouter } from './app/router'
import './app/styles/main.css'

createApp(App).use(createAppRouter()).mount('#app')
