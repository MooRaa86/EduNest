# 🗂️ EduNest Database Indexing Recommendations

بناءً على بنية المشروع والجداول (Entities) الموجودة، دي قائمة بأهم الأعمدة (Columns) اللي بتحتاج يتعملها Indexing عشان تسرع الـ Queries وتحسن أداء الـ Database بشكل عام.

## 📝 القواعد العامة للـ Indexing
قبل ما نبدأ في التفاصيل، دي حالات عامة دايماً بتحتاج Index:
1. **Foreign Keys (FKs):** أي عمود بيربط جدول بالتاني (زي `mentor_id` في جدول الكورسات).
2. **Where Clauses:** الأعمدة اللي بنستخدمها كتير في البحث أو الفلترة (زي `email` أو `status`).
3. **Sorting (Order By):** الأعمدة اللي بنرتب بيها البيانات (زي `created_at` أو `joined_at`).
4. **Unique Constraints:** زي الإيميل أو رقم الموبايل (بيتعملهم Unique Index تلقائياً في الغالب).

---

## 1. 👥 جدول المستخدمين (`users` / `UserEntity`)
- **`email`**: لازم يكون **Unique Index** لأنه بيُستخدم في الـ Login بشكل أساسي.
- **`phone_number`**: لو بيُستخدم في البحث أو كبديل للـ Login (يفضل Unique Index).
- **`role`**: لو بتعمل استعلامات كتير عشان تجيب كل الـ Mentors أو الـ Students.
- **`enabled` / `deleted`**: لو بتفلتر دايماً عشان تجيب المستخدمين النشطين فقط (ممكن تعمل Partial Index لو الداتابيز بتدعم ده).

## 2. 📚 جدول الكورسات أو المنح (`mentorship` / `MentorShip`)
- **`mentor_id`**: (Foreign Key) ضروري عشان تجيب كل الكورسات الخاصة بـ Mentor معين.
- **`category`**: مفيد جداً في الفلترة (مثلاً: إيجاد كل كورسات الـ `backEnd`).
- **`status`**: للفلترة (مثلاً: إظهار الكورسات الـ `PUBLISHED` فقط للمستخدمين).
- **`difficulty_level`**: لو الطالب بيقدر يفلتر الكورسات حسب الصعوبة.
- **`rating` & `price`**: لو فيه ترتيب (Sorting) للكورسات من الأعلى تقييماً أو الأقل سعراً.
- **`title`**: لو فيه شريط بحث باسم الكورس (يفضل **Full-Text Index** أو B-Tree حسب الاحتياج).

## 3. 🎓 جدول الاشتراكات (`enrollments` / `Enrollment`)
- **`student_id`**: عشان تجيب كل الكورسات اللي الطالب مشترك فيها.
- **`mentorship_id`**: عشان تجيب كل الطلاب المشتركين في كورس معين.
- **Composite Index على `(student_id, mentorship_id)`**: عشان تتأكد بسرعة إن الطالب ده مشترك في الكورس ده (ولمنع التكرار لو مسموح باشتراك واحد).
- **`joined_at`**: لو بتجيب أحدث الاشتراكات أو بتعمل إحصائيات.

## 4. 📅 جدول الأسابيع والمحتوى (`weeks`, `lectures`, `tasks`, `projects`, `quiz`)
- **`mentorship_id`** في جدول الـ `weeks`: لسرعة جلب محتوى الكورس بالكامل.
- **`week_id`** (أو ما يعادله) في الجداول التانية (`lectures`, `tasks`...): لربط المحتوى بالأسبوع.
- **`order`** أو **`start_date`**: لو المحتوى بيترتب بيهم.

## 5. ✍️ جداول التسليمات (`task_submission`, `project_submission`)
- **`student_id`**: لعرض تسليمات طالب معين.
- **`task_id` / `project_id`**: عشان الـ Mentor يشوف كل تسليمات الطلبة لتاسك معين.
- **`status`**: لو الـ Mentor بيفلتر التسليمات اللي لسه متراجعتش (مثلاً `PENDING`).
- **Composite Index على `(task_id, student_id)`**: لسرعة جلب تسليم طالب لتاسك معين.

## 6. ⭐ التقييمات والمراجعات (`mentorship_reviews` / `MentorShipReviews`)
- **`mentorship_id`**: لجلب تقييمات الكورس لعرضها.
- **`student_id`**: لمنع الطالب من التقييم أكتر من مرة (أو للبحث عن تقييماته).
- **`rating`**: لو عاوز تفلتر التقييمات الإيجابية أو السلبية.

## 7. 🔔 الإشعارات (`notifications`, `user_notification`)
- **`user_id`**: ضروري جداً لسرعة جلب إشعارات مستخدم معين.
- **`is_read`**: للفلترة عشان تجيب الإشعارات اللي لسه متقرتش.
- **`created_at`**: لترتيب الإشعارات من الأحدث للأقدم.

---

### 💡 إزاي تضيف الـ Indexes في Spring Boot / JPA؟
ممكن تضيف الـ Index مباشرة في الـ Entity عن طريق أنوتيشن `@Table`:

```java
@Entity
@Table(name = "mentorship", indexes = {
    @Index(name = "idx_mentorship_category", columnList = "category"),
    @Index(name = "idx_mentorship_status", columnList = "status"),
    @Index(name = "idx_mentorship_mentor_id", columnList = "mentor_id")
})
public class MentorShip extends BaseEntity {
    // ...
}
```

**نصيحة أخيرة:** متعملش Index لكل حاجة! الـ Index بيسرع عملية الـ `SELECT` (القراءة) لكنه بيبطئ عملية الـ `INSERT` و `UPDATE` و `DELETE` (الكتابة)، فاعمل Index للأعمدة اللي عليها عمليات بحث وفلترة كتير بس.
