package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.config.security.JwtSecretProvider;
import com.example.gradproj.EduNest.entity.mentorship.Enrollment;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.register.EducationalLevel;
import com.example.gradproj.EduNest.entity.users.Admin;
import com.example.gradproj.EduNest.entity.users.Mentor;
import com.example.gradproj.EduNest.entity.Roles;
import com.example.gradproj.EduNest.entity.users.Student;
import com.example.gradproj.EduNest.repository.mentorShip.EnrollmentRepository;
import com.example.gradproj.EduNest.repository.mentorShip.MentorShipRepository;
import com.example.gradproj.EduNest.repository.users.AdminRepository;
import com.example.gradproj.EduNest.repository.users.MentorRepository;
import com.example.gradproj.EduNest.repository.RoleRepository;
import com.example.gradproj.EduNest.repository.users.StudentRepository;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * P2-03 / EB-37 — probe fixture. Seeds the rows the prod chain's JWT validator
 * requires (enabled & not-deleted users with the given roles, admins by email),
 * builds the minimal MentorshipChain the content/room/leaderboard probes need,
 * and mints REAL JWTs signed with the test-profile secret — same claim contract
 * as {@code JwtService#generateToken} (username / authorities / fullName) without
 * depending on the login flow.
 *
 * Doctrine (Option A): this fixture only makes TODAY's behavior reachable. It
 * holds no opinions about the G-register holes.
 */
@Component
public class AuthFixture {

	public static final String ADMIN = "ADMIN";
	public static final String MENTOR = "MENTOR";
	public static final String STUDENT = "STUDENT";

	public record MentorshipChain(Mentor mentor, MentorShip mentorship) {
	}

	private final RoleRepository roles;
	private final StudentRepository students;
	private final MentorRepository mentors;
	private final AdminRepository admins;
	private final MentorShipRepository mentorships;
	private final EnrollmentRepository enrollments;
	private final JwtSecretProvider jwtSecretProvider;
	private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

	public AuthFixture(RoleRepository roles, StudentRepository students, MentorRepository mentors,
			AdminRepository admins, MentorShipRepository mentorships, EnrollmentRepository enrollments,
			JwtSecretProvider jwtSecretProvider,
			org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
		this.roles = roles;
		this.students = students;
		this.mentors = mentors;
		this.admins = admins;
		this.mentorships = mentorships;
		this.enrollments = enrollments;
		this.jwtSecretProvider = jwtSecretProvider;
		this.passwordEncoder = passwordEncoder;
	}

	/* ---------- seeding ---------- */

	public Roles role(String name) {
		return roles.findByName(name).orElseGet(() -> roles.save(new Roles(null, name)));
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public Student student(String email) {
		Student s = students.findByEmail(email).orElseGet(() -> Student.builder()
				.firstName("Probe").lastName("Student" + System.nanoTime())
				.email(email).password(passwordEncoder.encode("probe-password"))
				.educationalLevel(EducationalLevel.FRESH)
				.role(role(STUDENT)).enabled(true).deleted(false).build());
		return students.saveAndFlush(s);
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public Mentor mentor(String email) {
		Mentor m = mentors.findByEmail(email).orElseGet(() -> Mentor.builder()
				.firstName("Probe").lastName("Mentor" + System.nanoTime())
				.email(email).password(passwordEncoder.encode("probe-password"))
				.jobTitle("Probe")
				.bio("probe").yearsOfExperience(1.0).role(role(MENTOR))
				.enabled(true).deleted(false).build());
		return mentors.saveAndFlush(m);
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public Admin admin(String email) {
		return admins.findByEmail(email).orElseGet(() -> admins.saveAndFlush(Admin.builder()
				.firstName("Probe").lastName("Admin" + System.nanoTime())
				.email(email).password(passwordEncoder.encode("probe-password"))
				.role(role(ADMIN)).build()));
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public MentorshipChain mentorship(String mentorEmail, Status status) {
		Mentor m = mentor(mentorEmail);
		MentorShip ms = MentorShip.builder()
				.title("Probe " + status + " " + System.nanoTime())
				.subtitle("probe").description("probe").category("backEnd")
				.price(0.0).duration(1.0).status(status).mentor(m).build();
		return new MentorshipChain(m, mentorships.saveAndFlush(ms));
	}

	@Transactional(propagation = Propagation.REQUIRED)
	public Enrollment enroll(Long mentorshipId, String studentEmail) {
		Student s = student(studentEmail);
		Enrollment e = Enrollment.builder().student(s)
				.mentorShip(mentorships.findById(mentorshipId).orElseThrow())
				.joinedAt(java.time.LocalDateTime.now()).build();
		return enrollments.saveAndFlush(e);
	}

	/**
	 * JWT minting — mirrors JwtService#generateToken's claim contract
	 * (username / authorities / fullName).
	 *
	 * ⚠️ APP CONTRACT QUIRK (documented for EB-12 / P1-05): the JWT validator
	 * (JwtTokenValidatorFilter) passes the RAW Authorization header into
	 * validateToken — the app does NOT use a "Bearer " prefix today. Probes
	 * must send the raw JWT, exactly like the FE does.
	 */
	public String authToken(String email, String roleName) {
		SecretKey key = jwtSecretProvider.getSecretKey();
		String jwt = Jwts.builder()
				.issuer("EduNest")
				.subject("JWT Token")
				.issuedAt(new Date())
				.expiration(new Date(System.currentTimeMillis() + 3_600_000))
				.claim("username", email)
				.claim("authorities", "ROLE_" + roleName)
				.claim("fullName", "Probe User")
				.signWith(key)
				.compact();
		return jwt; // RAW — no "Bearer " prefix (see contract note above)
	}
}
