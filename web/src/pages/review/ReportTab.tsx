import { AlertTriangle, CheckCircle2, Printer, RefreshCw, Sparkles, Target } from 'lucide-react'
import { useGenerateReport, useReport } from '@/api/hooks'
import type {
  EvaluationStatus,
  QuestionEvaluation,
  ReadinessLevel,
  RoadmapPriority,
  SessionReportData,
} from '@/api/types'
import { ErrorState } from '@/components/PageStates'
import { SectionLabel } from '@/components/SectionLabel'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

const READINESS_META: Record<ReadinessLevel, { label: string; tone: string }> = {
  ready: {
    label: 'Sẵn sàng phỏng vấn',
    tone: 'border-emerald-300 bg-emerald-50 text-emerald-800',
  },
  almost_ready: {
    label: 'Gần đạt — nên ôn thêm trọng tâm',
    tone: 'border-sky-300 bg-sky-50 text-sky-800',
  },
  needs_practice: {
    label: 'Cần luyện tập thêm',
    tone: 'border-amber-300 bg-amber-50 text-amber-800',
  },
  insufficient_data: {
    label: 'Chưa đủ dữ liệu',
    tone: 'border-line bg-surface-muted text-ink2',
  },
}

const STATUS_META: Record<EvaluationStatus, { label: string; tone: string }> = {
  strong: { label: 'Tốt', tone: 'bg-emerald-100 text-emerald-800' },
  adequate: { label: 'Đạt', tone: 'bg-sky-100 text-sky-800' },
  weak: { label: 'Cần cải thiện', tone: 'bg-amber-100 text-amber-800' },
  not_assessed: { label: 'Chưa đánh giá', tone: 'bg-surface-muted text-ink2' },
}

const PRIORITY_META: Record<RoadmapPriority, { label: string; tone: string }> = {
  high: { label: 'Ưu tiên cao', tone: 'bg-rose-100 text-rose-800' },
  medium: { label: 'Trung bình', tone: 'bg-amber-100 text-amber-800' },
  low: { label: 'Nâng cao', tone: 'bg-slate-100 text-slate-700' },
}

/**
 * Post-session evaluation & coaching tab (`A7 Evaluator + A8 Coach`).
 * Shows overall readiness, per-competency weighted scores, per-question feedback,
 * sample answers, and a prioritized practice roadmap.
 */
export function ReportTab({ sessionId }: { sessionId: string }) {
  const query = useReport(sessionId)
  const generate = useGenerateReport(sessionId)

  if (query.isPending) {
    return (
      <div className="grid gap-4" aria-label="Đang tải báo cáo">
        <Skeleton className="h-36 rounded-xl" />
        <Skeleton className="h-48 rounded-xl" />
      </div>
    )
  }

  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => void query.refetch()} />
  }

  const data = query.data
  const busy = generate.isPending || data.status === 'running'

  if (data.status === 'pending') {
    return (
      <section className="rounded-xl border border-line bg-surface p-6">
        <div className="flex flex-col items-start gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="grid gap-1.5">
            <h2 className="flex items-center gap-2 text-lg font-semibold text-ink">
              <Sparkles className="size-5 text-accent" />
              Đánh giá phiên phỏng vấn bằng AI
            </h2>
            <p className="max-w-2xl text-sm text-ink2">
              Hệ thống chấm điểm từng câu trả lời (bỏ qua các câu chưa trả lời), tổng hợp mức độ
              sẵn sàng theo từng chủ đề và gợi ý lộ trình ôn tập tiếp theo.
            </p>
          </div>
          <Button
            type="button"
            disabled={busy}
            onClick={() => generate.mutate()}
            className="shrink-0 bg-accent text-white hover:bg-accent/90"
          >
            {busy ? 'Đang khởi tạo...' : 'Tạo báo cáo đánh giá'}
          </Button>
        </div>
        {generate.isError && (
          <div className="mt-4">
            <ErrorState error={generate.error} />
          </div>
        )}
      </section>
    )
  }

  if (data.status === 'running') {
    return (
      <section className="rounded-xl border border-line bg-surface p-6">
        <div className="flex items-center gap-3">
          <RefreshCw className="size-5 animate-spin text-accent" />
          <div>
            <h2 className="font-semibold text-ink">AI đang chấm điểm và tổng hợp báo cáo...</h2>
            <p className="text-sm text-ink2">
              Quá trình phân tích chi tiết từng câu trả lời thường mất khoảng 5–15 giây.
            </p>
          </div>
        </div>
      </section>
    )
  }

  if (data.status === 'failed' || !data.report) {
    return (
      <section className="rounded-xl border border-rose-200 bg-rose-50/60 p-6">
        <p className="text-sm font-medium text-rose-800">
          {data.error?.message ?? 'Chưa tạo được báo cáo đánh giá cho phiên này.'}
        </p>
        <Button
          type="button"
          variant="outline"
          disabled={busy}
          onClick={() => generate.mutate()}
          className="mt-3"
        >
          Thử tạo lại báo cáo
        </Button>
      </section>
    )
  }

  return (
    <ReportBody
      report={data.report}
      stale={data.stale}
      busy={busy}
      onRegenerate={() => generate.mutate()}
    />
  )
}

