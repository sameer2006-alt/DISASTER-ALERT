package com.disaster.service;

import com.disaster.model.DisasterEvent;
import com.disaster.model.DisasterType;
import com.disaster.model.Organisation;
import com.disaster.repository.OrganisationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceMatchingServiceTest {

    @Mock
    private OrganisationRepository organisationRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private ResourceMatchingService resourceMatchingService;

    @Test
    @DisplayName("Should return empty list when organisations collection is empty without calling geoNear")
    void testFindNearestOrganisationsEmptyCollection() {
        when(organisationRepository.count()).thenReturn(0L);

        DisasterEvent event = DisasterEvent.builder()
                .disasterType(DisasterType.FLOOD)
                .latitude(19.076)
                .longitude(72.8777)
                .affectedRadius(50)
                .severity(8)
                .build();

        List<Organisation> result = resourceMatchingService.findNearestOrganisations(event, 5);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(mongoTemplate, never()).geoNear(any(), any());
    }
}
