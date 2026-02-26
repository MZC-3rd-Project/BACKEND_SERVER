package com.example.mediaworker.service;

import com.example.mediaworker.entity.MediaDerivativeTask;

public interface MediaDerivativeProcessor {

    void process(MediaDerivativeTask task);
}
