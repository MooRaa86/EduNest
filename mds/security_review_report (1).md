# تقرير مراجعة إعدادات الحماية (Spring Security)

تمت مراجعة إعدادات `authorizeHttpRequests` الحالية لمشروع **EduNest** بناءً على الـ Controllers الموجودة في النظام. الإعدادات الحالية تغطي نسبة كبيرة من مسارات النظام بشكل جيد، ولكن يوجد **بعض الثغرات والملاحظات الهامة جداً** التي يجب تعديلها لضمان عدم وجود مسارات مكشوفة أو تعمل بشكل غير متوقع.

---

> [!WARNING]
> **ملخص المشكلة الرئيسية:** الإعدادات الحالية تحتوي على مسارات مكتوبة بصيغة المتغيرات (مثل `{id}`)، بالإضافة إلى تحديد مسارات فرعية بعينها ونسيان مسارات أخرى تابعة لنفس الـ Controller، مما يجعلها متاحة لأي مستخدم مسجل دخول (الطالب يمكنه الوصول لمسارات خاصة بالمينتور والعكس).

---

## 1. مشكلة استخدام المتغيرات `{id}` في المسارات (خطأ شائع)
Spring Security في ملف الإعدادات لا يتعرف على المتغيرات مثل `{id}` أو `{mid}` بنفس الطريقة التي تفهمها دوال الـ Controllers. إذا تم كتابتها بهذا الشكل، فإنه يبحث عن مسار يحتوي حرفياً على كلمة `{id}` ولن يتعرف على الأرقام الحقيقية.
**يجب استبدال المتغيرات بعلامات البدل (Wildcards):**
- `*` (نجمة واحدة): للتعبير عن مستوى واحد من المسار (مثل ID).
- `**` (نجمتين): للتعبير عن أي عدد من المستويات أو المسارات الفرعية.

**أمثلة يجب تصحيحها:**
- ❌ `"/api/v1/week/{id}"` ➔ ✅ `"/api/v1/week/*"`
- ❌ `"/api/v1/mentorship/{mid}"` ➔ ✅ `"/api/v1/mentorship/*"`
- ❌ `"/api/v1/badges/{badgeId}"` ➔ ✅ `"/api/v1/badges/*"`

---

## 2. مسارات فرعية غير محمية (بسبب التحديد الزائد)
في قسم الـ **MENTOR**، تم تحديد بعض المسارات حرفياً وتجاهل مسارات أخرى فرعية تابعة لنفس الـ Controller. 
- **مثال:** في `WeekController` تم تأمين `"/api/v1/week/create"`، ولكن هناك مسارات أخرى في نفس الكلاس مثل `/api/v1/week/{mentorshipId}/weeks` لم تُذكر.
- **النتيجة:** هذه المسارات الفرعية لن تُعتبر تابعة لـ Mentor، بل ستقع تحت القاعدة الافتراضية `.anyRequest().authenticated()`، مما يعني أن **أي طالب أو مستخدم عادي يمكنه استدعائها**.

> [!TIP]
> **الحل الأمثل:** استخدام `/**` لتأمين الـ Controller بالكامل للمينتور بدلاً من كتابة كل مسار فرعي على حدة. 
> *مثال:* `"/api/v1/week/**"` سيغطي كل عمليات الأسابيع للمينتور بشكل آمن ومضمون.

---

## 3. مسارات عامة يجب تحديد صلاحياتها بوضوح
المسارات التالية غير مذكورة في الإعدادات تماماً، مما يعني أنها تقع تحت `.anyRequest().authenticated()` وهي **متاحة لأي مستخدم مسجل الدخول**:
1. `/api/v1/notifications/**` (إدارة الإشعارات)
2. `/api/v1/conversation/**` (المحادثات الخاصة)
3. `/settings/**` (الإعدادات)
4. `/api/v1/badges` (إذا كان هناك استعلام لجميع الشارات)
5. `/api/v1/chat-room/**` (الإنشاء والعرض، حيث أن الانضمام `/*/join` فقط مخصص للطالب)

