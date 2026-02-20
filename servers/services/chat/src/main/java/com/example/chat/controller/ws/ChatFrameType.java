package com.example.chat.controller.ws;

public enum ChatFrameType {
    SUBSCRIBE_ROOM,
    SEND_MESSAGE,
    READ,
    PING,

    SUBSCRIBED,
    MESSAGE_ACK,
    READ_ACK,
    ROOM_MESSAGE,
    ERROR,
    PONG
}
