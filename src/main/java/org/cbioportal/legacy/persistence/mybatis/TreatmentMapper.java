package org.cbioportal.legacy.persistence.mybatis;

import org.cbioportal.legacy.model.ClinicalEventSample;
import org.cbioportal.legacy.model.Treatment;

import java.util.List;
import java.util.Set;

public interface TreatmentMapper {
    List<Treatment> getAllTreatments(List<String> sampleIds, List<String> studyIds, String key);

    List<ClinicalEventSample> getAllSamples(List<String> sampleIds, List<String> studyIds);
    
    List<ClinicalEventSample> getAllShallowSamples(List<String> sampleIds, List<String> studyIds);

    Boolean hasTreatmentData(List<String> sampleIds, List<String> studyIds, String key);

    Boolean hasSampleTimelineData(List<String> sampleIds, List<String> studyIds);
}
