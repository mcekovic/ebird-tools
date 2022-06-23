package org.strangeforest.ebird.domain;

public record ObservationCounts(long obsCount, long userObsCount, long userYearObsCount) {

   public static final ObservationCounts EMPTY = new ObservationCounts(-1L, -1L, -1L);
}
