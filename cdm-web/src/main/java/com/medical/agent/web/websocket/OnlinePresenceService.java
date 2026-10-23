package com.medical.agent.web.websocket;

import com.medical.agent.web.security.AuthenticatedUser;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OnlinePresenceService {

    private final SimpUserRegistry simpUserRegistry;

    public OnlinePresenceService(SimpUserRegistry simpUserRegistry) {
        this.simpUserRegistry = simpUserRegistry;
    }

    public Map<String, Object> getSnapshot() {
        long totalUsers = 0L;
        long totalSessions = 0L;
        long doctorUsers = 0L;
        long patientUsers = 0L;
        long adminUsers = 0L;
        long anonymousUsers = 0L;

        for (SimpUser user : simpUserRegistry.getUsers()) {
            if (user == null) {
                continue;
            }
            totalUsers++;
            totalSessions += countSessions(user);

            Principal principal = user.getPrincipal();
            if (principal instanceof AuthenticatedUser authenticatedUser) {
                if (authenticatedUser.isDoctor()) {
                    doctorUsers++;
                } else if (authenticatedUser.isPatient()) {
                    patientUsers++;
                } else if (authenticatedUser.isAdmin()) {
                    adminUsers++;
                } else {
                    anonymousUsers++;
                }
            } else {
                anonymousUsers++;
            }
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("totalUsers", totalUsers);
        snapshot.put("totalSessions", totalSessions);
        snapshot.put("doctorUsers", doctorUsers);
        snapshot.put("patientUsers", patientUsers);
        snapshot.put("adminUsers", adminUsers);
        snapshot.put("anonymousUsers", anonymousUsers);
        snapshot.put("messageChannelConnected", totalSessions > 0);
        return snapshot;
    }

    private long countSessions(SimpUser user) {
        long sessions = 0L;
        for (SimpSession ignored : user.getSessions()) {
            sessions++;
        }
        return sessions;
    }
}
