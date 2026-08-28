package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tp.appsinter.cine.repository.SeatRepository;

@Service
@RequiredArgsConstructor
class SeatService {
    private final SeatRepository seatRepository;
}
