package com.example.core.id.jpa;

import com.example.core.id.Snowflake;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.IdentifierGenerator;

/**
 * Hibernate용 Snowflake ID 생성기.
 *
 * JPA 엔티티에서 다음과 같이 사용한다:
 *   @Id
 *   @SnowflakeGenerated
 *   private Long id;
 *
 * Spring Bean으로 등록된 Snowflake를 사용하면 설정(datacenter/worker ID)이 적용되고,
 * Bean이 없으면 기본값(datacenter=1, worker=1)으로 폴백한다.
 */
public class SnowflakeIdentifierGenerator implements IdentifierGenerator {

    private static volatile Snowflake INSTANCE;

    /**
     * Spring 컨텍스트에서 Snowflake Bean을 주입받아 설정한다.
     * SnowflakeAutoConfiguration에서 호출한다.
     */
    public static void setInstance(Snowflake snowflake) {
        INSTANCE = snowflake;
    }

    @Override
    public Object generate(SharedSessionContractImplementor session, Object object) {
        return getSnowflake().nextId();
    }

    private Snowflake getSnowflake() {
        if (INSTANCE == null) {
            synchronized (SnowflakeIdentifierGenerator.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Snowflake(1, 1);
                }
            }
        }
        return INSTANCE;
    }
}
