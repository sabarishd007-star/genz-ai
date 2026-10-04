export type Role = 'STUDENT' | 'ADMIN' | 'TEACHER' | 'MENTOR';

export interface User {
  userId: string;
  email: string;
  fullName: string;
  role: Role;
}

export interface AuthResponse extends User {
  accessToken: string;
  refreshToken: string;
}

export interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}
