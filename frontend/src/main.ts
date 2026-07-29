import { createApp } from 'vue'

import App from './app/App.vue'
import { createAppRouter } from './app/router'
import { installRuntimeDiagnostics } from './shared/diagnostics/frontendDiagnostics'
import './app/styles/main.css'

installRuntimeDiagnostics()
createApp(App).use(createAppRouter()).mount('#app')
