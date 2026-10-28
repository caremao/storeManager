package com.caremao.storemanager.inventory;

import org.springframework.http.HttpStatus;

import com.caremao.storemanager.common.ApiException;

public class InsufficientFundsException extends ApiException {

    public InsufficientFundsException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
