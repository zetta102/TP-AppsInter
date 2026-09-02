package tp.appsinter.cine.exception;

/**
 * Root of the domain error hierarchy. Sealed so the global handler can switch exhaustively.
 */
public sealed abstract class DomainException extends RuntimeException
        permits NotFoundException, ConflictException, BusinessRuleException {

    protected DomainException(String message) {
        super(message);
    }
}
