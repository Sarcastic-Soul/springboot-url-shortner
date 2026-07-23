import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext.tsx';
import { AppThemeProvider } from './contexts/ThemeContext.tsx';
import App from './App.tsx';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <AppThemeProvider>
          <App />
        </AppThemeProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
