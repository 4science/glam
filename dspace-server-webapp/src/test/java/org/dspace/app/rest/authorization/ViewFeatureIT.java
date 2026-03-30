/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest.authorization;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;

import org.apache.commons.codec.CharEncoding;
import org.apache.commons.io.IOUtils;
import org.dspace.app.rest.authorization.impl.ViewFeature;
import org.dspace.app.rest.converter.BitstreamConverter;
import org.dspace.app.rest.matcher.AuthorizationMatcher;
import org.dspace.app.rest.model.BitstreamRest;
import org.dspace.app.rest.projection.Projection;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.app.rest.utils.Utils;
import org.dspace.authorize.service.ResourcePolicyService;
import org.dspace.builder.BitstreamBuilder;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Bitstream;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for the View authorization feature.
 * Tests verify view permissions for bitstreams under different authorization scenarios.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.com)
 */
public class ViewFeatureIT extends AbstractControllerIntegrationTest {

    @Autowired
    private Utils utils;
    @Autowired
    private BitstreamConverter bitstreamConverter;
    @Autowired
    private ResourcePolicyService resourcePolicyService;
    @Autowired
    private AuthorizationFeatureService authorizationFeatureService;

    private AuthorizationFeature viewFeature;

    private Collection collectionA;
    private Item itemA;
    private Bitstream bitstreamA;
    private Bitstream bitstreamB;
    private Bitstream bitstreamNoDownload;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();
        viewFeature = authorizationFeatureService.find(ViewFeature.NAME);

        Community communityA = CommunityBuilder.createCommunity(context)
                                               .build();
        collectionA = CollectionBuilder.createCollection(context, communityA)
                                       .build();
        itemA = ItemBuilder.createItem(context, collectionA)
                           .withTitle("Item A")
                           .build();

        try (InputStream is = IOUtils.toInputStream("Dummy content", CharEncoding.UTF_8)) {
            bitstreamA = BitstreamBuilder.createBitstream(context, itemA, is)
                                         .withName("Bitstream")
                                         .withDescription("Description")
                                         .withMimeType("text/plain")
                                         .build();
            bitstreamB = BitstreamBuilder.createBitstream(context, itemA, is)
                                         .withName("Bitstream2")
                                         .withDescription("Description2")
                                         .withMimeType("text/plain")
                                         .build();
            bitstreamNoDownload = BitstreamBuilder.createBitstream(context, itemA, is)
                                         .withName("BitstreamNoDownload")
                                         .withDescription("No download bitstream")
                                         .withMimeType("text/plain")
                                         .withMetadata("bitstream", "viewer", "provider", "nodownload")
                                         .build();
        }
        resourcePolicyService.removePolicies(context, bitstreamB, Constants.READ);

