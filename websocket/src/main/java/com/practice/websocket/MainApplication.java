package com.practice.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;

@SpringBootApplication
@Controller
@RequiredArgsConstructor
public class MainApplication {

    private static List<String> USERS = Arrays.asList("a", "b", "c");

    private final SimpMessagingTemplate simpMessagingTemplate;

    public static void main(String[] args) {
        SpringApplication.run(MainApplication.class, args);
    }

    @MessageMapping("/chat")
    public void chat(Message<Object> message, @Payload Chat chat) {
        Principal principal = message.getHeaders().get(SimpMessageHeaderAccessor.USER_HEADER, Principal.class);
        String sender = principal.getName();
        chat.setSender(sender);

        for (String user : USERS) {
            simpMessagingTemplate.convertAndSendToUser(user, "/queue/messages", chat);
        }
    }

    @GetMapping("/queue")
    @ResponseBody
    public void queue() {
        Chat chat = new Chat();
        chat.setMessage("Hi :)");

        for (String user : USERS) {
            simpMessagingTemplate.convertAndSendToUser(user, "/queue/messages", chat);
        }
    }

}
