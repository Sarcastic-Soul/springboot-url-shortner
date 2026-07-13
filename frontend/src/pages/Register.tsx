import { TextInput, PasswordInput, Paper, Title, Container, Button, Stack, Text, Alert } from '@mantine/core';
import { IconAlertCircle } from '@tabler/icons-react';
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuth } from '../contexts/AuthContext';

export default function Register() {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { login } = useAuth();

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    
    try {
      const response = await authApi.register({ username, email, password });
      login(response.accessToken);
      navigate('/');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to register. Username or email might be taken.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Container size="xs" mt={100}>
      <Paper shadow="sm" radius="md" p="xl" withBorder>
        <Stack gap="lg">
          <Title order={2} ta="center">Create an Account</Title>
          <Text c="dimmed" ta="center" size="sm" mt="-md">
            Join TrimURL today and start shortening links
          </Text>
          
          {error && <Alert icon={<IconAlertCircle size={16} />} color="red">{error}</Alert>}

          <form onSubmit={handleRegister}>
            <Stack>
              <TextInput
                label="Username"
                placeholder="yourusername"
                required
                value={username}
                onChange={(e) => setUsername(e.currentTarget.value)}
              />
              <TextInput
                label="Email"
                placeholder="you@mantine.dev"
                required
                value={email}
                onChange={(e) => setEmail(e.currentTarget.value)}
              />
              <PasswordInput
                label="Password"
                placeholder="Your password (min 8 characters)"
                required
                value={password}
                onChange={(e) => setPassword(e.currentTarget.value)}
              />
              <Button type="submit" fullWidth loading={loading} mt="md" variant="gradient" gradient={{ from: 'blue', to: 'cyan', deg: 90 }}>
                Sign up
              </Button>
            </Stack>
          </form>

          <Text ta="center" size="sm">
            Already have an account?{' '}
            <Text component={Link} to="/login" c="blue" fw={500}>
              Sign in
            </Text>
          </Text>
        </Stack>
      </Paper>
    </Container>
  );
}
