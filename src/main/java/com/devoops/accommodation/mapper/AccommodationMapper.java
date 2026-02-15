package com.devoops.accommodation.mapper;

import com.devoops.accommodation.dto.request.CreateAccommodationRequest;
import com.devoops.accommodation.dto.response.AccommodationResponse;
import com.devoops.accommodation.entity.Accommodation;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AccommodationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hostId", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    Accommodation toEntity(CreateAccommodationRequest request);

    AccommodationResponse toResponse(Accommodation accommodation);

    List<AccommodationResponse> toResponseList(List<Accommodation> accommodations);
}
