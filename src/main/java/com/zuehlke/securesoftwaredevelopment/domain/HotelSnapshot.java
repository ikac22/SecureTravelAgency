package com.zuehlke.securesoftwaredevelopment.domain;

import java.time.LocalDateTime;

public class HotelSnapshot {
    private final long id;
    private final int hotelId;
    private final String fileName;
    private final LocalDateTime createdAt;
    private final Long parentSnapshotId;
    private final String parentFileName;
    private final boolean baseline;

    public HotelSnapshot(long id,
                         int hotelId,
                         String fileName,
                         LocalDateTime createdAt,
                         Long parentSnapshotId,
                         String parentFileName,
                         boolean baseline) {
        this.id = id;
        this.hotelId = hotelId;
        this.fileName = fileName;
        this.createdAt = createdAt;
        this.parentSnapshotId = parentSnapshotId;
        this.parentFileName = parentFileName;
        this.baseline = baseline;
    }

    public long getId() {
        return id;
    }

    public int getHotelId() {
        return hotelId;
    }

    public String getFileName() {
        return fileName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getParentSnapshotId() {
        return parentSnapshotId;
    }

    public String getParentFileName() {
        return parentFileName;
    }

    public boolean isBaseline() {
        return baseline;
    }
}
