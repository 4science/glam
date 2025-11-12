/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.rest;

import static org.dspace.authority.service.AuthorityValueService.GENERATE;
import static org.dspace.authority.service.AuthorityValueService.SPLIT;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.dspace.app.rest.matcher.ItemAuthorityMatcher;
import org.dspace.app.rest.test.AbstractControllerIntegrationTest;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.builder.ItemBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Item;
import org.dspace.content.authority.DCInputAuthority;
import org.dspace.content.authority.service.ChoiceAuthorityService;
import org.dspace.content.authority.service.MetadataAuthorityService;
import org.dspace.core.service.PluginService;
import org.dspace.importer.external.datamodel.ImportRecord;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.dspace.importer.external.viaf.ViafImportMetadataSourceServiceImpl;
import org.dspace.importer.external.viaf.ViafServiceFactory;
import org.dspace.importer.external.viaf.ViafServiceFactoryImpl;
import org.dspace.services.ConfigurationService;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration tests for {@link org.dspace.content.authority.ViafAuthority}.
 *
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 */
public class ViafAuthorityIT extends AbstractControllerIntegrationTest {

    private static MockedStatic<ViafServiceFactory> mockViafServiceFactory;

    public static final String VIAF_PERSON_AUTHORITY =
        "org.dspace.content.authority.ViafAuthority = ViafAuthority";

    @Autowired
    protected ChoiceAuthorityService choiceAuthorityService;
    @Autowired
    protected PluginService pluginService;
    @Autowired
    protected MetadataAuthorityService metadataAuthorityService;
    @Autowired
    protected ConfigurationService configurationService;
    @Mock
    private ViafImportMetadataSourceServiceImpl metadataSourceService;


    private Collection collection;

    @BeforeClass
    public static void init() {
        mockViafServiceFactory = Mockito.mockStatic(ViafServiceFactory.class);
    }

    @AfterClass
    public static void close() {
        mockViafServiceFactory.close();
    }

    @After
    public void tearDown() throws Exception {
        DCInputAuthority.reset();
        pluginService.clearNamedPluginClasses();
        choiceAuthorityService.clearCache();
    }

    @Before
    public void setup() throws Exception {

        ViafServiceFactoryImpl viafServiceFactory =
            new ViafServiceFactoryImpl(this.metadataSourceService);

        // Default mock behavior - return empty list for any search
        when(metadataSourceService.getRecords(anyString(), anyInt(), anyInt())).thenReturn(List.of());

        mockViafServiceFactory.when(ViafServiceFactory::getInstance).thenReturn(viafServiceFactory);

        configurationService.setProperty(
            "plugin.named.org.dspace.content.authority.ChoiceAuthority",
            new String[] {VIAF_PERSON_AUTHORITY}
        );

        context.turnOffAuthorisationSystem();

        parentCommunity = CommunityBuilder.createCommunity(context).build();

        collection = CollectionBuilder.createCollection(context, parentCommunity)
                                      .withName("Test collection")
                                      .build();

        context.restoreAuthSystemState();

    }

    @After
    @Override
    public void destroy() throws Exception {
        super.destroy();
        pluginService.clearNamedPluginClasses();
        choiceAuthorityService.clearCache();
    }

