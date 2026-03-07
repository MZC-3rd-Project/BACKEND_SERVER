package com.example.store.service.query;

import com.example.core.exception.BusinessException;
import com.example.core.exception.CommonErrorCode;
import com.example.core.exception.ErrorCode;
import com.example.core.exception.TechnicalException;
import com.example.store.dto.response.StoreListResponse;
import com.example.store.repository.StoresRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Pageable;

import static com.example.store.exception.StoreErrorCode.STORE_NOT_FOUND;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StoreQueryService {

    private final StoresRepository storesRepository;

    public Page<StoreListResponse>  getStoreListInfo(Pageable pageable){
        try {
            return storesRepository.findStoreList(pageable);
        } catch (BusinessException e){
            throw new BusinessException(STORE_NOT_FOUND);
        } catch (TechnicalException e){
            throw new TechnicalException(CommonErrorCode.INTERNAL_ERROR);
        }
    }


}
