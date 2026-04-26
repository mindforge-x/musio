package com.musio.ai;

public class ChatModelException extends RuntimeException {
    public ChatModelException(String message) {
        super(message);
    }

    public ChatModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
