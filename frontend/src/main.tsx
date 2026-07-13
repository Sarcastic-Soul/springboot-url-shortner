import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import { AppThemeProvider } from './contexts/ThemeContext';
import { Notifications } from '@mantine/notifications';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import App from './App.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AppThemeProvider>
      <Notifications position="top-right" />
      <BrowserRouter>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </AppThemeProvider>
  </StrictMode>,
)
