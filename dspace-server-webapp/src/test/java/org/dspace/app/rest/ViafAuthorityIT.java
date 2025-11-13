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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
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

    public static final String VIAF_PERSON_AUTHORITY =
        "org.dspace.content.authority.ViafAuthority = ViafAuthority";
    private static MockedStatic<ViafServiceFactory> mockViafServiceFactory;
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
        return ItemAuthorityMatcher.matchItemAuthorityProperties(
            authority, title, title, "vocabularyEntry");
    }

    private ImportRecord createImportRecord(String title, String identifier) {
        MetadatumDTO titleMetadata = new MetadatumDTO();
        titleMetadata.setSchema("dc");
        titleMetadata.setElement("title");
        titleMetadata.setQualifier(null);
        titleMetadata.setValue(title);

        MetadatumDTO identifierMetadata = new MetadatumDTO();
        identifierMetadata.setSchema("person");
        identifierMetadata.setElement("identifier");
        identifierMetadata.setQualifier(null);
        identifierMetadata.setValue(identifier);

        return new ImportRecord(List.of(titleMetadata, identifierMetadata));
    }

    @Test
    public void testViafAuthorityBuildExtrasWithDisabledFields() throws Exception {
        // Override configuration to disable certain fields
        configurationService.setProperty("cris.ViafAuthority.gender.display", false);
        configurationService.setProperty("cris.ViafAuthority.birthDate.display", false);
        configurationService.setProperty("cris.ViafAuthority.nationality.as-data", false);
        configurationService.setProperty("cris.ViafAuthority.role.display", false);
        configurationService.setProperty("cris.ViafAuthority.role.as-data", false);

        // Create a comprehensive ImportRecord with all VIAF metadata fields
        ImportRecord completeViafRecord = createCompleteViafImportRecord();

        List<ImportRecord> viafRecords = List.of(completeViafRecord);

        when(metadataSourceService.getRecords(eq("config-test"), anyInt(), anyInt()))
            .thenReturn(viafRecords);

        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get("/api/submission/vocabularies/ViafAuthority/entries")
                                     .param("filter", "config-test"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$._embedded.entries", hasSize(1)))
                        .andExpect(
                            jsonPath("$._embedded.entries[0].authority", is("will be generated::VIAF-ID::12345678")))
                        .andExpect(jsonPath("$._embedded.entries[0].display", is("Leonardo da Vinci")))
                        .andExpect(jsonPath("$._embedded.entries[0].value", is("Leonardo da Vinci")))
                        .andExpect(jsonPath("$._embedded.entries[0].type", is("vocabularyEntry")))

                        // Test fields that should still be displayed (not disabled)
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation.viaf_person_id", is("12345678")))
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_id']", is("12345678")))
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation.viaf_person_birthYear", is("1452")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_birthYear']",
                                            is("1452")))
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation.viaf_person_deathDate", is("1519-05-02")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_deathDate']",
                                            is("1519-05-02")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation.viaf_person_subject",
                                            is("Artists--Italy")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_subject']",
                                            is("Artists--Italy")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation.viaf_person_viafLink",
                                            is("http://viaf.org/viaf/12345678")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_viafLink']",
                                            is("http://viaf.org/viaf/12345678")))

                        // Test fields that should NOT be displayed (disabled display)
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation.viaf_person_gender").doesNotExist())
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation.viaf_person_birthDate").doesNotExist())
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation.viaf_person_role").doesNotExist())

                        // Test fields that should still have data attributes (unless as-data disabled)
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_gender']", is("male")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_birthDate']",
                                            is("1452-04-15")))

                        // Test field with both display and as-data disabled (should not exist at all)
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation.viaf_person_role").doesNotExist())
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_role']").doesNotExist())

                        // Test field with as-data disabled but display enabled by default (nationality)
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation.viaf_person_nationality", is("Italian")))
                        .andExpect(jsonPath(
                            "$._embedded.entries[0].otherInformation['data-viaf_person_nationality']").doesNotExist());

        // Clean up configuration changes to not affect other tests
        configurationService.setProperty("cris.ViafAuthority.gender.display", true);
        configurationService.setProperty("cris.ViafAuthority.birthDate.display", true);
        configurationService.setProperty("cris.ViafAuthority.nationality.as-data", true);
        configurationService.setProperty("cris.ViafAuthority.role.display", true);
        configurationService.setProperty("cris.ViafAuthority.role.as-data", true);
    }

    @Test
    public void testViafAuthorityBuildExtrasAllFields() throws Exception {
        // Create a comprehensive ImportRecord with all 12 VIAF metadata fields
        ImportRecord completeViafRecord = createCompleteViafImportRecord();

        List<ImportRecord> viafRecords = List.of(completeViafRecord);

        when(metadataSourceService.getRecords(eq("complete"), anyInt(), anyInt()))
            .thenReturn(viafRecords);

        String token = getAuthToken(eperson.getEmail(), password);
        getClient(token).perform(get("/api/submission/vocabularies/ViafAuthority/entries")
                                     .param("filter", "complete"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$._embedded.entries", hasSize(1)))
                        .andExpect(
                            jsonPath("$._embedded.entries[0].authority", is("will be generated::VIAF-ID::12345678")))
                        .andExpect(jsonPath("$._embedded.entries[0].display", is("Leonardo da Vinci")))
                        .andExpect(jsonPath("$._embedded.entries[0].value", is("Leonardo da Vinci")))
                        .andExpect(jsonPath("$._embedded.entries[0].type", is("vocabularyEntry")))
                        // Test all buildExtras fields
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation.viaf_person_id", is("12345678")))
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_id']", is("12345678")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation.viaf_person_gender", is("male")))
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_gender']", is("male")))
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation.viaf_person_birthDate", is("1452-04-15")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_birthDate']",
                                            is("1452-04-15")))
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation.viaf_person_birthYear", is("1452")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_birthYear']",
                                            is("1452")))
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation.viaf_person_deathDate", is("1519-05-02")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_deathDate']",
                                            is("1519-05-02")))
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation.viaf_person_deathYear", is("1519")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_deathYear']",
                                            is("1519")))
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation.viaf_person_nationality", is("Italian")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_nationality']",
                                            is("Italian")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation.viaf_person_role", is("Artist")))
                        .andExpect(
                            jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_role']", is("Artist")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation.viaf_person_subject",
                                            is("Artists--Italy")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_subject']",
                                            is("Artists--Italy")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_variantNames']",
                                            is("Leonardo di ser Piero; Léonard de Vinci")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation.viaf_person_viafLink",
                                            is("http://viaf.org/viaf/12345678")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_viafLink']",
                                            is("http://viaf.org/viaf/12345678")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation.viaf_person_wikipediaLink",
                                            is("https://en.wikipedia.org/wiki/Leonardo_da_Vinci")))
                        .andExpect(jsonPath("$._embedded.entries[0].otherInformation['data-viaf_person_wikipediaLink']",
                                            is("https://en.wikipedia.org/wiki/Leonardo_da_Vinci")))
                        // Verify variant names are not displayed (display=false in config)
                        .andExpect(jsonPath(
                            "$._embedded.entries[0].otherInformation.viaf_person_variantNames").doesNotExist());
    }

    /**
     * Creates a comprehensive ImportRecord with all 12 VIAF metadata fields for testing buildExtras
     */
    private ImportRecord createCompleteViafImportRecord() {
        List<MetadatumDTO> metadata = new ArrayList<>();

        // 1. dc.title
        metadata.add(createMetadatum("dc", "title", null, "Leonardo da Vinci"));

        // 2. person.identifier
        metadata.add(createMetadatum("person", "identifier", null, "12345678"));

        // 3. glamperson.gender
        metadata.add(createMetadatum("glamperson", "gender", null, "male"));

        // 4. person.birthDate
        metadata.add(createMetadatum("person", "birthDate", null, "1452-04-15"));

        // 5. glamperson.birthYear
        metadata.add(createMetadatum("glamperson", "birthYear", null, "1452"));

        // 6. glamperson.deathDate
        metadata.add(createMetadatum("glamperson", "deathDate", null, "1519-05-02"));

        // 7. glamperson.deathYear
        metadata.add(createMetadatum("glamperson", "deathYear", null, "1519"));

        // 8. person.nationality
        metadata.add(createMetadatum("person", "nationality", null, "Italian"));

        // 9. glamperson.role
        metadata.add(createMetadatum("glamperson", "role", null, "Artist"));

        // 10. dc.subject.lcsh
        metadata.add(createMetadatum("dc", "subject", "lcsh", "Artists--Italy"));

        // 11. crisrp.name.variant (multiple entries to test collection joining)
        metadata.add(createMetadatum("crisrp", "name", "variant", "Leonardo di ser Piero"));
        metadata.add(createMetadatum("crisrp", "name", "variant", "Léonard de Vinci"));

        // 12. glam.link.viaf
        metadata.add(createMetadatum("glam", "link", "viaf", "http://viaf.org/viaf/12345678"));

        // 13. glam.link.wikipedia
        metadata.add(createMetadatum("glam", "link", "wikipedia", "https://en.wikipedia.org/wiki/Leonardo_da_Vinci"));

        return new ImportRecord(metadata);
    }

    /**
     * Helper method to create MetadatumDTO objects
     */
    private MetadatumDTO createMetadatum(String schema, String element, String qualifier, String value) {
        MetadatumDTO dto = new MetadatumDTO();
        dto.setSchema(schema);
        dto.setElement(element);
        dto.setQualifier(qualifier);
        dto.setValue(value);
        return dto;
    }

    private String id(Item item) {
        return item.getID().toString();
    }
}
