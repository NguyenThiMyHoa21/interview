// Hand-written from docs/design/api.md until the backend exposes OpenAPI (`npm run gen:api`).
// Keep in sync with api.md §2.

export type Level = 'junior' | 'mid' | 'senior'
export type Mode = 'text' | 'voice_text' | 'voice_voice'
export type AnalysisStatus = 'pending' | 'running' | 'done' | 'failed'
export type SessionStatus = 'draft' | 'in_progress' | 'completed'
export type SessionStep = 'match' | 'plan' | 'interview' | 'review'
export type VersionOrigin = 'analysis' | 'user_edit' | 'reanalysis'

export interface ApiErrorBody {
  code: string
  message: string
  fields?: Record<string, string>
}

// ---- Auth (UI-11) ----
export type UserRole = 'USER' | 'ADMIN'

export interface User {
  id: string
  email: string
  role?: UserRole
}

export interface AdminOverview {
  total_users: number
  total_admins: number
  total_sessions: number
  completed_sessions: number
  total_cvs: number
  total_jds: number
}

export interface AdminUserRow {
  id: string
  email: string
  role: UserRole
  session_count: number
  cv_count: number
  created_at: string
}

export interface AdminSessionRow {
  id: string
  user_email: string
  role_name: string
  level: string
  status: string
  asked: number
  planned: number
  created_at: string
}

/** `GET /auth/me`. `auth_enabled: false`: the backend still uses its default user (no login). */
export interface AuthState {
  auth_enabled: boolean
  user: User | null
}

export interface Credentials {
  email: string
  password: string
}

/** `GET /account/data`: what the system keeps about the user (page 10). */
export interface AccountData {
  cv_count: number
  jd_count: number
  session_count: number
  /** Recordings of the candidate's spoken answers. */
  audio_count: number
  audio_bytes: number
}

export interface PasswordChange {
  current_password: string
  new_password: string
}

export interface ForgotPasswordResponse {
  message: string
  dev_code?: string
}

export interface ResetPasswordInput {
  email: string
  code: string
  new_password: string
}

export interface Role {
  id: string
  name: string
}

export interface CompetencyRef {
  id: string
  name: string
}

// ---- CV ----
export interface CvSummary {
  id: string
  name: string
  file_name: string
  latest_version: number | null
  analysis_status: AnalysisStatus
  session_count: number
  created_at: string
  updated_at: string
}

export interface Skill {
  id: string
  name: string
  years: number | null
  source_quote: string | null
}

export interface Experience {
  id: string
  organization: string
  role: string
  period: string
  technologies: string[]
  source_quote: string | null
}

export interface Claim {
  id: string
  statement: string
  metric: string | null
  subject: string | null
  verifiable: boolean
  source_quote: string | null
  quote_status: 'found' | 'not_found' | 'user_added'
}

export interface CandidateProfile {
  skills: Skill[]
  experiences: Experience[]
  claims: Claim[]
}

export interface VersionInfo {
  version: number
  created_at: string
  origin: VersionOrigin
}

export interface CvVersion extends VersionInfo {
  cv_id: string
  profile: CandidateProfile
}

// ---- JD ----
export interface JdSummary {
  id: string
  name: string
  company: string | null
  position: string | null
  latest_version: number | null
  analysis_status: AnalysisStatus
  requirement_count: number
  session_count: number
  created_at: string
  updated_at: string
}

export interface JdDetail extends JdSummary {
  text: string
}

export interface Requirement {
  id: string
  name: string
  kind: 'required' | 'preferred'
  importance: 1 | 2 | 3
  source_quote: string | null
}

export interface JdRequirements {
  requirements: Requirement[]
}

export interface JdVersion extends VersionInfo {
  jd_id: string
  requirements: JdRequirements
}

// ---- Analysis ----
export type StepStatus = 'done' | 'running' | 'pending' | 'failed'

export interface AnalysisProgress {
  status: AnalysisStatus
  steps: { key: string; label: string; status: StepStatus }[]
  error: ApiErrorBody | null
}

// ---- Sessions ----
export interface VersionRef {
  id: string
  name: string
  version: number | null
}

export interface SessionSummary {
  id: string
  role: Role
  level: Level
  status: SessionStatus
  step: SessionStep
  mode: Mode
  cv: VersionRef
  jd: VersionRef
  progress: { asked: number; planned: number }
  created_at: string
  started_at: string | null
  ended_at: string | null
}

export interface CreateSessionInput {
  cv_id: string
  jd_id: string
  role_id: string | null
  role_custom: string | null
  level: Level
}

export interface BlueprintCompetency {
  id: string
  name: string
  weight: number
  depth_band: [string, string]
  questions_min: number
  questions_max: number
}

