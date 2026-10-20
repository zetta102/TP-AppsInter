package tp.appsinter.cine.hold;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tp.appsinter.cine.room.SeatHolds;
import tp.appsinter.cine.screening.HeldSeats;
import tp.appsinter.cine.shared.persistence.IdCount;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Responde a {@code screening} y {@code room} sobre los bloqueos vigentes, sin que esas
 * funcionalidades tengan que conocer los repositorios de {@code hold}.
 */
@Component
@RequiredArgsConstructor
class SeatHoldLookups implements HeldSeats, SeatHolds {

    private final SeatHoldRepository seatHoldRepository;

    @Override
    public boolean anyLive(UUID seatUuid, OffsetDateTime now) {
        return seatHoldRepository.existsLiveContaining(seatUuid, now);
    }

    @Override
    public Map<UUID, Integer> countByScreening(Collection<UUID> screeningUuids, @Nullable UUID viewerUuid,
                                               OffsetDateTime now) {
        return IdCount.toMap(seatHoldRepository.countHeldByScreening(screeningUuids, viewerUuid, now));
    }

    @Override
    public List<UUID> findIds(UUID screeningUuid, @Nullable UUID viewerUuid, OffsetDateTime now) {
        return seatHoldRepository.findHeldSeatIds(screeningUuid, viewerUuid, now);
    }

    @Override
    public List<UUID> findIdsAmong(UUID screeningUuid, Collection<UUID> seatUuids, @Nullable UUID viewerUuid,
                                   OffsetDateTime now) {
        return seatHoldRepository.findHeldSeatIdsAmong(screeningUuid, seatUuids, viewerUuid, now);
    }
}
