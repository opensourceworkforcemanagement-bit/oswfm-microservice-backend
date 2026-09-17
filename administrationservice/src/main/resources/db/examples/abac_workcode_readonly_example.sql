-- ============================================================================
-- ABAC Example: Allow access to Work Code Management page, deny creation
-- ============================================================================
-- Scenario: Users in the "Workforce Viewers" group can VIEW the workforce codes
-- page and READ/EDIT/DELETE existing codes, but CANNOT CREATE new workforce codes.
--
-- This demonstrates the decoupled subject attribute model:
--   - Subject attributes are defined independently (not tied to a user)
--   - Groups bundle attributes together (e.g., "Workforce Viewers" group)
--   - Users get attributes via group membership or direct assignment
--   - Policies match on attribute values regardless of how they were assigned
--
-- This is achieved with two policies:
--   1. A PERMIT policy allowing read/update/delete on the workcode-management resource
--   2. A DENY policy (higher priority) blocking the "create" action on the same resource
-- ============================================================================


-- ============================================================================
-- 1. LOOKUP TABLE SEED DATA
-- ============================================================================

-- Resource types
INSERT INTO resource_types (resource_type_id, type_name, description)
VALUES
  (1, 'page',     'A frontend page or view'),
  (2, 'api',      'A backend API endpoint'),
  (3, 'document', 'A document or file resource');

-- Attribute categories
INSERT INTO attribute_categories (attribute_category_id, category_name, description)
VALUES
  (1, 'subject',     'Attributes describing the user making the request'),
  (2, 'resource',    'Attributes describing the object being accessed'),
  (3, 'environment', 'Contextual attributes like time, IP, location'),
  (4, 'action',      'Attributes describing the operation being performed');

-- Policy types
INSERT INTO policy_types (policy_type_id, type_name, description)
VALUES
  (1, 'permit', 'Grants access when conditions are met'),
  (2, 'deny',   'Denies access when conditions are met');

-- Obligation types
INSERT INTO obligation_types (obligation_type_id, type_name, description)
VALUES
  (1, 'log_access',   'Log the access attempt'),
  (2, 'notify_admin', 'Send a notification to an administrator'),
  (3, 'encrypt_data', 'Ensure data is encrypted before transmission');

-- Data types
INSERT INTO data_types (data_type_id, type_name, description)
VALUES
  (1, 'string',   'Text value'),
  (2, 'number',   'Numeric value'),
  (3, 'boolean',  'True or false'),
  (4, 'datetime', 'Date and time value'),
  (5, 'list',     'A list of values');


-- ============================================================================
-- 2. ACTIONS - define the operations relevant to Work Code Management
-- ============================================================================
INSERT INTO actions (action_id, action_name, description)
VALUES
  (1, 'read',   'View/list resources'),
  (2, 'create', 'Create a new resource'),
  (3, 'update', 'Edit an existing resource'),
  (4, 'delete', 'Delete a resource');


-- ============================================================================
-- 3. RESOURCE - register the Work Code Management page as a protected resource
-- ============================================================================
INSERT INTO resources (resource_id, resource_type_id, resource_name, resource_uri, owner_id, is_active)
VALUES (1, 1, 'Work Code Management', '/timesheetmanagement/work-codes', NULL, TRUE);
--         ^  resource_type_id = 1 (page)


-- ============================================================================
-- 4. ATTRIBUTE DEFINITIONS
-- ============================================================================
INSERT INTO attribute_definitions (attribute_id, attribute_name, attribute_category_id, data_type_id, description, is_required)
VALUES
  (1, 'role',       1, 1, 'The role assigned to the user',                        FALSE),
  --                ^  ^   attribute_category_id=1 (subject), data_type_id=1 (string)
  (2, 'department', 1, 1, 'The department the user belongs to',                   FALSE),
  --                ^  ^   attribute_category_id=1 (subject), data_type_id=1 (string)
  (3, 'module',     2, 1, 'The application module this resource belongs to',      FALSE);
  --                ^  ^   attribute_category_id=2 (resource), data_type_id=1 (string)


-- ============================================================================
-- 5. SUBJECT ATTRIBUTES - define reusable attributes (not tied to any user)
-- ============================================================================
INSERT INTO subject_attributes (subject_attr_id, attribute_id, attribute_value)
VALUES
  (1, 1, 'workforce_viewer'),   -- role = workforce_viewer
  (2, 2, 'HR');                 -- department = HR


-- ============================================================================
-- 5a. USER GROUP - create a group to bundle these attributes
-- ============================================================================

-- User group type required by user_groups.user_group_type_id
INSERT INTO user_group_type (user_group_type_id, type_name, description)
VALUES (1, 'standard', 'Standard user group');

INSERT INTO user_groups (group_id, group_name, user_group_type_id, description)
VALUES (1, 'Workforce Viewers', 1, 'Users who can view, edit, and delete but not create workforce codes');


-- ============================================================================
-- 5b. GROUP ATTRIBUTE ASSIGNMENTS - attach attributes to the group
-- ============================================================================
INSERT INTO group_subject_attributes (id, group_id, subject_attr_id)
VALUES
  (1, 1, 1),   -- group "Workforce Viewers" gets role = workforce_viewer
  (2, 1, 2);   -- group "Workforce Viewers" gets department = HR


