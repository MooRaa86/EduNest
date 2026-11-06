-- =====================================================================
-- Migration V6: Global Public UUID Identifiers (EB-109)
-- Purpose: Add opaque cryptographically random UUID public_id to all
--          BaseEntity tables for IDOR protection and ID enumeration defense.
-- Note: V5 is reserved for EB-91 (Refresh Token Sessions).
-- =====================================================================

-- Ensure pgcrypto extension is available for gen_random_uuid() fallback on older Postgres
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. Core Users and Identity (mentors and students share users.public_id via joined inheritance)
ALTER TABLE users ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_users_public_id ON users(public_id);

ALTER TABLE admin ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_admin_public_id ON admin(public_id);

-- 2. Mentorships, Reviews, What Will Learn, Tags
ALTER TABLE mentorship ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_mentorship_public_id ON mentorship(public_id);

ALTER TABLE mentorship_reviews ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_mentorship_reviews_public_id ON mentorship_reviews(public_id);

ALTER TABLE mentorship_what_will_learn ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_mentorship_what_will_learn_public_id ON mentorship_what_will_learn(public_id);

ALTER TABLE tags ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_tags_public_id ON tags(public_id);

-- 3. Enrollments & Financials
ALTER TABLE enrollments ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_enrollments_public_id ON enrollments(public_id);

ALTER TABLE commission ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_commission_public_id ON commission(public_id);

-- 4. Curriculum: Weeks, Lectures, Sessions & Attendance
ALTER TABLE weeks ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_weeks_public_id ON weeks(public_id);

ALTER TABLE lectures ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_lectures_public_id ON lectures(public_id);

ALTER TABLE sessions ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_sessions_public_id ON sessions(public_id);

ALTER TABLE session_attendance ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_session_attendance_public_id ON session_attendance(public_id);

ALTER TABLE session_attendance_result ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_session_attendance_result_public_id ON session_attendance_result(public_id);

-- 5. Assessments: Quizzes, Questions, Student Answers, Submissions
ALTER TABLE quiz ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_quiz_public_id ON quiz(public_id);

ALTER TABLE questions ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_questions_public_id ON questions(public_id);

ALTER TABLE student_answer ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_student_answer_public_id ON student_answer(public_id);

ALTER TABLE quiz_submission ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_quiz_submission_public_id ON quiz_submission(public_id);

-- 6. Practical Work: Tasks, Projects & Submissions
ALTER TABLE tasks ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_tasks_public_id ON tasks(public_id);

ALTER TABLE task_submission ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_task_submission_public_id ON task_submission(public_id);

ALTER TABLE projects ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_projects_public_id ON projects(public_id);

ALTER TABLE project_submission ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_project_submission_public_id ON project_submission(public_id);

-- 7. Gamification: Badges, Awards, Certificates & Points
ALTER TABLE badges ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_badges_public_id ON badges(public_id);

ALTER TABLE badge_awards ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_badge_awards_public_id ON badge_awards(public_id);

ALTER TABLE admin_badges ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_admin_badges_public_id ON admin_badges(public_id);

ALTER TABLE user_admin_badges ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_user_admin_badges_public_id ON user_admin_badges(public_id);

ALTER TABLE certificates ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_certificates_public_id ON certificates(public_id);

ALTER TABLE total_points ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_total_points_public_id ON total_points(public_id);

-- 8. Communication: Chat, Conversations & Contact
ALTER TABLE chat_rooms ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_chat_rooms_public_id ON chat_rooms(public_id);

ALTER TABLE chat_messages ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_chat_messages_public_id ON chat_messages(public_id);

ALTER TABLE conversations ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_conversations_public_id ON conversations(public_id);

ALTER TABLE contact_message ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_contact_message_public_id ON contact_message(public_id);

-- 9. Notifications, Social Media, OTP & Security
ALTER TABLE notification ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_notification_public_id ON notification(public_id);

ALTER TABLE user_notification ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_user_notification_public_id ON user_notification(public_id);

ALTER TABLE admin_notification ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_admin_notification_public_id ON admin_notification(public_id);

ALTER TABLE social_media ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_social_media_public_id ON social_media(public_id);

ALTER TABLE otp ADD COLUMN public_id UUID DEFAULT gen_random_uuid() NOT NULL;
CREATE UNIQUE INDEX idx_otp_public_id ON otp(public_id);
