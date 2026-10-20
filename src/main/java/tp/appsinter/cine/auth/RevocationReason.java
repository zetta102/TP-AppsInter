package tp.appsinter.cine.auth;

/**
 * Motivo por el que se revocó un token de refresco.
 *
 * <p>Distingue lo normal de lo sospechoso: presentar un token cerrado por {@link #LOGOUT} es un
 * reintento inocente, mientras que la revocación por {@link #REUSE} sólo ocurre cuando alguien
 * insistió con un token ya rotado fuera de la ventana de gracia.
 */
public enum RevocationReason {
    LOGOUT,
    REUSE,
    PASSWORD_CHANGED,
    ACCOUNT_DELETED
}
