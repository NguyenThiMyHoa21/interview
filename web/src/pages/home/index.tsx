import { ArrowRight, BarChart3, Plus, TrendingUp } from 'lucide-react'
import { Link, useNavigate } from 'react-router'
import { useCreateSession, useRoles, useSessions, useSessionStats } from '@/api/hooks'
import type { SessionStats, SessionSummary } from '@/api/types'
import { Card } from '@/components/Card'
import { Page, PageTitle } from '@/components/Page'
import { PageSkeleton, QueryView } from '@/components/PageStates'
import { SectionLabel } from '@/components/SectionLabel'
import { StatusDot } from '@/components/StatusDot'
import { Button } from '@/components/ui/button'
import { sessionLabel, sessionPath } from '@/features/sessions/session-nav'
import { errorMessage } from '@/lib/errors'
import { LEVEL_LABEL, progressLabel, relativeDay } from '@/lib/format'
import { notifyError } from '@/lib/notify'

// [1] Page 1 (docs/design/ui-pages.md §3). The full session list lives in the sidebar.
export function HomePage() {
  const sessions = useSessions()
  return (
    <Page>
      <PageTitle>Chào bạn, hôm nay luyện phỏng vấn gì?</PageTitle>
      <div className="mt-6">
        <QueryView query={sessions} loading={<PageSkeleton rows={2} />}>
          {(data) => {
            const [first, ...rest] = data
            return first ? <Overview sessions={[first, ...rest]} /> : <FirstRun />
          }}
        </QueryView>
      </div>
    </Page>
  )
}

/** `sessions` is non-empty and newest first (as the API lists them). */
function Overview({ sessions }: { sessions: [SessionSummary, ...SessionSummary[]] }) {
  const stats = useSessionStats()
  const active = sessions.find((s) => s.status === 'in_progress')
  const [latest] = sessions
  return (
    <div className="grid gap-10">
      <div>
        <Button asChild>
          <Link to="/sessions/new">
            <Plus /> Phiên mới
          </Link>
        </Button>
      </div>
      {stats.data && <DashboardStats stats={stats.data} />}
      {active && (
        <section className="grid gap-3">
          <SectionLabel>Tiếp tục</SectionLabel>
          <Card className="flex flex-wrap items-center justify-between gap-3">
            <div className="flex min-w-0 items-center gap-3">
              <StatusDot status={active.status} />
              <p className="min-w-0">
                <span className="font-medium text-ink">{sessionLabel(active)}</span>
                <span className="text-ink2">
                  {' · '}
                  {progressLabel(active.progress)} ·{' '}
                  {relativeDay(active.started_at ?? active.created_at)}
                </span>
              </p>
            </div>
            <Button asChild variant="secondary">
              <Link to={sessionPath(active)}>
                Tiếp tục <ArrowRight />
              </Link>
            </Button>
          </Card>
        </section>
      )}
      <QuickRepeat session={latest} />
    </div>
  )
}

