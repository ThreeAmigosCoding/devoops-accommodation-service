package com.devoops.accommodation.mapper;

import com.devoops.accommodation.dto.response.AccommodationPhotoResponse;
import com.devoops.accommodation.entity.AccommodationPhoto;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AccommodationPhotoMapper {

    AccommodationPhotoResponse toResponse(AccommodationPhoto photo);

    List<AccommodationPhotoResponse> toResponseList(List<AccommodationPhoto> photos);
}
