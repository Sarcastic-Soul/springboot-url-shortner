import { useState } from 'react';
import { Card, CardContent, CardHeader } from '../components/ui/Card';
import { Input, TextField, Label } from '../components/ui/Input';
import { Button, Spinner } from '../components/ui/Button';
import { IconAlertCircle } from '@tabler/icons-react';
import { useNavigate, Link } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuth } from '../contexts/AuthContext';

export default function Login() {
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
    <div className="flex justify-center items-center mt-24 px-4">
      <Card className="w-full max-w-md p-6">
        <CardHeader className="flex flex-col gap-1 pb-4">
          <h2 className="text-2xl font-bold">Welcome back</h2>
          <p className="text-default-500 text-sm">
            Enter your credentials to access your account
          </p>
        </CardHeader>
        <CardContent>
          {error && (
            <div className="bg-danger/20 text-danger-600 p-3 rounded-md flex items-center gap-2 mb-6 text-sm">
              <IconAlertCircle size={18} />
              <span>{error}</span>
            </div>
          )}

          <form onSubmit={handleLogin} className="flex flex-col gap-4">
            <TextField
              isRequired
              value={email}
              onChange={setEmail}
              className="flex flex-col gap-1"
            >
              <Label className="text-sm font-medium">Email</Label>
              <Input type="email" placeholder="you@example.com" />
            </TextField>
            <TextField
              isRequired
              value={password}
              onChange={setPassword}
              className="flex flex-col gap-1"
            >
              <Label className="text-sm font-medium">Password</Label>
              <Input type="password" placeholder="Your password" />
            </TextField>
            <Button 
              type="submit" 
              variant="primary"
              isDisabled={loading}
              className="mt-2 w-full"
            >
              {loading ? <Spinner size="sm" /> : "Sign in"}
            </Button>
          </form>

          <p className="text-center text-sm text-default-500 mt-6">
            Don't have an account?{' '}
            <Link to="/register" className="text-primary font-medium hover:underline">
              Register
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
