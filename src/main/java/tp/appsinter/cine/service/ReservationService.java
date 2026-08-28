package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tp.appsinter.cine.repository.ReservationRepository;

@Service
@RequiredArgsConstructor
class ReservationService {
    private final ReservationRepository reservationRepository;


}
