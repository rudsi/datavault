-- Migration: Fix file_metadata primary key to composite (file_id, chunk_id)
--
-- Required when upgrading from a database that was created before the
-- @IdClass(FileMetadataId.class) annotation was added to FileMetadata.
-- Hibernate ddl-auto=update does NOT alter existing primary key constraints.
--
-- Run this manually if you see:
--   "duplicate key value violates unique constraint file_metadata_pkey"
-- when uploading multi-chunk files.

-- Drop the old single-column primary key
ALTER TABLE file_metadata DROP CONSTRAINT IF EXISTS file_metadata_pkey;

-- Add the composite primary key
ALTER TABLE file_metadata ADD PRIMARY KEY (file_id, chunk_id);
