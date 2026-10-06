import { useState, useEffect } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '../lib/api'
import Navbar from '../components/Navbar'
import PasswordField, { checkPasswordRequirements } from '../components/PasswordField'
import LocationSelector from '../components/LocationSelector'
import { Eye, EyeOff, Check, X, Mail, Navigation } from 'lucide-react'
import { getLocationCoordinates } from '../data/indianLocations'

export default function Signup() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [step, setStep] = useState(1) // 1 = Registration form, 2 = OTP form
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showConfirmPassword, setShowConfirmPassword] = useState(false)
  const [resendTimer, setResendTimer] = useState(0)
  const [resendLoading, setResendLoading] = useState(false)
  const [resendMessage, setResendMessage] = useState('')
  const [locationState, setLocationState] = useState({
    state: '',
    city: '',
    location: '',
  })
  const [coords, setCoords] = useState(null)
  const [geoLoading, setGeoLoading] = useState(false)
  const [geoStatus, setGeoStatus] = useState('idle')

  const requestGeolocation = () => {
    if (!navigator.geolocation) {
      setGeoStatus('unsupported')
      return
    }
    setGeoLoading(true)
    setGeoStatus('detecting')
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        setCoords({
          latitude: pos.coords.latitude,
          longitude: pos.coords.longitude,
        })
        setGeoStatus('granted')
        setGeoLoading(false)
      },
      () => {
        setGeoStatus('denied')
        setGeoLoading(false)
      },
      { timeout: 10000, enableHighAccuracy: true }
    )
  }

  // 30-second countdown timer for Resend OTP
  useEffect(() => {
    let interval = null
    if (resendTimer > 0) {
      interval = setInterval(() => {
        setResendTimer((prev) => (prev > 0 ? prev - 1 : 0))
      }, 1000)
    }
    return () => {
      if (interval) clearInterval(interval)
    }
  }, [resendTimer])

  const handleResendOtp = async () => {
    if (resendTimer > 0 || resendLoading) return
    setError('')
    setResendMessage('')
    setResendLoading(true)
    try {
      await authApi.resendOtp(email)
      setResendMessage('A new verification code has been dispatched to your email!')
      setResendTimer(30)
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Failed to resend OTP')
    } finally {
      setResendLoading(false)
    }
  }

  const handleRegister = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    const fd = new FormData(e.target)
    const data = Object.fromEntries(fd)
    data.password = password
    data.confirmPassword = confirmPassword
    data.state = locationState.state || data.state
    data.city = locationState.city || data.city
    data.location = locationState.location || data.location

    const reqs = checkPasswordRequirements(password)
    if (!reqs.hasMinLength) {
      setError('Password must be at least 8 characters long.')
      setLoading(false)
      return
    }
    if (!reqs.hasNoSpaces) {
      setError('Password cannot contain spaces.')
      setLoading(false)
      return
    }
    if (!reqs.hasUppercase) {
      setError('Password must contain at least one uppercase letter (A–Z).')
      setLoading(false)
      return
    }
    if (!reqs.hasLowercase) {
      setError('Password must contain at least one lowercase letter (a–z).')
      setLoading(false)
      return
    }
    if (!reqs.hasNumber) {
      setError('Password must contain at least one number (0–9).')
      setLoading(false)
      return
    }
    if (!reqs.hasSpecial) {
      setError('Password must contain at least one symbol (@, #, $, !, %, etc.).')
      setLoading(false)
      return
    }
    if (!reqs.isNotCommon) {
      setError('This password is too common (e.g. 12345678, password123). Please choose a stronger password.')
      setLoading(false)
      return
    }
    if (password !== confirmPassword) {
      setError('Passwords do not match.')
      setLoading(false)
      return
    }
    if (!data.state) {
      setError('Please select your state.')
      setLoading(false)
      return
    }
    if (!data.city) {
      setError('Please select your city.')
      setLoading(false)
      return
    }
    if (!data.location) {
      setError('Please select or enter your locality / neighborhood.')
      setLoading(false)
      return
    }

    // Geolocation / coordinate assignment
    if (coords) {
      data.latitude = coords.latitude
      data.longitude = coords.longitude
    } else if (data.state && data.city) {
      const fallback = getLocationCoordinates(data.state, data.city)
      if (fallback) {
        data.latitude = fallback.latitude
        data.longitude = fallback.longitude
      }
    }

    try {
      const res = await authApi.register(data)
      if (res.data.requireOtp) {
        setEmail(data.email)
        setStep(2)
      } else {
        localStorage.setItem('token', res.data.token)
        localStorage.setItem('role', res.data.role)
        localStorage.setItem('username', res.data.username)
        navigate('/dashboard')
      }
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'Registration failed')
    } finally {
      setLoading(false)
    }
  }

  const handleVerifyOtp = async (e) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    const otp = e.target.otp.value
    try {
      const res = await authApi.verifyOtp(email, otp)
      localStorage.setItem('token', res.data.token)
      localStorage.setItem('role', res.data.role)
      localStorage.setItem('username', res.data.username)
      navigate('/dashboard')
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || 'OTP verification failed')
    } finally {
      setLoading(false)
    }
  }

  const inputClass =
    'w-full px-4 py-3 rounded-xl bg-white/5 border border-white/10 focus:border-accent-blue outline-none text-headline'

  if (step === 2) {
    return (
      <div className="min-h-screen bg-cinematic-black">
        <Navbar />
        <div className="pt-24 pb-16 px-4 flex justify-center">
          <form onSubmit={handleVerifyOtp} className="glass max-w-md w-full rounded-2xl p-8 space-y-4">
            <h1 className="text-2xl font-bold text-headline">Verify Email</h1>
            <p className="text-body text-sm">
              We sent a 6-digit verification code to <strong className="text-accent-blue">{email}</strong>.
            </p>

            <div className="p-3.5 rounded-xl bg-blue-950/40 border border-blue-500/30 text-xs text-blue-200 flex items-center gap-2">
              <Mail className="w-4 h-4 text-cyan-400 shrink-0" />
              <p><strong>Verification code sent!</strong> Check your <strong>Inbox</strong> (and <strong>Spam / Junk</strong> folder if not visible).</p>
            </div>

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
            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 rounded-xl bg-accent-blue font-medium disabled:opacity-50"
            >
              {loading ? 'Verifying...' : 'Verify & Register'}
            </button>
            {resendMessage && (
              <p className="text-emerald-400 text-xs text-center font-medium animate-in fade-in">
                ✓ {resendMessage}
              </p>
            )}

            <div className="flex gap-2">
              <button
                type="button"
                disabled={resendTimer > 0 || resendLoading}
                onClick={handleResendOtp}
                className={`flex-1 py-2 text-center text-xs transition-colors rounded-lg ${
                  resendTimer > 0 || resendLoading
                    ? 'text-slate-500 cursor-not-allowed bg-white/5'
                    : 'text-accent-blue hover:underline cursor-pointer'
                }`}
              >
                {resendLoading
                  ? 'Sending...'
                  : resendTimer > 0
                  ? `Resend OTP in ${resendTimer}s`
                  : 'Resend OTP'}
              </button>
              <button
                type="button"
                onClick={() => setStep(1)}
                className="flex-1 py-2 text-center text-xs text-body hover:text-white transition-colors"
              >
                Go Back
              </button>
            </div>
          </form>
        </div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-cinematic-black">
      <Navbar />
      <div className="pt-24 pb-16 px-4 flex justify-center">
        <form onSubmit={handleRegister} className="glass max-w-lg w-full rounded-2xl p-8 space-y-4">
          <h1 className="text-2xl font-bold text-headline">Create Account</h1>

          <div className="space-y-1">
            <input
              name="username"
              type="text"
              placeholder="Username"
              className={inputClass}
              required
            />
          </div>

          <div className="space-y-1">
            <input
              name="email"
              type="email"
              placeholder="Email"
              className={inputClass}
              required
            />
          </div>

          <div className="space-y-1">
            <PasswordField
              password={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Password"
              showChecklist={true}
              required
            />
          </div>

          <div className="space-y-1">
            <div className="relative">
              <input
                name="confirmPassword"
                type={showConfirmPassword ? 'text' : 'password'}
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="Confirm Password"
                className="w-full px-4 py-3 pr-11 rounded-xl bg-white/5 border border-white/10 focus:border-accent-blue outline-none text-headline transition-colors"
                required
              />
              <button
                type="button"
                onClick={() => setShowConfirmPassword(!showConfirmPassword)}
                className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-white transition-colors"
                tabIndex={-1}
                aria-label={showConfirmPassword ? 'Hide password' : 'Show password'}
              >
                {showConfirmPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
              </button>
            </div>
            {confirmPassword && (
              <div className="flex items-center gap-1.5 pt-1 text-xs">
                {password === confirmPassword ? (
                  <span className="text-emerald-400 flex items-center gap-1">
                    <Check className="w-3.5 h-3.5" /> Passwords match
                  </span>
                ) : (
                  <span className="text-red-400 flex items-center gap-1">
                    <X className="w-3.5 h-3.5" /> Passwords do not match
                  </span>
                )}
              </div>
            )}
          </div>

          {/* Optional Browser Geolocation with Visible Consent */}
          <div className="p-3.5 rounded-xl bg-white/5 border border-white/10 space-y-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium text-slate-300 flex items-center gap-1.5">
                <Navigation className="w-3.5 h-3.5 text-accent-blue" />
                Precise Geolocation (Optional)
              </span>
              <button
                type="button"
                onClick={requestGeolocation}
                disabled={geoLoading}
                className="text-xs px-2.5 py-1 rounded-lg bg-accent-blue/20 hover:bg-accent-blue/30 text-accent-blue border border-accent-blue/30 transition-all font-medium flex items-center gap-1"
              >
                {geoLoading ? 'Detecting...' : coords ? 'Re-detect GPS' : 'Detect GPS'}
              </button>
            </div>
            <p className="text-[11px] text-slate-400">
              Your location is used only to send disaster alerts near you.
            </p>
            {coords && (
              <p className="text-[11px] text-emerald-400 flex items-center gap-1 font-medium">
                <Check className="w-3 h-3" /> GPS locked ({coords.latitude.toFixed(4)}, {coords.longitude.toFixed(4)})
              </p>
            )}
            {geoStatus === 'denied' && (
              <p className="text-[11px] text-amber-400">
                GPS access declined or unavailable. Your chosen city/state centroid will be used automatically.
              </p>
            )}
          </div>

          {/* Interactive Indian States, Cities, and Localities Cascading Dropdowns */}
          <LocationSelector
            state={locationState.state}
            city={locationState.city}
            location={locationState.location}
            onChange={(loc) => setLocationState(loc)}
            required={true}
          />

          {error && <p className="text-neon-red text-sm">{error}</p>}
          <button
            type="submit"
            disabled={loading}
            className="w-full py-3 rounded-xl bg-accent-blue font-medium disabled:opacity-50"
          >
            {loading ? 'Sending verification...' : 'Create Account'}
          </button>
          <p className="text-body text-sm text-center">
            Have an account? <Link to="/login" className="text-accent-blue">Login</Link>
          </p>
        </form>
      </div>
    </div>
  )
}
