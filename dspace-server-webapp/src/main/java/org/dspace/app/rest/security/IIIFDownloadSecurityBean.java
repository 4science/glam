/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.security;

import java.sql.SQLException;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.dspace.app.rest.utils.ContextUtil;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.Item;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component(value = "iiifdownloadSecurity")
public class IIIFDownloadSecurityBean {

    private static Logger logger = LoggerFactory.getLogger(IIIFDownloadSecurityBean.class);

    @Autowired
    private BitstreamService bitstreamService;
    @Autowired
    private AuthorizeService authorizeService;

    public boolean isDownloadable(HttpServletRequest request, UUID bitstreamUUID) throws SQLException {
        Context context = ContextUtil.obtainContext(request);;
        Bitstream bitstream = bitstreamService.find(context, bitstreamUUID);
        if (bitstream == null) {
            return true;
        }
        String value = bitstreamService.getMetadataFirstValue(bitstream,"bitstream", "viewer", "provider", Item.ANY);
        if (StringUtils.equals("nodownload", value) && !authorizeService.isAdmin(context)) {
            return false;
        }
        return true;
    }

}