    @Test
    public void testViafAuthorityReturnsLocalItems() throws Exception {
        context.turnOffAuthorisationSystem();

        Item author_1 = buildPerson("Author 1");
        Item author_2 = buildPerson("Author 2");
        Item author_3 = buildPerson("Author 3");
        Item author_4 = buildPerson("Author 4");

        context.commit();

        context.restoreAuthSystemState();

        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get("/api/submission/vocabularies/ViafAuthority/entries")
            .param("filter", "author"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.entries", containsInAnyOrder(
                localEntry(author_1, "Author 1"),
                localEntry(author_2, "Author 2"),
                localEntry(author_3, "Author 3"),
                localEntry(author_4, "Author 4"))))
            .andExpect(jsonPath("$.page.size", Matchers.is(20)))
            .andExpect(jsonPath("$.page.totalPages", Matchers.is(1)))
            .andExpect(jsonPath("$.page.totalElements", Matchers.is(4)));
    }
    @Test
    public void testViafAuthorityWithPagination() throws Exception {
        context.turnOffAuthorisationSystem();

        // Create multiple items to test pagination
        Item author_1 = buildPerson("Author One");
        Item author_2 = buildPerson("Author Two");
        Item author_3 = buildPerson("Author Three");
        Item author_4 = buildPerson("Author Four");
        Item author_5 = buildPerson("Author Five");

        context.commit();

        context.restoreAuthSystemState();

        String token = getAuthToken(eperson.getEmail(), password);

        // Test first page with size 2
        getClient(token).perform(get("/api/submission/vocabularies/ViafAuthority/entries")
            .param("filter", "Author")
            .param("page", "0")
            .param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.entries.length()", Matchers.is(2)))
            .andExpect(jsonPath("$.page.size", Matchers.is(2)))
            .andExpect(jsonPath("$.page.totalElements", Matchers.is(5)));

        // Test second page
        getClient(token).perform(get("/api/submission/vocabularies/ViafAuthority/entries")
            .param("filter", "Author")
            .param("page", "1")
            .param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.entries.length()", Matchers.is(2)))
            .andExpect(jsonPath("$.page.size", Matchers.is(2)))
            .andExpect(jsonPath("$.page.totalElements", Matchers.is(5)));
    }

    @Test
    public void testViafAuthorityWithNonMatchingFilter() throws Exception {
        context.turnOffAuthorisationSystem();

        Item author_1 = buildPerson("John Doe");
        Item author_2 = buildPerson("Jane Smith");

        context.commit();

        context.restoreAuthSystemState();

        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get("/api/submission/vocabularies/ViafAuthority/entries")
            .param("filter", "NonExistentAuthor"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.entries.length()", Matchers.is(0)))
            .andExpect(jsonPath("$.page.size", Matchers.is(20)))
            .andExpect(jsonPath("$.page.totalElements", Matchers.is(0)));
    }

    @Test
    public void testViafAuthorityWithMockedViafResults() throws Exception {
        // Mock VIAF service to return ImportRecord objects
        List<ImportRecord> viafRecords = List.of(
            createImportRecord("John Smith", "123456789"),
            createImportRecord("Jane Doe", "987654321")
        );

        when(metadataSourceService.getRecords(eq("test"), anyInt(), anyInt()))
            .thenReturn(viafRecords);

        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get("/api/submission/vocabularies/ViafAuthority/entries")
            .param("filter", "test"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.entries", containsInAnyOrder(
                viafEntry("John Smith", "123456789"),
                viafEntry("Jane Doe", "987654321"))))
            .andExpect(jsonPath("$.page.size", Matchers.is(20)))
            .andExpect(jsonPath("$.page.totalPages", Matchers.is(1)))
            .andExpect(jsonPath("$.page.totalElements", Matchers.is(2)));
    }

    @Test
    public void testViafAuthorityWithBothLocalAndViafResults() throws Exception {
        context.turnOffAuthorisationSystem();

        // Create local items
        Item localAuthor = buildPerson("Local Author");
        context.commit();

        // Mock VIAF service to return ImportRecord objects
        List<ImportRecord> viafRecords = List.of(
            createImportRecord("VIAF Author", "555666777")
        );

        when(metadataSourceService.getRecords(eq("author"), anyInt(), anyInt()))
            .thenReturn(viafRecords);

        context.restoreAuthSystemState();

        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get("/api/submission/vocabularies/ViafAuthority/entries")
            .param("filter", "author"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.entries", containsInAnyOrder(
                localEntry(localAuthor, "Local Author"),
                viafEntry("VIAF Author", "555666777"))))
            .andExpect(jsonPath("$.page.size", Matchers.is(20)))
            .andExpect(jsonPath("$.page.totalPages", Matchers.is(1)))
            .andExpect(jsonPath("$.page.totalElements", Matchers.is(2)));
    }

    private Item buildPerson(String title) {
        return ItemBuilder.createItem(context, collection)
            .withTitle(title)
            .withEntityType("Person")
            .build();
    }

    private Matcher<? super Object> localEntry(Item item, String title) {
        return ItemAuthorityMatcher.matchItemAuthorityProperties(id(item), title, title, "vocabularyEntry");
    }

    private Matcher<? super Object> viafEntry(String title, String viafId) {
        String authority = GENERATE + "VIAF-ID" + SPLIT + viafId;
        String value = title + (viafId != null ? " (" + viafId + ")" : "");
        return ItemAuthorityMatcher.matchItemAuthorityProperties(
            authority, title, value, "vocabularyEntry");
    }

    private ImportRecord createImportRecord(String title, String identifier) {
        MetadatumDTO titleMetadata = new MetadatumDTO();
        titleMetadata.setSchema("dc");
        titleMetadata.setElement("title");
        titleMetadata.setQualifier(null);
        titleMetadata.setValue(title);

        MetadatumDTO identifierMetadata = new MetadatumDTO();
        identifierMetadata.setSchema("dc");
        identifierMetadata.setElement("identifier");
        identifierMetadata.setQualifier(null);
        identifierMetadata.setValue(identifier);

        return new ImportRecord(List.of(titleMetadata, identifierMetadata));
    }

    private String id(Item item) {
        return item.getID().toString();
    }
}
