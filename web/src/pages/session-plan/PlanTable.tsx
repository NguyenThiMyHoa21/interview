import { Info, Pencil, Plus, X } from 'lucide-react'
import { useId, useState } from 'react'
import { useUpdateBlueprint } from '@/api/hooks'
import type { BlueprintCompetency, BlueprintView } from '@/api/types'
import { Card } from '@/components/Card'
import { SectionLabel } from '@/components/SectionLabel'
import { WeightBar } from '@/components/WeightBar'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { errorMessage } from '@/lib/errors'

const LEVELS = ['L1', 'L2', 'L3', 'L4', 'L5', 'L6', 'L7'] as const
const range = (min: number, max: number) => (min === max ? String(min) : `${min}–${max}`)
const depth = ([from, to]: BlueprintCompetency['depth_band']) =>
  from === to ? from : `${from}–${to}`

interface DraftRow {
  id?: string
  name: string
  weightPct: number
  from: string
  to: string
  qMin: number
  qMax: number
}

const toDraft = (comps: BlueprintCompetency[]): DraftRow[] =>
  comps.map((c) => ({
    id: c.id,
    name: c.name,
    weightPct: Math.max(5, Math.round(c.weight * 100)),
    from: c.depth_band[0],
    to: c.depth_band[1],
    qMin: c.questions_min,
    qMax: c.questions_max,
  }))

/**
 * The blueprint as the candidate sees it: topic, weight, depth band, question count.
 * When `sessionId` is provided (draft sessions), allows customizing topics/weights/quotas.
 */
