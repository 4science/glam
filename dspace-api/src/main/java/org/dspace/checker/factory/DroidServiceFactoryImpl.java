/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker.factory;

import org.dspace.checker.service.DroidCheckResultService;
import org.dspace.checker.service.DroidCheckStatusService;
import org.dspace.checker.service.DroidHistoryService;
import org.dspace.checker.service.DroidValidationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
public class DroidServiceFactoryImpl extends DroidServiceFactory {

    private final DroidCheckResultService droidCheckResultService;

    private final DroidCheckStatusService droidCheckStatusService;

    private final DroidHistoryService droidHistoryService;

    private final DroidValidationService droidValidationService;

    public DroidServiceFactoryImpl(
        @Autowired DroidCheckResultService droidCheckResultService,
        @Autowired DroidCheckStatusService droidCheckStatusService,
        @Autowired DroidHistoryService droidHistoryService,
        @Autowired DroidValidationService droidValidationService
    ) {
        this.droidCheckResultService = droidCheckResultService;
        this.droidCheckStatusService = droidCheckStatusService;
        this.droidHistoryService = droidHistoryService;
        this.droidValidationService = droidValidationService;
    }

    @Override
    public DroidCheckResultService getDroidCheckResultService() {
        return droidCheckResultService;
    }

    @Override
    public DroidCheckStatusService getDroidCheckStatusService() {
        return droidCheckStatusService;
    }

    @Override
    public DroidHistoryService getDroidHistoryService() {
        return droidHistoryService;
    }

    @Override
    public DroidValidationService getDroidValidationService() {
        return droidValidationService;
    }
}
