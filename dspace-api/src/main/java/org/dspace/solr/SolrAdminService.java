/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.solr;

import java.io.IOException;
import java.util.List;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;

/**
 *
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 **/
public interface SolrAdminService {
    List<String> listShards() throws SolrServerException, IOException;
    SolrClient createShard(String shardName) throws SolrServerException, IOException;
}
