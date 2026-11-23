import { Shield, ShieldAlert, ShieldCheck, Trash2, Users } from 'lucide-react'
import { Link } from 'react-router'
import {
  useAdminDeleteUser,
  useAdminOverview,
  useAdminSessions,
  useAdminUsers,
  useMe,
  useUpdateUserRole,
} from '@/api/hooks'
import { Card } from '@/components/Card'
import { Page, PageTitle } from '@/components/Page'
import { ErrorState, PageSkeleton } from '@/components/PageStates'
import { Button } from '@/components/ui/button'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { errorMessage } from '@/lib/errors'
import { fullDate } from '@/lib/format'
import { notifyError, notifySaved } from '@/lib/notify'
import { cn } from '@/lib/utils'

const STATUS_LABEL: Record<string, { label: string; tone: string }> = {
  completed: { label: 'Hoàn thành', tone: 'bg-emerald-100 text-emerald-800' },
  in_progress: { label: 'Đang phỏng vấn', tone: 'bg-sky-100 text-sky-800' },
  draft: { label: 'Bản nháp', tone: 'bg-slate-100 text-slate-700' },
}

export function AdminPage() {
  const me = useMe()
  const currentUser = me.data?.user
  const isAdmin = currentUser?.role === 'ADMIN'

  const overview = useAdminOverview(isAdmin)
  const users = useAdminUsers(isAdmin)
  const sessions = useAdminSessions(isAdmin)
  const updateRole = useUpdateUserRole()
  const deleteUser = useAdminDeleteUser()

  if (!me.data) return null

  if (!isAdmin) {
    return (
      <Page className="grid gap-6">
        <Card className="mx-auto mt-8 grid max-w-md justify-items-center gap-4 p-8 text-center">
          <ShieldAlert className="size-10 text-danger" />
          <div className="grid gap-1">
            <h1 className="text-lg font-semibold text-ink">403 — Không có quyền truy cập</h1>
            <p className="text-sm text-ink2">
              Trang này chỉ dành cho tài khoản Quản trị viên (ADMIN). Tài khoản hiện tại của bạn có
              quyền <span className="font-medium text-ink">{currentUser?.role ?? 'USER'}</span>.
            </p>
          </div>
          <Button asChild variant="secondary">
            <Link to="/">Quay về trang chủ</Link>
          </Button>
        </Card>
      </Page>
    )
  }

  return (
    <Page width="wide" className="grid gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="grid gap-1">
          <PageTitle className="flex items-center gap-2">
            <Shield className="size-6 text-accent" />
            Quản trị hệ thống (Admin)
          </PageTitle>
          <p className="text-sm text-ink2">
            Quản lý tài khoản, phân quyền USER / ADMIN và theo dõi tất cả phiên phỏng vấn.
          </p>
        </div>
        <span className="inline-flex items-center gap-1.5 rounded-full border border-emerald-300 bg-emerald-50 px-3 py-1 text-xs font-medium text-emerald-800">
          <ShieldCheck className="size-3.5" />
          ADMIN · {currentUser?.email}
        </span>
      </div>

      {/* Overview Stats */}
      {overview.isPending ? (
        <PageSkeleton variant="list" />
      ) : overview.isError ? (
        <ErrorState error={overview.error} onRetry={() => void overview.refetch()} />
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <StatCard
            label="Tổng người dùng"
            value={overview.data.total_users}
            sub={`${overview.data.total_admins} quản trị viên`}
          />
          <StatCard
            label="Tổng phiên phỏng vấn"
            value={overview.data.total_sessions}
            sub={`${overview.data.completed_sessions} đã hoàn thành`}
          />
          <StatCard label="Tổng hồ sơ CV" value={overview.data.total_cvs} sub="Trong thư viện" />
          <StatCard label="Tổng tin tuyển dụng JD" value={overview.data.total_jds} sub="Trong thư viện" />
        </div>
      )}

      {/* Management Tabs */}
      <Tabs defaultValue="users" className="grid gap-4">
        <TabsList className="h-10 w-fit border border-line bg-surface">
          <TabsTrigger
            value="users"
            className="gap-1.5 px-4 text-ink2 data-[state=active]:bg-accent-soft data-[state=active]:text-accent data-[state=active]:shadow-none"
          >
            <Users className="size-4" />
            Người dùng ({users.data?.length ?? 0})
          </TabsTrigger>
          <TabsTrigger
            value="sessions"
            className="px-4 text-ink2 data-[state=active]:bg-accent-soft data-[state=active]:text-accent data-[state=active]:shadow-none"
          >
            Tất cả phiên phỏng vấn ({sessions.data?.length ?? 0})
          </TabsTrigger>
        </TabsList>

        <TabsContent value="users">
          {users.isPending ? (
            <PageSkeleton variant="list" />
          ) : users.isError ? (
            <ErrorState error={users.error} onRetry={() => void users.refetch()} />
          ) : (
            <Card className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-line bg-surface-muted/60 text-xs uppercase text-ink2">
                  <tr>
                    <th className="px-4 py-3">Email</th>
                    <th className="px-4 py-3">Quyền</th>
                    <th className="px-4 py-3">CV</th>
                    <th className="px-4 py-3">Phiên PV</th>
                    <th className="px-4 py-3">Ngày tạo</th>
                    <th className="px-4 py-3 text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {users.data.map((u) => {
                    const isSelf = u.id === currentUser?.id
                    const nextRole = u.role === 'ADMIN' ? 'USER' : 'ADMIN'
                    return (
                      <tr key={u.id} className="hover:bg-surface-muted/30">
                        <td className="px-4 py-3 font-medium text-ink">
                          {u.email}
                          {isSelf && (
                            <span className="ml-2 rounded bg-accent-soft px-1.5 py-0.5 text-[11px] text-accent">
                              Bạn
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3">
                          <span
                            className={cn(
                              'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
                              u.role === 'ADMIN'
                                ? 'bg-emerald-100 text-emerald-800'
                                : 'bg-slate-100 text-slate-700',
                            )}
                          >
                            {u.role}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-ink2">{u.cv_count}</td>
                        <td className="px-4 py-3 text-ink2">{u.session_count}</td>
                        <td className="px-4 py-3 text-ink2">{fullDate(u.created_at)}</td>
                        <td className="px-4 py-3 text-right">
                          <div className="inline-flex items-center gap-2">
                            <Button
                              size="sm"
                              variant="outline"
                              disabled={isSelf || updateRole.isPending}
                              onClick={() =>
                                updateRole.mutate(
                                  { userId: u.id, role: nextRole },
                                  {
                                    onSuccess: () =>
                                      notifySaved(`Đã đổi quyền ${u.email} thành ${nextRole}`),
                                    onError: (err) => notifyError(errorMessage(err)),
                                  },
                                )
                              }
                            >
                              {u.role === 'ADMIN' ? 'Hạ xuống USER' : 'Cấp quyền ADMIN'}
                            </Button>
                            <Button
                              size="sm"
                              variant="danger"
                              disabled={isSelf || deleteUser.isPending}
                              onClick={() => {
                                if (!window.confirm(`Xóa tài khoản ${u.email} và toàn bộ dữ liệu?`))
                                  return
                                deleteUser.mutate(u.id, {
                                  onSuccess: () => notifySaved(`Đã xóa tài khoản ${u.email}`),
                                  onError: (err) => notifyError(errorMessage(err)),
                                })
                              }}
                            >
                              <Trash2 className="size-3.5" />
                            </Button>
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </Card>
          )}
        </TabsContent>

        <TabsContent value="sessions">
          {sessions.isPending ? (
            <PageSkeleton variant="list" />
          ) : sessions.isError ? (
            <ErrorState error={sessions.error} onRetry={() => void sessions.refetch()} />
          ) : (
            <Card className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-line bg-surface-muted/60 text-xs uppercase text-ink2">
                  <tr>
                    <th className="px-4 py-3">Người dùng</th>
                    <th className="px-4 py-3">Vị trí</th>
                    <th className="px-4 py-3">Cấp độ</th>
                    <th className="px-4 py-3">Trạng thái</th>
                    <th className="px-4 py-3">Tiến độ</th>
                    <th className="px-4 py-3">Ngày tạo</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {sessions.data.map((s) => {
                    const st = STATUS_LABEL[s.status] ?? {
                      label: s.status,
                      tone: 'bg-slate-100 text-slate-700',
                    }
                    return (
                      <tr key={s.id} className="hover:bg-surface-muted/30">
                        <td className="px-4 py-3 font-medium text-ink">{s.user_email}</td>
                        <td className="px-4 py-3 text-ink">{s.role_name}</td>
                        <td className="px-4 py-3 capitalize text-ink2">{s.level}</td>
                        <td className="px-4 py-3">
                          <span
                            className={cn(
                              'inline-flex rounded-full px-2.5 py-0.5 text-xs font-medium',
                              st.tone,
                            )}
                          >
                            {st.label}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-ink2">
                          {s.asked}/{s.planned || '—'} câu
                        </td>
                        <td className="px-4 py-3 text-ink2">{fullDate(s.created_at)}</td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </Card>
          )}
        </TabsContent>
      </Tabs>
    </Page>
  )
}

function StatCard({ label, value, sub }: { label: string; value: number; sub: string }) {
  return (
    <Card className="grid gap-1 p-5">
      <span className="text-xs font-medium uppercase tracking-wide text-ink2">{label}</span>
      <span className="text-3xl font-bold text-ink">{value}</span>
      <span className="text-xs text-ink2">{sub}</span>
    </Card>
  )
}