function DashboardStats({ stats }: { stats: SessionStats }) {
  return (
    <section aria-label="Tiến độ luyện tập" className="grid gap-4">
      <SectionLabel className="flex items-center gap-1.5">
        <BarChart3 className="size-4 text-accent" /> Tiến độ luyện tập
      </SectionLabel>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <StatTile label="Tổng số phiên" value={String(stats.total_sessions)} />
        <StatTile label="Đã hoàn thành" value={String(stats.completed_sessions)} />
        <StatTile label="Câu đã trả lời" value={String(stats.total_questions_answered)} />
        <StatTile
          label="Điểm trung bình"
          value={stats.average_score !== null ? `${stats.average_score.toFixed(1)}/10` : '—'}
        />
      </div>

      {stats.weak_competencies.length > 0 && (
        <Card className="grid gap-2 p-4">
          <p className="flex items-center gap-1.5 text-xs font-medium text-ink2">
            <TrendingUp className="size-3.5 text-amber-600" />
            Chủ đề nên ưu tiên ôn tập
          </p>
          <div className="flex flex-wrap gap-2">
            {stats.weak_competencies.map((w) => (
              <span
                key={w.name}
                className="rounded-full border border-amber-300 bg-amber-50 px-2.5 py-0.5 text-xs font-medium text-amber-900"
              >
                {w.name} · {w.average_score.toFixed(1)}/10
              </span>
            ))}
          </div>
        </Card>
      )}

      {stats.recent_reports.length > 0 && (
        <Card className="grid gap-2 p-4">
          <p className="text-xs font-medium text-ink2">Báo cáo đánh giá gần đây</p>
          <ul className="grid gap-1.5">
            {stats.recent_reports.map((r) => (
              <li key={r.session_id} className="flex items-center justify-between gap-2 text-sm">
                <Link
                  to={`/sessions/${r.session_id}/review`}
                  className="truncate text-ink hover:text-accent hover:underline"
                >
                  {r.label}
                </Link>
                <span className="shrink-0 font-medium text-accent tabular-nums">
                  {r.score !== null ? `${r.score.toFixed(1)}/10` : 'Chưa chấm'}
                </span>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </section>
  )
}

function StatTile({ label, value }: { label: string; value: string }) {
  return (
    <Card className="grid gap-1 p-3.5">
      <span className="text-xs text-ink2">{label}</span>
      <span className="text-xl font-semibold text-ink tabular-nums">{value}</span>
    </Card>
  )
}

/**
 * Same CV + JD + role + level as the latest session, as a new session. Skips page 2 but still
 * opens page 3: the CV or JD may have a newer version since (POST uses the latest version).
 */
function QuickRepeat({ session }: { session: SessionSummary }) {
  const navigate = useNavigate()
  const roles = useRoles()
  const createSession = useCreateSession()

  async function start() {
    // A role outside the catalog was typed in by the user ("Khác").
    const known = roles.data?.some((r) => r.id === session.role.id)
    try {
      const created = await createSession.mutateAsync({
        cv_id: session.cv.id,
        jd_id: session.jd.id,
        role_id: known ? session.role.id : null,
        role_custom: known ? null : session.role.name,
        level: session.level,
      })
      navigate(`/sessions/${created.id}/match`)
    } catch (err) {
      notifyError(errorMessage(err))
    }
  }

  return (
    <section className="grid gap-3">
      <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <SectionLabel>Luyện lại nhanh</SectionLabel>
        <p className="text-sm text-ink2">Dùng CV và JD có sẵn</p>
      </div>
      <Card className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="font-medium break-words text-ink">
            {session.cv.name} + {session.jd.name} · {LEVEL_LABEL[session.level]}
          </p>
          <p className="text-sm text-ink2">{session.role.name}</p>
        </div>
        <Button
          variant="secondary"
          onClick={start}
          disabled={!roles.data || createSession.isPending}
        >
          {createSession.isPending ? 'Đang tạo…' : 'Phiên mới'} <ArrowRight />
        </Button>
      </Card>
    </section>
  )
}

const FIRST_RUN_STEPS = [
  { title: 'Thêm CV và JD', text: 'Tải CV (PDF/DOCX) và dán mô tả công việc bạn muốn ứng tuyển.' },
  { title: 'Kiểm tra hồ sơ', text: 'Xem và sửa những gì AI đọc được từ CV và JD.' },
  { title: 'Phỏng vấn', text: 'Trả lời bằng chữ hoặc giọng nói, không tính giờ.' },
]

function FirstRun() {
  return (
    <section className="grid gap-6">
      <ol className="grid gap-3 sm:grid-cols-3">
        {FIRST_RUN_STEPS.map((step, i) => (
          <li key={step.title}>
            <Card className="h-full">
              <p className="label-caps text-ink2">Bước {i + 1}</p>
              <p className="mt-2 font-semibold text-ink">{step.title}</p>
              <p className="mt-1 text-sm text-ink2">{step.text}</p>
            </Card>
          </li>
        ))}
      </ol>
      <div>
        <Button asChild>
          <Link to="/sessions/new">
            Bắt đầu phiên đầu tiên <ArrowRight />
          </Link>
        </Button>
      </div>
    </section>
  )
}