function ReportBody({
  report,
  stale,
  busy,
  onRegenerate,
}: {
  report: SessionReportData
  stale: boolean
  busy: boolean
  onRegenerate: () => void
}) {
  const readyMeta = READINESS_META[report.readiness]

  return (
    <div className="grid gap-6">
      {/* Print-only Official Header */}
      <div className="hidden print:block border-b-2 border-line pb-4 mb-2">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold uppercase tracking-wider text-ink">
              Báo cáo đánh giá năng lực phỏng vấn — Intervia AI
            </h1>
            <p className="text-xs text-ink2 mt-1">
              Hệ thống mô phỏng & đánh giá phỏng vấn tự động bằng Trí tuệ nhân tạo
            </p>
          </div>
          <div className="text-right text-xs text-ink2">
            <p>Ngày in: {new Date().toLocaleDateString('vi-VN')}</p>
          </div>
        </div>
      </div>

      {stale && (
        <div
          role="status"
          className="flex flex-col items-start justify-between gap-3 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 sm:flex-row sm:items-center print:hidden"
        >
          <div className="flex items-center gap-2">
            <AlertTriangle className="size-4 shrink-0 text-amber-600" />
            <span>
              Bạn đã chỉnh sửa câu trả lời sau khi tạo báo cáo — hãy đánh giá lại để cập nhật điểm.
            </span>
          </div>
          <Button
            type="button"
            size="sm"
            variant="outline"
            disabled={busy}
            onClick={onRegenerate}
            className="border-amber-400 bg-white text-amber-900 hover:bg-amber-100"
          >
            <RefreshCw className={cn('mr-1.5 size-3.5', busy && 'animate-spin')} />
            Đánh giá lại
          </Button>
        </div>
      )}

      {/* Overall overview card */}
      <section className="rounded-xl border border-line bg-surface p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex items-start gap-5">
            <div className="flex size-20 shrink-0 flex-col items-center justify-center rounded-xl border border-line bg-surface-muted">
              <span className="text-2xl font-bold text-ink">
                {report.overall_score !== null ? report.overall_score.toFixed(1) : '—'}
              </span>
              <span className="text-xs text-ink2">/ 10</span>
            </div>
            <div className="grid gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <span
                  className={cn(
                    'inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium',
                    readyMeta.tone,
                  )}
                >
                  {readyMeta.label}
                </span>
              </div>
              <p className="text-sm leading-relaxed text-ink">{report.summary}</p>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-2 print:hidden">
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => window.print()}
              className="shrink-0 text-ink border-line hover:bg-surface-muted"
            >
              <Printer className="mr-1.5 size-3.5" />
              In / Xuất PDF
            </Button>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={busy}
              onClick={onRegenerate}
              className="shrink-0"
            >
              <RefreshCw className={cn('mr-1.5 size-3.5', busy && 'animate-spin')} />
              Đánh giá lại
            </Button>
          </div>
        </div>

        {(report.strengths.length > 0 || report.improvements.length > 0) && (
          <div className="mt-5 grid gap-4 border-t border-line pt-4 md:grid-cols-2">
            <div>
              <SectionLabel as="h3">Điểm nổi bật</SectionLabel>
              <ul className="mt-2 grid gap-1.5 text-sm text-ink">
                {report.strengths.map((s, idx) => (
                  <li key={idx} className="flex items-start gap-2">
                    <CheckCircle2 className="mt-0.5 size-4 shrink-0 text-emerald-600" />
                    <span>{s}</span>
                  </li>
                ))}
              </ul>
            </div>
            <div>
              <SectionLabel as="h3">Điểm cần cải thiện</SectionLabel>
              <ul className="mt-2 grid gap-1.5 text-sm text-ink">
                {report.improvements.map((s, idx) => (
                  <li key={idx} className="flex items-start gap-2">
                    <Target className="mt-0.5 size-4 shrink-0 text-amber-600" />
                    <span>{s}</span>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}
      </section>

      {/* Competencies breakdown */}
      {report.competencies.length > 0 && (
        <section className="rounded-xl border border-line bg-surface p-6">
          <SectionLabel as="h2">Kết quả theo chủ đề</SectionLabel>
          <ul className="mt-3 grid gap-3">
            {report.competencies.map((c) => {
              const st = STATUS_META[c.status]
              const pct = c.score !== null ? Math.min(100, Math.round(c.score * 10)) : 0
              return (
                <li
                  key={c.competency_id}
                  className="rounded-lg border border-line bg-surface-muted/40 p-3.5"
                >
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-ink">{c.competency_name}</span>
                      <span className="text-xs text-ink2">
                        ({Math.round(c.weight * 100)}%)
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <span className={cn('rounded-full px-2 py-0.5 text-xs font-medium', st.tone)}>
                        {st.label}
                      </span>
                      <span className="min-w-12 text-right font-semibold text-ink">
                        {c.score !== null ? `${c.score.toFixed(1)}/10` : '—'}
                      </span>
                    </div>
                  </div>
                  <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-line/70">
                    <div className="h-full rounded-full bg-accent" style={{ width: `${pct}%` }} />
                  </div>
                  {c.summary && <p className="mt-2 text-xs text-ink2">{c.summary}</p>}
                </li>
              )
            })}
          </ul>
        </section>
      )}

      {/* Question-by-question evaluations */}
      <section className="rounded-xl border border-line bg-surface p-6">
        <SectionLabel as="h2">Chấm điểm từng câu hỏi</SectionLabel>
        <div className="mt-3 grid gap-4">
          {report.questions.map((q) => (
            <QuestionEvaluationCard key={q.question_id} item={q} />
          ))}
        </div>
      </section>

      {/* Coaching roadmap */}
      {report.roadmap.length > 0 && (
        <section className="rounded-xl border border-line bg-surface p-6">
          <SectionLabel as="h2">Lộ trình ôn tập tiếp theo</SectionLabel>
          <div className="mt-3 grid gap-3 md:grid-cols-2">
            {report.roadmap.map((r, idx) => {
              const p = PRIORITY_META[r.priority]
              return (
                <div key={idx} className="rounded-lg border border-line p-4">
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-medium text-ink">{r.competency_name}</span>
                    <span className={cn('rounded-full px-2 py-0.5 text-xs font-medium', p.tone)}>
                      {p.label}
                    </span>
                  </div>
                  <p className="mt-2 text-sm font-medium text-ink">{r.goal}</p>
                  <p className="mt-1 text-xs leading-relaxed text-ink2">{r.action}</p>
                  <p className="mt-2.5 rounded-md bg-surface-muted px-3 py-2 text-xs text-ink">
                    <span className="font-medium">Câu hỏi tự luyện: </span>
                    {r.practice_question}
                  </p>
                </div>
              )
            })}
          </div>
        </section>
      )}
    </div>
  )
}

function QuestionEvaluationCard({ item: q }: { item: QuestionEvaluation }) {
  const st = STATUS_META[q.status]
  return (
    <article className="rounded-lg border border-line p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="grid gap-0.5">
          <div className="flex flex-wrap items-center gap-2 text-xs text-ink2">
            <span className="font-semibold text-ink">Câu {q.question_index}</span>
            {q.competency_name && <span>· {q.competency_name}</span>}
          </div>
          <h3 className="text-sm font-medium text-ink">{q.question_text}</h3>
        </div>
        <div className="flex items-center gap-2">
          <span className={cn('rounded-full px-2.5 py-0.5 text-xs font-medium', st.tone)}>
            {st.label}
          </span>
          <span className="font-semibold text-ink">
            {q.score !== null ? `${q.score}/10` : '—'}
          </span>
        </div>
      </div>

      {q.answer_excerpt && (
        <p className="mt-2 rounded-md bg-surface-muted/70 px-3 py-2 text-xs text-ink2">
          <span className="font-medium text-ink">Câu trả lời: </span>
          {q.answer_excerpt}
        </p>
      )}

      {q.feedback && <p className="mt-2.5 text-sm text-ink">{q.feedback}</p>}

      {(q.strengths.length > 0 || q.gaps.length > 0) && (
        <div className="mt-2.5 grid gap-2 text-xs sm:grid-cols-2">
          {q.strengths.length > 0 && (
            <ul className="grid gap-1 text-emerald-800">
              {q.strengths.map((s, i) => (
                <li key={i}>+ {s}</li>
              ))}
            </ul>
          )}
          {q.gaps.length > 0 && (
            <ul className="grid gap-1 text-amber-800">
              {q.gaps.map((g, i) => (
                <li key={i}>• Cần bổ sung: {g}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      {q.sample_answer && (
        <details className="mt-3 text-xs">
          <summary className="cursor-pointer font-medium text-accent select-none">
            Xem hướng trả lời mẫu
          </summary>
          <p className="mt-1.5 rounded-md border border-line bg-surface-muted/40 p-3 leading-relaxed text-ink">
            {q.sample_answer}
          </p>
        </details>
      )}
    </article>
  )
}
