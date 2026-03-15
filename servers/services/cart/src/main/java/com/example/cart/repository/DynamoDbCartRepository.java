package com.example.cart.repository;

import com.example.cart.config.CartDynamoDbProperties;
import com.example.cart.domain.CartLine;
import com.example.cart.domain.CartLineIdentity;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

@Slf4j
@Repository
public class DynamoDbCartRepository implements CartRepository {

    private final DynamoDbTable<CartLineDocument> table;

    public DynamoDbCartRepository(DynamoDbEnhancedClient enhancedClient, CartDynamoDbProperties properties) {
        this.table = enhancedClient.table(properties.getTableName(), TableSchema.fromBean(CartLineDocument.class));
    }

    @Override
    public List<CartLine> findAllByUserId(Long userId) {
        long now = Instant.now().getEpochSecond();
        return table.query(request -> request.queryConditional(
                        QueryConditional.keyEqualTo(Key.builder().partitionValue(CartKeyFactory.partitionKey(userId)).build())))
                .items()
                .stream()
                .map(this::toDomain)
                .filter(line -> !line.isExpired(now))
                .toList();
    }

    @Override
    public void save(CartLine line, Long userId) {
        table.putItem(toDocument(line, userId));
    }

    @Override
    public void saveAll(List<CartLine> lines, Long userId) {
        for (CartLine line : lines) {
            save(line, userId);
        }
    }

    @Override
    public void delete(Long userId, CartLineIdentity identity) {
        table.deleteItem(Key.builder()
                .partitionValue(CartKeyFactory.partitionKey(userId))
                .sortValue(CartKeyFactory.sortKey(identity))
                .build());
    }

    private CartLineDocument toDocument(CartLine line, Long userId) {
        CartLineDocument document = new CartLineDocument();
        document.setPk(CartKeyFactory.partitionKey(userId));
        document.setSk(CartKeyFactory.sortKey(line.getIdentity()));
        document.setUserId(userId);
        document.setItemId(line.getIdentity().itemId());
        document.setReferenceId(line.getIdentity().referenceId());
        document.setChannelType(line.getIdentity().channelType());
        document.setChannelRefId(line.getIdentity().channelRefId());
        document.setStockItemType(line.getStockItemType());
        document.setQuantity(line.getQuantity());
        document.setSelected(line.isSelected());
        document.setStoreId(line.getStoreId());
        document.setItemTitle(line.getItemTitle());
        document.setThumbnailUrl(line.getThumbnailUrl());
        document.setStoreName(line.getStoreName());
        document.setDisplayPrice(line.getDisplayPrice());
        document.setSalesStatus(line.getSalesStatus());
        document.setCreatedAt(line.getCreatedAt());
        document.setUpdatedAt(line.getUpdatedAt());
        document.setExpiresAtEpoch(line.getExpiresAtEpoch());
        document.setVersion(line.getVersion());
        return document;
    }

    private CartLine toDomain(CartLineDocument document) {
        return CartLine.rehydrate(
                CartLineIdentity.of(
                        document.getItemId(),
                        document.getReferenceId(),
                        document.getChannelType(),
                        document.getChannelRefId()
                ),
                document.getStockItemType(),
                document.getQuantity(),
                Boolean.TRUE.equals(document.getSelected()),
                document.getStoreId(),
                document.getItemTitle(),
                document.getThumbnailUrl(),
                document.getStoreName(),
                document.getDisplayPrice(),
                document.getSalesStatus(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                document.getExpiresAtEpoch(),
                document.getVersion()
        );
    }
}
