--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

-------------------------------------------------------
-- Create the droid checker tables
-------------------------------------------------------
ALTER TABLE droid_check_result
    ADD COLUMN IF NOT EXISTS process_date TIMESTAMP NOT NULL;

-- Creates index for the new queue query
CREATE INDEX IF NOT EXISTS dcr_bits_queue on droid_check_result(bitstream_id, process_date);