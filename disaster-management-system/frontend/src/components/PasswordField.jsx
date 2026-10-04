import React, { useState } from 'react'
import { Eye, EyeOff, Check, X, ShieldAlert, ShieldCheck } from 'lucide-react'

const COMMON_PASSWORDS = new Set([
  '12345678',
  '123456789',
  '1234567890',
  'password',
  'password123',
  'adminpassword',
  'qwerty123',
  'welcome123',
  'letmein123',
  'pass1234',
  'iloveyou',
  'monkey123',
  'dragon123',
  'master123',
  'sunshine',
])

export function checkPasswordRequirements(password = '') {
  const p = password || ''
  const hasMinLength = p.length >= 8
  const hasUppercase = /[A-Z]/.test(p)
  const hasLowercase = /[a-z]/.test(p)
  const hasNumber = /[0-9]/.test(p)
  const hasSpecial = /[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(p)
  const hasNoSpaces = !/\s/.test(p) && p.length > 0
  const isNotCommon = !COMMON_PASSWORDS.has(p.toLowerCase().trim())

  let score = 0
  if (hasMinLength) score++
  if (hasUppercase) score++
  if (hasLowercase) score++
  if (hasNumber) score++
  if (hasSpecial) score++
  if (hasNoSpaces && p.length > 0) score++
  if (isNotCommon && p.length > 0) score++

  const allPassed =
    hasMinLength &&
    hasUppercase &&
    hasLowercase &&
    hasNumber &&
    hasSpecial &&
    hasNoSpaces &&
    isNotCommon

  return {
    hasMinLength,
    hasUppercase,
    hasLowercase,
    hasNumber,
    hasSpecial,
    hasNoSpaces,
    isNotCommon,
    allPassed,
    score,
  }
}

export default function PasswordField({
  password = '',
  onChange,
  placeholder = 'Password',
  name = 'password',
  id = 'password',
  className = '',
  showChecklist = true,
  required = true,
}) {
  const [showPassword, setShowPassword] = useState(false)
  const [isFocused, setIsFocused] = useState(false)

  const reqs = checkPasswordRequirements(password)

  // Determine strength label & color
  let strengthLabel = 'Too Weak'
  let strengthColor = 'bg-red-500'
  let strengthWidth = '15%'
  let strengthTextColor = 'text-red-400'

  if (reqs.score <= 2) {
    strengthLabel = 'Weak'
    strengthColor = 'bg-red-500'
    strengthWidth = '25%'
    strengthTextColor = 'text-red-400'
  } else if (reqs.score <= 4) {
    strengthLabel = 'Fair'
    strengthColor = 'bg-amber-500'
    strengthWidth = '50%'
    strengthTextColor = 'text-amber-400'
  } else if (reqs.score <= 6) {
    strengthLabel = 'Good'
    strengthColor = 'bg-blue-500'
    strengthWidth = '75%'
    strengthTextColor = 'text-blue-400'
  } else if (reqs.allPassed) {
    strengthLabel = 'Strong'
    strengthColor = 'bg-emerald-500'
    strengthWidth = '100%'
    strengthTextColor = 'text-emerald-400'
  }

  const checklistItems = [
    { label: 'Minimum 8 characters', passed: reqs.hasMinLength },
    { label: 'At least one uppercase letter (A–Z)', passed: reqs.hasUppercase },
    { label: 'At least one lowercase letter (a–z)', passed: reqs.hasLowercase },
    { label: 'At least one number (0–9)', passed: reqs.hasNumber },
    { label: 'At least one symbol (@, #, $, !, %, etc.)', passed: reqs.hasSpecial },
    {
      label: 'No spaces allowed',
      passed: reqs.hasNoSpaces,
      isViolated: /\s/.test(password),
    },
    {
      label: 'No common passwords (e.g. 12345678, password123)',
      passed: reqs.isNotCommon && password.length > 0,
      isViolated: !reqs.isNotCommon && password.length > 0,
    },
  ]

  return (
    <div className="space-y-2">
      <div className="relative">
        <input
          name={name}
          id={id}
          type={showPassword ? 'text' : 'password'}
          value={password}
          onChange={onChange}
          onFocus={() => setIsFocused(true)}
          placeholder={placeholder}
          className={`w-full px-4 py-3 pr-11 rounded-xl bg-white/5 border border-white/10 focus:border-accent-blue outline-none text-headline transition-colors ${className}`}
          required={required}
        />
        <button
          type="button"
          onClick={() => setShowPassword(!showPassword)}
          className="absolute right-3.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-white transition-colors"
          tabIndex={-1}
          aria-label={showPassword ? 'Hide password' : 'Show password'}
        >
          {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
        </button>
      </div>

      {/* Password Strength Meter & Interactive Checklist */}
      {showChecklist && (password.length > 0 || isFocused) && (
        <div className="p-3.5 rounded-xl bg-white/5 border border-white/10 space-y-2.5 transition-all text-xs">
          {/* Strength Bar */}
          <div>
            <div className="flex items-center justify-between text-[11px] mb-1">
              <span className="text-slate-400 flex items-center gap-1 font-medium">
                {reqs.allPassed ? (
                  <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
                ) : (
                  <ShieldAlert className="w-3.5 h-3.5 text-slate-400" />
                )}
                Password Strength
              </span>
              <span className={`font-semibold ${strengthTextColor}`}>{strengthLabel}</span>
            </div>
            <div className="w-full h-1.5 rounded-full bg-white/10 overflow-hidden">
              <div
                className={`h-full ${strengthColor} transition-all duration-300 ease-out`}
                style={{ width: password.length === 0 ? '0%' : strengthWidth }}
              />
            </div>
          </div>

          {/* Checklist */}
          <div className="space-y-1.5 pt-1">
            <p className="text-[11px] text-slate-400 font-medium">Password Requirements:</p>
            <div className="grid grid-cols-1 gap-1">
              {checklistItems.map((item, idx) => (
                <div key={idx} className="flex items-center gap-2 text-[11px]">
                  {item.passed ? (
                    <span className="w-3.5 h-3.5 rounded-full bg-emerald-500/20 text-emerald-400 flex items-center justify-center flex-shrink-0">
                      <Check className="w-2.5 h-2.5" />
                    </span>
                  ) : item.isViolated ? (
                    <span className="w-3.5 h-3.5 rounded-full bg-red-500/20 text-red-400 flex items-center justify-center flex-shrink-0">
                      <X className="w-2.5 h-2.5" />
                    </span>
                  ) : (
                    <span className="w-3.5 h-3.5 rounded-full bg-white/10 flex items-center justify-center flex-shrink-0">
                      <span className="w-1 h-1 rounded-full bg-slate-400" />
                    </span>
                  )}
                  <span
                    className={
                      item.passed
                        ? 'text-emerald-300/90'
                        : item.isViolated
                        ? 'text-red-400 font-medium'
                        : 'text-slate-400'
                    }
                  >
                    {item.label}
                  </span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
