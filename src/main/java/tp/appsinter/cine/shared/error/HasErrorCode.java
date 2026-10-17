package tp.appsinter.cine.shared.error;

/**
 * Excepción ajena a {@link DomainException} —por ejemplo, una de Spring Security— que igual trae un
 * {@link ErrorCode} propio.
 *
 * <p>Permite que el manejador global publique ese código sin conocer la clase concreta, de modo que
 * {@code shared} no dependa de la funcionalidad que la lanza.
 */
public interface HasErrorCode {

    ErrorCode getCode();
}
