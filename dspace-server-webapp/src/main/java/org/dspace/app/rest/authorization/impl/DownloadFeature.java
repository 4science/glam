/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.authorization.impl;

import static org.dspace.content.Item.ANY;

import java.sql.SQLException;

import org.dspace.app.rest.authorization.AuthorizationFeature;
import org.dspace.app.rest.authorization.AuthorizationFeatureDocumentation;
import org.dspace.app.rest.authorization.AuthorizeServiceRestUtil;
import org.dspace.app.rest.model.BaseObjectRest;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.security.BitstreamCrisSecurityService;
import org.dspace.app.rest.security.DSpaceRestPermission;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
import org.dspace.content.DSpaceObject;
import org.dspace.content.service.BitstreamService;
import org.dspace.core.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * The download bitstream feature. It can be used to verify if a bitstream can be downloaded.
 * Authorization is granted if the current user has READ permissions on the given bitstream.
 * 
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.it)
 */
@Component
@AuthorizationFeatureDocumentation(name = DownloadFeature.NAME,
        description = "It can be used to verify if the user can download a bitstream")
public class DownloadFeature implements AuthorizationFeature {

    public final static String NAME = "canDownload";

    private static final Logger log = LoggerFactory.getLogger(DownloadFeature.class);

    @Autowired
    private Utils utils;
    @Autowired
    private AuthorizeService authorizeService;
    @Autowired
    private BitstreamService bitstreamService;
    @Autowired
    private AuthorizeServiceRestUtil authorizeServiceRestUtil;
    @Autowired
    private BitstreamCrisSecurityService bitstreamCrisSecurityService;

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isAuthorized(Context context, BaseObjectRest object) throws SQLException {
        if (object instanceof BitstreamRest) {
            if (authorizeServiceRestUtil.authorizeActionBoolean(context, object, DSpaceRestPermission.READ)) {
                DSpaceObject dso = (DSpaceObject) utils.getDSpaceAPIObjectFromRest(context, object);
                if (dso instanceof Bitstream  bitstream && isNoDownload(context, bitstream)) {
                    return false;
                }
                return true;
            }
        }
        try {
            DSpaceObject dSpaceObject = (DSpaceObject) utils.getDSpaceAPIObjectFromRest(context, object);
            if (dSpaceObject == null) {
                return false;
            }

            if (dSpaceObject instanceof Bitstream bitstream && bitstreamCrisSecurityService
                                .isBitstreamAccessAllowedByCrisSecurity(context, context.getCurrentUser(), bitstream)) {
                return !isNoDownload(context, bitstream);
            }
        } catch (Exception e) {
            log.warn(
                    "We got an exception during the cris security evaluation, safe fallback " +
                    "ignoring extra grant given by cris",
                    e);
        }
        return false;
    }

    /**
     * Returns {@code true} if the bitstream has the {@code nodownload} metadata value
     * and the current user is not an administrator.
     */
    private boolean isNoDownload(Context context, Bitstream bitstream) throws SQLException {
        var hasNodownloadValue = bitstreamService.getMetadata(bitstream,"bitstream", "viewer", "provider", ANY)
                                                 .stream()
                                                 .anyMatch(mv -> mv.getValue().equals("nodownload"));
        return hasNodownloadValue && !authorizeService.isAdmin(context);
    }

    @Override
    public String[] getSupportedTypes() {
        return new String[]{
            BitstreamRest.CATEGORY + "." + BitstreamRest.NAME,
        };
    }

}