package com.example.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.Instant;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = ItemDocument.ITEMS_INDEX)
public class ItemDocument {

    public static final String ITEMS_INDEX = "items";
    public static final String ITEMS_READ_ALIAS = "items-read";
    public static final String ITEMS_WRITE_ALIAS = "items-write";

    @Id
    @Field(type = FieldType.Keyword)
    private Long itemId;

    @Field(type = FieldType.Text, analyzer = "ko_index_analyzer", searchAnalyzer = "ko_search_analyzer")
    private String title;

    @Field(type = FieldType.Text, analyzer = "ko_index_analyzer", searchAnalyzer = "ko_search_analyzer")
    private String description;

    @Field(type = FieldType.Keyword)
    private String category;

    @Field(type = FieldType.Keyword)
    private String domainType;

    @Field(type = FieldType.Long)
    private Long price;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Integer)
    private Integer stock;

    @Field(type = FieldType.Long)
    private Long stockVersion;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    @Field(type = FieldType.Date, format = DateFormat.date_time)
    private Instant createdAt;
}
