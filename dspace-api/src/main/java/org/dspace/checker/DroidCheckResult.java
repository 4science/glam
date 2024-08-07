/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.checker;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.dspace.core.ReloadableEntity;
import org.dspace.core.converter.LocalDateTimeAttributeConverter;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
@Entity
@Table(name = "droid_check_result")
public class DroidCheckResult implements ReloadableEntity<Long> {

    @Id
    @Column(name = "check_id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "droid_check_result_id_seq")
    @SequenceGenerator(name = "droid_check_result_id_seq", sequenceName = "droid_check_result_id_seq",
        allocationSize = 1)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bitstream_id", referencedColumnName = "bitstream_id")
    private MostRecentChecksum mostRecentChecksum;

    @Column(name = "uri")
    private String URI;

    @Column(name = "path")
    private String path;

    @Column(name = "filename")
    private String filename;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status", referencedColumnName = "status_code")
    private DroidCheckStatus status;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "type")
    private String type;

    @Column(name = "file_extension")
    private String fileExtension;

    @Column(name = "last_modified_date")
    @Convert(converter = LocalDateTimeAttributeConverter.class)
    private LocalDateTime lastModifiedDate;

    @Column(name = "extension_mismatch")
    private boolean extensionMismatch;

    @Column(name = "puid")
    private String PUID;

    @Column(name = "mime_type")
    private String mimeType;

    @Column(name = "file_format")
    private String fileFormat;

    @Column(name = "format_version")
    private String formatVersion;

    public DroidCheckResult(MostRecentChecksum mostRecentChecksum) {
        this.mostRecentChecksum = mostRecentChecksum;
    }

    public DroidCheckResult() {
    }

    @Override
    public Long getID() {
        return id;
    }

    public MostRecentChecksum getMostRecentChecksum() {
        return mostRecentChecksum;
    }

    public DroidCheckResult setMostRecentChecksum(MostRecentChecksum mostRecentChecksum) {
        this.mostRecentChecksum = mostRecentChecksum;
        return this;
    }

    public String getURI() {
        return URI;
    }

    public DroidCheckResult setURI(String URI) {
        this.URI = URI;
        return this;
    }

    public String getPath() {
        return path;
    }

    public DroidCheckResult setPath(String path) {
        this.path = path;
        return this;
    }

    public String getFilename() {
        return filename;
    }

    public DroidCheckResult setFilename(String filename) {
        this.filename = filename;
        return this;
    }

    public DroidCheckStatus getStatus() {
        return status;
    }

    public DroidCheckResult setStatus(DroidCheckStatus status) {
        this.status = status;
        return this;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public DroidCheckResult setFileSize(Long fileSize) {
        this.fileSize = fileSize;
        return this;
    }

    public String getType() {
        return type;
    }

    public DroidCheckResult setType(String type) {
        this.type = type;
        return this;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public DroidCheckResult setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
        return this;
    }

    public LocalDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public DroidCheckResult setLastModifiedDate(LocalDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
        return this;
    }

    public boolean isExtensionMismatch() {
        return extensionMismatch;
    }

    public DroidCheckResult setExtensionMismatch(boolean extensionMismatch) {
        this.extensionMismatch = extensionMismatch;
        return this;
    }

    public String getPUID() {
        return PUID;
    }

    public DroidCheckResult setPUID(String PUID) {
        this.PUID = PUID;
        return this;
    }

    public String getMimeType() {
        return mimeType;
    }

    public DroidCheckResult setMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public DroidCheckResult setFileFormat(String fileFormat) {
        this.fileFormat = fileFormat;
        return this;
    }

    public String getFormatVersion() {
        return formatVersion;
    }

    public DroidCheckResult setFormatVersion(String formatVersion) {
        this.formatVersion = formatVersion;
        return this;
    }
}
