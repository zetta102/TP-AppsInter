package tp.appsinter.cine.shared;

/**
 * Enumeración del contrato con una etiqueta para mostrar, publicada en {@code GET /api/metadata/enums}.
 *
 * <p>La etiqueta cubre los casos que no se deducen del nombre ({@code THREED} → {@code 3D}).
 */
public interface DisplayNamed {

    String getDisplayName();
}
