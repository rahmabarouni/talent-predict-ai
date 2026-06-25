-- ============================================================================
-- TalentPredict Database Migration: Account → User
-- ============================================================================
-- IMPORTANT: Execute this script in pgAdmin or psql FIRST before starting the application
-- Database: talentpredict (or your database name)
-- ============================================================================

-- Step 1: Rename main table from 'accounts' to 'users'
ALTER TABLE IF EXISTS accounts RENAME TO users;

-- Step 2: Rename all 'account_id' foreign key columns to 'user_id' in related tables
ALTER TABLE IF EXISTS profiles RENAME COLUMN account_id TO user_id;
ALTER TABLE IF EXISTS password_reset_tokens RENAME COLUMN account_id TO user_id;
ALTER TABLE IF EXISTS skills RENAME COLUMN account_id TO user_id;
ALTER TABLE IF EXISTS predictions RENAME COLUMN account_id TO user_id;
ALTER TABLE IF EXISTS formations RENAME COLUMN account_id TO user_id;
ALTER TABLE IF EXISTS recommendations RENAME COLUMN account_id TO user_id;
ALTER TABLE IF EXISTS tests_personnalite RENAME COLUMN account_id TO user_id;
ALTER TABLE IF EXISTS competence_account RENAME COLUMN account_id TO user_id;

-- Step 3: Rename junction table (optional - entity class name stays CompetenceAccount)
ALTER TABLE IF EXISTS competence_account RENAME TO competence_user;

-- ============================================================================
-- VERIFICATION QUERIES (run after migration)
-- ============================================================================

-- 1. Verify 'users' table exists
SELECT tablename FROM pg_tables WHERE tablename = 'users';

-- 2. List all tables with 'user_id' column
SELECT table_name, column_name 
FROM information_schema.columns 
WHERE column_name = 'user_id' 
ORDER BY table_name;
-- Expected: profiles, password_reset_tokens, skills, predictions, 
--           formations, recommendations, tests_personnalite, competence_user

-- 3. Check for remaining 'account_id' columns (should return 0 rows)
SELECT table_name, column_name 
FROM information_schema.columns 
WHERE column_name = 'account_id' 
ORDER BY table_name;

-- 4. Verify all tables exist
SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- ============================================================================
-- NOTES
-- ============================================================================
-- - Ignore "relation does not exist" warnings - these are expected if tables
--   were already renamed in a previous run
-- - Foreign key constraints are automatically updated when renaming columns
-- - Existing data is preserved during rename operations
-- - After successful migration, start the application: mvn spring-boot:run
-- ============================================================================