        context.restoreAuthSystemState();
    }

    @Test
    public void viewOfBitstreamAAsAdmin() throws Exception {
        BitstreamRest bitstreamRest = bitstreamConverter.convert(bitstreamA, Projection.DEFAULT);
        String bitstreamUri = utils.linkToSingleResource(bitstreamRest, "self").getHref();

        Authorization authorizationFeature = new Authorization(admin, viewFeature, bitstreamRest);

        String token = getAuthToken(admin.getEmail(), password);

        getClient(token).perform(get("/api/authz/authorizations/search/object")
                        .param("uri", bitstreamUri)
                        .param("feature", viewFeature.getName()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$._embedded.authorizations", contains(
                                   Matchers.is(AuthorizationMatcher.matchAuthorization(authorizationFeature)))));
    }

    @Test
    public void viewOfBitstreamAAsEpersonTest() throws Exception {
        BitstreamRest bitstreamRest = bitstreamConverter.convert(bitstreamA, Projection.DEFAULT);
        String bitstreamUri = utils.linkToSingleResource(bitstreamRest, "self").getHref();
        Authorization authorizationFeature = new Authorization(eperson, viewFeature, bitstreamRest);

        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get("/api/authz/authorizations/search/object")
                        .param("uri", bitstreamUri)
                        .param("feature", viewFeature.getName()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.page.totalElements", greaterThan(0)))
                        .andExpect(jsonPath("$._embedded.authorizations", contains(
                                   Matchers.is(AuthorizationMatcher.matchAuthorization(authorizationFeature)))));
    }

    @Test
    public void viewOfBitstreamAAsAnonymous() throws Exception {
        BitstreamRest bitstreamRest = bitstreamConverter.convert(bitstreamA, Projection.DEFAULT);
        String bitstreamUri = utils.linkToSingleResource(bitstreamRest, "self").getHref();

        Authorization authorizationFeature = new Authorization(null, viewFeature, bitstreamRest);

        getClient().perform(get("/api/authz/authorizations/search/object")
                   .param("uri", bitstreamUri)
                   .param("feature", viewFeature.getName()))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$._embedded.authorizations",
                              contains(Matchers.is(AuthorizationMatcher.matchAuthorization(authorizationFeature)))));
    }

    @Test
    public void viewOfBitstreamBAsAdmin() throws Exception {
        BitstreamRest bitstreamRest = bitstreamConverter.convert(bitstreamB, Projection.DEFAULT);
        String bitstreamUri = utils.linkToSingleResource(bitstreamRest, "self").getHref();
        Authorization authorizationFeature = new Authorization(admin, viewFeature, bitstreamRest);

        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(get("/api/authz/authorizations/search/object")
                        .param("uri", bitstreamUri)
                        .param("feature", viewFeature.getName()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.page.totalElements", greaterThan(0)))
                        .andExpect(jsonPath("$._embedded.authorizations", contains(
                                   Matchers.is(AuthorizationMatcher.matchAuthorization(authorizationFeature)))));
    }

    @Test
    public void viewOfBitstreamBAsEperson() throws Exception {
        BitstreamRest bitstreamRest = bitstreamConverter.convert(bitstreamB, Projection.DEFAULT);
        String bitstreamUri = utils.linkToSingleResource(bitstreamRest, "self").getHref();

        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get("/api/authz/authorizations/search/object")
                        .param("uri", bitstreamUri)
                        .param("feature", viewFeature.getName()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.page.totalElements", is(0)))
                        .andExpect(jsonPath("$._embedded").doesNotExist());
    }

    @Test
    public void viewOfBitstreamBAsAnonymous() throws Exception {
        BitstreamRest bitstreamRest = bitstreamConverter.convert(bitstreamB, Projection.DEFAULT);
        String bitstreamUri = utils.linkToSingleResource(bitstreamRest, "self").getHref();

        getClient().perform(get("/api/authz/authorizations/search/object")
                   .param("uri", bitstreamUri)
                   .param("feature", viewFeature.getName()))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.page.totalElements", is(0)))
                   .andExpect(jsonPath("$._embedded").doesNotExist());
    }

    @Test
    public void viewOfIIIFNoDownloadBitstreamAsAdmin() throws Exception {
        BitstreamRest bitstreamRest = bitstreamConverter.convert(bitstreamNoDownload, Projection.DEFAULT);
        String bitstreamUri = utils.linkToSingleResource(bitstreamRest, "self").getHref();
        Authorization authorizationFeature = new Authorization(admin, viewFeature, bitstreamRest);

        String token = getAuthToken(admin.getEmail(), password);
        getClient(token).perform(get("/api/authz/authorizations/search/object")
                        .param("uri", bitstreamUri)
                        .param("feature", viewFeature.getName()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.page.totalElements", greaterThan(0)))
                        .andExpect(jsonPath("$._embedded.authorizations", contains(
                                   Matchers.is(AuthorizationMatcher.matchAuthorization(authorizationFeature)))));
    }

    @Test
    public void viewOfNoDownloadBitstreamAsEperson() throws Exception {
        BitstreamRest bitstreamRest = bitstreamConverter.convert(bitstreamNoDownload, Projection.DEFAULT);
        String bitstreamUri = utils.linkToSingleResource(bitstreamRest, "self").getHref();
        Authorization authorizationFeature = new Authorization(eperson, viewFeature, bitstreamRest);

        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get("/api/authz/authorizations/search/object")
                        .param("uri", bitstreamUri)
                        .param("feature", viewFeature.getName()))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.page.totalElements", greaterThan(0)))
                        .andExpect(jsonPath("$._embedded.authorizations", contains(
                                   Matchers.is(AuthorizationMatcher.matchAuthorization(authorizationFeature)))));
    }

    @Test
    public void viewOfNoDownloadBitstreamAsAnonymous() throws Exception {
        BitstreamRest bitstreamRest = bitstreamConverter.convert(bitstreamNoDownload, Projection.DEFAULT);
        String bitstreamUri = utils.linkToSingleResource(bitstreamRest, "self").getHref();
        Authorization authorizationFeature = new Authorization(null, viewFeature, bitstreamRest);

        getClient().perform(get("/api/authz/authorizations/search/object")
                   .param("uri", bitstreamUri)
                   .param("feature", viewFeature.getName()))
                   .andExpect(status().isOk())
                   .andExpect(jsonPath("$.page.totalElements", greaterThan(0)))
                   .andExpect(jsonPath("$._embedded.authorizations", contains(
                              Matchers.is(AuthorizationMatcher.matchAuthorization(authorizationFeature)))));
    }

}