-- ============================================================================
-- 5c. USER GROUP MEMBERSHIPS - assign users to the group
-- ============================================================================
-- User 1 (jane.doe), User 2 (john.smith), User 3 (bob.jones) all get
-- the workforce_viewer role and HR department via group membership
INSERT INTO user_group_memberships (membership_id, user_id, group_id)
VALUES
  (1, 1, 1),   -- jane.doe  -> Workforce Viewers
  (2, 2, 1),   -- john.smith -> Workforce Viewers
  (3, 3, 1);   -- bob.jones  -> Workforce Viewers

-- Optionally, assign an attribute directly to a specific user (override/addition)
-- INSERT INTO user_subject_attributes (id, user_id, subject_attr_id) VALUES (1, 1, 1);


-- ============================================================================
-- 6. RESOURCE ATTRIBUTES - assign attributes to the resource
-- ============================================================================
INSERT INTO resource_attributes (resource_attr_id, resource_id, attribute_id, attribute_value)
VALUES
  (1, 1, 3, 'timesheet');  -- module = timesheet


-- ============================================================================
-- 7. POLICIES
-- ============================================================================

-- Policy 1: PERMIT read, update, delete on Work Code Management
--           for users with role = "workforce_viewer"
INSERT INTO policies (policy_id, policy_name, description, policy_type_id, priority, is_active, created_by)
VALUES (
  1,
  'Allow Work Code Page Access',
  'Permits users with the workforce_viewer role to view, edit, and delete workforce codes',
  1,    -- policy_type_id = 1 (permit)
  10,
  TRUE,
  NULL
);

-- Policy 2: DENY create on Work Code Management
--           for users with role = "workforce_viewer"
--           Higher priority ensures this overrides the permit policy
INSERT INTO policies (policy_id, policy_name, description, policy_type_id, priority, is_active, created_by)
VALUES (
  2,
  'Deny Work Code Creation',
  'Denies workforce_viewer users from creating new workforce codes',
  2,    -- policy_type_id = 2 (deny)
  20,
  TRUE,
  NULL
);


-- ============================================================================
-- 8. POLICY RULES - conditions that must match for each policy
-- ============================================================================

-- Rule for Policy 1: subject role must equal "workforce_viewer"
INSERT INTO policy_rules (rule_id, policy_id, rule_name, attribute_id, operator, comparison_value, logical_operator, rule_order)
VALUES (1, 1, 'Subject role is workforce_viewer', 1, 'equals', 'workforce_viewer', 'AND', 1);

-- Rule for Policy 2: subject role must equal "workforce_viewer"
INSERT INTO policy_rules (rule_id, policy_id, rule_name, attribute_id, operator, comparison_value, logical_operator, rule_order)
VALUES (2, 2, 'Subject role is workforce_viewer', 1, 'equals', 'workforce_viewer', 'AND', 1);


-- ============================================================================
-- 9. POLICY RESOURCE TARGETS - which resources each policy applies to
-- ============================================================================

-- Both policies target the Work Code Management page
INSERT INTO policy_resource_targets (target_id, policy_id, resource_id) VALUES (1, 1, 1);
INSERT INTO policy_resource_targets (target_id, policy_id, resource_id) VALUES (2, 2, 1);


-- ============================================================================
-- 10. POLICY ACTION TARGETS - which actions each policy governs
-- ============================================================================

-- Policy 1 (PERMIT): allows read, update, delete
INSERT INTO policy_action_targets (target_id, policy_id, action_id) VALUES (1, 1, 1);  -- read
INSERT INTO policy_action_targets (target_id, policy_id, action_id) VALUES (2, 1, 3);  -- update
INSERT INTO policy_action_targets (target_id, policy_id, action_id) VALUES (3, 1, 4);  -- delete

-- Policy 2 (DENY): blocks create
INSERT INTO policy_action_targets (target_id, policy_id, action_id) VALUES (4, 2, 2);  -- create


-- ============================================================================
-- 11. POLICY OBLIGATIONS - actions triggered on policy evaluation
-- ============================================================================

-- Log when create is denied
INSERT INTO policy_obligations (obligation_id, policy_id, obligation_type_id, obligation_params, is_mandatory)
VALUES (
  1,
  2,
  1,    -- obligation_type_id = 1 (log_access)
  '{"message": "Work code creation denied for workforce_viewer user", "severity": "info"}',
  TRUE
);


-- ============================================================================
-- EVALUATION SUMMARY
-- ============================================================================
-- When any user in the "Workforce Viewers" group accesses:
--
--   Resource: Work Code Management (resource_id=1)
--
--   The user's attributes are resolved from group membership:
--     group "Workforce Viewers" -> role=workforce_viewer, department=HR
--
--   Action: read   -> Policy 1 matches (PERMIT, priority 10) -> ALLOWED
--   Action: update -> Policy 1 matches (PERMIT, priority 10) -> ALLOWED
--   Action: delete -> Policy 1 matches (PERMIT, priority 10) -> ALLOWED
--   Action: create -> Policy 2 matches (DENY,   priority 20) -> DENIED
--
-- Result: Users can view the page, edit and delete existing codes,
--         but the "Add Work Code" button should be hidden or disabled.
--
-- To add a new user, just add one row to user_group_memberships:
--   INSERT INTO user_group_memberships (user_id, group_id) VALUES (4, 1);
-- No need to duplicate subject_attributes or policies.
-- ============================================================================
