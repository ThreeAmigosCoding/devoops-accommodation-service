package com.devoops.accommodation.grpc;

import com.devoops.accommodation.grpc.proto.CheckReservationsExistRequest;
import com.devoops.accommodation.grpc.proto.CheckReservationsExistResponse;
import com.devoops.accommodation.grpc.proto.ReservationInternalServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
@Slf4j
public class ReservationGrpcClient {

    @GrpcClient("reservation-service")
    private ReservationInternalServiceGrpc.ReservationInternalServiceBlockingStub reservationStub;

    public boolean hasApprovedReservations(UUID accommodationId, LocalDate startDate, LocalDate endDate) {
        log.debug("gRPC: Checking approved reservations for accommodation {} between {} and {}",
                accommodationId, startDate, endDate);

        CheckReservationsExistRequest request = CheckReservationsExistRequest.newBuilder()
                .setAccommodationId(accommodationId.toString())
                .setStartDate(startDate.toString())
                .setEndDate(endDate.toString())
                .build();

        CheckReservationsExistResponse response = reservationStub.checkReservationsExist(request);
        return response.getHasReservations();
    }
}
