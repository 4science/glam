--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

-------------------------------------------------------
-- Updates column of the droid checker tables
-------------------------------------------------------
ALTER TABLE IF EXISTS droid_check_result ALTER COLUMN type RENAME TO method;
ALTER TABLE IF EXISTS droid_check_history ALTER COLUMN type RENAME TO method;