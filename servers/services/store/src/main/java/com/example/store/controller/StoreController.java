package com.example.store.controller;

import com.example.store.entity.StoreProfile;

import com.example.store.entity.Stores;
import com.example.store.repository.StoresRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
public class StoreController {
    private final StoresRepository storesRepository;
    @GetMapping
    public List<Stores> getStore(){
        return storesRepository.findAll();
    }
}
