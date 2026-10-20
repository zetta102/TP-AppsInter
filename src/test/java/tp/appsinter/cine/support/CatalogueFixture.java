package tp.appsinter.cine.support;

import tp.appsinter.cine.inventory.Inventory;
import tp.appsinter.cine.inventory.InventoryRepository;
import tp.appsinter.cine.inventory.ProductCategory;
import tp.appsinter.cine.movie.Movie;
import tp.appsinter.cine.movie.MovieGenre;
import tp.appsinter.cine.movie.MovieRepository;
import tp.appsinter.cine.room.Position;
import tp.appsinter.cine.room.ScreenRoom;
import tp.appsinter.cine.room.ScreenRoomRepository;
import tp.appsinter.cine.room.ScreenRoomType;
import tp.appsinter.cine.room.Seat;
import tp.appsinter.cine.room.SeatRepository;
import tp.appsinter.cine.room.SeatType;
import tp.appsinter.cine.screening.MovieScreening;
import tp.appsinter.cine.screening.MovieScreeningRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static tp.appsinter.cine.shared.BusinessConstants.SCREENING_TURNOVER_MINUTES;

/**
 * Arma el catálogo mínimo sobre el que trabajan las pruebas: una película, una sala con butacas en la
 * fila {@code A}, una función dentro de dos días y un producto de confitería.
 *
 * <p>Va directo a los repositorios: lo que se prueba es lo que pasa después, no el alta
 * administrativa, que ya tiene sus propias pruebas. Las funciones respetan la misma regla que la
 * aplicación —fin = inicio + duración + recambio—, para que el catálogo de prueba no tenga horarios
 * que la API nunca produciría.
 */
public final class CatalogueFixture {

    public static final BigDecimal BASE_PRICE = new BigDecimal("10.00");
    public static final BigDecimal POPCORN_PRICE = new BigDecimal("4.00");
    public static final int POPCORN_STOCK = 10;

    private final MovieRepository movieRepository;
    private final ScreenRoomRepository screenRoomRepository;
    private final SeatRepository seatRepository;
    private final MovieScreeningRepository movieScreeningRepository;
    private final InventoryRepository inventoryRepository;
    private final Clock clock;

    public CatalogueFixture(MovieRepository movieRepository,
                            ScreenRoomRepository screenRoomRepository,
                            SeatRepository seatRepository,
                            MovieScreeningRepository movieScreeningRepository,
                            InventoryRepository inventoryRepository,
                            Clock clock) {
        this.movieRepository = movieRepository;
        this.screenRoomRepository = screenRoomRepository;
        this.seatRepository = seatRepository;
        this.movieScreeningRepository = movieScreeningRepository;
        this.inventoryRepository = inventoryRepository;
        this.clock = clock;
    }

    /**
     * Catálogo con una butaca por tipo pedido: {@code A1}, {@code A2}… en ese orden.
     */
    public Catalogue create(SeatType... seatTypes) {
        Movie movie = movie("Arrival", 116);
        ScreenRoom room = room("Room 1");
        List<Seat> seats = new ArrayList<>();
        for (int column = 1; column <= seatTypes.length; column++) {
            seats.add(seat(room, "A", String.valueOf(column), seatTypes[column - 1]));
        }
        MovieScreening screening = screening(movie, room, now().plusDays(2));
        Inventory popcorn = inventoryRepository.save(Inventory.builder()
                .productName("Popcorn")
                .category(ProductCategory.SNACK)
                .cost(POPCORN_PRICE)
                .quantity(POPCORN_STOCK)
                .build());
        return new Catalogue(movie, room, screening, List.copyOf(seats), popcorn);
    }

    public Movie movie(String name, int runtime) {
        return movieRepository.save(Movie.builder()
                .movieName(name)
                .genres(new ArrayList<>(List.of(MovieGenre.SCI_FI)))
                .runtime(runtime)
                .releaseYear(2016)
                .mainCast(new ArrayList<>(List.of("Amy Adams")))
                .build());
    }

    public ScreenRoom room(String name) {
        return screenRoomRepository.save(ScreenRoom.builder()
                .roomName(name)
                .screenRoomType(ScreenRoomType.NORMAL)
                .build());
    }

    public Seat seat(ScreenRoom room, String row, String column, SeatType type) {
        return seatRepository.save(Seat.builder()
                .position(new Position(row, column))
                .seatType(type)
                .screenRoom(room)
                .build());
    }

    public MovieScreening screening(Movie movie, ScreenRoom room, OffsetDateTime startsAt) {
        return movieScreeningRepository.save(MovieScreening.builder()
                .movie(movie)
                .screenRoom(room)
                .startsAt(startsAt)
                .endsAt(startsAt.plusMinutes(movie.getRuntime() + SCREENING_TURNOVER_MINUTES))
                .basePrice(BASE_PRICE)
                .build());
    }

    public OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }

    /**
     * El catálogo armado. {@code seat(1)} es {@code A1}.
     */
    public record Catalogue(Movie movie,
                            ScreenRoom room,
                            MovieScreening screening,
                            List<Seat> seats,
                            Inventory popcorn) {

        public Seat seat(int column) {
            return seats.get(column - 1);
        }
    }
}
