package com.pretriage.backend.adapters.email;

import com.pretriage.backend.services.ports.InvitationEmailPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pretriage.invitations.email.mode", havingValue = "local", matchIfMissing = true)
public class LocalInvitationEmailAdapter implements InvitationEmailPort {
    @Override
    public DeliveryResult deliver(InvitationEmailMessage message) {
        return new DeliveryResult(false, true);
    }
}
