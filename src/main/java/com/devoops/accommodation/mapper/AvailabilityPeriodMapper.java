package com.devoops.accommodation.mapper;

import com.devoops.accommodation.dto.request.CreateAvailabilityPeriodRequest;
import com.devoops.accommodation.dto.response.AvailabilityPeriodResponse;
import com.devoops.accommodation.entity.AvailabilityPeriod;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AvailabilityPeriodMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "accommodationId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    AvailabilityPeriod toEntity(CreateAvailabilityPeriodRequest request);

    AvailabilityPeriodResponse toResponse(AvailabilityPeriod period);

    List<AvailabilityPeriodResponse> toResponseList(List<AvailabilityPeriod> periods);
}
