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
import javax.persistence.OneToOne;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

import org.dspace.core.ReloadableEntity;
import org.dspace.core.converter.LocalDateTimeAttributeConverter;

/**
 * @author Vincenzo Mecca (vins01-4science - vincenzo.mecca at 4science.com)
 **/
@Entity
@Table(name = "droid_check_history")
public class DroidCheckHistory implements ReloadableEntity<Long> {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "droid_check_history_id_seq")
    @SequenceGenerator(
        name = "droid_check_history_id_seq",
        sequenceName = "droid_check_history_id_seq",
        allocationSize = 1
    )
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "check_id", referencedColumnName = "check_id")
    private ChecksumHistory checksumHistory;

    @OneToOne
    @JoinColumn(name = "status", referencedColumnName = "status_code")
    private DroidCheckStatus status;

    @Column(name = "uri")
    private String URI;

    @Column(name = "path")
    private String path;

    @Column(name = "filename")
    private String filename;

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


    @Override
    public Long getID() {
        return this.id;
    }

    public ChecksumHistory getChecksumHistory() {
        return checksumHistory;
    }

    public DroidCheckHistory setChecksumHistory(ChecksumHistory checksumHistory) {
        this.checksumHistory = checksumHistory;
        return this;
    }

    public String getURI() {
        return URI;
    }

    public DroidCheckHistory setURI(String URI) {
        this.URI = URI;
        return this;
    }

    public String getPath() {
        return path;
    }

    public DroidCheckHistory setPath(String path) {
        this.path = path;
        return this;
    }

    public String getFilename() {
        return filename;
    }

    public DroidCheckHistory setFilename(String filename) {
        this.filename = filename;
        return this;
    }

    public DroidCheckStatus getStatus() {
        return status;
    }

    public DroidCheckHistory setStatus(DroidCheckStatus status) {
        this.status = status;
        return this;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public DroidCheckHistory setFileSize(Long fileSize) {
        this.fileSize = fileSize;
        return this;
    }

    public String getType() {
        return type;
    }

    public DroidCheckHistory setType(String type) {
        this.type = type;
        return this;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public DroidCheckHistory setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
        return this;
    }

    public LocalDateTime getLastModifiedDate() {
        return lastModifiedDate;
    }

    public DroidCheckHistory setLastModifiedDate(LocalDateTime lastModifiedDate) {
        this.lastModifiedDate = lastModifiedDate;
        return this;
    }

    public boolean isExtensionMismatch() {
        return extensionMismatch;
    }

    public DroidCheckHistory setExtensionMismatch(boolean extensionMismatch) {
        this.extensionMismatch = extensionMismatch;
        return this;
    }

    public String getPUID() {
        return PUID;
    }

    public DroidCheckHistory setPUID(String PUID) {
        this.PUID = PUID;
        return this;
    }

    public String getMimeType() {
        return mimeType;
    }

    public DroidCheckHistory setMimeType(String mimeType) {
        this.mimeType = mimeType;
        return this;
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public DroidCheckHistory setFileFormat(String fileFormat) {
        this.fileFormat = fileFormat;
        return this;
    }

    public String getFormatVersion() {
        return formatVersion;
    }

    public DroidCheckHistory setFormatVersion(String formatVersion) {
        this.formatVersion = formatVersion;
        return this;
    }
}
