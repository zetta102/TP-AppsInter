package tp.appsinter.cine.model.entities;

import jakarta.persistence.*;
import lombok.*;
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

    @ElementCollection(fetch = FetchType.EAGER)
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
    @CollectionTable(name = "movie_main_cast", joinColumns = @JoinColumn(name = "movie_id"))
    @Column(name = "actor", nullable = false)
    @Builder.Default
    private List<String> mainCast = new ArrayList<>();
}
