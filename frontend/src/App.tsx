import { AppShell, Group, Text, ActionIcon, useMantineColorScheme, Container, Button } from '@mantine/core';
import { IconSun, IconMoon, IconBrandGithub, IconLogout, IconLink } from '@tabler/icons-react';
import { Routes, Route, useNavigate, Link, Navigate } from 'react-router-dom';
import Dashboard from './pages/Dashboard';
import Login from './pages/Login';
import Register from './pages/Register';
import MyLinks from './pages/MyLinks';
import { useAuth } from './contexts/AuthContext';

function App() {
  const { colorScheme, toggleColorScheme } = useMantineColorScheme();
  const { isAuthenticated, user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <AppShell header={{ height: 60 }} padding="md">
      <AppShell.Header>
        <Group h="100%" px="md" justify="space-between">
          <Text component={Link} to="/" fw={700} size="xl" c="blue" style={{ textDecoration: 'none' }}>
            TrimURL
          </Text>
          <Group>
            {isAuthenticated && (
              <Button component={Link} to="/links" variant="subtle" leftSection={<IconLink size={16} />}>
                My Links
              </Button>
            )}
            <ActionIcon variant="default" onClick={() => toggleColorScheme()} size="lg" aria-label="Toggle color scheme">
              {colorScheme === 'dark' ? <IconSun size={18} /> : <IconMoon size={18} />}
            </ActionIcon>
            {!isAuthenticated ? (
              <Button variant="light" size="sm" onClick={() => navigate('/login')}>Login</Button>
            ) : (
              <Group gap="sm">
                <Text size="sm" c="dimmed" visibleFrom="xs">{user?.email}</Text>
                <Button variant="subtle" color="red" size="sm" leftSection={<IconLogout size={16}/>} onClick={handleLogout}>
                  Logout
                </Button>
              </Group>
            )}
          </Group>
        </Group>
      </AppShell.Header>

      <AppShell.Main>
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/login" element={!isAuthenticated ? <Login /> : <Navigate to="/" />} />
          <Route path="/register" element={!isAuthenticated ? <Register /> : <Navigate to="/" />} />
          <Route path="/links" element={isAuthenticated ? <MyLinks /> : <Navigate to="/login" />} />
          <Route path="*" element={
            <Container style={{ textAlign: 'center', marginTop: '100px' }}>
              <Text size="xl" fw={700}>404 - Page Not Found</Text>
              <Text c="dimmed" mt="md">The page you are looking for doesn't exist.</Text>
              <Button mt="xl" component={Link} to="/">Go Home</Button>
            </Container>
          } />
        </Routes>
      </AppShell.Main>

      <Container size="md" pb="xl" pt="xl">
        <Group justify="center" gap="sm">
          <Text c="dimmed" size="sm">© 2026 TrimURL Platform.</Text>
          <ActionIcon component="a" href="https://github.com/Sarcastic-Soul/springboot-url-shortner" target="_blank" variant="subtle" color="gray">
            <IconBrandGithub size={20} />
          </ActionIcon>
        </Group>
      </Container>
    </AppShell>
  );
}

export default App;
