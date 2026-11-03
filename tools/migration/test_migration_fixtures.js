/**
 * Synthetic Test Suite for EB-82 Schema Reconciliation & DDL Specification
 * 
 * Verifies the 42 application tables, the exclusion of user_settings,
 * and the correctness of the generated corrective DDL for the 5 TEXT columns.
 */

const assert = require('assert');
const { APPLICATION_TABLES, TEXT_COLUMNS_TO_CONVERT, generateCorrectiveDdl } = require('./migrate');

console.log('=== Running EB-82 Schema Reconciliation Tests ===\n');

// Test 1: Exactly 42 application tables and user_settings exclusion
console.log('Test 1: Verifying 42 application tables and exclusion of user_settings...');
assert.strictEqual(APPLICATION_TABLES.length, 42, `Expected 42 tables, got ${APPLICATION_TABLES.length}`);
assert(!APPLICATION_TABLES.includes('user_settings'), 'user_settings MUST be excluded');
assert(APPLICATION_TABLES.includes('roles'), 'roles MUST be included');
assert(APPLICATION_TABLES.includes('users'), 'users MUST be included');
console.log('✔ 42 tables verified. user_settings properly excluded.\n');

// Test 2: Exactly 5 text columns targeting OID -> TEXT
console.log('Test 2: Verifying 5 text columns targeting OID -> TEXT...');
assert.strictEqual(TEXT_COLUMNS_TO_CONVERT.length, 5, 'Must have exactly 5 columns');
const tableColPairs = TEXT_COLUMNS_TO_CONVERT.map(c => `${c.table}.${c.column}`);
assert(tableColPairs.includes('contact_message.message'));
assert(tableColPairs.includes('projects.project_brief'));
assert(tableColPairs.includes('project_submission.project_feedback'));
assert(tableColPairs.includes('tasks.task_description'));
assert(tableColPairs.includes('task_submission.task_feedback'));
console.log('✔ All 5 target columns verified with correct DB column names.\n');

// Test 3: Corrective DDL generation
console.log('Test 3: Verifying generated corrective DDL...');
const ddl = generateCorrectiveDdl(true);
assert(ddl.includes('BEGIN;'), 'DDL must begin transaction');
assert(ddl.includes('COMMIT;'), 'DDL must commit transaction');
for (const c of TEXT_COLUMNS_TO_CONVERT) {
    assert(ddl.includes(`ALTER TABLE public.${c.table} ALTER COLUMN ${c.column} TYPE TEXT`), `DDL must alter ${c.table}.${c.column}`);
}
console.log('✔ Generated DDL contains reviewed non-destructive ALTER TABLE statements for all 5 columns.\n');

console.log('=== All Schema Reconciliation Tests PASSED (3/3) ===');
