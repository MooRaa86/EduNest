package com.example.gradproj.EduNest.security.probes;

import com.example.gradproj.EduNest.exception.globalLogicException.globalLogicEx;
import com.example.gradproj.EduNest.entity.mentorship.MentorShip;
import com.example.gradproj.EduNest.entity.mentorship.Week;
import com.example.gradproj.EduNest.entity.tasks.Task;
import com.example.gradproj.EduNest.enums.mentorShip.Status;
import com.example.gradproj.EduNest.enums.tasks.TaskStatus;
import com.example.gradproj.EduNest.repository.tasks.TaskRepository;
import com.example.gradproj.EduNest.repository.tasks.TaskSubmissionRepository;
import com.example.gradproj.EduNest.repository.week.WeekRepository;
import com.example.gradproj.EduNest.service.fileSotageService.FileStorageService;
import com.example.gradproj.EduNest.service.mentorShip.ImageStorageService;
import com.example.gradproj.EduNest.testing.ProdChainIntegrationTestBase;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * EB-29 [P1-22] — upload-validation hardening regression probes
 * (FILE-02, FILE-03, FILE-04, MSH-06).
 *
 * Re-runs the relevant parts of the P0-06 matrix with hardened expectations:
 * real content is required (magic bytes + structural validation + JDK decode
 * where available), per-surface allowlists hold, stored extensions derive from
 * detection, and spoofed/dead/empty uploads answer 400 ERR_BUSINESS_RULE
 * without persisting file bytes or database metadata. EB-28's exposure and
 * authorization policy is exercised by its own probe classes and stays
 * untouched here.
 */
@Tag("critical")
class UploadValidationHardeningProbeTest extends ProdChainIntegrationTestBase {

	private static final String STUDENT = "eb29-student@example.org";
	private static final String MENTOR = "eb29-mentor@example.org";
	private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

	private static final byte[] JPEG = encodedFixture("jpg");
	private static final byte[] PNG = encodedFixture("png");
	private static final byte[] HTML = "<html><body>inert eb29 probe fixture</body></html>".getBytes(StandardCharsets.UTF_8);
	private static final byte[] GARBAGE = {0, 1, 2, 3, 4, 5};

	/**
	 * Genuine WebP fixtures from the libwebp test corpus
	 * (webmproject/libwebp-test-data), each verified decodable
	 * through Windows Graphics Imaging's WebP decoder before being pinned
	 * here: small_1x1.webp (lossy VP8 1x1 + ICMT metadata),
	 * one_color_no_palette.webp (lossless VP8L 100x100),
	 * alpha_filter_2.webp (extended VP8X + ALPH + VP8 16x16).
	 */
	private static final byte[] LOSSY_WEBP = Base64.getDecoder().decode(
			"UklGRlYAAABXRUJQVlA4IDoAAADwAgCdASoBAAEAAEcIhYWIhYSIAgICdaoD+AP6Ag1NGAD+/vNYf/5gZt2KO//mBv/80F4SW6//zLwASUNNVAgAAAB0ZXN0MXgxAA==");
	private static final byte[] LOSSLESS_WEBP = Base64.getDecoder().decode(
			"UklGRhQAAABXRUJQVlA4TAgAAAAvY8AYEIiICA==");
	private static final byte[] EXTENDED_WEBP = Base64.getDecoder().decode(
			"UklGRmoAAABXRUJQVlA4WAoAAAAQAAAADwAADwAAQUxQSCMAAAAJFyAgSUPg8IqIWCCTttEwT2H+Ne2dgYj+E0zSVNsxRabbAQBWUDggIAAAAHABAJ0BKhAAEAAPwP4loAJ0AUAAAP7aJpV6vLkWwAAA");

	@Autowired private AuthFixture auth;
	@Autowired private ImageStorageService images;
	@Autowired private FileStorageService files;
	@Autowired private WeekRepository weeks;
	@Autowired private TaskRepository tasks;
	@Autowired private TaskSubmissionRepository submissions;
	@Value("${app.upload.base-dir}") private String baseDir;
	@TempDir Path scratch;
	private final List<Path> writtenFiles = new ArrayList<>();

	@BeforeEach
	void seedUsers() {
		auth.student(STUDENT);
		auth.mentor(MENTOR);
	}

	@AfterEach
	void cleanup() {
		for (Path file : writtenFiles) {
			try {
				Files.deleteIfExists(file);
			} catch (Exception ignored) {
				// best effort — uploads land in the disposable probe root
			}
		}
		writtenFiles.clear();
	}

	// ------------------------------------------------------------------
	// fixtures
	// ------------------------------------------------------------------

