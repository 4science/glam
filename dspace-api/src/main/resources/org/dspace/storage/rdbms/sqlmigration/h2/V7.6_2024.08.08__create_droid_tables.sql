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
-- list of the possible results as determined
-- by the system or an administrator
CREATE TABLE droid_check_status
(
    status_code VARCHAR(20) PRIMARY KEY,
    result_description VARCHAR(256)
);

ALTER TABLE IF EXISTS most_recent_checksum
    ALTER COLUMN bitstream_id SET NOT NULL;
ALTER TABLE IF EXISTS most_recent_checksum
    ADD CONSTRAINT most_recent_checksum_pk PRIMARY KEY (bitstream_id);

-- This table has a one-to-one relationship
-- with the most_recent_checksum table. A row will be inserted
-- every time a row is inserted into the most_recent_checksum table, and
-- that row will be updated every time the droid validation will be re-valuated
CREATE SEQUENCE droid_check_result_id_seq;
CREATE TABLE droid_check_result
(
    check_id BIGINT PRIMARY KEY,
    bitstream_id UUID REFERENCES most_recent_checksum(bitstream_id),
    status VARCHAR(20) REFERENCES droid_check_status(status_code),
    uri VARCHAR(256),
    path VARCHAR(256),
    filename VARCHAR(256),
    file_size NUMERIC,
    type VARCHAR(100),
    file_extension VARCHAR(10),
    last_modified_date TIMESTAMP,
    extension_mismatch BOOLEAN NOT NULL,
    puid VARCHAR(100),
    mime_type VARCHAR(30),
    file_format VARCHAR(100),
    format_version VARCHAR(100)
);


-- A row will be inserted into this table every
-- time a droid validation is re-evaluated.
CREATE SEQUENCE droid_check_history_id_seq;
CREATE TABLE droid_check_history
(
    id BIGINT PRIMARY KEY,
    check_id BIGINT REFERENCES checksum_history(check_id),
    status VARCHAR(20) REFERENCES droid_check_status(status_code),
    uri VARCHAR(256),
    path VARCHAR(256),
    filename VARCHAR(256),
    file_size NUMERIC,
    type VARCHAR(100),
    file_extension VARCHAR(10),
    last_modified_date TIMESTAMP,
    extension_mismatch BOOLEAN NOT NULL,
    puid VARCHAR(100),
    mime_type VARCHAR(30),
    file_format VARCHAR(100),
    format_version VARCHAR(100)
);

-- this will insert into the result code
-- the initial results
insert into droid_check_status
values
(
    'NOT_PROCESSED',
    'The droid validation was not applied'
);

insert into droid_check_status
values
(
    'NOT_FOUND',
    'The bitstream is not complaint with any droid registered format'
);

insert into droid_check_status
values
(
    'PROCESSED',
    'The bitstream has been processed with the droid validation'
);


insert into droid_check_status
values
(
    'VALIDATED',
    'Bitstream has been validated correctly with droid'
);

insert into droid_check_status
values
(
    'PARTIAL_VALIDATION',
    'Bitstream validation partially performed due to a lack of information'
);

insert into droid_check_status
values
(
    'VALIDATION_ERROR',
    'Some errors occurred during the validation of the bitstream'
);

-- Adds droid_status column to the checksum related tables.
ALTER TABLE most_recent_checksum ADD COLUMN droid_status VARCHAR(20) REFERENCES droid_check_status(status_code);
ALTER TABLE checksum_history ADD COLUMN droid_status VARCHAR(20) REFERENCES droid_check_status(status_code);

-- Creates index for the new droid_status column
CREATE INDEX dcr_status_fk_idx ON droid_check_result( status );
CREATE INDEX dch_status_fk_idx ON droid_check_history( status );
CREATE INDEX mrc_droid_status_fk_idx ON most_recent_checksum( droid_status );
CREATE INDEX ch_droid_status_fk_idx ON checksum_history( droid_status );