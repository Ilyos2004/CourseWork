package com.cw.tutoring.repository.projection;

public interface AvailableSlotProjection {

    Integer getSlotId();
    Integer getTutorId();
    Integer getSubjectId();
    String getStartDt();
    String getEndDt();
    Integer getCapacity();
    Integer getBookedCount();
    Integer getFreePlaces();
}
