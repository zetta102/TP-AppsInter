package tp.appsinter.cine.contact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface ContactMessageRepository
        extends JpaRepository<ContactMessage, UUID>, JpaSpecificationExecutor<ContactMessage> {
}
