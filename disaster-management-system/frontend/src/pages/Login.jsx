import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../lib/api'
import Navbar from '../components/Navbar'

export default function Login() {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [loading, setLoading] = useState(false)
  const [step, setStep] = useState(1) // 1 = Login form, 2 = Registration verification OTP form
  const [email, setEmail] = useState('')

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccessMessage('');
    const data = { username: e.target.username.value, password: e.target.password.value };
    try {
      const res = await authApi.login(data.username, data.password);
      localStorage.setItem('token', res.data.token);
      localStorage.setItem('role', res.data.role);
      localStorage.setItem('username', res.data.username);
      navigate('/dashboard');
    } catch (err) {
      if (err.response?.status === 403) {
        setEmail(err.response?.data?.email || data.username);
        setError('Your account is not verified yet. Check your email for the OTP sent during signup.');
        setStep(2);
      } else {
        setError(err.response?.data?.error || err.response?.data?.message || 'Login failed');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyRegisterOtp = async (e) => {
    e.preventDefault();
    setLoading(true);
    setError('');
    setSuccessMessage('');
    const otp = e.target.otp.value;
    try {
      const res = await authApi.verifyOtp(email, otp);
      localStorage.setItem('token', res.data.token);
      localStorage.setItem('role', res.data.role);
      localStorage.setItem('username', res.data.username);
      navigate('/dashboard');
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Verification failed');
    } finally {
      setLoading(false);
    }
  };

  const handleResendOtp = async () => {
    setLoading(true);
    setError('');
    setSuccessMessage('');
    try {
      await authApi.resendOtp(email);
      setSuccessMessage('A new verification code has been sent to your email.');
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Failed to resend OTP');
    } finally {
      setLoading(false);
    }
  };

  const inputClass =
    'w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 outline-none focus:border-accent-blue text-headline'

  if (step === 2) {
    return (
      <div className="min-h-screen bg-cinematic-black">
        <Navbar />
        <div className="pt-24 pb-16 px-4 flex justify-center">
          <form onSubmit={handleVerifyRegisterOtp} className="glass max-w-md w-full rounded-2xl p-8 space-y-4">
            <h1 className="text-2xl font-bold text-headline">Verify Account</h1>
            <p className="text-body text-sm">
              Your account is not verified. Please enter the 6-digit verification code sent to <strong className="text-accent-blue">{email}</strong>.
            </p>
            <input
              name="otp"
              type="text"
              pattern="[0-9]{6}"
              placeholder="Enter 6-digit OTP"
              className={inputClass}
              maxLength={6}
              required
            />
            {error && <p className="text-neon-red text-sm">{error}</p>}
            {successMessage && <p className="text-accent-blue text-sm">{successMessage}</p>}
            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 rounded-xl bg-accent-blue font-medium disabled:opacity-50"
            >
              {loading ? 'Verifying...' : 'Verify & Login'}
            </button>
            <button
              type="button"
              onClick={handleResendOtp}
              disabled={loading}
              className="w-full py-2 text-center text-sm text-headline hover:underline transition-colors bg-transparent border-none cursor-pointer"
            >
              Resend OTP
            </button>
            <button
              type="button"
              onClick={() => setStep(1)}
              className="w-full py-2 text-center text-sm text-body hover:text-white transition-colors bg-transparent border-none cursor-pointer"
            >
              Cancel
            </button>
          </form>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-cinematic-black">
      <Navbar />
      <div className="pt-24 pb-16 px-4 flex justify-center">
        <form onSubmit={handleSubmit} className="glass max-w-md w-full rounded-2xl p-8 space-y-4">
          <h1 className="text-2xl font-bold text-headline">Login</h1>
          <input name="username" placeholder="Username" className={inputClass} required />
          <input name="password" type="password" placeholder="Password" className={inputClass} required />
          {error && <p className="text-neon-red text-sm">{error}</p>}
          <button type="submit" disabled={loading} className="w-full py-3 rounded-xl bg-accent-blue font-medium disabled:opacity-50">
            {loading ? 'Logging in...' : 'Login'}
          </button>
          <p className="text-body text-sm text-center">
            <Link to="/signup" className="text-accent-blue hover:underline">Create account</Link>
          </p>
        </form>
      </div>
    </div>
  )
}
