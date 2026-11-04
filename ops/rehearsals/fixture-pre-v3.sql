-- EB-49 synthetic fixture only. Never load this into a shared database.
INSERT INTO users (id, deleted, enabled, created_at, updated_at, created_by, updated_by, role_id, email, first_name, last_name)
VALUES (901, false, true, now(), now(), 'fixture', 'fixture', 2, 'mentor@fixture.invalid', 'Fixture', 'Mentor'),
       (902, false, true, now(), now(), 'fixture', 'fixture', 3, 'student1@fixture.invalid', 'Fixture', 'Student'),
       (903, false, true, now(), now(), 'fixture', 'fixture', 3, 'student2@fixture.invalid', 'Fixture', 'Student');
INSERT INTO mentors (mentor_id, years_of_experience, job_title) VALUES (901, 2.0, 'Fixture');
INSERT INTO students (student_id, educational_level) VALUES (902, 'JUNIOR'), (903, 'JUNIOR');
INSERT INTO mentorship (id, mentor_id, created_at, updated_at, created_by, updated_by, title, status, duration, price, discount_percentage)
VALUES (904, 901, now(), now(), 'fixture', 'fixture', 'Fixture', 'ACTIVE', 1.0, 10.0, 0);
INSERT INTO enrollments (id, student_id, mentorship_id, joined_at, created_at, updated_at, created_by, updated_by)
VALUES (905, 902, 904, now(), now(), now(), 'fixture', 'fixture'),
       (906, 902, 904, now(), now(), now(), 'fixture', 'fixture');
INSERT INTO mentorship_reviews (id, mentorship_id, student_id, rating, feed_back, created_at, updated_at, created_by, updated_by)
VALUES (907, 904, 902, 5, 'fixture', now(), now(), 'fixture', 'fixture'),
       (908, 904, 902, 3, 'fixture', now(), now(), 'fixture', 'fixture');
INSERT INTO conversations (id, created_at, user1_id, user2_id)
VALUES (909, now(), 902, 903), (910, now(), 903, 902), (911, now(), 902, NULL);
INSERT INTO messages (id, conversation_id, sender_id, sent_at, content, seen, updated)
VALUES (912, 910, 903, now(), 'fixture', false, false),
       (913, 911, 902, now(), 'fixture', false, false);
