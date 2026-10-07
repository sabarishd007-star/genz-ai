import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { axiosClient } from '../api/axiosClient';
import { useAuth } from '../context/AuthContext';
import { AuthResponse } from '../types/auth';

export const Login: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await axiosClient.post<AuthResponse>('/auth/login', {
        email,
        password,
      });

      login(response.data);
      navigate('/app/dashboard');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to authenticate. Please check your credentials.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="relative min-h-screen min-h-[100dvh] bg-black text-white overflow-x-hidden flex flex-col justify-between select-none">
      
      {/* 1. Grain Overlay */}
      <div 
        className="fixed inset-0 z-50 pointer-events-none opacity-[0.04]"
        style={{
          backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")`
        }}
      />

      {/* 2. Hero Ambient Background */}
      <div className="fixed inset-0 z-0 pointer-events-none overflow-hidden">
        <video 
          autoPlay 
          loop 
          muted 
          playsInline 
          className="w-full h-full object-cover opacity-60"
        >
          <source 
            src="https://d8j0ntlcm91z4.cloudfront.net/user_38xzZboKViGWJOttwIXH07lWA1P/hf_20260818_072341_50851634-bbc3-4c33-9acc-7647d4db44aa.mp4" 
            type="video/mp4" 
          />
        </video>
        <div className="absolute inset-0 bg-gradient-to-b from-black/60 via-black/40 to-black/90 pointer-events-none" />
      </div>

      {/* 3. Header */}
      <header className="relative z-40 grid grid-cols-[1fr_auto_1fr] items-center px-6 md:px-10 py-5">
        {/* Logo */}
        <Link 
          to="/" 
          className="inline-flex items-center gap-2.5 justify-self-start text-[15.5px] font-semibold tracking-[-0.03em] text-white hover:opacity-90 transition-opacity"
        >
          <svg className="w-[22px] height-[22px]" viewBox="0 0 24 24" fill="currentColor">
            <g transform="rotate(-30 12 12)">
              <circle cx="7.3" cy="3.2" r="1.45" />
              <rect x="5.5" y="4.7" width="3.6" height="14.6" rx="1.8" />
              <rect x="14.9" y="4.7" width="3.6" height="14.6" rx="1.8" />
              <circle cx="16.7" cy="20.8" r="1.45" />
            </g>
          </svg>
          <span>GenZ<span className="font-normal text-white/90">.ai</span></span>
        </Link>

        {/* Center Nav Pills (Desktop) */}
        <nav className="hidden md:flex items-center gap-2 justify-self-center">
          <Link to="/" className="nav-liquid-pill h-10 px-4.5 rounded-[7px] text-[13.5px] text-[#f3f3f3] inline-flex items-center justify-center">
            Platform
          </Link>
          <a href="#features" className="nav-liquid-pill h-10 px-4.5 rounded-[7px] text-[13.5px] text-[#f3f3f3] inline-flex items-center justify-center">
            Workflows
          </a>
          <a href="#docs" className="nav-liquid-pill h-10 px-4.5 rounded-[7px] text-[13.5px] text-[#f3f3f3] inline-flex items-center justify-center">
            Docs
          </a>
        </nav>

        {/* Header Right Action */}
        <div className="justify-self-end">
          <Link 
            to="/register" 
            className="btn-liquid-solid h-9.5 px-4 text-[13px] tracking-[-0.02em]"
          >
            Create Account
          </Link>
        </div>
      </header>

      {/* 4. Center Hero / Login Form */}
      <main className="relative z-30 flex-1 flex flex-col items-center justify-center px-4 py-8">
        <div className="w-full max-w-[860px] flex flex-col items-center text-center">
          
          {/* Badge */}
          <div className="inline-flex items-center gap-2 mb-4 px-3.5 py-2 rounded-[5px] bg-gradient-to-r from-[#7d7d7d] via-[#2a2a2a] to-[#0a0a0a] text-[#f2f2f2] text-[12.5px] tracking-[-0.01em] shadow-sm">
            <svg className="w-[18px] h-[20px] fill-white drop-shadow-[0_0_3px_rgba(255,255,255,0.45)]" viewBox="0 0 24 24">
              <path d="M12 2.6C12.55 2.6 12.88 3.15 13.08 4.7c.62 4.7 1.52 5.6 6.22 6.22 1.55.2 2.1.53 2.1 1.08s-.55.88-2.1 1.08c-4.7.62-5.6 1.52-6.22 6.22-.2 1.55-.53 2.1-1.08 2.1s-.88-.55-1.08-2.1c-.62-4.7-1.52-5.6-6.22-6.22C3.15 12.88 2.6 12.55 2.6 12s.55-.88 2.1-1.08c4.7-.62 5.6-1.52 6.22-6.22C11.12 3.15 11.45 2.6 12 2.6Z" />
            </svg>
            <span>Operational AI Infrastructure</span>
          </div>

          {/* Typography H1 */}
          <h1 className="text-[34px] sm:text-[44px] md:text-[48px] font-medium tracking-[-0.045em] leading-[1.12] text-white flex flex-col items-center">
            <span className="block">
              Sign in to <em className="font-serif-italic text-[1.08em] tracking-[-0.03em] text-[#9a9a9a] not-italic">AI agents</em>
            </span>
            <span className="block">and secure workflows.</span>
          </h1>

          {/* Lede */}
          <p className="max-w-[470px] mt-2.5 mb-6 text-[#9a9a9a] text-[15px] leading-[1.5] tracking-[-0.015em]">
            Enter your credentials to securely access your high-performance student workspace.
          </p>

          {/* Liquid Glass Login Card */}
          <div className="w-full max-w-[400px] bg-gradient-to-br from-[#141414]/80 to-[#050505]/95 border border-white/[0.14] rounded-[14px] p-6 sm:p-7 backdrop-blur-xl shadow-[0_20px_48px_-10px_rgba(0,0,0,0.85),inset_0_1px_0_rgba(255,255,255,0.15)] text-left">
            
            {error && (
              <div className="mb-4 p-3 bg-red-950/60 border border-red-800/80 rounded-lg text-xs text-red-200">
                {error}
              </div>
            )}

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="flex justify-between items-center text-[12px] font-medium text-[#9a9a9a] mb-1.5">
                  Email address
                </label>
                <input
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="student@university.edu"
                  className="w-full h-[42px] px-3.5 rounded-[7px] border border-white/[0.14] bg-[#0c0c0c]/80 text-white text-[13.5px] outline-none focus:border-white/60 focus:bg-[#121212] focus:shadow-[0_0_16px_rgba(255,255,255,0.12)] transition-all placeholder:text-[#555]"
                />
              </div>

              <div>
                <div className="flex justify-between items-center text-[12px] font-medium text-[#9a9a9a] mb-1.5">
                  <span>Password</span>
                  <a href="#forgot" className="text-[#d8d8d8] text-[11.5px] hover:text-white hover:underline transition-colors">
                    Forgot?
                  </a>
                </div>
                <input
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••••••"
                  className="w-full h-[42px] px-3.5 rounded-[7px] border border-white/[0.14] bg-[#0c0c0c]/80 text-white text-[13.5px] outline-none focus:border-white/60 focus:bg-[#121212] focus:shadow-[0_0_16px_rgba(255,255,255,0.12)] transition-all placeholder:text-[#555]"
                />
              </div>

              <button
                type="submit"
                disabled={loading}
                className="btn-liquid-solid w-full h-[42px] text-[14px] mt-2 disabled:opacity-50 cursor-pointer"
              >
                {loading ? 'Authenticating...' : 'Authenticate & Enter'}
              </button>
            </form>

            <div className="mt-4 pt-3.5 border-t border-white/[0.08] flex items-center justify-center gap-1.5 text-[12.5px] text-[#9a9a9a]">
              <span>Don't have an account?</span>
              <Link to="/register" className="text-white font-medium hover:underline">
                Register free
              </Link>
            </div>
          </div>

        </div>
      </main>

      {/* 5. Stats Footer */}
      <footer className="relative z-30 flex flex-col md:flex-row items-center justify-between gap-4 md:gap-6 px-6 md:px-18 py-6 pb-[max(24px,env(safe-area-inset-bottom))] text-[#d8d8d8] text-[13px] md:text-[13.5px]">
        
        {/* Stat 1 */}
        <div className="inline-flex items-center gap-3.5 whitespace-nowrap">
          <svg className="w-5 h-5 flex-shrink-0" viewBox="0 0 24 24">
            <defs>
              <linearGradient id="reactPillGrad1" x1="3" y1="2" x2="14" y2="22" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#ffffff" stopOpacity="0.38"/>
                <stop offset="100%" stopColor="#3a3a3a" stopOpacity="0.62"/>
              </linearGradient>
              <linearGradient id="reactPillGrad2" x1="3" y1="2" x2="14" y2="22" gradientUnits="userSpaceOnUse">
                <stop offset="0%" stopColor="#3a3a3a" stopOpacity="0.38"/>
                <stop offset="100%" stopColor="#ffffff" stopOpacity="0.62"/>
              </linearGradient>
            </defs>
            <rect x="3.4" y="2.6" width="7.2" height="18.8" rx="3.6" fill="url(#reactPillGrad1)" />
            <rect x="13.4" y="2.6" width="7.2" height="18.8" rx="3.6" fill="url(#reactPillGrad2)" />
            <rect x="9.2" y="10.9" width="5.6" height="2.2" rx="1.1" fill="#4a4a4a" />
          </svg>
          <span>4.2M+ workflows automated</span>
        </div>

        {/* Stat 2 */}
        <div className="inline-flex items-center gap-3.5 whitespace-nowrap">
          <svg className="w-5 h-5 flex-shrink-0" viewBox="0 0 24 24">
            <rect x="2.4" y="2.4" width="19.2" height="19.2" rx="6.2" fill="#ffffff" />
            <path d="M12 7.1v7.4" stroke="#111111" strokeWidth="1.85" strokeLinecap="round" />
            <path d="M8.15 12.35L12 16.2l3.85-3.85" stroke="#111111" strokeWidth="1.85" strokeLinecap="round" strokeLinejoin="round" fill="none" />
          </svg>
          <span>92% reduction in manual operations</span>
        </div>

        {/* Stat 3 */}
        <div className="inline-flex items-center gap-3.5 whitespace-nowrap">
          <svg className="w-[38px] h-[21px] flex-shrink-0" viewBox="0 0 40 22">
            <circle cx="10.2" cy="11" r="9.2" fill="#2b2b2b" />
            <ellipse cx="10.2" cy="12.1" rx="4.15" ry="3.7" fill="#f4f4f4" />
            <polygon points="8.1,7.2 9.2,9.2 7.1,9.2" fill="#2b2b2b" />
            <polygon points="12.3,7.2 13.3,9.2 11.2,9.2" fill="#2b2b2b" />
            <circle cx="8.8" cy="11.8" r="0.7" fill="#1a1a1a" />
            <circle cx="11.6" cy="11.8" r="0.7" fill="#1a1a1a" />

            <circle cx="20.2" cy="11" r="9.2" fill="#ffffff" />
            <circle cx="17.2" cy="10" r="1.7" fill="#111111" />
            <circle cx="23.2" cy="10" r="1.7" fill="#111111" />
            <ellipse cx="20.2" cy="12.5" rx="1.2" ry="0.8" fill="#111111" />
            <path d="M 16.8 14.2 Q 20.2 17.5 23.6 14.2" stroke="#111111" strokeWidth="1.2" fill="none" strokeLinecap="round" />

            <circle cx="30.2" cy="11" r="9.2" fill="#f26b1d" />
            <text x="30.2" y="15.1" fontFamily="'Inter', sans-serif" fontWeight="700" fontSize="12.5" fill="#ffffff" textAnchor="middle">e</text>
          </svg>
          <span>180+ operational teams onboarded</span>
        </div>

      </footer>

    </div>
  );
};
