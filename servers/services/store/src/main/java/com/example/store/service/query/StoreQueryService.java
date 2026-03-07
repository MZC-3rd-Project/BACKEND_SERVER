package com.example.store.service.query;

import com.example.store.dto.response.StoreListResponse;
import com.example.store.repository.StoresRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Pageable;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryService {

    private final StoresRepository storesRepository;

    public Page<StoreListResponse>  getStoreListInfo(Pageable pageable){
        return storesRepository.findStoreList(pageable);
    }


}
