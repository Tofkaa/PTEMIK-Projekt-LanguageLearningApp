package com.languageapp.backend.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.languageapp.backend.security.RequestBudgetStore.budget;

@Service @RequiredArgsConstructor
public class ApiRequestLimiter {
    private final RequestBudgetStore store;
    public void request(RequestOperation operation,String ip,String account,boolean invitation) {
        var limits=new ArrayList<RequestBudgetStore.Budget>();
        // A single shared public budget also bounds the creation of arbitrary email/IP buckets.
        limits.add(budget("requests","global", "all",120000,60));
        if(account==null || operation.publicEndpoint()) limits.add(budget("public","global","all",6000,60));
        limits.add(budget(operation.name(),"ip",ip,operation==RequestOperation.CHECK?60000:2400,60));
        if(account!=null && !invitation && !operation.publicEndpoint()) {
            // A resend rejected during the short cooldown must not spend the hourly issuance budget.
            if(operation.mailIssue()) limits.add(budget(operation.name(),"cooldown",account,1,60));
            limits.add(accountBudget(operation,account));
        }
        store.check(limits);
    }
    public void anonymousAccount(RequestOperation operation,String identity) {
        // The same operation budget applies to known and unknown accounts, independent of existence.
        String key=identity==null?"":identity.trim().toLowerCase(Locale.ROOT);
        if(key.length()>320) throw new com.languageapp.backend.exception.BadRequestException("Érvénytelen azonosító.");
        var limits=new ArrayList<RequestBudgetStore.Budget>();
        if(operation==RequestOperation.RESET_REQUEST) limits.add(budget(operation.name(),"cooldown",key,1,60));
        limits.add(accountBudget(operation,key));
        store.check(limits);
    }
    public void account(RequestOperation operation,String identity) { store.check(List.of(accountBudget(operation,identity))); }
    private RequestBudgetStore.Budget accountBudget(RequestOperation operation,String identity) {
        return budget(operation.name(),"account",identity.trim().toLowerCase(Locale.ROOT),operation.capacity,operation.seconds);
    }
}
