/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.service.impl;

import static org.dspace.util.FunctionalUtils.throwingMapperWrapper;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.dspace.checker.DroidCheckResult;
import org.dspace.checker.DroidValidationException;
import org.dspace.checker.MostRecentChecksum;
import org.dspace.checker.dao.DroidCheckResultDAO;
import org.dspace.checker.service.AbstractDroidCheckResultService;
import org.dspace.checker.service.DroidValidationService;
import org.dspace.content.Bitstream;
import org.dspace.core.Context;
import org.dspace.services.ConfigurationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DroidCheckResultServiceImpl extends AbstractDroidCheckResultService {

    @Autowired
    private DroidValidationService validationService;
    @Autowired
    protected AbstractDroidValidationMapper mapper;
    @Autowired
    private DroidCheckResultDAO droidCheckResultDAO;
    @Autowired
    private ConfigurationService configurationService;

    @Override
    public DroidCheckResult create(Context context, MostRecentChecksum recentChecksum) {
        return new DroidCheckResult(recentChecksum);
    }

    @Override
    public DroidCheckResult save(Context context, DroidCheckResult droidCheckResult) throws SQLException {
        this.droidCheckResultDAO.save(context, droidCheckResult);
        return droidCheckResult;
    }

    private DroidCheckResult add(Context context, DroidCheckResult droidCheckResult,
                                 MostRecentChecksum mostRecentChecksum) throws SQLException {
        droidCheckResult.setMostRecentChecksum(mostRecentChecksum);
        return this.save(context, this.droidCheckResultDAO.create(context, droidCheckResult));
    }

    @Override
    public List<DroidCheckResult> findBy(Context context, Bitstream bitstream) throws SQLException {
        return droidCheckResultDAO.findBy(context, bitstream);
    }

    @Override
    public List<DroidCheckResult> validate(Context context, MostRecentChecksum checksum)
        throws SQLException, DroidValidationException {
        return toCheckResults(context, this.mapper, this.validationService.validate(context, checksum.getBitstream()))
            .stream()
            .map(throwingMapperWrapper(result -> add(context, result, checksum), null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

}
