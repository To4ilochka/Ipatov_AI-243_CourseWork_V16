-- V1__init_schema.sql
-- Початкова реляційна схема платформи волонтерських ініціатив (Варіант 16)

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(120) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(120) NOT NULL,
    role            VARCHAR(24)  NOT NULL CHECK (role IN ('VOLUNTEER', 'COORDINATOR')),
    city            VARCHAR(80)  NOT NULL,
    phone           VARCHAR(32)  NOT NULL,
    telegram_handle VARCHAR(64),
    bio             VARCHAR(600),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE skills (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(48)  NOT NULL UNIQUE,
    title       VARCHAR(100) NOT NULL UNIQUE,
    category    VARCHAR(60)  NOT NULL
);

CREATE TABLE volunteer_skills (
    id                BIGSERIAL PRIMARY KEY,
    volunteer_id      BIGINT   NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    skill_id          BIGINT   NOT NULL REFERENCES skills(id) ON DELETE RESTRICT,
    proficiency_level SMALLINT NOT NULL CHECK (proficiency_level BETWEEN 1 AND 3),
    notes             VARCHAR(200),
    CONSTRAINT uq_volunteer_skill UNIQUE (volunteer_id, skill_id)
);

CREATE TABLE initiatives (
    id              BIGSERIAL PRIMARY KEY,
    coordinator_id  BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title           VARCHAR(160) NOT NULL,
    category        VARCHAR(60)  NOT NULL,
    location_city   VARCHAR(80)  NOT NULL,
    summary         VARCHAR(1500) NOT NULL,
    status          VARCHAR(24)  NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'ARCHIVED')),
    starts_on       DATE         NOT NULL,
    ends_on         DATE         NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_initiative_dates CHECK (ends_on >= starts_on)
);

CREATE TABLE tasks (
    id               BIGSERIAL PRIMARY KEY,
    initiative_id    BIGINT        NOT NULL REFERENCES initiatives(id) ON DELETE CASCADE,
    title            VARCHAR(160)  NOT NULL,
    description      VARCHAR(2000) NOT NULL,
    location_text    VARCHAR(200)  NOT NULL,
    work_format      VARCHAR(20)   NOT NULL CHECK (work_format IN ('ONSITE', 'REMOTE', 'HYBRID')),
    urgency          VARCHAR(20)   NOT NULL CHECK (urgency IN ('NORMAL', 'HIGH', 'CRITICAL')),
    max_volunteers   INTEGER       NOT NULL CHECK (max_volunteers BETWEEN 1 AND 500),
    approved_count   INTEGER       NOT NULL DEFAULT 0 CHECK (approved_count >= 0 AND approved_count <= max_volunteers),
    estimated_hours  NUMERIC(5,1)  NOT NULL CHECK (estimated_hours > 0 AND estimated_hours <= 200),
    deadline_at      TIMESTAMPTZ   NOT NULL,
    status           VARCHAR(24)   NOT NULL CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    version          BIGINT        NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE task_required_skills (
    id         BIGSERIAL PRIMARY KEY,
    task_id    BIGINT   NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    skill_id   BIGINT   NOT NULL REFERENCES skills(id) ON DELETE RESTRICT,
    min_level  SMALLINT NOT NULL CHECK (min_level BETWEEN 1 AND 3),
    mandatory  BOOLEAN  NOT NULL DEFAULT FALSE,
    weight     SMALLINT NOT NULL DEFAULT 2 CHECK (weight BETWEEN 1 AND 5),
    CONSTRAINT uq_task_skill UNIQUE (task_id, skill_id)
);

CREATE TABLE applications (
    id            BIGSERIAL PRIMARY KEY,
    task_id       BIGINT       NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    volunteer_id  BIGINT       NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    motivation    VARCHAR(800) NOT NULL,
    match_score   NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (match_score BETWEEN 0 AND 100),
    status        VARCHAR(24)  NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    decision_note VARCHAR(500),
    decided_by    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    decided_at    TIMESTAMPTZ,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Частковий унікальний індекс: не більше однієї активної (PENDING або APPROVED) заявки волонтера на завдання
CREATE UNIQUE INDEX ux_applications_active_volunteer_task
    ON applications (task_id, volunteer_id)
    WHERE status IN ('PENDING', 'APPROVED');

CREATE TABLE execution_reports (
    id                   BIGSERIAL PRIMARY KEY,
    application_id       BIGINT        NOT NULL UNIQUE REFERENCES applications(id) ON DELETE CASCADE,
    author_id            BIGINT        NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    summary              VARCHAR(2000) NOT NULL,
    hours_spent          NUMERIC(5,1)  NOT NULL CHECK (hours_spent > 0 AND hours_spent <= 200),
    impact_metrics       VARCHAR(400),
    status               VARCHAR(24)   NOT NULL CHECK (status IN ('SUBMITTED', 'VERIFIED', 'NEEDS_REVISION')),
    coordinator_feedback VARCHAR(500),
    submitted_at         TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    verified_at          TIMESTAMPTZ,
    version              BIGINT        NOT NULL DEFAULT 0
);

CREATE TABLE audit_events (
    id          BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   BIGINT       NOT NULL,
    actor_id    BIGINT       REFERENCES users(id) ON DELETE SET NULL,
    action      VARCHAR(60)  NOT NULL,
    details     VARCHAR(600),
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Індекси для пошуку, фільтрації та сортування у каталозі й кабінетах
CREATE INDEX idx_initiatives_status_category_city ON initiatives (status, category, location_city);
CREATE INDEX idx_initiatives_coordinator ON initiatives (coordinator_id, created_at DESC);
CREATE INDEX idx_tasks_initiative_status ON tasks (initiative_id, status);
CREATE INDEX idx_tasks_status_urgency_deadline ON tasks (status, urgency, deadline_at ASC);
CREATE INDEX idx_tasks_work_format ON tasks (work_format, status);
CREATE INDEX idx_task_req_skills_skill ON task_required_skills (skill_id, task_id);
CREATE INDEX idx_volunteer_skills_volunteer ON volunteer_skills (volunteer_id, skill_id);
CREATE INDEX idx_applications_volunteer_created ON applications (volunteer_id, created_at DESC);
CREATE INDEX idx_applications_task_status ON applications (task_id, status, created_at ASC);
CREATE INDEX idx_reports_status_submitted ON execution_reports (status, submitted_at DESC);

-- Аналітичне подання для зведеної статистики за ініціативами
CREATE VIEW v_initiative_summary AS
SELECT
    i.id AS initiative_id,
    i.title,
    i.category,
    i.location_city,
    i.status,
    COUNT(DISTINCT t.id) AS total_tasks,
    COALESCE(SUM(t.max_volunteers), 0) AS total_slots,
    COALESCE(SUM(t.approved_count), 0) AS filled_slots
FROM initiatives i
LEFT JOIN tasks t ON t.initiative_id = i.id
GROUP BY i.id, i.title, i.category, i.location_city, i.status;
