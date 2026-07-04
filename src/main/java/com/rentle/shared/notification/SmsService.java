package com.rentle.shared.notification;

public interface SmsService {

    void send(String toPhone, String message);
}
