package tp.appsinter.cine.model.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import tp.appsinter.cine.model.utils.Position;
import tp.appsinter.cine.model.utils.SeatType;

import java.util.UUID;

@Entity
public class Seat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;
    private transient Position position; // TODO validar después
    private SeatType seatType;
}
