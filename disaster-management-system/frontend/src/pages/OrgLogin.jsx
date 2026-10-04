import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { orgApi } from '../lib/api'
import Navbar from '../components/Navbar'

export default function OrgLogin() {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [loading, setLoading] = useState(false)
  const [step, setStep] = useState(1) // 1 = Login form, 2 = Registration verification OTP form
  const [email, setEmail] = useState('')

  const handleSubmit = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    setSuccessMessage('')
    const fd = new FormData(e.target)
    const emailVal = fd.get('email')
    const passwordVal = fd.get('password')
    try {
      const res = await orgApi.login(emailVal, passwordVal)
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('role', res.data.role)
      localStorage.setItem('username', res.data.username)
      navigate('/org/dashboard')
    } catch (err) {
      if (err.response?.status === 403) {
        setEmail(err.response?.data?.email || emailVal)
        setError('Your account is not verified yet. Check your email for the OTP sent during signup.')
        setStep(2)
      } else {
        setError(err.response?.data?.error || err.response?.data?.message || 'Login failed')
      }
    } finally {
      setLoading(false)
    }
  }

  const handleVerifyRegisterOtp = async (e) => {
    e.preventDefault()
    setLoading(true)
    setError('')
    setSuccessMessage('')
    const otp = e.target.otp.value
    try {
      const res = await orgApi.verifyOtp(email, otp)
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('role', res.data.role)
      localStorage.setItem('username', res.data.username)
      navigate('/org/dashboard')
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Verification failed')
    } finally {
      setLoading(false)
    }
  }

  const handleResendOtp = async () => {
    setLoading(true)
    setError('')
    setSuccessMessage('')
    try {
      await orgApi.resendOtp(email)
      setSuccessMessage('A new verification code has been sent to your email.')
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Failed to resend OTP')
    } finally {
      setLoading(false)
    }
  }

  const inputClass =
    'w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 outline-none focus:border-accent-orange text-headline'

  if (step === 2) {
    return (
      <div className="min-h-screen bg-cinematic-black">
        <Navbar />
        <div className="pt-24 pb-16 px-4 flex justify-center">
          <form onSubmit={handleVerifyRegisterOtp} className="glass max-w-md w-full rounded-2xl p-8 space-y-4">
            <h1 className="text-2xl font-bold text-headline">Verify Organisation</h1>
            <p className="text-body text-sm">
              Your organisation is not verified. Please enter the 6-digit verification code sent to <strong className="text-accent-orange">{email}</strong>.
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
            {successMessage && <p className="text-accent-orange text-sm">{successMessage}</p>}
            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 rounded-xl bg-accent-orange font-medium disabled:opacity-50"
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
          <h1 className="text-2xl font-bold text-headline">Organisation Login</h1>
          <input name="email" type="email" placeholder="Official Email" className={inputClass} required />
          <input name="password" type="password" placeholder="Password" className={inputClass} required />
          {error && <p className="text-neon-red text-sm">{error}</p>}
          <button type="submit" disabled={loading} className="w-full py-3 rounded-xl bg-accent-orange font-medium disabled:opacity-50">
            {loading ? 'Logging in...' : 'Login'}
          </button>
          <Link to="/org/signup" className="block text-center text-sm text-body hover:text-white transition-colors">Register organisation</Link>
        </form>
      </div>
    </div>
  )
}
