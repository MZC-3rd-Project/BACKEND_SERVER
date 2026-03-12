package com.example.store.entity;


import com.example.core.id.jpa.SnowflakeGenerated;
import com.example.data.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Entity
@Table(name = "store_contacts")
public class StoreContact extends BaseEntity {
    @Id
    @SnowflakeGenerated
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Stores store;

    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 30)
    private ContactType contactType;

    @Column(name = "contact_value", nullable = false, length = 100)
    private String contactValue;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = false;


    public static StoreContact of(Stores store, ContactType contactType, String contactValue, boolean isPrimary) {
        return  StoreContact.builder()
            .store(store)
            .contactType(contactType)
            .contactValue(contactValue)
            .isPrimary(isPrimary)
            .build();
    }
    public static StoreContact of(ContactType contactType, String contactValue, boolean isPrimary){
        return  StoreContact.builder()
            .contactType(contactType)
            .contactValue(contactValue)
            .isPrimary(isPrimary)
            .build();
    }

    public void updateStoreContact(String contactValue, ContactType contactType) {
        if (contactValue != null) this.contactValue = contactValue;
        if (contactType != null)  this.contactType = contactType;
    }

}
