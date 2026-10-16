# Enrollment, Pricing & Commission

Verified mechanics of joining a mentorship and where money-like values are computed/persisted.

## 1. Join flow (`mentorShipServiceI#joinMentorship`, lines 387–441)

1. Caller must be a STUDENT (`@PreAuthorize("hasRole('STUDENT')")` on controller; email from `SecurityContextHolder`).
2. Mentorship must exist and be **ACTIVE** (DRAFT and COMPLETED rejected, line 400).
3. Not already enrolled (`enrollmentRepository.existsByMentorShip_IdAndStudent…`, line 404).
4. Compute pricing snapshot (lines 411–420):
   - `priceAfterDiscount = price − price * discountPercentage / 100`
   - `platformProfit = CommissionService.calculatePlatformCut(priceAfterDiscount) = priceAfterDiscount * latestRate / 100`
   - mentor earning = `priceAfterDiscount − platformProfit` — **displayed in a notification string only, not persisted** (line 429).
5. Persist: `Enrollment{student, mentorShip, price, platformProfit, joinedAt}` **and** a zero `TotalPoints` row (lines 422–426).
6. Notify mentor (`sendToUserByEmail("New Student Enrolled")`, line 431).

**No state machine on Enrollment:** a row *is* the state; there is no payment status, no cancel/leave/transfer, no reprice. `joinedAt` is the only lifecycle timestamp. No payment gateway exists — enrollment is a free claim (open question in [MDS.md](MDS.md)).

## 2. Commission model (`service/mentorShip/CommissionService`)

- **Rate source:** latest row of `entity/mentorship/Commission{rate}` ordered by `createdAt` (`findTopByOrderByCreatedAtDesc`). **Append-only history**; `setCommissionRate` (ADMIN) creates a new row; range check 0–100.
- **No default:** on a fresh deployment the `commission` table is empty → `calculatePlatformCut` throws `globalLogicEx("Commission rate not set")` → **enrollment is impossible until an admin sets a rate** (MSH-05).
- Money math uses `Double` (no currency field; rounding only happens in a few report queries via SQL `ROUND(...,2)`).

## 3. Where revenue numbers come from (reporting)

| View | Formula | Source |
|---|---|---|
| Mentor dashboard `totalRevenue` | `SUM(price − platformProfit)` | `EnrollmentRepository#getTotalRevenueByMentorId` (net of commission) |
| Mentor monthly sales chart | `SUM(price − platformProfit)` grouped by year/month | `getMonthlyRevenueForMentor` |
| Admin dashboard `totalRevenue` | `SUM(price)` (**gross**) | `MentorShipRepository#getAdminDashboardCards` (native, lines 74–82) |
| Admin sales chart / payments pages | `SUM(price)` (gross) + per-row `platformProfit` | `EnrollmentRepository#getMonthlyRevenueForAdmin`, `findAllEnrollmentPayments` |

⚠️ Metric inconsistency: admin sees gross, mentor net; the platform's own take is derivable only from per-row `platformProfit`. Tracked in [../ISSUES.md](../ISSUES.md) (DASH group).

## 4. Data-integrity notes

- `Enrollment` has a plain (non-unique) index `idx_enrollments_student_mentorship`; dedupe relies on a service `exists`-check. A concurrent double-join fails **only** because both transactions insert the same-unique `TotalPoints` (uk_student_mentorship) row and the second rolls back — incidental, not designed (MSH-04). Recommend a DB unique constraint on `(student_id, mentorship_id)`.
- Review uniqueness similarly service-enforced only (`MentorShipReviews` — no DB unique on (mentorship_id, student_id)).
- Pricing snapshot means later discount/admin-rate changes never retro-affect existing enrollments (intended denormalization).

## 5. Test recommendations

1. Join with no commission set → clean business error (not 500).
2. Concurrent join (2 threads) → one success, one clean "already enrolled" (after adding unique constraint); today: one success, one 500.
3. Snapshot math: price=100, discount=20%, rate=10% → platformProfit=8, mentor earns 72 (assert).
4. Reporting: mentor completion flow with price/profit totals survives mentorship COMPLETED transition.
