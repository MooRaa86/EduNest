package edunestprototype;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity(name = "PrototypeCertificate")
@Table(name = "prototype_certificate")
public class PrototypeCertificate {
    @Id
    private Long id;

    protected PrototypeCertificate() { }

    public PrototypeCertificate(long id) {
        this.id = id;
    }
}