export interface BlueprintCompetencyInput {
  id?: string
  name: string
  weight: number
  depth_band: [string, string]
  questions_min: number
  questions_max: number
}

export interface BlueprintView {
  status: 'pending' | 'running' | 'done' | 'failed'
  stale: boolean
  total_questions: number
  competencies: BlueprintCompetency[]
  error: ApiErrorBody | null
}

// ---- Session Evaluation Report (A7 + A8) & Stats ----
export type EvaluationStatus = 'strong' | 'adequate' | 'weak' | 'not_assessed'
export type ReadinessLevel = 'ready' | 'almost_ready' | 'needs_practice' | 'insufficient_data'
export type RoadmapPriority = 'high' | 'medium' | 'low'
export type ReportJobStatus = 'pending' | 'running' | 'done' | 'failed'

export interface QuestionEvaluation {
  question_id: string
  question_index: number
  competency_id: string | null
  competency_name: string | null
  question_text: string
  answer_excerpt: string
  status: EvaluationStatus
  score: number | null
  strengths: string[]
  gaps: string[]
  feedback: string
  sample_answer: string
}

export interface CompetencyAssessment {
  competency_id: string
  competency_name: string
  weight: number
  score: number | null
  status: EvaluationStatus
  summary: string
}

export interface RoadmapItem {
  priority: RoadmapPriority
  competency_name: string
  goal: string
  action: string
  practice_question: string
}

export interface SessionReportData {
  overall_score: number | null
  readiness: ReadinessLevel
  summary: string
  strengths: string[]
  improvements: string[]
  competencies: CompetencyAssessment[]
  questions: QuestionEvaluation[]
  roadmap: RoadmapItem[]
}

export interface SessionReportView {
  status: ReportJobStatus
  generated_at: string | null
  stale: boolean
  error: ApiErrorBody | null
  report: SessionReportData | null
}

export interface RecentReportSummary {
  session_id: string
  label: string
  score: number | null
  readiness: ReadinessLevel | null
  created_at: string
}

export interface WeakCompetencySummary {
  name: string
  average_score: number
  session_count: number
}

export interface SessionStats {
  total_sessions: number
  completed_sessions: number
  total_questions_answered: number
  average_score: number | null
  recent_reports: RecentReportSummary[]
  weak_competencies: WeakCompetencySummary[]
}

// ---- Transcript ----
export type EventType =
  'REPEAT' | 'CLARIFY' | 'HINT' | 'SKIP' | 'THINKING' | 'OFF_TOPIC' | 'MODE_CHANGE' | 'STOP'

export type QuickEventType = Extract<EventType, 'REPEAT' | 'CLARIFY' | 'HINT' | 'SKIP'>

export interface QuestionItem {
  kind: 'question'
  id: string
  index: number
  text: string
  competency: CompetencyRef | null
  depth: string | null
  unplanned: boolean
  created_at: string
}

export interface AnswerItem {
  kind: 'answer'
  id: string
  question_id: string
  text_original: string
  text_edited: string | null
  source: 'text' | 'stt'
  has_audio: boolean
  duration_ms: number | null
  created_at: string
}

export interface EventItem {
  kind: 'event'
  id: string
  question_id: string | null
  type: EventType
  hint_level: number | null
  text: string | null
  created_at: string
}

export type TranscriptItem = QuestionItem | AnswerItem | EventItem

export interface SessionEndSummary {
  question_count: number
  duration_seconds: number
  assessed: CompetencyRef[]
  not_assessed: CompetencyRef[]
}

export interface Transcript {
  session: SessionSummary
  items: TranscriptItem[]
  summary: SessionEndSummary | null
}

export interface Coverage {
  competency: CompetencyRef
  question_count: number
  planned: number
}

// ---- SSE stream events (api.md §3 "Sự kiện SSE") ----
export type StreamEvent =
  | {
      event: 'message_start'
      data: { item_id: string; kind: 'question' | 'event'; event_type?: EventType }
    }
  | { event: 'token'; data: { text: string } }
  | { event: 'message_done'; data: { item: TranscriptItem } }
  | { event: 'user_item'; data: { item: TranscriptItem } }
  | { event: 'progress'; data: { asked: number; planned: number } }
  | { event: 'session_ended'; data: SessionEndSummary }
  | { event: 'error'; data: ApiErrorBody }

// ---- Limits (api.md §1, proposed) ----
export const LIMITS = {
  cvMaxBytes: 5 * 1024 * 1024,
  jdMaxChars: 20_000,
  answerMaxChars: 8_000,
  nameMaxChars: 120,
  passwordMinChars: 6,
} as const
