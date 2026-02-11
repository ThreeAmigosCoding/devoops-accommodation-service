package com.devoops.accommodation.mapper;

import com.devoops.accommodation.dto.request.CreateAccommodationRequest;
import com.devoops.accommodation.dto.response.AccommodationResponse;
import com.devoops.accommodation.entity.Accommodation;
import com.devoops.accommodation.entity.Amenity;
import com.devoops.accommodation.entity.AmenityType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AccommodationMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "hostId", ignore = true)
    @Mapping(target = "amenities", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    Accommodation toEntity(CreateAccommodationRequest request);

    @Mapping(target = "amenities", source = "amenities", qualifiedByName = "mapAmenities")
    AccommodationResponse toResponse(Accommodation accommodation);

    List<AccommodationResponse> toResponseList(List<Accommodation> accommodations);

    @Named("mapAmenities")
    default List<AmenityType> mapAmenities(List<Amenity> amenities) {
        if (amenities == null) {
            return List.of();
        }
        return amenities.stream()
                .map(Amenity::getType)
                .toList();
    }
}
