package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import tp.appsinter.cine.model.utils.MovieGenre;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "movie")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"genres", "mainCast"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID uuid;

    @Column(nullable = false)
    private String movieName;

    /*
     * Ambas colecciones se cargan en lotes: sin @BatchSize una página de cartelera dispara dos
     * consultas por película (géneros y elenco) además de la consulta principal.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 50)
    @CollectionTable(name = "movie_genre", joinColumns = @JoinColumn(name = "movie_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "genre", nullable = false)
    @Builder.Default
    private List<MovieGenre> genres = new ArrayList<>();

    @Column(nullable = false)
    private Integer runtime;

    @Column(length = 2000)
    private String description;

    private String director;

    @ElementCollection(fetch = FetchType.EAGER)
    @BatchSize(size = 50)
    @CollectionTable(name = "movie_main_cast", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "actor", nullable = false)
    @Builder.Default
    private List<String> mainCast = new ArrayList<>();

    /**
     * Campos de presentación del catálogo. Se guardan las direcciones de los recursos, no los
     * recursos: alojar imágenes queda fuera del alcance del sistema.
     */
    @Column(length = 2048)
    private String posterUrl;

    @Column(length = 2048)
    private String backdropUrl;

    private Integer releaseYear;

    @Column(length = 16)
    private String certification;

    @Column(length = 8)
    private String originalLanguage;

    @Column(length = 2048)
    private String trailerUrl;
}
