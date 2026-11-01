package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.enums.message.MessageStatus;
import com.example.gradproj.EduNest.repository.contactus.ContactMessageRepository;
import com.example.gradproj.EduNest.service.register.EmailService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.util.AopTestUtils;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P0-04 / EB-2 — V-02: contact-reply HTML-injection POC (SEL-15).
 *
 * Audit ({@code mds/ISSUES.md} SEL-15): {@code ContactMessageService#sendAdminReply}
 * merges {@code {{name}}} and {@code {{reply}}} into {@code admin-reply.html} with
 * plain {@code String.replace} — no {@code HtmlUtils.htmlEscape}-style escaping
 * (contrast {@code AdminBadgeService}, which escapes). {@code {{name}}} is
 * fully attacker-controlled:
 * {@code POST /api/v1/contact/save-message} is PUBLIC (PublicRoutesPolicy) and
 * the DTO validates {@code name} as {@code @NotBlank} only — a hostile name
 * lands in the DB, and the FIRST ADMIN REPLY turns it into executable HTML
 * inside a real email. {@code {{reply}}} (admin-controlled) is equally raw.
 *
 * POC mechanics (Option A doctrine — pinning TODAY's behavior, the verdict
 * flips when P1-24 applies the escaping fix): the real prod chain runs
 * end-to-end (public save → ADMIN-only reply → real template file → real
 * merge). The "sentinel inbox" recording bean is the capture at the {@code
 * EmailService} boundary (the app's SMTP hop is unreachable in tests per
 * application-test.properties), so the captured {@code htmlBody} is exactly
 * what a mail client would render.
 *
 * Expected verdict: {@code <img src=x onerror=...>} / {@code <script>...}
 * survive VERBATIM (no {@code &lt;} escaping anywhere on the path).
 */
@Tag("critical")
class ContactReplyInjectionPocTest extends ProdChainIntegrationTestBase {

	/**
	 * Sentinel inbox: the real chain stops one hop earlier than a mail server —
	 * at the {@code EmailService} boundary — and hands the finished email to
	 * this recording bean instead. A plain subclass (not a Mockito mock), so
	 * no matcher bookkeeping can interfere with the {@code @Async} dispatch;
	 * {@code @Primary} resolves the context's by-type ambiguity with the real
	 * bean. Template loading is untouched REAL production code.
	 */
	@TestConfiguration
	static class SentinelInbox {
		@Bean
		@Primary
		EmailService emailService() {
			return new RecordingEmailService();
		}
	}

	/**
	 * A REAL {@link EmailService} with exactly ONE deviation: outgoing mail is
	 * recorded into {@code AtomicReference}s instead of being pushed to SMTP
	 * (the test-profile SMTP hop is unreachable anyway). Everything else —
	 * most importantly loading {@code templates/emails/admin-reply.html} —
	 * runs the production code path, so the captured {@code htmlBody} is
	 * exactly what a mail client would render.
	 */
	static class RecordingEmailService extends EmailService {

		private final AtomicReference<String> recipient = new AtomicReference<>();
		private final AtomicReference<String> subject = new AtomicReference<>();
		private final AtomicReference<String> htmlBody = new AtomicReference<>();

		RecordingEmailService() {
			super(null); // SMTP client never used — the sentinel records instead
		}

		@Override
		public void sendEmail(String to, String subject, String htmlBody) {
			this.recipient.set(to);
			this.subject.set(subject);
			this.htmlBody.set(htmlBody);
		}

		void reset() {
			recipient.set(null);
			subject.set(null);
			htmlBody.set(null);
		}
	}

	@Autowired private EmailService emailService; // the @Primary sentinel (RecordingEmailService)
	@Autowired private ContactMessageRepository contactMessages;
	@Autowired private AuthFixture auth;

	/** Resolve the sentinel through any Spring proxy layer (e.g. @Async). */
	private RecordingEmailService inbox() {
		return AopTestUtils.getUltimateTargetObject(emailService);
	}

	private void resetInbox() {
		inbox().reset();
	}

	/** Public unauthenticated save with a controlled {name,email,phone}; returns the row id. */
	private Long saveMessage(String email, String name) throws Exception {
		mockMvc.perform(post("/api/v1/contact/save-message")
						.contentType("application/json")
						.content("{\"name\":\"" + name + "\",\"email\":\"" + email + "\"," +
								"\"phone\":\"01012345678\",\"message\":\"Please review my account.\"}"))
				.andExpect(status().isCreated());
		return contactMessages.findAll().stream()
				.filter(m -> email.equals(m.getEmail()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("probe: saved contact message not found"))
				.getId();
	}

	/** Bounded wait so the @Async dispatch reaches the sentinel either way. */
	private String awaitCapturedHtml(String email) throws Exception {
		RecordingEmailService inbox = inbox();
		long deadline = System.currentTimeMillis() + 3000;
		while (inbox.htmlBody.get() == null && System.currentTimeMillis() < deadline) {
			Thread.sleep(50);
		}
		String html = inbox.htmlBody.get();
		assertThat(html).as("admin reply email captured at the EmailService boundary").isNotNull();
		assertThat(inbox.recipient.get()).as("email goes to the contact sender").isEqualTo(email);
		assertThat(inbox.subject.get()).isEqualTo("Re: Support Reply");
		return html;
	}

	/**
	 * V-02 CONFIRMED — the ATTACKER path: an attacker's hostile NAME (public
	 * endpoint, no validation) is merged unescaped into the admin's reply
	 * email. The payload survives verbatim — no HtmlUtils-style escaping
	 * anywhere on the path (contrast AdminBadgeService).
	 */
	@Test
	@DisplayName("V-02: hostile PUBLIC name merged raw into the admin reply email → injection")
	void v02_hostilePublicNameInjected() throws Exception {
		resetInbox();
		auth.admin("probe-eb2-v02-admin@example.org");
		String hostile = "<img src=x onerror=alert(EduNestV02)>";

		Long id = saveMessage("probe-eb2-v02-name@example.org", hostile);
		mockMvc.perform(post("/api/v1/contact/message/{id}/reply", id)
						.header("Authorization", auth.authToken(
								"probe-eb2-v02-admin@example.org", AuthFixture.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content("Your request has been reviewed."))
				.andExpect(status().isOk());

		assertThat(contactMessages.findById(id).orElseThrow().getStatus())
				.as("reply flow ran to completion").isEqualTo(MessageStatus.COMPLETED);
		String html = awaitCapturedHtml("probe-eb2-v02-name@example.org");

		assertThat(html).contains(hostile); // payload VERBATIM — the injection
		assertThat(html).doesNotContain("&lt;"); // zero escaping on the path
	}

	/**
	 * V-02 — the REPLY slot is equally raw: admin's own text is merged
	 * unescaped, so any HTML in replies is live too (matter of record; same
	 * fix closes both).
	 */
	@Test
	@DisplayName("V-02: admin reply text merged raw into the {{reply}} slot → injection")
	void v02_replySlotInjected() throws Exception {
		resetInbox();
		auth.admin("probe-eb2-v02-admin2@example.org");
		String hostile = "<script>alert(document.cookie)</script>";

		Long id = saveMessage("probe-eb2-v02-reply@example.org", "Probe Victim");
		mockMvc.perform(post("/api/v1/contact/message/{id}/reply", id)
						.header("Authorization", auth.authToken(
								"probe-eb2-v02-admin2@example.org", AuthFixture.ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(hostile))
				.andExpect(status().isOk());

		String html = awaitCapturedHtml("probe-eb2-v02-reply@example.org");

		assertThat(html).contains(hostile);
		assertThat(html).doesNotContain("&lt;script");
	}
}