	private static byte[] encodedFixture(String format) {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			if (!ImageIO.write(image, format, out)) throw new IllegalStateException("no writer for " + format);
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return out.toByteArray();
	}

	// ------------------------------------------------------------------
	// WebP malformed variants (derived from genuine verified fixtures)
	// ------------------------------------------------------------------

	/** Genuine content truncated to header-length only (declared RIFF size overruns). */
	private static byte[] truncatedWebp() {
		return Arrays.copyOf(LOSSLESS_WEBP, 16);
	}

	/** Valid container plus one appended byte past the declared RIFF size. */
	private static byte[] appendedGarbageWebp() {
		byte[] copy = Arrays.copyOf(LOSSLESS_WEBP, LOSSLESS_WEBP.length + 1);
		copy[copy.length - 1] = 0x5A;
		return copy;
	}

	/** RIFF size field inflated past the actual byte count. */
	private static byte[] lengthEstimateWebp() {
		byte[] copy = LOSSLESS_WEBP.clone();
		copy[4] += 16; // declared size += 16 while bytes stay the same
		return copy;
	}

	/** The old synthetic VP8X-only container: valid shape, but RFC 9649 carries NO image bitstream — undecodable. */
	private static byte[] syntheticVp8xNoBitstream() {
		byte[] bytes = new byte[26];
		System.arraycopy("RIFF".getBytes(StandardCharsets.US_ASCII), 0, bytes, 0, 4);
		for (int i = 0; i < 4; i++) bytes[4 + i] = (byte) (18 >>> (8 * i)); // 26-8
		System.arraycopy("WEBP".getBytes(StandardCharsets.US_ASCII), 0, bytes, 8, 4);
		System.arraycopy("VP8X".getBytes(StandardCharsets.US_ASCII), 0, bytes, 12, 4);
		bytes[16] = 10; bytes[17] = 0; bytes[18] = 0; bytes[19] = 0; // VP8X chunk size = 10
		// payload left zeroed: flags/width/height zero-padded, no VP8/VP8L follows
		return bytes;
	}

	/** Genuine lossy webp with the VP8 keyframe sync code corrupted. */
	private static byte[] corruptedVp8SyncCode() {
		byte[] copy = LOSSY_WEBP.clone();
		copy[23] ^= (byte) 0xFF; // VP8 payload starts at 20; sync code 9D 01 2A at 23..25
		return copy;
	}

	/** Genuine lossless webp with nonzero VP8L version bits. */
	private static byte[] corruptedVp8lVersionBits() {
		byte[] copy = LOSSLESS_WEBP.clone();
		copy[24] |= (byte) 0x20; // version occupies the top three bits of the 32-bit header
		return copy;
	}

	/** Extended webp with VP8X canvas dimensions above our conservative limit. */
	private static byte[] oversizedCanvasWebp() {
		byte[] copy = EXTENDED_WEBP.clone();
		copy[24] = (byte) 0xFF; // canvas width-1 (24-bit LE at bytes 24..26) = 16383
		copy[25] = (byte) 0x3F;
		return copy;
	}

	/** Lossy webp with the VP8 fourCC replaced by an unknown chunk type. */
	private static byte[] unknownChunkWebp() {
		byte[] copy = LOSSY_WEBP.clone();
		System.arraycopy("XXXX".getBytes(StandardCharsets.US_ASCII), 0, copy, 12, 4);
		return copy;
	}

