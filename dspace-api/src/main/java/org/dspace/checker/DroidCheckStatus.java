/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Table;

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
