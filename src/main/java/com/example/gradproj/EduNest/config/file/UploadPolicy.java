package com.example.gradproj.EduNest.config.file;

import java.util.List;

/**
 * P1-21 / EB-28 — Upload exposure policy (FILE-01, TD-09/G14), per decision D-07.
 *
 * THE single source of truth for which {@code uploads/} folders may be served
 * anonymously. Both serving layers MUST be generated from these constants:
 *  - the prod security chain (ProjectSecurityConfig#prodChain), and
 *  - the Spring MVC resource handlers (WebConfig#addResourceHandlers).
 *
 * Policy:
 *  - PUBLIC_FOLDERS: anonymously fetchable static images (unchanged public URLs —
 *    mentorship covers, student/mentor profile pictures, chatroom images).
 *  - PRIVATE_FOLDERS: NEVER served via /uploads/** — not even to an authenticated
 *    user. Reachable only through ownership-checked typed /files/** endpoints
 *    (FileAccessService). The chain denyAlls them; no resource handler is mapped.
 *  - every other folder (unknown categories, admin, future additions) is
 *    fail-closed: falls into the /uploads/** denyAll bucket until it is added
 *    here deliberately.
 *
 * Folder names are the ACTUAL on-disk names written by ImageStorageService and
 * FileStorageService — this resolves the TD-09/G14 mismatch (the chain used to
 * list phantom names like student-profile/mentor-profile/profile-images and a
 * public catch-all /uploads/**). No folder renames (TD-09 resolution by constants).
 *
 * Supabase note (EB-83 / D-07): when object storage replaces local disks these
 * sets map onto public/private bucket policies — the sets stay canonical.
 */
public final class UploadPolicy {

	/** Anonymous-public upload categories (served by BOTH layers). */
	public static final List<String> PUBLIC_FOLDERS = List.of(
			"mentorship-cover-images",
			"student",
			"mentor",
			"chatroom"
	);

	/** Ownership-checked-only categories: denied at every other serving layer. */
	public static final List<String> PRIVATE_FOLDERS = List.of(
			"submissions",
			"task-attachment",
			"project-attachment"
	);

	/** Spring ACL string patterns for the anonymous-public allowlist. */
	public static final List<String> PUBLIC_UPLOAD_PATTERNS = PUBLIC_FOLDERS.stream()
			.map(folder -> "/uploads/" + folder + "/**")
			.toList();

	private UploadPolicy() {
	}
}
