/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.dao;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.core.Context;

/**
 * Database Access Object interface class for the Bitstream object.
 * The implementation of this class is responsible for all database calls for the Bitstream object and is autowired
 * by spring
 * This class should only be accessed from a single service and should never be exposed outside of the API
 *
 * @author kevinvandevelde at atmire.com
 */
public interface BitstreamDAO extends DSpaceObjectLegacySupportDAO<Bitstream> {

    public Iterator<Bitstream> findAll(Context context, int limit, int offset) throws SQLException;

    public List<Bitstream> findDeletedBitstreams(Context context, int limit, int offset) throws SQLException;

    public List<Bitstream> findDuplicateInternalIdentifier(Context context, Bitstream bitstream) throws SQLException;

    public List<Bitstream> findBitstreamsWithNoRecentChecksum(Context context) throws SQLException;

    public Iterator<Bitstream> findByCommunity(Context context, Community community) throws SQLException;

    public Iterator<Bitstream> findByCollection(Context context, Collection collection) throws SQLException;

    public Iterator<Bitstream> findByItem(Context context, Item item) throws SQLException;

    public Iterator<Bitstream> findByStoreNumber(Context context, Integer storeNumber) throws SQLException;

    public Long countByStoreNumber(Context context, Integer storeNumber) throws SQLException;

    public int countRows(Context context) throws SQLException;

    public int countDeleted(Context context) throws SQLException;

    public int countWithNoPolicy(Context context) throws SQLException;

    public List<Bitstream> getNotReferencedBitstreams(Context context) throws SQLException;

    public Iterator<Bitstream> findShowableByItem(Context context, UUID itemId, String bundleName) throws SQLException;

    /**
     * Find bitstreams with a specific metadata value within a given bundle of a specific item.
     * This method searches for bitstreams that have a specific metadata field value
     * and are contained within a bundle with the specified name, belonging to a specific item.
     *
     * @param context       The relevant DSpace Context
     * @param itemId        The UUID of the item to search within
     * @param bundleName    The name of the bundle to search within (e.g., "PDFA")
     * @param metadataField The metadata field in format "schema.element.qualifier" (e.g., "bitstream.master")
     * @param metadataValue The value to search for (e.g., a UUID string)
     * @return Iterator of matching Bitstream objects
     * @throws SQLException if database error occurs
     */
    public Iterator<Bitstream> findByMetadataValueInBundle(Context context, UUID itemId, String bundleName,
                                                           String metadataField, String metadataValue)
        throws SQLException;
}