> [!IMPORTANT]
> يرجى من التيم مراجعة هذه المسارات الـ 5. إذا كان مسموحاً لجميع المستخدمين باستخدامها فالوضع الحالي سليم، أما إذا كانت تخص دور معين (Role) فيجب إضافتها لمجموعتها.

---

## 4. طبقة الحماية المزدوجة (Defense in Depth)
يوجد استخدام ممتاز لـ `@PreAuthorize("hasRole(...)")` بداخل بعض الـ Controllers مثل `QuizSubmissionController`.
استمرار التيم في استخدام هذه الطريقة يعتبر ممارسة برمجية ممتازة (Best Practice) لأنها توفر حماية مزدوجة في حال تم نسيان مسار في ملف الـ Security.

---

## 💻 الكود المقترح للإعدادات (بعد التصحيح)

يرجى استبدال كود الـ `requestMatchers` الحالي بهذا الكود الذي يحل جميع المشاكل المذكورة أعلاه ويقوم بتأمين المسارات بشكل جذري:

```java
.authorizeHttpRequests(auth -> auth
        // ========== Public APIs ==========
        .requestMatchers(
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/swagger-resources/**",
                "/api/auth/**",
                "/api/v1/register/**",
                "/login-api",
                "/forget-password/**",
                "/api/v1/admin/register",
                "/ws/**",
                "/uploads/**",
                "/files/**",
                "/api/v1/contact/save-message",
                "/api/v1/student/mentorships/*/overview",
                "/api/v1/student/mentorships/*/reviews",
                "/api/v1/mentorship/explore",
                "/api/v1/mentorship/categories",
                "/api/v1/profile/mentor/**"
        ).permitAll()
        
        // ========== ADMIN Only ==========
        .requestMatchers(
                "/api/v1/admin/**",
                "/admin/**",
                "/api/admin/**",
                "/api/users/*/badges",
                "/api/v1/contact/all-messages",
                "/api/v1/contact/message/**",
                "/api/v1/contact/messages/**"
        ).hasRole("ADMIN")
        
        // ========== MENTOR Only ==========
        .requestMatchers(
                "/api/v1/dashboard/**",
                "/mentor/**",
                "/profile/students/**",
                "/lectures/**", // تم تبسيطها لتشمل كل ما يخص المحاضرات
                "/api/v1/week/**", // تم دمج كل مسارات الأسابيع لتجنب أي ثغرات
                "/api/v1/mentorship/**", // تم دمج كل مسارات المينتورشيب بدلاً من ذكر المتغيرات
                "/api/v1/task/**",
                "/api/v1/task-submission/*/grade",
                "/api/v1/project/**",
                "/api/v1/project/submissions/*/grade",
                "/api/v1/quiz/**",
                "/api/v1/question/**",
                "/api/v1/answer/**",
                "/api/v1/submissions/quiz/**",
                "/api/v1/badges/mentorship/**",
                "/api/v1/badges/*", // تصحيح الخطأ البرمجي في المتغير {badgeId}
                "/api/v1/badge-awards/**",
                "/api/v1/liveSession/**" // دمج كل عمليات البث للمينتور بشكل آمن
        ).hasRole("MENTOR")
        
        // ========== STUDENT Only ==========
        .requestMatchers(
                "/student/**",
                "/api/v1/my-learning",
                "/api/v1/homepage/**",
                "/api/v1/student/**",
                "/api/v1/mentorship/*/join",
                "/api/v1/mentorship/*/rate",
                "/api/v1/task-submission/**",
                "/api/v1/project/*/submissions",
                "/api/v1/submit-quiz-answer/**",
                "/api/v1/submissions/student/**",
                "/api/v1/liveSession/join/**",
                "/api/v1/liveSession/myAttendance/**",
                "/api/v1/liveSession/student/**",
                "/api/v1/chat-room/*/join"
        ).hasRole("STUDENT")
        
        // ========== Authenticated Users ==========
        .anyRequest().authenticated()
);
```
