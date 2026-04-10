--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

-------------------------------------------------------
-- alter droid_check_result and droid_check_history tables to increase the size of mime_type column to 100 characters
-------------------------------------------------------

ALTER TABLE IF EXISTS droid_check_result
    ALTER COLUMN mime_type SET DATA TYPE VARCHAR(256);

ALTER TABLE IF EXISTS droid_check_history
    ALTER COLUMN mime_type SET DATA TYPE VARCHAR(256);
