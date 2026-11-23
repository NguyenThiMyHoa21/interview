import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { ApiError } from '@/api/client'
import { useForgotPassword, useMe, useResetPassword } from '@/api/hooks'
import { LIMITS } from '@/api/types'
import { Card } from '@/components/Card'
import { PasswordInput } from '@/components/PasswordInput'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Field } from '@/features/library/fields'
import { errorMessage } from '@/lib/errors'
import { notifySaved } from '@/lib/notify'

export function ForgotPasswordPage() {
  const me = useMe()
  const navigate = useNavigate()
  const forgot = useForgotPassword()
  const reset = useResetPassword()

  const [step, setStep] = useState<'request' | 'reset'>('request')
  const [email, setEmail] = useState('')
  const [devCode, setDevCode] = useState<string | null>(null)
  const [code, setCode] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [formError, setFormError] = useState<string | null>(null)

  if (me.data && (!me.data.auth_enabled || me.data.user)) return <Navigate to="/" replace />

  const submitRequest = (e: FormEvent) => {
    e.preventDefault()
    if (forgot.isPending) return
    setFormError(null)
    const trimmed = email.trim()
    if (!trimmed || !/^\S+@\S+\.\S+$/.test(trimmed)) {
      setFormError('Vui lòng nhập email hợp lệ.')
      return
    }
    forgot.mutate(trimmed, {
      onSuccess: (res) => {
        setDevCode(res.dev_code ?? null)
        setStep('reset')
      },
      onError: (err) => setFormError(errorMessage(err)),
    })
  }

  const submitReset = (e: FormEvent) => {
    e.preventDefault()
    if (reset.isPending) return
    setFormError(null)
    if (code.trim().length !== 6) {
      setFormError('Mã xác nhận gồm 6 chữ số.')
      return
    }
    if (newPassword.length < LIMITS.passwordMinChars) {
      setFormError(`Mật khẩu mới cần ít nhất ${LIMITS.passwordMinChars} ký tự.`)
      return
    }
    if (confirm !== newPassword) {
      setFormError('Mật khẩu nhập lại không khớp.')
      return
    }
    reset.mutate(
      { email: email.trim(), code: code.trim(), new_password: newPassword },
      {
        onSuccess: () => {
          notifySaved('Đặt lại mật khẩu thành công. Hãy đăng nhập lại!')
          navigate('/login', { replace: true })
        },
        onError: (err) => {
          if (err instanceof ApiError && err.fields) {
            const first = Object.values(err.fields)[0]
            if (first) return setFormError(first)
          }
          setFormError(errorMessage(err))
        },
      },
    )
  }

  return (
    <div className="grid w-full max-w-sm gap-6 py-8">
      <div className="grid gap-1 text-center">
        <p className="text-xl font-semibold text-accent">Intervia</p>
        <p className="text-sm text-ink2">Khôi phục mật khẩu tài khoản</p>
      </div>

      <Card className="grid gap-5 p-6">
        <h1 className="text-lg font-semibold text-ink">
          {step === 'request' ? 'Quên mật khẩu' : 'Đặt lại mật khẩu mới'}
        </h1>

        {step === 'request' ? (
          <form onSubmit={submitRequest} noValidate className="grid gap-4">
            <p className="text-sm text-ink2">
              Nhập email tài khoản của bạn để nhận mã xác nhận 6 số đặt lại mật khẩu.
            </p>
            <Field label="Email">
              {({ id }) => (
                <Input
                  id={id}
                  type="email"
                  autoComplete="email"
                  placeholder="ten@example.com"
                  value={email}
                  readOnly={forgot.isPending}
                  onChange={(e) => setEmail(e.target.value)}
                />
              )}
            </Field>
            {formError && (
              <p role="alert" className="text-sm text-danger">
                {formError}
              </p>
            )}
            <Button type="submit" loading={forgot.isPending} className="w-full">
              Gửi mã xác nhận
            </Button>
          </form>
        ) : (
          <form onSubmit={submitReset} noValidate className="grid gap-4">
            {devCode && (
              <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-900">
                <p className="font-medium">Mã xác nhận (Demo Local):</p>
                <p className="mt-1 font-mono text-xl font-bold tracking-widest">{devCode}</p>
                <p className="mt-1 text-xs text-emerald-700">
                  Có hiệu lực trong 10 phút cho email {email.trim()}.
                </p>
              </div>
            )}
            <Field label="Mã xác nhận (6 số)">
              {({ id }) => (
                <Input
                  id={id}
                  inputMode="numeric"
                  maxLength={6}
                  placeholder="123456"
                  value={code}
                  readOnly={reset.isPending}
                  onChange={(e) => setCode(e.target.value)}
                />
              )}
            </Field>
            <Field label="Mật khẩu mới" hint={`Ít nhất ${LIMITS.passwordMinChars} ký tự`}>
              {({ id }) => (
                <PasswordInput
                  id={id}
                  autoComplete="new-password"
                  value={newPassword}
                  readOnly={reset.isPending}
                  onChange={(e) => setNewPassword(e.target.value)}
                />
              )}
            </Field>
            <Field label="Nhập lại mật khẩu mới">
              {({ id }) => (
                <PasswordInput
                  id={id}
                  autoComplete="new-password"
                  value={confirm}
                  readOnly={reset.isPending}
                  onChange={(e) => setConfirm(e.target.value)}
                />
              )}
            </Field>
            {formError && (
              <p role="alert" className="text-sm text-danger">
                {formError}
              </p>
            )}
            <Button type="submit" loading={reset.isPending} className="w-full">
              Đặt lại mật khẩu
            </Button>
            <button
              type="button"
              onClick={() => {
                setStep('request')
                setFormError(null)
              }}
              className="text-center text-xs text-ink2 underline-offset-4 hover:underline"
            >
              Gửi lại mã cho email khác
            </button>
          </form>
        )}
      </Card>

      <p className="text-center text-sm text-ink2">
        Nhớ lại mật khẩu rồi?{' '}
        <Link to="/login" className="font-medium text-accent underline-offset-4 hover:underline">
          Quay lại đăng nhập
        </Link>
      </p>
    </div>
  )
}
