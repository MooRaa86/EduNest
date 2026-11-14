<div align="center">

# EduNest
### Next-Generation Mentorship & Structured-Learning Platform

[![Java](https://img.shields.io/badge/Java-21-007396?style=flat-square&logo=openjdk&logoColor=white)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.7-6DB33F?style=flat-square&logo=spring&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring_Security-6-6DB33F?style=flat-square&logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Supabase-336791?style=flat-square&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![WebSocket](https://img.shields.io/badge/WebSocket-STOMP-000000?style=flat-square&logo=socket.io&logoColor=white)](#)
[![Live sessions](https://img.shields.io/badge/Live_sessions-Zoom_%2F_Jitsi-1D70B8?style=flat-square)](mds/live-sessions/MDS.md)

A modular educational ecosystem for mentor-led curricula, submitted projects, student profiles and database-backed completion certificate records.
</div>

---

## Executive Summary
For engineering documentation and current implementation details, see [mds/README.md](mds/README.md).
EduNest is a next-generation, mentor-led structured-learning platform designed to address the fundamental shortcomings of contemporary online education. It bridges the gap between passive online courses and real mentorship by empowering mentors to design week-by-week learning journeys containing lectures, quizzes, tasks, and capstone projects.

Unlike competitors that offer one-to-one mentorship (expensive and unscalable) or traditional MOOCs (passive with low completion rates), EduNest introduces a **scalable group mentorship model** integrating curriculum delivery, real-time communication, live sessions, assessments, gamification, and certification into a single ecosystem.

---

## The Problem & Our Solution
**The Problem:** The online learning market is flooded with platforms that rely on pre-recorded video content with no human oversight. Completion rates remain critically low, students receive no personal guidance, and they finish courses without verifiable credentials or portfolio-worthy projects. Tool fragmentation forces users to juggle video platforms, messaging apps, and grading spreadsheets.

**The Solution:** EduNest provides a centralized learning ecosystem that unifies every component of the online learning experience. 
- **For Students:** Structured weekly curricula, real-time communication, graded submissions, and a gamified reward system. Checkpoint-based prerequisite unlocking is not implemented.
- **For Mentors:** A unified tool to structure curricula, communicate with learners, and track their progress without needing third-party tools.

---

## Key Features & Scope

### 1. Centralized Learning & Group-Based Mentorship
Mentors create structured mentorship programs with defined curricula, pricing, and enrollment. Students join as cohorts, enabling collaborative learning, peer interaction, and community building at scale.

### 2. Weekly Curriculum Builder
Each mentorship is organized into ordered weeks containing:
- **Lectures**: External video URLs.
- **Quizzes**: MCQ formats with A/B/C/D choices and auto-grading.
- **Tasks**: File submissions with manual grading and points. `passPoints` is stored but does not enforce prerequisite unlocking.
- **Projects**: Capstone assignments with briefs, deadlines, and point awards.

### 3. Live and Recorded Sessions
Mentors can schedule live sessions with an automated lifecycle. Zoom meeting creation is available when enabled and configured; otherwise Jitsi links are generated as a fallback. Zoom is disabled by default. Attendance snapshots can be recorded, while attendance evaluation/enforcement is disabled by default.

### 4. Built-in Real-Time Communication
Powered by WebSocket STOMP, the platform offers group chat rooms and private conversations. JWT is validated on STOMP CONNECT; room SUBSCRIBE and SEND operations apply authorization checks.

### 5. Gamification Engine & Badges
- **Badges**: 8 configurable categories (ACHIEVEMENT, PERFORMANCE, CONSISTENCY, PROBLEM_SOLVING, CREATIVITY, LEADERSHIP, COMMUNITY, SPECIAL_RECOGNITION).
- **Points & Leaderboards**: Points are accumulated per mentorship and displayed on cohort-specific leaderboards.
- **Admin Badges**: Platform-wide recognition like `TOP_MENTOR` and `INNOVATOR_AWARD`.

### 6. Measurable Outcomes (Certificates & Portfolios)
- **Completion certificates**: Completion processing creates database-backed certificate records for eligible ranked students; students can view their records. A completion-certificate PDF renderer or download is not implemented. Admin badge PDFs are a separate feature.
- **Student Skill Profiles**: Public profiles aggregating skills, social links (GitHub, LinkedIn), earned badges, certificates, and an auto-generated portfolio of graded project submissions.

### 7. Mentor Analytics Dashboard
Real-time visibility into enrollment statistics, platform commission/revenue tracking, and pending submission queues.

---

## Users and Roles

### STUDENT
- Register via email/OTP.
- Browse, filter, and enroll in mentorships.
- Navigate weekly curricula (Lectures, Quizzes, Tasks, Projects).
- Submit files securely (validated by Apache Tika).
- Engage in real-time chat and join configured Zoom or fallback Jitsi sessions.
- Earn points, badges, certificates, and build a public profile.

### MENTOR
- Create mentorships (`DRAFT` → `ACTIVE` → `COMPLETED`).
- Build week-by-week curricula and schedule live sessions.
- Grade tasks and projects manually.
- Create custom gamification badges.
- Track revenues and student progress via the Mentor Dashboard.

### ADMIN
- View and search all platform users via `AdminUserDirectory`.
- Handle "Contact Us" workflows (`PENDING` → `UNDER_REVIEW` → `COMPLETED`).
- Issue global Admin Badges.
- Access platform-wide analytics and payment overviews.
- Send broadcast notifications.

---

## System Architecture & Technologies

| Category | Technologies |
|----------|--------------|
| **Core Backend** | Java 21, Spring Boot 3.5.7 |
| **Security** | Spring Security 6 & jjwt 0.12.5 |
| **Database & ORM** | PostgreSQL, Spring Data JPA |
| **Real-Time & Media** | Spring WebSocket + STOMP, optional Zoom integration / Jitsi fallback |
| **Document Processing**| Apache Tika 2.9.2, iText7 for admin badge PDFs |
| **Tooling & Build** | Spring Mail, springdoc-openapi 2.7 |

---

## UML Diagrams Overview

### Use Case Diagram
```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#ebf5fb', 'primaryTextColor': '#154360', 'primaryBorderColor': '#3498db', 'lineColor': '#3498db', 'textColor': '#3498db'}}}%%
flowchart LR
  Student([Student])
  Mentor([Mentor])
  Admin([Admin])

  subgraph EduNest Platform
    UC1(Register & OTP Verify)
    UC2(Browse & Enroll)
    UC3(View Weekly Curriculum)
    UC4(Submit Quizzes)
    UC5(Submit Tasks & Projects)
    UC6(Join Live Session)
    UC7(Chat - Group & Private)
    UC8(Earn Points & Badges)
    UC9(View Certificate)
    UC10(Create Mentorship)
    UC11(Build Curriculum)
    UC12(Grade Submissions)
    UC13(Schedule Live Session)
    UC14(Create Badges)
    UC15(View Mentor Dashboard)
    UC16(Manage Users)
    UC17(Handle Contact Messages)
    UC18(Manage Admin Badges)
    UC19(View Payments)
  end

  Student --> UC1
  Student --> UC2
  Student --> UC3
  Student --> UC4
  Student --> UC5
  Student --> UC6
  Student --> UC7
  Student --> UC8
  Student --> UC9

  Mentor --> UC1
  Mentor --> UC10
  Mentor --> UC11
  Mentor --> UC12
  Mentor --> UC13
  Mentor --> UC14
  Mentor --> UC15
  Mentor --> UC7

  Admin --> UC1
  Admin --> UC16
  Admin --> UC17
  Admin --> UC18
  Admin --> UC19
```

### Core Domain (Class Diagram)
```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#ebf5fb', 'primaryTextColor': '#154360', 'primaryBorderColor': '#3498db', 'lineColor': '#3498db', 'classText': '#154360', 'textColor': '#3498db'}}}%%
classDiagram
  BaseEntity <|-- UserEntity
  BaseEntity <|-- Admin
  UserEntity <|-- Student
  UserEntity <|-- Mentor

  class BaseEntity {
    +Long id
    +LocalDateTime createdAt
    +LocalDateTime updatedAt
    +String createdBy
    +String updatedBy
  }
  class UserEntity {
    +String firstName
    +String lastName
    +String email
    +String password
    +String phoneNumber
    +String profileImageUrl
    +Roles role
    +boolean enabled
    +boolean deleted
  }
  class Student {
    +EducationalLevel educationalLevel
    +String address
    +String jobTitle
    +String bio
  }
  class Mentor {
    +String jobTitle
    +String bio
    +double yearsOfExperience
  }
  class Admin {
    +String email
    +String password
    +String headline
  }
  class MentorShip {
    +String title
    +String category
    +DifficultyLevel difficultyLevel
    +Status status
    +Double price
    +Integer discountPercentage
    +Double rating
  }
  class Week {
    +String title
  }
  class Quiz {
    +String title
    +Integer durationMinutes
    +QuizStatus status
  }
  class Task {
    +String title
    +Integer points
    +Integer passPoints
    +TaskStatus status
  }
  class Project {
    +String title
    +String brief
    +Integer points
    +ProjectStatus status
  }
  class Enrollment {
    +Double price
    +Double platformProfit
    +LocalDateTime joinedAt
  }

  class Badge {
    +String title
    +BadgeCategory category
    +String description
    +int points
  }

  class Lecture {
    +String title
    +String lectureUrl
  }

  class Session {
    +String title
    +LocalDateTime scheduledAt
    +LocalDateTime actualStartTime
    +LocalDateTime actualEndTime
    +String meetingUrl
    +SessionStatus status
  }

  Mentor "1" --> "*" MentorShip : creates
  MentorShip "1" --> "*" Week : contains
  Week "1" --> "*" Quiz : has
  Week "1" --> "*" Task : has
  Week "1" --> "*" Project : has
  Week "1" --> "*" Lecture : has
  Week "1" --> "*" Session : has
  Student "1" --> "*" Enrollment : enrolls
  MentorShip "1" --> "*" Badge : offers
  MentorShip "1" --> "*" Enrollment : has
```

---

## Full Documentation
For a complete, in-depth view of the system analysis, comprehensive sequence diagrams, API details, feasibility study, and competitive analysis, please refer to the main project documentation file:

**[EduNest Project Documentation](EduNest_Project_Documentation1.md)**

---

## How to Run Locally

1. **Clone the repository:**
   ```bash
   git clone https://github.com/MooRaa86/EduNest.git
   cd EduNest
   ```
2. **Configure the Database:**
   - Provide a reachable PostgreSQL database and set `DATABASE_URL`, `DATABASE_NAME` (database username), and `DATABASE_PASSWORD` as environment variables.
   - Supply the required `JWT_SECRET` and `MAIL_*` settings through the environment; select the appropriate security profile. Do not put credentials in tracked files. See [environment configuration](mds/deployment/environment-configuration.md).
3. **Build the project:**
   ```bash
   mvn clean install
   ```
4. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```


</div>
