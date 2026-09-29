package chat.server;

import ChatApp.ChatException;
import ChatApp.ChatMessage;
import ChatApp.ChatRoom;
import com.zeroc.Ice.Current;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public class ChatRoomI implements ChatRoom {
    // Estructuras thread-safe para soporte concurrente
    private final Set<String> onlineUsers = ConcurrentHashMap.newKeySet();
    private final List<ChatMessage> messageHistory = new CopyOnWriteArrayList<>();
    private final AtomicLong messageIdCounter = new AtomicLong(0);
    private final DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final Object historyLock = new Object();

    private void appendMessage(String sender, String text) {
        synchronized (historyLock) {
            long id = messageIdCounter.incrementAndGet();
            String now = LocalTime.now().format(timeFormat);
            messageHistory.add(new ChatMessage(id, sender, text, now));
        }
    }

    @Override
    public synchronized void login(String nickname, Current current) throws ChatException {
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new ChatException("El nickname no puede ser nulo o vacio.");
        }
        String cleanNick = nickname.trim();
        if (onlineUsers.contains(cleanNick)) {
            throw new ChatException("El nickname '" + cleanNick + "' ya se encuentra conectado.");
        }
        onlineUsers.add(cleanNick);
        System.out.println("[ICE-SERVER] Usuario conectado con exito: " + cleanNick);

        // Notificacion interna del sistema
        appendMessage("SISTEMA", cleanNick + " se unio a la sala.");
    }

    @Override
    public void postMessage(String nickname, String message, Current current) throws ChatException {
        if (nickname == null || !onlineUsers.contains(nickname)) {
            throw new ChatException("Acceso denegado: El usuario debe iniciar sesion primero.");
        }
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        appendMessage(nickname, message.trim());
        System.out.println("[" + LocalTime.now().format(timeFormat) + "] <" + nickname + "> " + message.trim());
    }

    @Override
    public ChatMessage[] getPendingMessages(String nickname, long lastMessageId, Current current) {
        List<ChatMessage> pending = new ArrayList<>();
        for (ChatMessage msg : messageHistory) {
            if (msg.id > lastMessageId) {
                pending.add(msg);
            }
        }
        return pending.toArray(new ChatMessage[0]);
    }

    @Override
    public String[] getOnlineUsers(Current current) {
        return onlineUsers.toArray(new String[0]);
    }

    @Override
    public synchronized void logout(String nickname, Current current) {
        if (nickname != null && onlineUsers.remove(nickname)) {
            System.out.println("[ICE-SERVER] Usuario desconectado: " + nickname);
            appendMessage("SISTEMA", nickname + " ha abandonado la sala.");
        }
    }
}