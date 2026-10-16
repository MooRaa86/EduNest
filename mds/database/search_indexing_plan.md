# 🔍 Search Engine Indexing Plan

Based on the project structure in the `src` and `uploads` directories, this document outlines the data and files that should be indexed to set up a robust Information Retrieval / Search Engine system (such as Elasticsearch, Meilisearch, or Algolia).

This plan focuses on the data that users (students or mentors) will search for to ensure quick and efficient access, **without requiring any changes to the current codebase**.

---

## 1️⃣ Data Extracted from the Database (Entities in `src`)
These entities contain the core data that users will look for using the platform's search bar:

### 📚 A. Mentorships and Courses (`MentorShip` & `Tags`)
This is the primary search component on the platform. The following fields must be indexed to make courses easily discoverable:
- `title`: For direct name searches.
- `description`: For keyword searches within course details.
- `category`: For filtering search results.
- `tags`: To link the course to specific topics (e.g., Java, Backend).
- `difficulty_level`: As a filtering tool.
- `mentor_id / mentor_name`: To search for courses provided by a specific mentor.

### 👤 B. Users and Mentors (`UserEntity`, `Mentor`, `StudentSkill`)
To allow students to search for specific mentors or skills:
- `first_name` & `last_name`: For searching by name.
- `bio` / `title`: To search for mentors based on their expertise or background.
- `skills` (from `StudentSkill`): To find individuals possessing specific skills.

### 📖 C. Course Content (`Lecture`, `Task`, `Project`, `Quiz`)
If you want to provide an "inner-course search" feature for enrolled students:
- `title`: Title of the lecture or task.
- `description` or `content`: Details of the lecture or project.

### 💬 D. Chats and Messages (`ChatMessage`, `ChatRoom`) - *Optional*
- If you wish to implement a "search in messages" feature within the project's chat system.

---

## 2️⃣ Files and Attachments (`uploads` Directory)
A robust Information Retrieval system should be able to perform **full-text searches within uploaded files**, not just the database.

### 📂 A. `uploads/admin` Directory (Uploaded Course Materials)
- **File Types:** PDFs, Word Documents, Presentations.
- **What to index:**
  - **File name** and path.
  - **File content (Text Content):** Use text extraction tools (like Apache Tika) to read text from inside PDF or Word files and index it. This way, if a student searches for a term located inside a PDF, it will appear in the search results.

### 📂 B. `uploads/submissions` Directory (Student Submissions)
- **What to index:**
  - Generally, it is not recommended to index the content of student submissions for general search (to maintain privacy).
  - You can index file names and link them to a `project_submission` or `task_submission` solely so that the Mentor can search through their specific students' submissions.

---

## 💡 Recommended Steps for Future Implementation (Search Engine)
1. **Use Elasticsearch or Typesense:** To index this data.
2. **Use Logstash or a Sync Tool:** To periodically copy (sync) the data from your primary database to the search engine.
3. **Apache Tika:** To perform text extraction on files in the `uploads/admin` directory and merge the extracted text with the course data in the search engine.
4. **Build Custom APIs:** In Spring Boot to receive search queries and send them to the search engine, returning results sorted by relevance.

> **Note:** This file was created purely as a structural reference based on your request. No Java code or project configurations were modified.