	private static byte[] genuineZip() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("eb29-fixture.txt"));
			zip.write("inert eb29 fixture".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return out.toByteArray();
	}

	/**
	 * Local-file-header-only archive: real PK magic and a complete first
	 * entry, but NO central directory and NO end-of-central-directory record.
	 * Tika detects it as application/zip; the central-directory check must
	 * reject it.
	 */
	private static byte[] zipWithoutCentralDirectory() {
		String entryName = "f.txt";
		byte[] entryData = "abcd".getBytes(StandardCharsets.UTF_8);
		java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(entryData);
		long checksum = crc.getValue();

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(new byte[]{0x50, 0x4B, 0x03, 0x04}); // PK\x03\x04
		out.write(20); out.write(0);   // version needed
		out.write(0); out.write(0);    // flags
		out.write(0); out.write(0);    // method: stored
		out.write(0); out.write(0);    // mod time
		out.write(0); out.write(0);    // mod date
		out.write((int) (checksum & 0xFF));
		out.write((int) ((checksum >>> 8) & 0xFF));
		out.write((int) ((checksum >>> 16) & 0xFF));
		out.write((int) ((checksum >>> 24) & 0xFF));
		out.write(4); out.write(0);    // compressed size (LE)
		out.write(4); out.write(0);    // uncompressed size (LE)
		out.write(entryName.length()); out.write(0);
		out.write(0); out.write(0);    // extra length
		out.writeBytes(entryName.getBytes(StandardCharsets.UTF_8));
		out.writeBytes(entryData);
		return out.toByteArray();
	}

	/** JPEG magic bits + a length-honest header, then junk instead of entropy. */
	private static byte[] jpegSignatureWithGarbageBody() {
		byte[] prefix = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
				'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 0};
		byte[] bytes = new byte[prefix.length + 160];
		System.arraycopy(prefix, 0, bytes, 0, prefix.length);
		for (int i = prefix.length; i < bytes.length; i++) bytes[i] = (byte) (i * 31 % 251);
		return bytes;
	}

	// ------------------------------------------------------------------
	// helpers
	// ------------------------------------------------------------------

	private Path folder(String name) {
		return Path.of(baseDir).resolve(name);
	}

	private long countFiles(String folder) {
		Path dir = folder(folder);
		if (!Files.isDirectory(dir)) return 0;
		try (var list = Files.list(dir)) {
			return list.filter(Files::isRegularFile).count();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private boolean tempRemnantLeft(String folder) {
		Path dir = folder(folder);
		if (!Files.isDirectory(dir)) return false;
		try (var list = Files.list(dir)) {
			return list.anyMatch(p -> p.getFileName().toString().endsWith(".tmp"));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private void trackWritten(String url) {
		if (url != null && !url.isBlank()) {
			writtenFiles.add(Path.of(baseDir).resolve(url.substring("/uploads/".length())));
		}
	}

	private Long seedMentorshipForCover() {
		return auth.mentorship(MENTOR, Status.ACTIVE).mentorship().getId();
	}

	private long seedPublishedTaskWithEnrollment() {
		MentorShip ms = auth.mentorship(MENTOR, Status.ACTIVE).mentorship();
		auth.enroll(ms.getId(), STUDENT);
		Week week = weeks.saveAndFlush(Week.builder().mentorship(ms).title("EB-29 probe week").build());
		Task task = tasks.saveAndFlush(Task.builder()
				.week(week)
				.title("EB-29 probe task")
				.description("EB-29 probe task description")
				.points(10).passPoints(5).estimatedMinutes(30)
				.dueAt(LocalDateTime.now().plusDays(1))
				.status(TaskStatus.PUBLISHED)
				.build());
		return task.getId();
	}

	// ------------------------------------------------------------------
	// cover image endpoint (real HTTP surface, FILE-02 / MSH-06)
	// ------------------------------------------------------------------

	static Stream<org.junit.jupiter.params.provider.Arguments> realImageFixtures() {
		return Stream.of(
				org.junit.jupiter.params.provider.Arguments.of(
						"jpg", "jpg", new MockMultipartFile("image", "photo.jpg", "image/jpeg", JPEG), JPEG),
				org.junit.jupiter.params.provider.Arguments.of(
						"png", "png", new MockMultipartFile("image", "photo.png", "image/png", PNG), PNG),
				org.junit.jupiter.params.provider.Arguments.of(
						"webp lossy", "webp", new MockMultipartFile("image", "photo.webp", "image/webp", LOSSY_WEBP), LOSSY_WEBP),
				org.junit.jupiter.params.provider.Arguments.of(
						"webp lossless", "webp", new MockMultipartFile("image", "photo.webp", "image/webp", LOSSLESS_WEBP), LOSSLESS_WEBP),
				org.junit.jupiter.params.provider.Arguments.of(
						"webp extended", "webp", new MockMultipartFile("image", "photo.webp", "image/webp", EXTENDED_WEBP), EXTENDED_WEBP));
	}

	@ParameterizedTest(name = "cover accepts real {0} and stores the detection-derived extension")
	@MethodSource("realImageFixtures")
	void coverEndpoint_acceptsRealImages(String format, String extension, MockMultipartFile upload, byte[] rawBytes) throws Exception {
		long mentorshipId = seedMentorshipForCover();
		long before = countFiles("mentorship-cover-images");

		MvcResult result = mockMvc.perform(multipart("/api/v1/mentorship/" + mentorshipId + "/change-cover-image")
						.file(upload)
						.header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isOk())
				.andReturn();

		String url = JsonPath.read(result.getResponse().getContentAsString(), "$.apiResponse.Image_URL");
		assertThat(url).startsWith("/uploads/mentorship-cover-images/").endsWith("." + extension);
		assertThat(countFiles("mentorship-cover-images")).isEqualTo(before + 1);

		Path stored = Path.of(baseDir).resolve(url.substring("/uploads/".length()));
		writtenFiles.add(stored);
		assertThat(Files.readAllBytes(stored)).isEqualTo(rawBytes);

		// anonymoulsy fetchable public folder (EB-28 contract kept) with identical bytes
		mockMvc.perform(get(url))
				.andExpect(status().isOk())
				.andExpect(content().bytes(rawBytes));
	}

	@Test
	@DisplayName("FILE-02: HTML bytes under a .jpg name and image/jpeg declaration are rejected")
	void coverEndpoint_rejectsSpoofedHtml() throws Exception {
		long mentorshipId = seedMentorshipForCover();
		long before = countFiles("mentorship-cover-images");

		MockMultipartFile spoof = new MockMultipartFile("image", "photo.jpg", "image/jpeg", HTML);
		MvcResult result = mockMvc.perform(multipart("/api/v1/mentorship/" + mentorshipId + "/change-cover-image")
						.file(spoof)
						.header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isBadRequest())
				.andReturn();

		assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.errorCode"))
				.isEqualTo("ERR_BUSINESS_RULE");

		assertThat(countFiles("mentorship-cover-images")).isEqualTo(before);
		assertThat(tempRemnantLeft("mentorship-cover-images")).isFalse();
	}

	@Test
	@DisplayName("FILE-02/MSH-06: garbage bytes named .png and double-extension HTML are rejected")
	void coverEndpoint_rejectsGarbageAndDoubleExtension() throws Exception {
		long mentorshipId = seedMentorshipForCover();
		long before = countFiles("mentorship-cover-images");

		mockMvc.perform(multipart("/api/v1/mentorship/" + mentorshipId + "/change-cover-image")
						.file(new MockMultipartFile("image", "photo.png", "image/png", GARBAGE))
						.header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isBadRequest());

		mockMvc.perform(multipart("/api/v1/mentorship/" + mentorshipId + "/change-cover-image")
						.file(new MockMultipartFile("image", "photo.html.jpg", "image/jpeg", HTML))
						.header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isBadRequest());

		assertThat(countFiles("mentorship-cover-images")).isEqualTo(before);
		assertThat(tempRemnantLeft("mentorship-cover-images")).isFalse();
	}

	@Test
	@DisplayName("empty cover upload is rejected with no file or temp remnant")
	void coverEndpoint_rejectsEmptyUpload() throws Exception {
		long mentorshipId = seedMentorshipForCover();
		long before = countFiles("mentorship-cover-images");

		mockMvc.perform(multipart("/api/v1/mentorship/" + mentorshipId + "/change-cover-image")
						.file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[0]))
						.header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isBadRequest());

		assertThat(countFiles("mentorship-cover-images")).isEqualTo(before);
		assertThat(tempRemnantLeft("mentorship-cover-images")).isFalse();
	}

	@Test
	@DisplayName("oversized (>5MB) cover upload is rejected")
	void coverEndpoint_rejectsOversizedUpload() throws Exception {
		long mentorshipId = seedMentorshipForCover();
		long before = countFiles("mentorship-cover-images");

		byte[] oversized = new byte[(int) MAX_IMAGE_BYTES + 16]; // zeros — not a decodable image either
		mockMvc.perform(multipart("/api/v1/mentorship/" + mentorshipId + "/change-cover-image")
						.file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", oversized))
						.header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isBadRequest());

		assertThat(countFiles("mentorship-cover-images")).isEqualTo(before);
	}

	@Test
	@DisplayName("FILE-03: a valid JPEG under a .exe filename is normalized — stored extension comes from detection, not the filename")
	void coverEndpoint_mislabelledFilenameStoredWithDerivedExtension() throws Exception {
		long mentorshipId = seedMentorshipForCover();

		MvcResult result = mockMvc.perform(multipart("/api/v1/mentorship/" + mentorshipId + "/change-cover-image")
						.file(new MockMultipartFile("image", "photo.exe", "image/jpeg", JPEG))
						.header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isOk())
				.andReturn();

		String url = JsonPath.read(result.getResponse().getContentAsString(), "$.apiResponse.Image_URL");
		assertThat(url).endsWith(".jpg").doesNotContain("exe");
		Path stored = Path.of(baseDir).resolve(url.substring("/uploads/".length()));
		writtenFiles.add(stored);
		assertThat(Files.readAllBytes(stored)).isEqualTo(JPEG);

		mockMvc.perform(get(url))
				.andExpect(status().isOk())
				.andExpect(content().bytes(JPEG));
	}

	@Test
	@DisplayName("a JPEG declared as a non-image MIME type never reaches validation (caller guard)")
	void coverEndpoint_rejectsNonImageContentType() throws Exception {
		long mentorshipId = seedMentorshipForCover();
		long before = countFiles("mentorship-cover-images");

		mockMvc.perform(multipart("/api/v1/mentorship/" + mentorshipId + "/change-cover-image")
						.file(new MockMultipartFile("image", "photo.jpg", "image/x-msdownload", JPEG))
						.header("Authorization", auth.authToken(MENTOR, AuthFixture.MENTOR)))
				.andExpect(status().isBadRequest());

		assertThat(countFiles("mentorship-cover-images")).isEqualTo(before);
	}

	// ------------------------------------------------------------------
	// avatar endpoints (student & mentor)
	// ------------------------------------------------------------------

	@ParameterizedTest(name = "{0} avatar endpoint accepts a real JPEG and rejects HTML bytes")
	@EnumSource(value = AvatarSurface.class)
	void avatarEndpoints_acceptRealImagesAndRejectSpoofed(AvatarSurface surface) throws Exception {
		String urlEndpoint = surface.endpoint();
		String token = surface == AvatarSurface.STUDENT
				? auth.authToken(STUDENT, AuthFixture.STUDENT)
				: auth.authToken(MENTOR, AuthFixture.MENTOR);

		MvcResult ok = mockMvc.perform(multipart(urlEndpoint)
						.file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", JPEG))
						.with(request -> { request.setMethod("PATCH"); return request; })
						.header("Authorization", token))
				.andExpect(status().isOk())
				.andReturn();
		String url = JsonPath.read(ok.getResponse().getContentAsString(), "$.apiResponse.profileImageUrl");
		assertThat(url).endsWith(".jpg");
		Path stored = Path.of(baseDir).resolve(url.substring("/uploads/".length()));
		writtenFiles.add(stored);
		assertThat(stored).as("stored file must exist").exists();

		long before = countFiles(surface.folder());
		mockMvc.perform(multipart(urlEndpoint)
						.file(new MockMultipartFile("image", "photo.jpg", "image/jpeg", HTML))
						.with(request -> { request.setMethod("PATCH"); return request; })
						.header("Authorization", token))
				.andExpect(status().isBadRequest());
		assertThat(countFiles(surface.folder())).isEqualTo(before);
		assertThat(tempRemnantLeft(surface.folder())).isFalse();
	}

	enum AvatarSurface {
		STUDENT("student", "/student/profile/image", AuthFixture.STUDENT),
		MENTOR("mentor", "/mentor/profile/image", AuthFixture.MENTOR);

		final String folder;
		final String endpoint;
		final String role;

		AvatarSurface(String folder, String endpoint, String role) {
			this.folder = folder;
			this.endpoint = endpoint;
			this.role = role;
		}

		String folder() { return folder; }
		String endpoint() { return endpoint; }
		String role() { return role; }
	}

	// ------------------------------------------------------------------
	// per-surface allowlists (service level — folder decides the surface)
	// ------------------------------------------------------------------

	@Test
	@DisplayName("chatroom surface: jpg/png accepted, genuine webp (decodable elsewhere, disallowed format here) rejected")
	void chatFolderAllowlist_isEnforced() {
		long journal = countFiles("chatroom");

		assertThatThrownBy(() -> images.saveImage("chatroom", 5001L,
				new MockMultipartFile("image", "photo.webp", "image/webp", EXTENDED_WEBP)))
				.isInstanceOf(globalLogicEx.class);
		assertThat(countFiles("chatroom")).isEqualTo(journal);
		assertThat(tempRemnantLeft("chatroom")).isFalse();

		for (Object[] f : new Object[][]{{JPEG, "image/jpeg"}, {PNG, "image/png"}}) {
			String url = images.saveImage("chatroom", 5001L,
					new MockMultipartFile("image", "photo.img", (String) f[1], (byte[]) f[0]));
			trackWritten(url);
			assertThat(Files.exists(Path.of(baseDir).resolve(url.substring("/uploads/".length())))).isTrue();
		}
	}

	@Test
	@DisplayName("admin avatar surface: webp accepted, HTML rejected, nothing persisted on rejection")
	void adminAvatarFolder_provesAvatarsAllowWebp() {
		long journal = countFiles("admin");

		assertThatThrownBy(() -> images.saveImage("admin", 9091L,
				new MockMultipartFile("image", "photo.jpg", "image/jpeg", HTML)))
				.isInstanceOf(globalLogicEx.class);
		assertThat(countFiles("admin")).isEqualTo(journal);
		assertThat(tempRemnantLeft("admin")).isFalse();

		String url = images.saveImage("admin", 9091L,
				new MockMultipartFile("image", "photo.webp", "image/webp", LOSSLESS_WEBP));
		trackWritten(url);
		assertThat(url).endsWith(".webp");
		assertThat(Files.exists(Path.of(baseDir).resolve(url.substring("/uploads/".length())))).isTrue();
	}

	// ------------------------------------------------------------------
	// malformed / truncated images never persist a file
	// ------------------------------------------------------------------

	static Stream<org.junit.jupiter.params.provider.Arguments> malformedImages() {
		return Stream.of(
				org.junit.jupiter.params.provider.Arguments.of("truncated JPEG magic", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}),
				org.junit.jupiter.params.provider.Arguments.of("JPEG header with garbage body", jpegSignatureWithGarbageBody()),
				org.junit.jupiter.params.provider.Arguments.of("truncated PNG", Arrays.copyOf(PNG, 20)),
				org.junit.jupiter.params.provider.Arguments.of("tampered IHDR CRC", tamperCrc(PNG)),
				org.junit.jupiter.params.provider.Arguments.of("PNG signature followed by garbage", brokenPng()),
				// genuine-derived WebP corruption matrix
				org.junit.jupiter.params.provider.Arguments.of("truncated WEBP (declared size overruns)", truncatedWebp()),
				org.junit.jupiter.params.provider.Arguments.of("WEBP with appended garbage past the declared size", appendedGarbageWebp()),
				org.junit.jupiter.params.provider.Arguments.of("WEBP RIFF size lying past actual bytes", lengthEstimateWebp()),
				org.junit.jupiter.params.provider.Arguments.of("VP8X-only WEBP (no image bitstream — undecodable)", syntheticVp8xNoBitstream()),
				org.junit.jupiter.params.provider.Arguments.of("WEBP VP8 sync code corrupted", corruptedVp8SyncCode()),
				org.junit.jupiter.params.provider.Arguments.of("WEBP VP8L version bits nonzero", corruptedVp8lVersionBits()),
				org.junit.jupiter.params.provider.Arguments.of("WEBP VP8X canvas above the conservative limit", oversizedCanvasWebp()),
				org.junit.jupiter.params.provider.Arguments.of("WEBP unknown chunk type", unknownChunkWebp()),
				org.junit.jupiter.params.provider.Arguments.of("plain garbage", GARBAGE));
	}

	private static byte[] tamperCrc(byte[] bytes) {
		byte[] copy = java.util.Arrays.copyOf(bytes, bytes.length);
		copy[31] ^= 0x5A; // IHDR CRC occupies bytes 29..32
		return copy;
	}

	private static byte[] brokenPng() {
		byte[] bytes = new byte[40];
		System.arraycopy(PNG, 0, bytes, 0, 33);
		for (int i = 33; i < 40; i++) bytes[i] = 0x21;
		return bytes;
	}

	@ParameterizedTest(name = "malformed image \u201C{0}\u201D is rejected with nothing stored")
	@MethodSource("malformedImages")
	void imageService_rejectsMalformedImages(String label, byte[] bytes) {
		long journal = countFiles("student");

		assertThatThrownBy(() -> images.saveImage("student", 777L,
				new MockMultipartFile("image", "photo.jpg", "application/octet-stream", bytes)))
				.as(label)
				.isInstanceOf(globalLogicEx.class);

		assertThat(countFiles("student")).isEqualTo(journal);
		assertThat(tempRemnantLeft("student")).isFalse();
	}

	// ------------------------------------------------------------------
	// ZIP / attachment surfaces (FILE-04): all four destination conventions
	// ------------------------------------------------------------------

	@ParameterizedTest(name = "ZIP hardening — {0}")
	@EnumSource(ZipDestination.class)
	void zipDestinations_acceptOnlyStructurallyCompleteArchives(ZipDestination destination) {
		String folder = destination.folder;
		long before = countFiles(folder);

		// genuine archive declared as zip → stored
		String storedPath = files.saveFile(folder, destination.typePrefix, 10L, 20L,
				new MockMultipartFile("file", "fixture.zip", "application/zip", genuineZip()));
		writtenFiles.add(Path.of(storedPath));
		assertThat(countFiles(folder)).isEqualTo(before + 1);

		// genuine archive declared as generic octet-stream → stored by CONTENT, not declaration
		String octetStored = files.saveFile(folder, destination.typePrefix, 10L, 21L,
				new MockMultipartFile("file", "fixture.zip", "application/octet-stream", genuineZip()));
		writtenFiles.add(Path.of(octetStored));

		// non-ZIP bytes named .zip declared octet-stream → rejected (FILE-04 closed)
		assertThatThrownBy(() -> files.saveFile(folder, destination.typePrefix, 10L, 22L,
				new MockMultipartFile("file", "fixture.zip", "application/octet-stream", GARBAGE)))
				.isInstanceOf(globalLogicEx.class);

		// archive with real magic but no central directory → rejected (header alone is not proof)
		assertThatThrownBy(() -> files.saveFile(folder, destination.typePrefix, 10L, 23L,
				new MockMultipartFile("file", "fixture.zip", "application/zip", zipWithoutCentralDirectory())))
				.isInstanceOf(globalLogicEx.class);

		// final state: only the two genuine uploads persisted, no .tmp remnants
		assertThat(countFiles(folder)).isEqualTo(before + 2);
		assertThat(tempRemnantLeft(folder)).isFalse();
	}

	enum ZipDestination {
		TASK_SUBMISSION("submissions", "task"),
		PROJECT_SUBMISSION("submissions", "project"),
		TASK_ATTACHMENT("task-attachment", "task"),
		PROJECT_ATTACHMENT("project-attachment", "project");

		final String folder;
		final String typePrefix;

		ZipDestination(String folder, String typePrefix) {
			this.folder = folder;
			this.typePrefix = typePrefix;
		}
	}

	@Test
	@DisplayName("doc-like extensions still behave (regression): real zip named .pdf is rejected")
	void fileStorageRegression_zipUnderDisallowedExtension_rejected() {
		assertThatThrownBy(() -> files.saveFile("task-attachment", "task", 10L, 30L,
				new MockMultipartFile("file", "fixture.pdf", "application/pdf", genuineZip())))
				.isInstanceOf(Exception.class);
		assertThat(tempRemnantLeft("task-attachment")).isFalse();
	}

	/**
	 * Complete central directory yet CORRUPT ENTRY DATA: the central directory
	 * parses fine (the pre-bounded-streaming check alone accepted these), but
	 * the last deflate bytes were flipped so the entry's CRC32 / declared-size
	 * verification no longer matches. Bounded streaming must reject it by
	 * comparing the calculated CRC against the central-directory value.
	 */
	private static byte[] zipWithValidCentralDirectoryButCorruptedEntryData() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		StringBuilder filler = new StringBuilder();
		for (int i = 0; i < 5_000; i++) {
			filler.append("inert eb29 zip integrity filler ").append(i).append(' ');
		}
		byte[] data = filler.toString().getBytes(StandardCharsets.UTF_8);
		java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(data);
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			ZipEntry entry = new ZipEntry("integrity-and-bombness.txt");
			entry.setMethod(ZipEntry.STORED);
			entry.setSize(data.length);
			entry.setCrc(crc.getValue());
			zip.putNextEntry(entry);
			zip.write(data);
			zip.closeEntry();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		byte[] genuine = out.toByteArray();
		int centralDirectoryStart = indexOf(genuine, new byte[]{0x50, 0x4B, 0x01, 0x02});
		byte[] corrupted = genuine.clone();
		for (int i = centralDirectoryStart - 8; i < centralDirectoryStart; i++) {
			corrupted[i] ^= 0x5A; // corrupt stored data, leaving the central directory intact
		}
		return corrupted;
	}

	/**
	 * Size-declaration lie: a small deflated entry whose LFH and central
	 * directory both declare an uncompressed size above the validator's 64 MB
	 * entry cap. The cap check runs on the DECLARED size before any
	 * decompression, so the rejection must stay instant.
	 */
	private static byte[] zipEntryLyingAboveUncompressedCap() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.putNextEntry(new ZipEntry("bombness.txt"));
			zip.write("AB".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		byte[] bytes = out.toByteArray();
		long lied = 64L * 1024 * 1024 + 1;
		for (int offset : new int[]{18, 22}) // LFH compressed / uncompressed size
		{
			putIntLittleEndian(bytes, offset, lied);
		}
		int centralDirectoryStart = indexOf(bytes, new byte[]{0x50, 0x4B, 0x01, 0x02});
		for (int offset : new int[]{20, 24}) // central directory compressed / uncompressed size
		{
			putIntLittleEndian(bytes, centralDirectoryStart + offset, lied);
		}
		return bytes;
	}

	private static void putIntLittleEndian(byte[] bytes, int offset, long value) {
		for (int i = 0; i < 4; i++) {
			bytes[offset + i] = (byte) (value >>> (8 * i));
		}
	}

	private static int indexOf(byte[] haystack, byte[] needle) {
		for (int i = 0; i + needle.length <= haystack.length; i++) {
			boolean match = true;
			for (int j = 0; j < needle.length; j++) {
				if (haystack[i + j] != needle[j]) {
					match = false;
					break;
				}
			}
			if (match) return i;
		}
		throw new IllegalStateException("pattern not found");
	}

	@Test
	@DisplayName("zip entry CRC/size verified via bounded streaming: valid CD + corrupted entry data rejected, nothing stored")
	void zipIntegrity_corruptedEntryData_withValidCentralDirectory_rejected() throws IOException {
		long before = countFiles("task-attachment");
		byte[] damaged = zipWithValidCentralDirectoryButCorruptedEntryData();
		Path candidate = scratch.resolve("damaged.zip");
		Files.write(candidate, damaged);
		try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(candidate.toFile())) {
			assertThat(zip.size()).isEqualTo(1);
		}
		assertThatThrownBy(() -> new com.example.gradproj.EduNest.service.fileSotageService.UploadContentValidator().verifyZipIntegrity(candidate))
				.isInstanceOf(globalLogicEx.class);

		assertThatThrownBy(() -> files.saveFile("task-attachment", "task", 10L, 40L,
				new MockMultipartFile("file", "corrupted-entry.zip", "application/zip",
						damaged)))
				.isInstanceOf(globalLogicEx.class);

		assertThat(countFiles("task-attachment")).as("no stored file may remain").isEqualTo(before);
		assertThat(tempRemnantLeft("task-attachment")).as("no .tmp remnant may remain").isFalse();
	}

	@Test
	@DisplayName("ZIP bomb guard: entry declared above the 64MB uncompressed cap is rejected before decompression")
	void zipIntegrity_entryAboveUncompressedCap_rejected() throws IOException {
		long before = countFiles("task-attachment");
		byte[] bomb = zipEntryLyingAboveUncompressedCap();
		Path candidate = scratch.resolve("bomb.zip");
		Files.write(candidate, bomb);
		assertThatThrownBy(() -> new com.example.gradproj.EduNest.service.fileSotageService.UploadContentValidator().verifyZipIntegrity(candidate))
				.isInstanceOf(globalLogicEx.class).hasMessageContaining("entry exceeds");

		assertThatThrownBy(() -> files.saveFile("task-attachment", "task", 10L, 41L,
				new MockMultipartFile("file", "bomb.zip", "application/zip",
						bomb)))
				.isInstanceOf(globalLogicEx.class);

		assertThat(countFiles("task-attachment")).isEqualTo(before);
		assertThat(tempRemnantLeft("task-attachment")).isFalse();
	}

	// ------------------------------------------------------------------
	// task-submission endpoint: metadata stays untouched on rejection
	// ------------------------------------------------------------------

	@Test
	@DisplayName("task submission endpoint: spoofed .zip rejected with NO metadata and NO file")
	void taskSubmissionEndpoint_spoofedZipRejectedWithoutMetadata() throws Exception {
		long taskId = seedPublishedTaskWithEnrollment();
		long rowsBefore = submissions.count();
		long filesBefore = countFiles("submissions");

		MvcResult result = mockMvc.perform(multipart("/api/v1/task-submission/" + taskId)
						.file(new MockMultipartFile("file", "evil.zip", "application/octet-stream", GARBAGE))
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isBadRequest())
				.andReturn();

		assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.errorCode"))
				.isEqualTo("ERR_BUSINESS_RULE");

		assertThat(submissions.count()).as("no submission row may be created").isEqualTo(rowsBefore);
		assertThat(countFiles("submissions")).as("no stored file may remain").isEqualTo(filesBefore);
		assertThat(tempRemnantLeft("submissions")).as("no .tmp remnant may remain").isFalse();
	}

	@Test
	@DisplayName("task submission endpoint: a genuine .zip still flows end-to-end")
	void taskSubmissionEndpoint_acceptsGenuineZip() throws Exception {
		long taskId = seedPublishedTaskWithEnrollment();
		long rowsBefore = submissions.count();

		MvcResult result = mockMvc.perform(multipart("/api/v1/task-submission/" + taskId)
						.file(new MockMultipartFile("file", "genuine.zip", "application/zip", genuineZip()))
						.header("Authorization", auth.authToken(STUDENT, AuthFixture.STUDENT)))
				.andExpect(status().isOk())
				.andReturn();

		String path = JsonPath.read(result.getResponse().getContentAsString(), "$.apiResponse.submission.uploadedFilePath");
		assertThat(path).isNotBlank().endsWith(".zip");
		writtenFiles.add(Path.of(path));
		assertThat(Path.of(path)).exists();
		assertThat(submissions.count()).isEqualTo(rowsBefore + 1);
	}
}
