/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.content.authority;

import static org.apache.commons.lang3.ArrayUtils.addAll;
import static org.dspace.authority.service.AuthorityValueService.GENERATE;
import static org.dspace.authority.service.AuthorityValueService.SPLIT;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.importer.external.datamodel.ImportRecord;
import org.dspace.importer.external.exception.MetadataSourceException;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.dspace.importer.external.viaf.ViafImportMetadataSourceServiceImpl;
import org.dspace.importer.external.viaf.ViafServiceFactory;
import org.dspace.services.ConfigurationService;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 */
public class ViafAuthority extends ItemAuthority {

    private static final Logger log = LoggerFactory.getLogger(ViafAuthority.class);

    private final ConfigurationService configurationService =
        DSpaceServicesFactory.getInstance().getConfigurationService();

    protected static boolean matchMetadatum(MetadatumDTO metadatum, String schema, String element, String qualifier) {
        return StringUtils.equals(metadatum.getSchema(), schema) &&
            StringUtils.equals(metadatum.getElement(), element) &&
            StringUtils.equals(metadatum.getQualifier(), qualifier);
    }

    @Override
    public Choices getMatches(String text, int start, int limit, String locale) {
        Choices itemChoices = super.getMatches(text, start, limit, locale);
        int viafSearchStart = start > itemChoices.total ? start - itemChoices.total : 0;
        int viafSearchLimit = limit > itemChoices.values.length ? limit - itemChoices.values.length : 0;

        try {

            Choices viafChoices = viafSearch(text, viafSearchStart, viafSearchLimit, locale);
            int total = itemChoices.total + viafChoices.total;

            Choice[] choices = addAll(itemChoices.values, viafChoices.values);
            return new Choices(choices, start, total, calculateConfidence(choices), total > (start + limit), 0);

        } catch (Exception ex) {
            log.error(
                "Error performing VIAF search with text='{}', start={}, limit={}",
                text, start, limit, ex
            );
            return itemChoices;
        }
    }

    private Choices viafSearch(String text, int start, int limit, String locale) {
        List<ImportRecord> records = importViafRecords(text, start, limit);
        if (CollectionUtils.isEmpty(records)) {
            return new Choices(Choices.CF_UNSET);
        }

        int total = records.size();
        Choice[] choices = records.stream()
                                  .map(r -> convertToChoice(r , locale))
                                  .toArray(Choice[]::new);

        return new Choices(choices, start, total, calculateConfidence(choices), total > (start + limit), 0);
    }

    private List<ImportRecord> importViafRecords(String text, int start, int limit) {
        try {
            return (List<ImportRecord>) getViafService().getRecords(text, start, limit);

        } catch (MetadataSourceException e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException("Failed to import VIAF records", e);
        }
    }

    private Choice convertToChoice(ImportRecord record, String locale) {
        String value = getMetadataValue(record, "dc", "title", null);
        String code = getMetadataValue(record, "person", "identifier", null);
        String authority = getAuthorityPrefix() + code;
        return new Choice(authority, value, value, buildExtras(record, locale), getSource());
    }

    private String getMetadataValue(ImportRecord record, String schema, String element, String qualifier) {
        return record.getValueList()
                     .stream()
                     .filter(metadatum -> matchMetadatum(metadatum, schema, element, qualifier))
                     .map(MetadatumDTO::getValue)
                     .findFirst()
                     .orElse("");
    }

    private String getAuthorityPrefix() {
        return DSpaceServicesFactory.getInstance().getConfigurationService()
                                    .getProperty("viaf.authority.prefix", GENERATE + "VIAF-ID" + SPLIT);
    }

    protected ViafImportMetadataSourceServiceImpl getViafService() {
        return ViafServiceFactory.getInstance().getRorImportMetadataSourceService();
    }

