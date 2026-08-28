package tp.appsinter.cine.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tp.appsinter.cine.repository.ScreenRoomRepository;

@Service
@RequiredArgsConstructor
class ScreenRoomService {
    private ScreenRoomRepository screenRoomRepository;
}
