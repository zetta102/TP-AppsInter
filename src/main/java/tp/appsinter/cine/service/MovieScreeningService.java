package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tp.appsinter.cine.repository.MovieScreeningRepository;

@Service
@RequiredArgsConstructor
class MovieScreeningService {
    private final MovieScreeningRepository movieScreeningRepository;
}
