# IDOR Vulnerability Audit Report

## Methods Already Protected

The following service layer methods and endpoints are already implementing appropriate IDOR checks by validating that the requesting user owns or has permission to access the specified resource:

| Service | Method | Verification Approach |
| --- | --- | --- |
| `TaskServiceImpl` | `createTask` | Validates mentorship ownership before adding task. |
| `TaskServiceImpl` | `updateTask`, `deleteTask`, `updateTaskStatus`, `getTaskStatistics` | Checks ownership via internal `validateMentorOwnershipAndGetTask`. |
| `TaskServiceImpl` | `getTaskDashboard`, `getFullTaskDashboard` | Uses internal `validateMentorshipOwnership`. |
| `TaskSubmissionServiceImpl` | `submit` | Checks student enrollment via `enrollmentRepository.isStudentEnrolledForTask`. |
| `TaskSubmissionServiceImpl` | `listByTask` | Validates mentor owns task via `validateMentorOwnsTask`. |
| `TaskSubmissionServiceImpl` | `grade` | Validates mentor owns submission via `validateMentorOwnsSubmission`. |
| `ProjectServiceImpl` | `createProject`, `getProjectDashboard`, `getFullProjectDashboard` | Uses internal `validateMentorshipOwnership`. |
| `ProjectServiceImpl` | `updateProject`, `deleteProject`, `updateProjectStatus`, `getProjectStatistics` | Checks ownership via internal `validateMentorOwnershipAndGetProject`. |
| `ProjectSubmissionServiceImpl` | `submit` | Checks student enrollment via internal `validateEnrolled`. |
| `ProjectSubmissionServiceImpl` | `listByProject` | Custom check using `mentorRepository` & `projectRepository` logic. |
| `ProjectSubmissionServiceImpl` | `gradeProject` | Validates mentor owns submission via internal `validateMentorOwnsSubmission`. |
| `WeekService` | `createWeek`, `getWeeksByMentorship` | Validates mentor ownership via internal `validateMentorOwnership`. |
| `WeekService` | `deleteWeek`, `updateWeekTitle`, `getWeekContents` | Validates mentor owns week via internal `validateMentorOwnsWeek`. |
| `LectureService` | `createLecture`, `updateLecture`, `deleteLecture` | Custom mentorship/week ownership check inside the methods. |
| `BadgeAwardService` | `awardBadge`, `getAwardsByMentorship`, `getAwardsByBadge` | Custom checks ensuring mentor owns the mentorship and student is enrolled. |
| `BadgeService` | `createBadge`, `updateBadge`, `deleteBadge` | Validates ownership via internal `validateOwnership(mentorship)`. |

*Note:* Most protected services rely on locally duplicated validation methods inside their respective service classes rather than utilizing the centralized `securityService`.

---

## Methods Missing Protection

The following service methods currently do not verify if the requesting user has the necessary ownership or permissions. An attacker could potentially pass any valid ID to access or manipulate data belonging to other users.

### Live Session Service (`LiveSessionServiceImp`)
- `updateSession(Long sessionId, ...)`: Updates a session without checking if the mentor owns the session.
- `getSessionById(Long sessionId)`: Retrieves a session without verifying if the user is the mentor who owns it or an enrolled student.
- `deleteSession(Long sessionId)`: Deletes a session without mentor ownership verification.
- `startLiveSession(Long sessionId)`: Starts a session without mentor ownership verification.
- `endSession(Long sessionId)`: Ends a session without mentor ownership verification.
- `recordSnapshot(Long sessionId, ...)`: Records attendance without verifying if the user performing the action is the mentor owning the session.
- `getAttendanceResult(Long sessionId)`: Retrieves attendance list without mentor ownership check.
- `getStudentAttendanceResult(Long sessionId)`: Gets student's own attendance, but lacks robust checks (relying only on finding the student by current email).

### Quiz Service (`QuizServiceImpl`)
- `createQuiz(...)`: Retrieves week by ID and creates a quiz without checking if the mentor owns the week.
- `deleteQuiz(Long id)`: Deletes a quiz by ID without mentor ownership verification.
- `updateQuiz(Long id, ...)`: Updates a quiz by ID without mentor ownership verification.
- `getQuizDetails(Long id)`: Retrieves a quiz by ID without verifying if the user has access.
- `getQuizzes(...)`: Paginates quizzes by mentorship ID without access checks.
- `getQuizDashboard(...)`, `getQuizStatistics(...)`, `getMentorshipQuizzesOverview(...)`, `getQuizOverviewDto(...)`: Returns sensitive statistics without access checks.
- `changeStatus(...)`: Changes quiz status (e.g., to PUBLISHED) without mentor ownership verification.

### Question Service (`QuestionServiceImp`)
- `createQuestion(...)`: Creates a question for a quiz ID without verifying if the mentor owns the quiz.
- `getQuestionsByQuizId(...)`: Retrieves questions without access checks.
- `getQuestionById(...)`: Retrieves a specific question without access checks.
- `updateQuestion(...)`: Updates a question without mentor ownership verification.
- `deleteQuestion(...)`: Deletes a question without mentor ownership verification.

### Quiz Submission Service (`QuizSubmissionServiceImpl`)
- `submitQuizAnswers(...)`: Submits answers by quiz ID. Fails to check if the student is actually enrolled in the mentorship.
- `getStudentAnswers(...)`: Retrieves answers for a submission without checking if the requester is the student or the mentor.
- `getAllSubmissionsByStudent(...)`: Retrieves student submissions without access checks.
- `getAllSubmissionsByQuiz(...)`: Retrieves all submissions for a quiz without verifying mentor ownership.

