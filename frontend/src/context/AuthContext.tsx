import React, { createContext, useContext, useEffect, useState } from 'react';
import { AuthResponse, AuthState, User } from '../types/auth';
import { axiosClient } from '../api/axiosClient';

interface AuthContextType extends AuthState {
  login: (authData: AuthResponse) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [state, setState] = useState<AuthState>({
    user: null,
    accessToken: null,
    refreshToken: null,
    isAuthenticated: false,
    isLoading: true,
  });

  useEffect(() => {
    const accessToken = localStorage.getItem('accessToken');
    const refreshToken = localStorage.getItem('refreshToken');
    const storedUser = localStorage.getItem('user');

    if (accessToken && refreshToken && storedUser) {
      try {
        const user: User = JSON.parse(storedUser);
        setState({
          user,
          accessToken,
          refreshToken,
          isAuthenticated: true,
          isLoading: false,
        });
      } catch {
        localStorage.clear();
        setState({ user: null, accessToken: null, refreshToken: null, isAuthenticated: false, isLoading: false });
      }
    } else {
      setState({ user: null, accessToken: null, refreshToken: null, isAuthenticated: false, isLoading: false });
    }
  }, []);

  const login = (authData: AuthResponse) => {
    const user: User = {
      userId: authData.userId,
      email: authData.email,
      fullName: authData.fullName,
      role: authData.role,
    };

    localStorage.setItem('accessToken', authData.accessToken);
    localStorage.setItem('refreshToken', authData.refreshToken);
    localStorage.setItem('user', JSON.stringify(user));

    setState({
      user,
      accessToken: authData.accessToken,
      refreshToken: authData.refreshToken,
      isAuthenticated: true,
      isLoading: false,
    });
  };

  const logout = () => {
    localStorage.clear();
    delete axiosClient.defaults.headers.common.Authorization;
    setState({
      user: null,
      accessToken: null,
      refreshToken: null,
      isAuthenticated: false,
      isLoading: false,
    });
  };

  return (
    <AuthContext.Provider value={{ ...state, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
