package com.rentle.shared.exception;

public class TooManyRequestsException extends RentleException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