    private Map<String, String> buildExtras(ImportRecord viafRecord, String locale) {
        Map<String, String> extras = new LinkedHashMap<>();

        // Add VIAF ID
        String viafId = getMetadataValue(viafRecord, "person", "identifier", null);
        if (StringUtils.isNotBlank(viafId)) {
            addExtra(extras, viafId, "id");
        }

        // Add gender
        String gender = getMetadataValue(viafRecord, "glamperson", "gender", null);
        if (StringUtils.isNotBlank(gender)) {
            addExtra(extras, gender, "gender");
        }

        // Add birth date/year
        String birthDate = getMetadataValue(viafRecord, "person", "birthDate", null);
        if (StringUtils.isNotBlank(birthDate)) {
            addExtra(extras, birthDate, "birthDate");
        }

        String birthYear = getMetadataValue(viafRecord, "glamperson", "birthYear", null);
        if (StringUtils.isNotBlank(birthYear)) {
            addExtra(extras, birthYear, "birthYear");
        }

        // Add death date/year
        String deathDate = getMetadataValue(viafRecord, "glamperson", "deathDate", null);
        if (StringUtils.isNotBlank(deathDate)) {
            addExtra(extras, deathDate, "deathDate");
        }

        String deathYear = getMetadataValue(viafRecord, "glamperson", "deathYear", null);
        if (StringUtils.isNotBlank(deathYear)) {
            addExtra(extras, deathYear, "deathYear");
        }

        // Add nationality
        String nationality = getMetadataValue(viafRecord, "person", "nationality", null);
        if (StringUtils.isNotBlank(nationality)) {
            addExtra(extras, nationality, "nationality");
        }

        // Add role/occupation
        String role = getMetadataValue(viafRecord, "glamperson", "role", null);
        if (StringUtils.isNotBlank(role)) {
            addExtra(extras, role, "role");
        }

        // Add subject/occupation (LCSH)
        String subject = getMetadataValue(viafRecord, "dc", "subject", "lcsh");
        if (StringUtils.isNotBlank(subject)) {
            addExtra(extras, subject, "subject");
        }

        // Add variant names (collect all variant names)
        String variantNames = viafRecord.getValue("crisrp", "name", "variant").stream()
            .map(MetadatumDTO::getValue)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.joining("; "));
        if (StringUtils.isNotBlank(variantNames)) {
            addExtra(extras, variantNames, "variantNames");
        }

        // Add VIAF link
        String viafLink = getMetadataValue(viafRecord, "glam", "link", "viaf");
        if (StringUtils.isNotBlank(viafLink)) {
            addExtra(extras, viafLink, "viafLink");
        }

        // Add Wikipedia link
        String wikipediaLink = getMetadataValue(viafRecord, "glam", "link", "wikipedia");
        if (StringUtils.isNotBlank(wikipediaLink)) {
            addExtra(extras, wikipediaLink, "wikipediaLink");
        }

        return extras;
    }

    private void addExtra(Map<String, String> extras, String value, String extraType) {
        if (StringUtils.isBlank(value)) {
            return;
        }

        String key = getKey(extraType);

        if (useAsData(extraType)) {
            extras.put("data-" + key, value);
        }
        if (useForDisplaying(extraType)) {
            extras.put(key, value);
        }
    }

    private boolean useForDisplaying(String extraType) {
        return configurationService.getBooleanProperty(
                "cris.ViafAuthority." + getPluginInstanceName() + "." + extraType + ".display",
                configurationService.getBooleanProperty(
                        "cris.ViafAuthority." + extraType + ".display", true));
    }

    private boolean useAsData(String extraType) {
        return configurationService.getBooleanProperty(
                "cris.ViafAuthority." + getPluginInstanceName() + "." + extraType + ".as-data",
                configurationService.getBooleanProperty(
                        "cris.ViafAuthority." + extraType + ".as-data", true));
    }

    private String getKey(String extraType) {
        return configurationService.getProperty(
                "cris.ViafAuthority." + getPluginInstanceName() + "." + extraType + ".key",
                configurationService.getProperty("cris.ViafAuthority." + extraType + ".key",
                        "viaf_person_" + extraType));
    }

}
