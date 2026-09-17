-- =============================================================================
-- V7: Add transport_mode to user_current_position and all history tables
-- =============================================================================
-- Coarse mode-of-transport reported by client-side activity recognition
-- (STILL / WALKING / RUNNING / ON_BICYCLE / IN_VEHICLE / UNKNOWN), stored
-- alongside each position. Nullable — older clients and rows predating this
-- migration have no value.
-- =============================================================================

ALTER TABLE user_current_position        ADD COLUMN transport_mode VARCHAR(20);
ALTER TABLE user_7_day_history_position   ADD COLUMN transport_mode VARCHAR(20);
ALTER TABLE user_31_day_history_position  ADD COLUMN transport_mode VARCHAR(20);
ALTER TABLE user_61_day_history_position  ADD COLUMN transport_mode VARCHAR(20);
ALTER TABLE user_91_day_history_position  ADD COLUMN transport_mode VARCHAR(20);
ALTER TABLE user_180_day_history_position ADD COLUMN transport_mode VARCHAR(20);
ALTER TABLE user_366_day_history_position ADD COLUMN transport_mode VARCHAR(20);

-- Re-create the fan-out trigger function to carry transport_mode into every history table.
CREATE OR REPLACE FUNCTION fn_propagate_position_to_history()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO user_7_day_history_position   (user_id, location, recorded_at, transport_mode) VALUES (NEW.user_id, NEW.location, NEW.last_update, NEW.transport_mode);
    INSERT INTO user_31_day_history_position  (user_id, location, recorded_at, transport_mode) VALUES (NEW.user_id, NEW.location, NEW.last_update, NEW.transport_mode);
    INSERT INTO user_61_day_history_position  (user_id, location, recorded_at, transport_mode) VALUES (NEW.user_id, NEW.location, NEW.last_update, NEW.transport_mode);
    INSERT INTO user_91_day_history_position  (user_id, location, recorded_at, transport_mode) VALUES (NEW.user_id, NEW.location, NEW.last_update, NEW.transport_mode);
    INSERT INTO user_180_day_history_position (user_id, location, recorded_at, transport_mode) VALUES (NEW.user_id, NEW.location, NEW.last_update, NEW.transport_mode);
    INSERT INTO user_366_day_history_position (user_id, location, recorded_at, transport_mode) VALUES (NEW.user_id, NEW.location, NEW.last_update, NEW.transport_mode);
    RETURN NEW;
END;
$$;
