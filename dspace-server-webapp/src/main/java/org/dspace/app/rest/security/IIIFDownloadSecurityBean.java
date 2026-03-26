/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.security;

import static org.dspace.content.Item.ANY;

import java.sql.SQLException;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component(value = "iiifdownloadSecurity")
public class IIIFDownloadSecurityBean {

    private static Logger log = LoggerFactory.getLogger(IIIFDownloadSecurityBean.class);

    @Autowired
    private BitstreamService bitstreamService;
    @Autowired
    private AuthorizeService authorizeService;

    /**
     * Determine whether the given bitstream can be downloaded.
     *
     * @param request       current HTTP request, used to retrieve the DSpace context
     * @param bitstreamUUID UUID of the bitstream that should be checked
     * @return true unless the bitstream is marked "nodownload" and the caller is not an admin
     */
    public boolean isDownloadable(HttpServletRequest request, UUID bitstreamUUID) throws SQLException {
        Context context = ContextUtil.obtainContext(request);
        Bitstream bitstream = bitstreamService.find(context, bitstreamUUID);
        if (bitstream == null) {
            log.warn("Bitstream with uuid {} not found", bitstreamUUID);
            return true;
        }
        return !hasNodownloadValue(bitstream) || authorizeService.isAdmin(context);
    }

    private boolean hasNodownloadValue(Bitstream bitstream) {
        return bitstreamService.getMetadata(bitstream,"bitstream", "viewer", "provider", ANY)
                               .stream()
                               .filter(mv -> mv.getValue().equals("nodownload"))
                               .findFirst()
                               .isPresent();
    }

}
