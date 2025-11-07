/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
@Entity
@Table(name = "droid_check_status")
public class DroidCheckStatus implements Serializable {
    @Id
    @Column(name = "status_code")
    @Enumerated(EnumType.STRING)
    private DroidResultCode statusCode;

    @Column(name = "result_description")
    private String resultDescription;

    /**
     * Protected constructor, new object creation impossible
     */
    protected DroidCheckStatus() {

    }

    public DroidResultCode getStatusCode() {
        return statusCode;
    }

    public String getResultDescription() {
        return resultDescription;
    }
}
