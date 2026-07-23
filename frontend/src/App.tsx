import { Toaster } from 'react-hot-toast';
import { Routes, Route, useNavigate, Link as RouterLink, Navigate } from 'react-router-dom';
import { IconSun, IconMoon, IconBrandGithub, IconLogout, IconLink } from '@tabler/icons-react';
import { useAuth } from './contexts/AuthContext';
import { useAppTheme } from './contexts/ThemeContext';

import Dashboard from './pages/Dashboard';
import Login from './pages/Login';
import Register from './pages/Register';
import MyLinks from './pages/MyLinks';

function App() {
  const { isAuthenticated, user, logout } = useAuth();
  const { theme, setTheme } = useAppTheme();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const toggleTheme = () => {
    setTheme(theme === 'dark' ? 'light' : 'dark');
  };

  return (
    <div className={`min-h-screen bg-background text-foreground flex flex-col ${theme}`}>
      <Toaster position="top-right" />

      <header className="border-b border-divider bg-background sticky top-0 z-40">
        <div className="container mx-auto px-4 h-16 flex items-center justify-between">
          <RouterLink to="/" className="flex items-center gap-2">
            <p className="font-bold text-xl text-primary">TrimURL</p>
          </RouterLink>

          <div className="flex items-center gap-2 sm:gap-4">
            {isAuthenticated && (
              <RouterLink
                to="/links"
                className="flex items-center gap-2 px-3 py-2 text-sm font-medium hover:bg-default-100 rounded-md transition-colors text-default-700"
              >
                <IconLink size={18} />
                <span className="hidden sm:inline">My Links</span>
              </RouterLink>
            )}

            <button
              onClick={toggleTheme}
              className="p-2 hover:bg-default-100 rounded-md transition-colors text-default-700"
              aria-label="Toggle Dark Mode"
            >
              {theme === 'dark' ? <IconSun size={20} /> : <IconMoon size={20} />}
            </button>

            {!isAuthenticated ? (
              <RouterLink
                to="/login"
                className="ml-2 bg-primary/10 text-primary px-4 py-2 rounded-md font-medium text-sm hover:bg-primary/20 transition-colors"
              >
                Login
              </RouterLink>
            ) : (
              <div className="flex items-center gap-4 ml-2">
                <span className="hidden sm:block text-default-500 text-sm">{user?.email}</span>
                <button
                  onClick={handleLogout}
                  className="flex items-center gap-2 text-danger hover:bg-danger/10 px-3 py-2 rounded-md transition-colors text-sm font-medium"
                >
                  <IconLogout size={18} />
                  <span className="hidden sm:inline">Logout</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      <main className="flex-grow">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/login" element={!isAuthenticated ? <Login /> : <Navigate to="/" />} />
            <Route path="/register" element={!isAuthenticated ? <Register /> : <Navigate to="/" />} />
            <Route path="/links" element={isAuthenticated ? <MyLinks /> : <Navigate to="/login" />} />
            <Route path="*" element={
              <div className="text-center mt-32">
                <h1 className="text-4xl font-bold">404 - Page Not Found</h1>
                <p className="text-default-500 mt-4">The page you are looking for doesn't exist.</p>
                <RouterLink to="/" className="inline-block mt-8 bg-primary text-primary-foreground px-6 py-3 rounded-md font-medium hover:bg-primary/90 transition-colors">
                  Go Home
                </RouterLink>
              </div>
            } />
          </Routes>
      </main>

      <footer className="w-full border-t border-divider py-6 mt-12 bg-background">
        <div className="container mx-auto px-4 flex justify-center items-center gap-4 text-default-500 text-sm">
          <span>© 2026 TrimURL Platform.</span>
          <a href="https://github.com/Sarcastic-Soul/springboot-url-shortner" target="_blank" rel="noreferrer" className="hover:text-foreground transition-colors">
            <IconBrandGithub size={20} />
          </a>
        </div>
      </footer>
    </div>
  );
}

export default App;
