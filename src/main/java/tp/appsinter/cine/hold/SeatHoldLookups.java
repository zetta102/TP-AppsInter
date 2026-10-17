package tp.appsinter.cine.hold;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import tp.appsinter.cine.screening.HeldSeats;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Responde a {@code screening} sobre los bloqueos vigentes, sin que {@code screening} tenga que
 * conocer los repositorios de {@code hold}.
 */
@Component
@RequiredArgsConstructor
class SeatHoldLookups implements HeldSeats {

    private final SeatHoldRepository seatHoldRepository;

    @Override
    public Map<UUID, Integer> countByScreening(Collection<UUID> screeningUuids, @Nullable UUID viewerUuid,
                                               OffsetDateTime now) {
        return seatHoldRepository.countHeldByScreening(screeningUuids, viewerUuid, now).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> ((Number) row[1]).intValue()));
    }

    @Override
    public List<UUID> findIds(UUID screeningUuid, @Nullable UUID viewerUuid, OffsetDateTime now) {
        return seatHoldRepository.findHeldSeatIds(screeningUuid, viewerUuid, now);
    }

    @Override
    public List<UUID> findIdsAmong(UUID screeningUuid, Collection<UUID> seatUuids, @Nullable UUID excludedUserUuid,
                                   OffsetDateTime now) {
        return seatHoldRepository.findHeldSeatIdsAmong(screeningUuid, seatUuids, excludedUserUuid, now);
    }
}
