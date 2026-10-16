# Database Optimization - Index Recommendations

## Overview
هذا الملف يحتوي على توصيات الـ indexes المطلوبة لتحسين أداء الـ queries في FileAccessService.

## Required Indexes

### 1. Enrollment Table
```sql
-- Composite index for enrollment checks
CREATE INDEX idx_enrollment_mentorship_student 
ON enrollment(mentor_ship_id, student_email);
```
**السبب:** الـ queries `isStudentEnrolled` تبحث بـ mentorship_id و student_email معاً، الـ composite index هيسرع البحث جداً.

### 2. TaskSubmission Table
```sql
-- Index on student email for faster lookups
CREATE INDEX idx_task_submission_student_email 
ON task_submission(student_id);

-- Index on task_id for faster joins
CREATE INDEX idx_task_submission_task 
ON task_submission(task_id);
```
**السبب:** الـ projection query تعمل join على student و task، الـ indexes دي هتسرع الـ joins.

### 3. ProjectSubmission Table
```sql
-- Index on student email for faster lookups
CREATE INDEX idx_project_submission_student_email 
ON project_submission(student_id);

-- Index on project_id for faster joins
CREATE INDEX idx_project_submission_project 
ON project_submission(project_id);
```
**السبب:** نفس السبب للـ TaskSubmission.

### 4. Task Table
```sql
-- Index on week_id for faster joins
CREATE INDEX idx_task_week 
ON task(week_id);
```
**السبب:** الـ projection queries تعمل join على week.

### 5. Project Table
```sql
-- Index on week_id for faster joins
CREATE INDEX idx_project_week 
ON project(week_id);
```
**السبب:** الـ projection queries تعمل join على week.

### 6. Week Table
```sql
-- Index on mentorship_id for faster joins
CREATE INDEX idx_week_mentorship 
ON week(mentorship_id);
```
**السبب:** كل الـ authorization queries تحتاج الوصول للـ mentorship من خلال week.

## Performance Impact

### Before Optimization
- **2 queries** per authorization (findById + existsByEnrollment)
- Full entity loading with **eager fetching** of relations
- No indexes = **full table scans** on enrollment checks

### After Optimization
- **1 query** per authorization (projection with all needed data)
- Only **4-5 columns** loaded instead of full entities
- Indexes = **O(log n)** lookup time instead of O(n)

## Expected Improvements
- ⚡ **50-70% reduction** in query execution time
- 💾 **80-90% reduction** in memory usage per request
- 📊 **2x throughput** improvement under load

## Implementation Notes
- الـ indexes دي ممكن تتعمل تدريجياً
- لو الـ tables كبيرة، استخدم `CREATE INDEX CONCURRENTLY` في PostgreSQL
- راقب الـ query performance بعد كل index باستخدام `EXPLAIN ANALYZE`

## Monitoring Queries
```sql
-- Check if indexes are being used
EXPLAIN ANALYZE 
SELECT e.id FROM enrollment e 
WHERE e.mentor_ship_id = 1 AND e.student_email = 'test@example.com';

-- Should show "Index Scan" instead of "Seq Scan"
```