### Notification Service (`NotificationService`)
- `markOneAsRead(Long relationId)`: Marks a notification as read without verifying if the notification belongs to the current user.
- `deleteNotification(Long relationId)`: Deletes a notification without verifying ownership.

### Chat Room / Conversation Services
- `getMessages(Long roomId, ...)`: Retrieves room messages without explicitly verifying if the user is a member of the room.
- `getRoomMembers(Long roomId)`: Retrieves room members without verifying access.

---

## Proposed Security Methods

To standardize authorization across the application, the following centralized security validation methods should be added to `securityService.java`. These methods are designed to minimize database queries by using specialized exists/join queries in their respective repositories.

```java
// 1. Live Session Validations
public Boolean isMentorOwnLiveSession(Long sessionId, String mentorEmail);
public Boolean isStudentEnrolledByLiveSessionId(String studentEmail, Long sessionId);

// 2. Quiz Validations
public Boolean isMentorOwnQuiz(Long quizId, String mentorEmail);
public Boolean isStudentEnrolledByQuizId(String studentEmail, Long quizId);

// 3. Question Validations
public Boolean isMentorOwnQuestion(Long questionId, String mentorEmail);

// 4. Notification Validations
public Boolean isUserOwnNotification(Long notificationId, String userEmail);

// 5. Chat Validations
public Boolean isUserMemberOfChatRoom(Long roomId, String userEmail);
```

### Query Logic Specifications (For Repositories)

- **`isMentorOwnLiveSession`**:
  `SELECT COUNT(s) > 0 FROM Session s WHERE s.id = :sessionId AND s.week.mentorship.mentor.email = :email`
- **`isStudentEnrolledByLiveSessionId`**:
  `SELECT COUNT(e) > 0 FROM Enrollment e JOIN Week w ON w.mentorship.id = e.mentorShip.id JOIN Session s ON s.week.id = w.id WHERE s.id = :sessionId AND e.student.email = :studentEmail`
- **`isMentorOwnQuiz`**:
  `SELECT COUNT(q) > 0 FROM Quiz q WHERE q.id = :quizId AND q.week.mentorship.mentor.email = :email`
- **`isStudentEnrolledByQuizId`**:
  `SELECT COUNT(e) > 0 FROM Enrollment e JOIN Week w ON w.mentorship.id = e.mentorShip.id JOIN Quiz q ON q.week.id = w.id WHERE q.id = :quizId AND e.student.email = :studentEmail`
- **`isMentorOwnQuestion`**:
  `SELECT COUNT(q) > 0 FROM Question q WHERE q.id = :questionId AND q.quiz.week.mentorship.mentor.email = :email`
- **`isUserOwnNotification`**:
  `SELECT COUNT(un) > 0 FROM UserNotification un WHERE un.id = :notificationId AND un.user.email = :email`
- **`isUserMemberOfChatRoom`**:
  *(Already partially handled by `EnrollmentRepository.isUserInRoomMentorship` but needs integration into `securityService`)*

---

## Implementation Map

| Missing Protection In Service | Method Needing Protection | Proposed `securityService` Method to Inject |
| --- | --- | --- |
| **LiveSessionServiceImp** | `updateSession`, `deleteSession`, `startLiveSession`, `endSession`, `recordSnapshot`, `getAttendanceResult` | `if(!securityService.isMentorOwnLiveSession(sessionId, email)) throw new AccessDeniedException(...)` |
| **LiveSessionServiceImp** | `getSessionById` | `if(!securityService.isMentorOwnLiveSession(...) && !securityService.isStudentEnrolledByLiveSessionId(...)) throw new AccessDeniedException(...)` |
| **QuizServiceImpl** | `createQuiz` | `if(!securityService.isMentorOwnWeek(weekId, email))` *(Reusing existing/similar logic)* |
| **QuizServiceImpl** | `deleteQuiz`, `updateQuiz`, `changeStatus`, `getQuizDashboard`, `getQuizStatistics`, `getMentorshipQuizzesOverview` | `if(!securityService.isMentorOwnQuiz(quizId, email)) throw new AccessDeniedException(...)` |
| **QuizServiceImpl** | `getQuizDetails` | `if(!securityService.isMentorOwnQuiz(...) && !securityService.isStudentEnrolledByQuizId(...)) throw new AccessDeniedException(...)` |
| **QuestionServiceImp** | `createQuestion`, `getQuestionsByQuizId` | `if(!securityService.isMentorOwnQuiz(quizId, email)) throw new AccessDeniedException(...)` |
| **QuestionServiceImp** | `getQuestionById`, `updateQuestion`, `deleteQuestion` | `if(!securityService.isMentorOwnQuestion(questionId, email)) throw new AccessDeniedException(...)` |
| **QuizSubmissionServiceImpl** | `submitQuizAnswers` | `if(!securityService.isStudentEnrolledByQuizId(email, quizId)) throw new AccessDeniedException(...)` |
| **QuizSubmissionServiceImpl** | `getAllSubmissionsByQuiz` | `if(!securityService.isMentorOwnQuiz(quizId, email)) throw new AccessDeniedException(...)` |
| **NotificationService** | `markOneAsRead`, `deleteNotification` | `if(!securityService.isUserOwnNotification(relationId, email)) throw new AccessDeniedException(...)` |
| **ChatRoomService/Controller** | `getMessages`, `getRoomMembers` | `if(!securityService.isUserMemberOfChatRoom(roomId, email)) throw new AccessDeniedException(...)` |
