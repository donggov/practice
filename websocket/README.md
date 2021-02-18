## WebSocket
- WebSocket은 단일 TCP 연결을 통해 클라이언트와 서버간에 전이중 양방향 통신(full duplex, 2-way communication) 채널을 설정하는 표준화 된 방법을 제공
- HTTP와 다른 TCP 프로토콜이지만 80, 443 포트를 사용하고 기존 방화벽 규칙을 재사용할 수 있도록 HTTP를 통해 작동하도록 설계되었다.
- 더 자세한 설명은 RFC6455 참고
- WebSocket 서버가 웹 서버(e.g. nginx) 뒤에서 실행중인 경우 WebSocket 업그레이드 요청을 WebSocket 서버로 전달하도록 서버를 구성해야한다.
- 마찬가지로 애플리케이션이 클라우드 환경에서 실행되는 경우 WebSocket 지원과 관련된 클라우드 제공자의 지시사항을 확인해야한다.

## STOMP
- Websocket의 세션관리 등의 번거로운 부분을 직접 구현 안해도 됨
- 사용자 정의 메시징 프로토콜 및 메시지 형식을 만들 필요가 없다.
- 메시지 브로커(RabbitMQ, ActiveMQ 등)를 사용하여 구독 및 브로드 캐스트 메시지를 관리할 수 있다.
- 응용 프로그램 논리는 원하는 수의 @Controller 인스턴스로 구성할 수 있으며 STOMP 대상 헤더 WebSocketHandler를 기준으로 지정된 연결에 대해 단일 Websocket 메시지를 처리하는 대신 메시지를 라우팅할 수 있다.
- Spring Security를 사용하여 STOMP 대상 및 메시지 유형을 기반으로 메시지를 보호 가능
- 스프링 의존적이라는게 단전이라고 하지만 내 생각엔 이게 딱히 단점이라고 느껴지지 않음

## 설정
- setApplicationDestinationPrefixes : client에서 SEND 요청을 처리한다.
- SimpMessagingTemplate : @EnableWebSocketMessageBroker를 통해서 등록되는 bean이다. 특정 Broker로 메시지를 전달한다.
- @MessageMapping : Client가 SEND를 할수 있는 경로다. WebSocketConfig에서 등록한 setApplicationDestinationPrefixes와 @MessageMapping의 경로가 합쳐진다. (/publish/chat/join)

## Sample Code
MainApplication.java
````java
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
````

SecurityConfig.java
````java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        auth.inMemoryAuthentication().withUser("a").password("{noop}a").roles("USER");
        auth.inMemoryAuthentication().withUser("b").password("{noop}b").roles("USER");
        auth.inMemoryAuthentication().withUser("c").password("{noop}c").roles("USER");
    }

}
````

Chat.java
````java
@Getter
@Setter
public class Chat {

    private String message;
    private String sender;

}
````

HandshakeInterceptor.java
````java
public class HandshakeInterceptor extends HttpSessionHandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
//        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
//        attributes.put(UserConstant.HEADER_USER_KEY, userService.findBy(user.getId()));
        return super.beforeHandshake(request, response, wsHandler, attributes);
    }
}
````

HttpHandshakeInterceptor.java
````java
@Configuration
public class HttpHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
//        UserDetails user = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
//        attributes.put(UserConstant.HEADER_USER_KEY, userService.findBy(user.getId()));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {
    }

}
````

WebSocketConfig.java
````java
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final HttpHandshakeInterceptor httpHandshakeInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOrigins("*").addInterceptors(httpHandshakeInterceptor).withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
    }

    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        return true;
    }

}
````

index.html
````html
<!DOCTYPE html>
<html lang="en">
<head>
    <script src="/webjars/sockjs-client/sockjs.min.js"></script>
    <script src="/webjars/stomp-websocket/stomp.min.js"></script>
    <script src="https://code.jquery.com/jquery-3.5.1.js" integrity="sha256-QWo7LDvxbWT2tbbQ97B53yJnYU3WhH/C8ycbRAkjPDc=" crossorigin="anonymous"></script>
</head>
<body>

    <div>Welcome</div>

    <div>
        <span>
            <input id="message" />
        </span>

        <span>
            <button id="send">Send</button>
        </span>
    </div>

    <div id="content"></div>

</body>
</html>

<script>
    document.getElementById("send").addEventListener("click", function () {
        stompClient.send("/app/chat", {}, JSON.stringify({
            'message' : $("#message").val(),
        }));
    });

    var sock = new SockJS('http://localhost:8080/ws');
    var stompClient = Stomp.over(sock);

    stompClient.connect({}, connectCallback);

    function connectCallback(frame) {
        console.log(frame);
        stompClient.subscribe('/user/queue/messages', function(message) {
            console.log(message.body);
            $("#content").append("<div>" + message.body + "</div>");
        });
    }

    function errorCallback() {
        console.log("errorCallback");
    }

    function closeEventCallback() {
        console.log("errorCallback");
    }

</script>
````

## 참고
- https://spring.io/guides/gs/messaging-stomp-websocket/
- https://hyeooona825.tistory.com/89
