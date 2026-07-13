import { TextInput, PasswordInput, Paper, Title, Container, Button, Stack, Text, Alert } from '@mantine/core';
import { IconAlertCircle } from '@tabler/icons-react';
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuth } from '../contexts/AuthContext';
import { useMantineTheme } from '@mantine/core';

export default function Login() {
  const theme = useMantineTheme();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    
    try {
      const response = await authApi.login({ email, password });
      login(response.accessToken);
      navigate('/');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to login');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container size="xs" mt={100}>
      <Paper shadow="sm" radius="md" p="xl" withBorder>
        <Stack gap="lg">
          <Title order={2} ta="center">Welcome back</Title>
          <Text c="dimmed" ta="center" size="sm" mt="-md">
            Enter your credentials to access your account
          </Text>
          
          {error && <Alert icon={<IconAlertCircle size={16} />} color="red">{error}</Alert>}

          <form onSubmit={handleLogin}>
            <Stack>
              <TextInput
                label="Email"
                placeholder="you@mantine.dev"
                required
                value={email}
                onChange={(e) => setEmail(e.currentTarget.value)}
              />
              <PasswordInput
                label="Password"
                placeholder="Your password"
                required
                value={password}
                onChange={(e) => setPassword(e.currentTarget.value)}
              />
              <Button type="submit" fullWidth loading={loading} mt="md">
                Sign in
              </Button>
            </Stack>
          </form>

          <Text ta="center" size="sm">
            Don't have an account?{' '}
            <Text component={Link} to="/register" c={theme.primaryColor} fw={500}>
              Register
            </Text>
          </Text>
        </Stack>
      </Paper>
    </Container>
  );
}
