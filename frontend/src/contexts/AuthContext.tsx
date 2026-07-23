import { createContext, useContext, useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import { userApi, type UserProfileResponse } from '../api/users';

interface AuthContextType {
  token: string | null;
  user: UserProfileResponse | null;
  login: (token: string) => void;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(localStorage.getItem('accessToken'));
  const [user, setUser] = useState<UserProfileResponse | null>(null);

  useEffect(() => {
    if (token) {
      localStorage.setItem('accessToken', token);
      userApi.getMe().then(setUser).catch(() => {
        setToken(null);
        setUser(null);
        localStorage.removeItem('accessToken');
      });
    } else {
      localStorage.removeItem('accessToken');
      setUser(null);
    }
  }, [token]);

  const login = (newToken: string) => {
    setToken(newToken);
  };

  const logout = () => {
    localStorage.removeItem('accessToken');
    setToken(null);
  };

  return (
    <AuthContext.Provider value={{ token, user, login, logout, isAuthenticated: !!token }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