export function PlanTable({
  blueprint,
  sessionId,
}: {
  blueprint: BlueprintView
  sessionId?: string
}) {
  const titleId = useId()
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<DraftRow[]>([])
  const [validationError, setValidationError] = useState<string | null>(null)
  const update = useUpdateBlueprint(sessionId ?? '')

  const enterEdit = () => {
    setDraft(toDraft(blueprint.competencies))
    setValidationError(null)
    update.reset()
    setEditing(true)
  }

  const updateRow = (idx: number, patch: Partial<DraftRow>) =>
    setDraft((rows) => rows.map((r, i) => (i === idx ? { ...r, ...patch } : r)))

  const saveEdit = async () => {
    for (const r of draft) {
      if (!r.name.trim()) {
        setValidationError('Tên chủ đề không được để trống.')
        return
      }
      if (LEVELS.indexOf(r.from as (typeof LEVELS)[number]) > LEVELS.indexOf(r.to as (typeof LEVELS)[number])) {
        setValidationError(`Độ sâu của "${r.name}" không hợp lệ (từ <= đến).`)
        return
      }
      if (r.qMin < 1 || r.qMax < r.qMin) {
        setValidationError(`Số câu tối đa của "${r.name}" phải >= tối thiểu.`)
        return
      }
    }
    setValidationError(null)
    try {
      await update.mutateAsync(
        draft.map((r) => ({
          ...(r.id ? { id: r.id } : {}),
          name: r.name.trim(),
          weight: Math.max(1, r.weightPct) / 100,
          depth_band: [r.from, r.to],
          questions_min: r.qMin,
          questions_max: r.qMax,
        })),
      )
      setEditing(false)
    } catch {
      // Displayed via update.error below
    }
  }

  return (
    <Card className="grid gap-4 p-5">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <SectionLabel id={titleId}>
          Kế hoạch phỏng vấn · khoảng {blueprint.total_questions} câu
        </SectionLabel>
        {sessionId && !editing && (
          <Button type="button" size="sm" variant="outline" onClick={enterEdit}>
            <Pencil className="mr-1.5 size-3.5" /> Tùy chỉnh
          </Button>
        )}
      </div>

      {editing ? (
        <div className="grid gap-3">
          {draft.map((row, idx) => (
            <div
              key={row.id ?? `new-${idx}`}
              className="grid gap-2 rounded-lg border border-line bg-surface-muted/40 p-3 sm:grid-cols-[minmax(0,1fr)_88px_136px_118px_auto] sm:items-end"
            >
              <label className="grid gap-1 text-xs text-ink2">
                Chủ đề
                <Input
                  value={row.name}
                  onChange={(e) => updateRow(idx, { name: e.target.value })}
                  aria-label={`Tên chủ đề ${idx + 1}`}
                  className="h-8 bg-surface text-sm text-ink"
                />
              </label>
              <label className="grid gap-1 text-xs text-ink2">
                Trọng số (%)
                <Input
                  type="number"
                  min={5}
                  max={100}
                  value={row.weightPct}
                  onChange={(e) => updateRow(idx, { weightPct: Number(e.target.value) || 5 })}
                  aria-label={`Trọng số ${row.name}`}
                  className="h-8 bg-surface text-sm text-ink tabular-nums"
                />
              </label>
              <div className="grid gap-1 text-xs text-ink2">
                <span>Độ sâu</span>
                <div className="flex items-center gap-1">
                  <select
                    aria-label={`Độ sâu từ của ${row.name}`}
                    value={row.from}
                    onChange={(e) => updateRow(idx, { from: e.target.value })}
                    className="h-8 rounded-md border border-line bg-surface px-1.5 text-sm text-ink"
                  >
                    {LEVELS.map((l) => (
                      <option key={l} value={l}>
                        {l}
                      </option>
                    ))}
                  </select>
                  <span className="text-ink2">–</span>
                  <select
                    aria-label={`Độ sâu đến của ${row.name}`}
                    value={row.to}
                    onChange={(e) => updateRow(idx, { to: e.target.value })}
                    className="h-8 rounded-md border border-line bg-surface px-1.5 text-sm text-ink"
                  >
                    {LEVELS.map((l) => (
                      <option key={l} value={l}>
                        {l}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
              <div className="grid gap-1 text-xs text-ink2">
                <span>Số câu (min–max)</span>
                <div className="flex items-center gap-1">
                  <Input
                    type="number"
                    min={1}
                    max={10}
                    value={row.qMin}
                    onChange={(e) => {
                      const val = Math.max(1, Number(e.target.value) || 1)
                      updateRow(idx, { qMin: val, qMax: Math.max(val, row.qMax) })
                    }}
                    aria-label={`Số câu tối thiểu ${row.name}`}
                    className="h-8 w-12 bg-surface px-1.5 text-center text-sm text-ink tabular-nums"
                  />
                  <span>–</span>
                  <Input
                    type="number"
                    min={1}
                    max={15}
                    value={row.qMax}
                    onChange={(e) =>
                      updateRow(idx, { qMax: Math.max(row.qMin, Number(e.target.value) || row.qMin) })
                    }
                    aria-label={`Số câu tối đa ${row.name}`}
                    className="h-8 w-12 bg-surface px-1.5 text-center text-sm text-ink tabular-nums"
                  />
                </div>
              </div>
              <Button
                type="button"
                size="icon-sm"
                variant="ghost"
                disabled={draft.length <= 1}
                aria-label={`Xóa chủ đề ${row.name}`}
                onClick={() => setDraft((rows) => rows.filter((_, i) => i !== idx))}
              >
                <X className="size-4" />
              </Button>
            </div>
          ))}

          {(validationError || update.isError) && (
            <p role="alert" className="text-sm text-rose-700">
              {validationError ?? errorMessage(update.error)}
            </p>
          )}

          <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
            <Button
              type="button"
              size="sm"
              variant="outline"
              disabled={draft.length >= 10}
              onClick={() =>
                setDraft((rows) => [
                  ...rows,
                  { name: 'Chủ đề mới', weightPct: 20, from: 'L2', to: 'L3', qMin: 2, qMax: 3 },
                ])
              }
            >
              <Plus className="mr-1 size-4" /> Thêm chủ đề
            </Button>
            <div className="flex items-center gap-2">
              <Button
                type="button"
                size="sm"
                variant="ghost"
                disabled={update.isPending}
                onClick={() => setEditing(false)}
              >
                Hủy
              </Button>
              <Button type="button" size="sm" loading={update.isPending} onClick={() => void saveEdit()}>
                Lưu kế hoạch
              </Button>
            </div>
          </div>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table aria-labelledby={titleId} className="w-full text-left text-sm">
            <thead className="text-xs whitespace-nowrap text-ink2">
              <tr>
                <th scope="col" className="py-2 pr-4 font-medium">
                  Chủ đề
                </th>
                <th scope="col" className="py-2 pr-4 font-medium">
                  Trọng số
                </th>
                <th scope="col" className="py-2 pr-4 font-medium">
                  Độ sâu
                </th>
                <th scope="col" className="py-2 font-medium">
                  Số câu
                </th>
              </tr>
            </thead>
            <tbody>
              {blueprint.competencies.map((c) => (
                <tr key={c.id} className="border-t border-line">
                  <th scope="row" className="py-2.5 pr-4 font-medium text-ink">
                    {c.name}
                  </th>
                  <td className="py-2.5 pr-4">
                    <WeightBar
                      value={c.weight}
                      name={`Trọng số ${c.name}`}
                      // Narrower bar on phones so the four columns fit without scrolling.
                      className="[&_[role=meter]]:w-12 sm:[&_[role=meter]]:w-24"
                    />
                  </td>
                  <td className="py-2.5 pr-4 whitespace-nowrap text-ink tabular-nums">
                    {depth(c.depth_band)}
                  </td>
                  <td className="py-2.5 whitespace-nowrap text-ink tabular-nums">
                    {range(c.questions_min, c.questions_max)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <DepthHelp />
    </Card>
  )
}

const DEPTHS = [
  ['L1', 'Nhớ khái niệm'],
  ['L2', 'Hiểu vì sao'],
  ['L3', 'Áp dụng vào bài toán cụ thể'],
  ['L4', 'Tìm và sửa lỗi'],
  ['L5', 'Cân nhắc đánh đổi giữa các cách làm'],
  ['L6', 'Thiết kế cả hệ thống'],
  ['L7', 'Dẫn dắt, chịu trách nhiệm kết quả'],
] as const

/** "ⓘ Độ sâu L1–L7 là gì?": a short explanation, folded by default. */
function DepthHelp() {
  const [open, setOpen] = useState(false)
  const panelId = useId()
  return (
    <div className="grid gap-2">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((o) => !o)}
        className="flex w-fit items-center gap-1.5 rounded-md text-sm text-accent hover:underline focus-visible:ring-2 focus-visible:ring-accent focus-visible:outline-none"
      >
        <Info className="size-4" aria-hidden /> Độ sâu L1–L7 là gì?
      </button>
      {open && (
        <div id={panelId} className="grid gap-2 rounded-lg bg-subtle p-4 text-sm text-ink">
          <p>
            Độ sâu cho biết câu hỏi đi xa tới đâu, từ nhớ khái niệm tới tự thiết kế và chịu trách
            nhiệm. Cấp độ càng cao thì người phỏng vấn hỏi càng sâu. Trong lúc phỏng vấn, độ sâu có
            thể tăng hoặc giảm một bậc tùy câu trả lời.
          </p>
          <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
            {DEPTHS.map(([level, text]) => (
              <div key={level} className="contents">
                <dt className="font-medium tabular-nums">{level}</dt>
                <dd className="text-ink2">{text}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </div>
  )
}
