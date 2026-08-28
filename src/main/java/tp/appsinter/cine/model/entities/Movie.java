package tp.appsinter.cine.model.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import tp.appsinter.cine.model.utils.MovieGenre;

import java.util.List;
import java.util.UUID;

@Entity
public class Movie {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;
    private String movieName;
    private List<MovieGenre> genres;
    private Integer runtime;
    private String description;
    private String director;
    private List<String> mainCast;
}
