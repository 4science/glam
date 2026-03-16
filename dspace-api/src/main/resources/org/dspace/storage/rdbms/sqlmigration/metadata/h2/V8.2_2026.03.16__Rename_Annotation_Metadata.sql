--
-- The contents of this file are subject to the license and copyright
-- detailed in the LICENSE and NOTICE files at the root of the source
-- tree and available online at
--
-- http://www.dspace.org/license/
--

-- ===============================================================
-- WARNING WARNING WARNING WARNING WARNING WARNING WARNING WARNING
--
-- DO NOT MANUALLY RUN THIS DATABASE MIGRATION. IT WILL BE EXECUTED
-- AUTOMATICALLY (IF NEEDED) BY "FLYWAY" WHEN YOU STARTUP DSPACE.
-- http://flywaydb.org/
-- ===============================================================

-------------------------------------------------------------
-- Migration script to rename 'dc.relation.annotation' metadata to 'dc.relation.webannotation'

UPDATE metadatafieldregistry
SET qualifier = 'webannotation'
WHERE metadata_field_id IN (
    SELECT mfr.metadata_field_id
    FROM metadatafieldregistry mfr
    LEFT JOIN metadataschemaregistry msr ON mfr.metadata_schema_id = msr.metadata_schema_id
    WHERE msr.short_id = 'dc'
      AND mfr.element = 'relation'
      AND mfr.qualifier = 'annotation'
);
