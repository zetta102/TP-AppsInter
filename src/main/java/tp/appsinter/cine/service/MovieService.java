package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tp.appsinter.cine.repository.MovieRepository;

@Service
@RequiredArgsConstructor
class MovieService {
    private final MovieRepository movieRepository;
}
