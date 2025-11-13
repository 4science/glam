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

import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.importer.external.datamodel.ImportRecord;
import org.dspace.importer.external.exception.MetadataSourceException;
import org.dspace.importer.external.metadatamapping.MetadatumDTO;
import org.dspace.importer.external.viaf.ViafImportMetadataSourceServiceImpl;
import org.dspace.importer.external.viaf.ViafServiceFactory;
import org.dspace.services.factory.DSpaceServicesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Adamo Fapohunda (adamo.fapohunda at 4science.com)
 */
public class ViafAuthority extends ItemAuthority {

    private static final Logger log = LoggerFactory.getLogger(ViafAuthority.class);

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

            Choices viafChoices = viafSearch(text, viafSearchStart, viafSearchLimit);
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

    private Choices viafSearch(String text, int start, int limit) {
        List<ImportRecord> records = importViafRecords(text, start, limit);
        if (CollectionUtils.isEmpty(records)) {
            return new Choices(Choices.CF_UNSET);
        }

        int total = records.size();
        Choice[] choices = records.stream()
                                  .map(this::convertToChoice)
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

    private Choice convertToChoice(ImportRecord record) {
        String value = getMetadataValue(record, "dc", "title", null);
        String code = getMetadataValue(record, "person", "identifier", null);
        String authority = getAuthorityPrefix() + code;
        return new Choice(authority, value, value, null, getSource());
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

}
